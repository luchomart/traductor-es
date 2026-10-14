package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una pasada completa: escanear, combinar fuentes, traducir lo que falta y escribir el pack.
 * Se ejecuta en su propio hilo; la interfaz solo lee el estado.
 */
public class TranslationJob implements Runnable {
    public enum State { SCANNING, TRANSLATING, PAUSED, DONE, ERROR, CANCELLED }

    private static final String UNCHANGED = "devolvió el texto sin traducir";
    private static final Pattern LINE_BREAK = Pattern.compile("\\\\n|\\n");

    /** Una clave concreta que recibe una traducción. ns puede ser un mod o un libro ("@patchouli/mod/libro", "@mantle/mod/libro"). */
    private record Target(String ns, String key) {}

    /** Un texto único a traducir (puede servir a varias claves con el mismo texto). */
    private static final class Item {
        final String text, modNs, requestKey;
        final boolean isName;
        final List<Target> targets = new ArrayList<>();
        Item(String text, String modNs, String requestKey, boolean isName) {
            this.text = text; this.modNs = modNs; this.requestKey = requestKey; this.isName = isName;
        }
    }

    private static class FatalException extends Exception {
        /** El problema es de configuración (sin conexión, sin modelo, clave inválida): lo resuelve el asistente. */
        final boolean setup;
        FatalException(String msg, Throwable cause) { this(msg, cause, false); }
        FatalException(String msg, Throwable cause, boolean setup) { super(msg, cause); this.setup = setup; }
    }

    /** Cancelación pedida mientras se esperaba (para salir de lo que se esté haciendo). */
    private static class Cancelled extends RuntimeException {}

    private static final int CONNECT_RETRIES = 6;          // ~2 minutos esperando a que vuelva la conexión
    private static final int TRANSIENT_RETRIES = 5;
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    /** Lo que se escaneó de los recursos del juego. */
    private record Scan(Map<String, Map<String, Map<String, String>>> lang, List<Books.Book> books) {}

    // Estado visible desde la interfaz.
    public volatile State state = State.SCANNING;
    public volatile String message = "Buscando textos…";
    public volatile String currentNamespace = "";
    public volatile int total, done, failed;
    public volatile int official, fromVariants, fromCache, corrected, bookFiles;
    public volatile long translateStartMs, pausedMs;
    public volatile boolean packChanged;
    /** Se escribieron misiones nuevas: se ven al volver a entrar al mundo. */
    public volatile boolean questsChanged;
    /** Terminó con un error de configuración (lo puede resolver el asistente). */
    public volatile boolean setupProblem;
    /**
     * Espera compartida por todas las peticiones simultáneas: si el servicio pide bajar el ritmo, paran todas hasta
     * que se cumpla la espera más larga (si no, cada una mostraba su propia cuenta y las demás seguían insistiendo).
     */
    private volatile long pauseUntil;
    private volatile String pauseWhy;
    private final Object pauseLock = new Object();

    private volatile boolean pauseRequested, cancelRequested;

    /** Configuración de esta traducción. Si cambia sola de servicio o modelo, se reemplaza entera (nunca a medias). */
    private volatile Config cfg;
    private final Consumer<TranslationJob> onFinish;
    private volatile LlmClient client = new LlmClient();
    private final TranslationCache cache;
    /** Textos que fallaron varias veces seguidas con la misma IA: se dejan de mandar. */
    private final FailureLog failureLog;
    /** Servicio y modelo de esta traducción (se fija al empezar, después de elegir el modelo). */
    private volatile String aiId = "";
    /** Textos que no se mandaron porque ya fallaron {@link FailureLog#MAX_RUNS} veces con esta IA. */
    public volatile int skipped;
    /** Variante del español de destino (es_es, es_mx…) y las que se usan como respaldo. */
    public final String target;
    private final List<String> fallbacks;
    private final Map<String, Map<String, String>> output = new TreeMap<>();
    private final List<String> failures = new ArrayList<>();
    private final Map<String, int[]> perMod = new TreeMap<>(); // [traducidos, fallidos]
    private List<Books.Book> books = List.of();
    /** Misiones de FTB Quests del modpack, o null si no tiene (o no en el formato con archivos de idioma). */
    private volatile FtbQuests.Quests quests;
    public volatile int questTexts;
    private Glossary glossary;
    private long lastFlushMs;
    /** Motivo por el que no se pudo restituir una ficha, por clave de petición (para el reintento). */
    private final Map<String, String> maskErrors = new ConcurrentHashMap<>();

    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** Solo rearma el pack (por ejemplo, después de guardar una corrección): no le pide nada a la IA. */
    public final boolean applyOnly;
    private Map<String, Map<String, String>> corrections = Map.of();

    public TranslationJob(Config cfg, String target, Consumer<TranslationJob> onFinish) {
        this(cfg, target, onFinish, false);
    }

    public TranslationJob(Config cfg, String target, Consumer<TranslationJob> onFinish, boolean applyOnly) {
        this.applyOnly = applyOnly;
        this.cfg = cfg;
        this.target = target;
        this.fallbacks = SpanishTarget.fallbacks(target);
        this.cache = new TranslationCache(target);
        this.failureLog = new FailureLog(target);
        this.onFinish = onFinish;
    }

    /** La IA de esta traducción corre en esta PC (con la configuración con la que empezó). */
    public boolean usesLocalAi() { return cfg.isLocalAi(); }

    private long pauseStartMs;
    /** Candado propio de la pausa del jugador (no el de la traducción: se consulta desde cualquier hilo). */
    private final Object pauseStateLock = new Object();

    /** La pausa vale desde que se pide: las peticiones que estaban esperando ya no reintentan hasta reanudar. */
    public void pause() {
        synchronized (pauseStateLock) {
            if (pauseRequested) return;
            pauseRequested = true;
            pauseStartMs = System.currentTimeMillis();
        }
    }

    public void resume() {
        synchronized (pauseStateLock) {
            if (!pauseRequested) return;
            pausedMs += System.currentTimeMillis() - pauseStartMs;
            pauseRequested = false;
        }
    }

    public void cancel() {
        cancelRequested = true;
        resume();
    }

    public boolean isPauseRequested() { return pauseRequested; }

    /**
     * Va mucho más lento de lo normal para el servicio (menos de la mitad) y falta más de media hora.
     * Se mira después de 3 minutos traduciendo, para no avisar por un arranque lento.
     */
    public boolean isSlow() {
        if (state != State.TRANSLATING || isPaused() || translateStartMs == 0) return false;
        if (System.currentTimeMillis() - translateStartMs - totalPausedMs() < 180_000) return false;
        double rate = displayRate();
        int left = total - done;
        if (rate <= 0) return left > 0;
        return rate < ServiceGuide.current(cfg).perMinute() * 0.5 && left / rate > 30;
    }

    /**
     * El cambio automático se lee de la configuración del panel (no de la copia de esta traducción): si el jugador
     * lo apaga a mitad de camino, vale enseguida.
     */
    private boolean autoSwitchOn() {
        return Config.get().autoSwitch && !cfg.isLocalAi();
    }

    /** Si va lento, ¿lo va a resolver el cambio automático? */
    public boolean willAutoSwitch() {
        return autoSwitchOn() && !noAlternative;
    }

    /** Qué probar cuando va lento (para el panel y el aviso). */
    public String slowAdvice() {
        if (willAutoSwitch()) {
            return "Si sigue así, en unos minutos pasa sola a otro modelo o servicio gratis con clave.";
        }
        return usesLocalAi()
                ? "Un modelo más chico (por ejemplo translategemma:4b) o un servicio en la nube va más rápido."
                : "Cada modelo tiene su propio límite: probá otro en «Elegir…» o cambiá de servicio con «¿Cuál conviene?», y tocá «Usar este servicio».";
    }

    private volatile boolean slowNotified;

    /** Avisa una sola vez por traducción si va lento. */
    private void checkSlow() {
        if (slowNotified || applyOnly || !isSlow()) return;
        if (willAutoSwitch()) return; // lo resuelve el cambio automático
        slowNotified = true;
        String msg = "Va lento (~" + Math.round(displayRate()) + " textos/min). "
                + (usesLocalAi() ? "Probá un modelo más chico o la nube: mirá el panel." : "Probá con otro modelo: mirá el panel.");
        TraductorES.LOG.info("[Traductor ES] {}", msg);
        Minecraft.getInstance().execute(() -> Platform.toast(net.minecraft.network.chat.Component.literal("Traductor ES"),
                net.minecraft.network.chat.Component.literal(msg)));
    }

    /** Tiempo en pausa, contando la pausa actual. */
    public long totalPausedMs() {
        synchronized (pauseStateLock) {
            return pausedMs + (pauseRequested ? System.currentTimeMillis() - pauseStartMs : 0);
        }
    }

    /** En pausa para el jugador (aunque alguna petición en curso todavía esté terminando). */
    public boolean isPaused() { return pauseRequested && isRunning(); }

    /** Se cancela para seguir con otro servicio: no hace falta avisar que se canceló. */
    public volatile boolean restarting;

    /** Servicio y modelo con los que traduce esta pasada ("Groq · openai/gpt-oss-120b"). */
    public String aiLabel() {
        Preset p = Preset.fromUrl(cfg.apiUrl);
        String name = p == Preset.CUSTOM ? cfg.apiUrl.replaceFirst("^https?://", "").replaceFirst("/.*$", "")
                : p.label.replace(" (nube)", "").replace(" (en tu PC)", "");
        return cfg.model.isBlank() ? name : name + " · " + cfg.model;
    }

    /** Si esta pasada usa la misma IA que esa configuración (servicio, modelo y clave). */
    public boolean usesAi(Config c) {
        return c.apiUrl.equals(cfg.apiUrl) && c.apiKey.equals(cfg.apiKey)
                && (c.model.equals(cfg.model) || c.model.isBlank());
    }
    public boolean isRunning() { return state == State.SCANNING || state == State.TRANSLATING || state == State.PAUSED; }

    /** Guarda el caché a disco (por ejemplo, al cerrar el juego). */
    public void flushCache() { cache.flush(); }

    /** Lo que le queda a la clave en el servicio (Groq y OpenRouter), o null si no se sabe. */
    public String quota() {
        return client.lastQuota();
    }

    private volatile long quotaCheckedMs;
    /** Mayúsculas como el oficial (se aprende del Minecraft en español al escanear). */
    private Capitalization caps = Capitalization.DEFAULT;

    /** Tiempo estimado para esa cantidad de textos según el servicio de esta traducción (antes de tener datos reales). */
    public String guessDuration(int texts) {
        return ServiceGuide.estimate(cfg).duration(texts);
    }

    /** Textos por minuto desde que empezó la traducción, sin contar pausas. */
    public double ratePerMinute() {
        long elapsed = System.currentTimeMillis() - translateStartMs - totalPausedMs();
        return translateStartMs == 0 || elapsed < 1000 ? 0 : done * 60000.0 / elapsed;
    }

    @Override
    public void run() {
        try {
            if (cfg.model.isBlank() && !applyOnly) chooseModel(null, null);
            List<Item> pending = prepare();
            if (cancelRequested) { finish(State.CANCELLED, "Cancelado"); return; }
            if (applyOnly) { finish(State.DONE, "Correcciones aplicadas"); return; }
            if (pending.isEmpty()) {
                finish(State.DONE, "Todo traducido: no había textos nuevos");
                return;
            }
            translateAll(pending);
            if (cancelRequested) finish(State.CANCELLED, "Cancelado. Lo traducido se guardó y se retoma la próxima vez");
            else finish(State.DONE, failed == 0 ? "¡Traducción terminada!" : "Traducción terminada (" + failed + " textos quedaron sin traducir)");
        } catch (Cancelled e) {
            finish(State.CANCELLED, "Cancelado. Lo traducido se guardó y se retoma la próxima vez");
        } catch (FatalException e) {
            setupProblem = e.setup;
            Traductor.setConnection(Traductor.Connection.FAIL, e.getMessage());
            TraductorES.LOG.error("[Traductor ES] {}", e.getMessage(), e.getCause());
            finish(State.ERROR, e.getMessage());
        } catch (Throwable t) {
            TraductorES.LOG.error("[Traductor ES] Error inesperado", t);
            finish(State.ERROR, "Error inesperado: " + t);
        }
    }

    // ---------- Preparación ----------

    private final Map<String, Item> byText = new LinkedHashMap<>();
    /**
     * Ya se combinaron todas las fuentes (oficial, variantes, caché, correcciones). Antes de eso output está vacío:
     * escribir el pack lo dejaría sin nada (por ejemplo, al cancelar mientras busca textos o sin internet al elegir modelo).
     */
    private volatile boolean prepared;
    private final Set<String> usedRequestKeys = new HashSet<>();
    private int bookCounter;

    /** Decide la fuente de cada texto y devuelve lo que hay que mandar a la IA. */
    private List<Item> prepare() throws IOException {
        state = State.SCANNING;
        message = "Buscando textos en los mods…";
        aiId = FailureLog.aiId(cfg);
        Scan scan = scanOnMainThread();
        Map<String, Map<String, Map<String, String>>> all = scan.lang();
        if (cfg.translateBooks) addBookTexts(all);
        corrections = Corrections.load();

        Map<String, Map<String, String>> vanilla = all.getOrDefault("minecraft", Map.of());
        glossary = Glossary.create();
        glossary.addVanilla(vanilla.getOrDefault("en_us", Map.of()), vanilla.getOrDefault(target, Map.of()));
        caps = Capitalization.learn(vanilla.getOrDefault(target, Map.of()));

        Set<String> excluded = new HashSet<>(cfg.excludedNamespaces);
        for (Map.Entry<String, Map<String, Map<String, String>>> nsEntry : all.entrySet()) {
            String ns = nsEntry.getKey();
            if (excluded.contains(ns)) continue;
            Map<String, Map<String, String>> langs = nsEntry.getValue();
            Map<String, String> source = sourceLanguage(langs);
            Map<String, String> es = langs.getOrDefault(target, Map.of());
            Map<String, String> corr = corrections.getOrDefault(ns, Map.of());
            Map<String, String> nsOut = output.computeIfAbsent(ns, k -> new TreeMap<>());
            nsOut.putAll(corr);
            corrected += corr.size();

            for (Map.Entry<String, String> kv : new TreeMap<>(source).entrySet()) {
                String key = kv.getKey(), text = kv.getValue();
                if (corr.containsKey(key)) { glossary.addModName(ns, key, text, corr.get(key)); continue; }
                // Si el "oficial" es el inglés copiado, se traduce como si no existiera.
                if (es.containsKey(key) && !Validator.isEnglishCopy(text, es.get(key))) {
                    official++; glossary.addModName(ns, key, text, es.get(key)); continue;
                }
                String variant = cfg.useSpanishVariants ? fromVariant(langs, key, text) : null;
                if (variant != null) { nsOut.put(key, variant); fromVariants++; glossary.addModName(ns, key, text, variant); continue; }
                if (Validator.isTrivial(text)) continue;
                String cached = usableCache(ns, key, text);
                if (cached != null) {
                    nsOut.put(key, cached); fromCache++; glossary.addModName(ns, key, text, cached); continue;
                }
                enqueue(ns, ns, key, key, text, Glossary.NAME_KEY.matcher(key).matches());
            }
        }
        // Correcciones de namespaces que no aparecieron en el escaneo.
        corrections.forEach((ns, corr) -> {
            if (!all.containsKey(ns)) { output.computeIfAbsent(ns, k -> new TreeMap<>()).putAll(corr); corrected += corr.size(); }
        });

        if (cfg.translateBooks) {
            prepareBooks(scan.books());
            prepareQuests();
        }
        glossary.sort();

        packChanged |= writePack();
        prepared = true;
        List<Item> pending = new ArrayList<>(byText.values());
        // Primero los nombres: después se usan como glosario en descripciones y libros.
        pending.sort(Comparator.comparing((Item it) -> !it.isName));
        total = pending.size();
        TraductorES.LOG.info("[Traductor ES] Glosario: {} términos. Oficiales: {}, variantes: {}, caché: {}, correcciones: {}, archivos de libros: {}, textos de misiones: {}, omitidos por fallar: {}, por traducir: {}",
                glossary.size(), official, fromVariants, fromCache, corrected, bookFiles, questTexts, skipped, total);
        return pending;
    }

    private void prepareBooks(List<Books.Book> scanned) {
        books = scanned;
        for (Books.Book book : books) {
            String pseudo = book.pseudoNamespace();
            Map<String, String> bookOut = output.computeIfAbsent(pseudo, k -> new TreeMap<>());
            fromVariants += book.fromVariant.size();
            official += book.official;
            bookFiles += book.english.size();
            for (Map.Entry<String, com.google.gson.JsonObject> file : book.english.entrySet()) {
                Books.extract(book.format, file.getKey(), file.getValue(), (pointer, text) -> {
                    if (Validator.isTrivial(text)) return;
                    String key = file.getKey() + "#" + pointer;
                    if (corrections.getOrDefault(pseudo, Map.of()).containsKey(key)) return; // ya está en la salida
                    String cached = usableCache(pseudo, key, text);
                    if (cached != null) { bookOut.put(key, cached); fromCache++; return; }
                    String field = pointer.substring(pointer.lastIndexOf('/') + 1);
                    enqueue(pseudo, book.ns, key, book.format.name().toLowerCase(Locale.ROOT) + "." + book.book + "." + field + "." + (++bookCounter), text, false);
                });
            }
        }
    }

    /**
     * Portadas de libros de Patchouli (book.json con "i18n"): el texto mismo es la clave de idioma. Se suman como
     * claves en inglés del mod, salvo que ya existan.
     */
    private void addBookTexts(Map<String, Map<String, Map<String, String>>> all) {
        Books.bookTexts(Minecraft.getInstance().gameDirectory.toPath().resolve("mods")).forEach((ns, texts) -> {
            Map<String, Map<String, String>> langs = all.computeIfAbsent(ns, k -> new LinkedHashMap<>());
            // Si el mod no tiene inglés pero sí otro idioma de origen, no se agrega: taparía ese idioma.
            if (!langs.isEmpty() && !langs.containsKey("en_us")) return;
            Map<String, String> en = langs.computeIfAbsent("en_us", k -> new HashMap<>());
            for (String text : texts) en.putIfAbsent(text, text);
        });
    }

    /** Misiones de FTB Quests: lo que no esté en el español oficial del modpack ni en otra variante. */
    private void prepareQuests() {
        FtbQuests.Quests q;
        try {
            q = FtbQuests.scan(target, cfg.useSpanishVariants ? fallbacks : List.of());
        } catch (Exception e) {
            // Un archivo de misiones que no se entiende no debe frenar el resto de la traducción.
            TraductorES.LOG.warn("[Traductor ES] No se pudieron leer las misiones de FTB Quests: {}", e.toString());
            return;
        }
        if (q == null) return;
        quests = q;
        official += q.official();
        fromVariants += q.fromVariant();
        Map<String, String> questOut = output.computeIfAbsent(FtbQuests.NS, k -> new TreeMap<>());
        FtbQuests.extract(q, (key, text) -> {
            if (Validator.isTrivial(text)) return;
            questTexts++;
            if (corrections.getOrDefault(FtbQuests.NS, Map.of()).containsKey(key)) return; // ya está en la salida
            String cached = usableCache(FtbQuests.NS, key, text);
            if (cached != null) { questOut.put(key, cached); fromCache++; return; }
            enqueue(FtbQuests.NS, "ftbquests", key, "ftbquests." + key, text, false);
        });
    }

    /** Traducción del caché si sigue valiendo: mismo original, marcadores bien y no "olvidada" sin traducir. */
    private String usableCache(String ns, String key, String text) {
        String cached = cache.get(ns, key, text);
        if (cached == null || Validator.check(text, cached) != null) return null;
        if (cfg.retryUnchanged && Validator.looksUntranslated(text, cached)) {
            if (!cache.isConfirmed(ns, key)) return null;
            // Se aceptó como nombre propio, pero hay textos parecidos del mismo mod traducidos: se revisa una vez más.
            if (!cache.wasReviewed(ns, key) && !cache.translatedLike(ns, text, 1).isEmpty()) return null;
        }
        // Traducciones anteriores con mayúsculas al estilo inglés: se corrigen sin volver a traducir.
        String fixed = caps.apply(text, cached);
        if (!fixed.equals(cached)) cache.updateText(ns, key, fixed);
        return fixed;
    }

    private void enqueue(String ns, String modNs, String key, String requestKey, String text, boolean isName) {
        if (failureLog.shouldSkip(ns, key, text, aiId)) { skipped++; return; } // siempre falla con esta IA
        Item item = byText.get(text);
        if (item == null) {
            String rk = requestKey;
            for (int n = 2; usedRequestKeys.contains(rk); n++) rk = requestKey + "~" + n;
            usedRequestKeys.add(rk);
            item = new Item(text, modNs, rk, isName);
            byText.put(text, item);
        }
        item.targets.add(new Target(ns, key));
    }

    /**
     * Lee los recursos en el hilo principal del juego y solo cuando no hay una recarga en curso:
     * así nunca se leen packs que se están cerrando o reemplazando.
     */
    private Scan scanOnMainThread() {
        Minecraft mc = Minecraft.getInstance();
        while (true) {
            if (cancelRequested) throw new Cancelled();
            CompletableFuture<Scan> f = new CompletableFuture<>();
            mc.execute(() -> {
                try {
                    if (mc.getOverlay() != null) { f.complete(null); return; } // recargando recursos
                    ResourceManager rm = mc.getResourceManager();
                    f.complete(new Scan(LangScanner.scan(rm),
                            cfg.translateBooks ? Books.scan(rm, target, cfg.useSpanishVariants ? fallbacks : List.of()) : List.of()));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            Scan result;
            try {
                result = f.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException re ? re : e;
            }
            if (result != null) return result;
            sleepQuietly(500);
        }
    }

    /** Inglés si existe; si no, el idioma con más textos que no sea español. */
    private static Map<String, String> sourceLanguage(Map<String, Map<String, String>> langs) {
        Map<String, String> en = langs.get("en_us");
        if (en != null) return en;
        Map<String, String> best = Map.of();
        for (Map.Entry<String, Map<String, String>> e : langs.entrySet()) {
            if (!e.getKey().startsWith("es_") && e.getValue().size() > best.size()) best = e.getValue();
        }
        return best;
    }

    private String fromVariant(Map<String, Map<String, String>> langs, String key, String english) {
        for (String v : fallbacks) {
            Map<String, String> m = langs.get(v);
            if (m != null && m.containsKey(key) && !Validator.isEnglishCopy(english, m.get(key))) return m.get(key);
        }
        return null;
    }

    /** Sincronizado: con peticiones simultáneas, accept/fail modifican output desde varios hilos. */
    private synchronized boolean writePack() throws IOException {
        boolean changed = PackWriter.write(output, target);
        changed |= Books.write(books, output, target);
        if (quests != null) {
            // Las misiones no están en el pack: FTB Quests las lee de su carpeta al cargar el mundo.
            questsChanged |= FtbQuests.write(quests, output.getOrDefault(FtbQuests.NS, Map.of()));
        }
        return changed;
    }

    // ---------- Traducción ----------

    private void translateAll(List<Item> pending) throws FatalException, IOException {
        state = State.TRANSLATING;
        message = "Traduciendo";
        translateStartMs = System.currentTimeMillis();
        lastFlushMs = translateStartMs;
        // Primero todos los nombres; recién después el resto, que los usa como glosario.
        runBatches(makeBatches(pending.stream().filter(it -> it.isName).toList()));
        glossary.sort();
        if (!cancelRequested) runBatches(makeBatches(pending.stream().filter(it -> !it.isName).toList()));
    }

    /** Agrupa textos del mismo mod en lotes que respeten el tamaño y los caracteres máximos. */
    private List<List<Item>> makeBatches(List<Item> items) {
        List<List<Item>> batches = new ArrayList<>();
        List<Item> batch = new ArrayList<>();
        int chars = 0;
        for (Item next : items) {
            if (!batch.isEmpty() && (batch.size() >= cfg.batchSize || chars + next.text.length() > cfg.batchMaxChars
                    || !next.modNs.equals(batch.get(0).modNs))) {
                batches.add(batch);
                batch = new ArrayList<>();
                chars = 0;
            }
            batch.add(next);
            chars += next.text.length();
        }
        if (!batch.isEmpty()) batches.add(batch);
        return batches;
    }

    /** Ejecuta los lotes, de a uno o varios a la vez según la configuración. */
    private void runBatches(List<List<Item>> batches) throws FatalException, IOException {
        int parallel = Math.max(1, cfg.parallelRequests);
        if (parallel == 1) {
            for (List<Item> batch : batches) {
                waitWhilePaused();
                if (cancelRequested) return;
                currentNamespace = batch.get(0).modNs;
                translateBatch(batch, 0);
                maybeFlush();
            }
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(parallel, r -> {
            Thread t = new Thread(r, "Traductor ES - petición");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        try {
            Iterator<List<Item>> it = batches.iterator();
            List<Future<?>> running = new ArrayList<>();
            while (it.hasNext() || !running.isEmpty()) {
                // Al cancelar (o «Usar este servicio») no se espera a las peticiones en curso: con un servicio saturado
                // pueden tardar minutos. Se cortan y esos lotes se traducen la próxima vez.
                if (cancelRequested && !running.isEmpty()) {
                    for (Future<?> f : running) f.cancel(true);
                    return;
                }
                // Con pausa no se mandan lotes nuevos; los que están en curso terminan.
                while (running.size() < parallel && it.hasNext() && !pauseRequested && !cancelRequested) {
                    List<Item> batch = it.next();
                    running.add(pool.submit(() -> {
                        currentNamespace = batch.get(0).modNs;
                        translateBatch(batch, 0);
                        return null;
                    }));
                }
                boolean progressed = false;
                for (Iterator<Future<?>> f = running.iterator(); f.hasNext(); ) {
                    Future<?> fut = f.next();
                    if (!fut.isDone()) continue;
                    f.remove();
                    progressed = true;
                    try {
                        fut.get();
                    } catch (ExecutionException e) {
                        Throwable c = e.getCause();
                        if (c instanceof FatalException fe) throw fe;
                        if (c instanceof RuntimeException re) throw re;
                        throw new IOException(c);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new FatalException("Traducción interrumpida", e);
                    }
                }
                if (running.isEmpty()) {
                    if (cancelRequested) return;
                    if (pauseRequested) waitWhilePaused();
                }
                maybeFlush();
                if (!progressed) sleepQuietly(50);
            }
        } finally {
            pool.shutdownNow();
            // Si se cortó por un error, las peticiones en curso todavía pueden anotar resultados: se espera que
            // terminen antes de escribir el informe y el pack (si no, se cruzan y la traducción puede quedar colgada).
            try {
                if (!pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    TraductorES.LOG.warn("[Traductor ES] Alguna petición no terminó a tiempo");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Cada minuto se guarda el caché y se actualiza el pack, por si el juego se cierra. */
    private void maybeFlush() throws IOException {
        monitorSpeed();
        checkSlow();
        if (System.currentTimeMillis() - lastFlushMs < 60_000) return;
        cache.flush();
        failureLog.save();
        packChanged |= writePack();
        lastFlushMs = System.currentTimeMillis();
    }

    /** Una petición a la IA. Los errores de conexión o de modelo inexistente detienen todo. */
    private Map<String, String> request(Map<String, String> req, String modNs, String note) throws FatalException, IOException {
        String prompt = Prompts.withGlossary(cfg.effectivePrompt(target), glossary.relevant(req.values(), modNs));
        if (note != null) prompt += "\n" + note;
        // Los marcadores viajan como fichas ⟦n⟧ para que la IA no pueda romperlos.
        Map<String, String> masked = new LinkedHashMap<>();
        Map<String, List<String>> tokens = new HashMap<>();
        req.forEach((k, v) -> {
            Masker.Masked mk = Masker.mask(v);
            masked.put(k, mk.text());
            tokens.put(k, mk.tokens());
        });

        Map<String, String> raw;
        Config c = cfg;
        String used = c.model;
        int connectFails = 0, transientFails = 0;
        while (true) {
            try {
                awaitPause();
                c = cfg; // pudo cambiar de servicio o modelo mientras esperaba
                used = c.model;
                raw = client.translate(c, prompt, masked);
                ModelStatus.ok(c.apiUrl, used);
                lastUsedModel.set(used);
                Traductor.setConnection(Traductor.Connection.OK, used);
                // OpenRouter no lo informa en cada respuesta: se consulta cada tanto (Groq se actualiza solo).
                if (Preset.fromUrl(c.apiUrl) == Preset.OPENROUTER && System.currentTimeMillis() - quotaCheckedMs > 120_000) {
                    quotaCheckedMs = System.currentTimeMillis();
                    client.quota(c);
                }
                consecutiveFailures.set(0);
                break;
            } catch (ConnectException | HttpConnectTimeoutException | java.net.UnknownHostException e) {
                // Ollama cerrado o reiniciándose, o se cortó internet: se espera un rato antes de rendirse.
                if (++connectFails > CONNECT_RETRIES) {
                    throw new FatalException("No se pudo conectar con la IA en " + cfg.apiUrl
                            + (cfg.isLocalAi() ? ". ¿Está abierto Ollama (o LM Studio)?" : ". ¿Hay conexión a internet?"), e, true);
                }
                backoff(Math.min(30, 5L << (connectFails - 1)), "Sin conexión");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FatalException("Traducción interrumpida", e);
            } catch (LlmClient.LlmException e) {
                // Retirado, sin plan gratis o sin cupo por horas: esperar no sirve, se sigue con otro modelo.
                ModelStatus.Info learned = ModelStatus.learn(c.apiUrl, used, e);
                if (learned != null && modelSwitches.get() < MAX_MODEL_SWITCHES) {
                    chooseModel(e, used);
                    continue;
                }
                if (e.isModelNotFound()) {
                    throw new FatalException("El servidor respondió 404: revisá que el modelo \"" + cfg.model + "\" exista y que la URL sea correcta", e, true);
                }
                if (e.isAuthError()) {
                    throw new FatalException("La IA rechazó la conexión: " + e.getMessage() + ". Revisá el campo Clave", e, true);
                }
                if (e.status == 402 && autoSwitchOn() && switchAway("se quedó sin saldo", used)) continue; // a uno gratis
                if (e.status == 402) {
                    // Sin saldo: reintentar no sirve, y cada intento vuelve a fallar igual.
                    throw new FatalException("El servicio no tiene saldo para seguir: " + e.getMessage()
                            + ". Elegí un modelo gratis (en OpenRouter terminan en \":free\") o cambiá de servicio", e, true);
                }
                if (e.isTransient() && ++transientFails <= TRANSIENT_RETRIES) {
                    // Lo que pide el servicio (Retry-After o "retry in 22s" en el mensaje); si no dice, cada vez más.
                    long asked = Math.max(e.retryAfterSeconds, ModelStatus.parseWait(e.getMessage()));
                    long wait = asked > 0 ? Math.min(asked + 1, 120) : Math.min(60, 5L << (transientFails - 1));
                    backoff(wait, e.status == 429 ? "Límite de la IA" : "IA ocupada");
                    continue;
                }
                throw countFailure(e);
            } catch (IOException e) {
                throw countFailure(e);
            }
        }

        Map<String, String> out = new HashMap<>();
        for (String k : req.keySet()) {
            String restored = Masker.unmask(raw.get(k), tokens.get(k));
            if (restored != null) out.put(k, restored);
            else if (raw.containsKey(k)) maskErrors.put(k, "fichas ⟦⟧ perdidas, repetidas o inventadas");
        }
        return out;
    }

    /** Modelo que respondió la última petición de este hilo (la traducción se guarda con el que la hizo de verdad). */
    private final ThreadLocal<String> lastUsedModel = new ThreadLocal<>();

    /** Cambios de modelo automáticos en esta traducción (tope para no dar vueltas si todo falla). */
    private final java.util.concurrent.atomic.AtomicInteger modelSwitches = new java.util.concurrent.atomic.AtomicInteger();
    private static final int MAX_MODEL_SWITCHES = 6;

    /**
     * Elige el modelo de la lista real del servicio (campo vacío o modelo retirado) y lo guarda en la configuración.
     * Sincronizado: con peticiones simultáneas, varias pueden descubrir a la vez que el modelo ya no existe.
     */
    private synchronized void chooseModel(LlmClient.LlmException cause, String failed) throws FatalException {
        // Con peticiones simultáneas, otra ya pudo haber cambiado de modelo.
        if (failed != null && !failed.equals(cfg.model)) return;
        String old = cfg.model;
        if (failed != null) modelSwitches.incrementAndGet();
        try {
            // Sin los retirados, sin los que no tienen plan gratis y sin los agotados.
            List<String> models = ModelStatus.usable(cfg.apiUrl, client.listModels(cfg));
            String pick = cause == null ? null : ModelPicker.suggestedReplacement(cause.getMessage());
            if (pick != null && ModelStatus.get(cfg.apiUrl, pick) != null) pick = null;
            if (pick == null || pick.equals(old) || (!models.isEmpty() && !models.contains(pick))) {
                List<String> others = new ArrayList<>(models);
                others.remove(old);
                others.removeIf(m -> triedAis.contains(cfg.apiUrl + " " + m)); // no volver a uno ya dejado por lento
                // Si se usaba un modelo gratis de OpenRouter, se sigue con uno gratis.
                pick = old.endsWith(":free") ? ModelPicker.recommendFree(others) : ModelPicker.recommend(Preset.fromUrl(cfg.apiUrl), others);
                if (pick != null && pick.equals(failed)) pick = null;
            }
            ModelStatus.Info exhausted = failed == null ? null : ModelStatus.get(cfg.apiUrl, failed);
            // Se acabó el cupo de un modelo rápido y en este servicio solo quedan más pesados (en Gemini, de «flash-lite»
            // a «flash»: cupo diario chico y a menudo saturados): primero otro servicio gratis con clave.
            if (pick != null && exhausted != null && exhausted.state() == ModelStatus.State.EXHAUSTED && autoSwitchOn()
                    && ModelPicker.kind(pick).ordinal() > ModelPicker.kind(failed).ordinal()
                    && switchAway("se acabó el cupo de " + failed + " y en este servicio quedan modelos más lentos", failed, true)) return;
            if (pick == null) {
                ModelStatus.Info why = exhausted;
                // No queda otro modelo en este servicio: se prueba con otro servicio gratis con clave.
                if (why != null && autoSwitchOn() && switchAway("se acabó el cupo de " + failed, failed)) return;
                if (why != null) {
                    throw new FatalException(why.explain(failed) + " No queda otro modelo disponible en este servicio: cambiá de servicio en el panel («¿Cuál conviene?»)", cause, true);
                }
                throw new FatalException("No hay un modelo elegido. Elegí uno en el panel (Probar conexión muestra los disponibles)", cause, true);
            }
            // Se reemplaza entera, como en switchAway: las peticiones en curso ven la anterior o la nueva, nunca a medias.
            Config next = cfg.snapshot();
            next.model = pick;
            cfg = next;
            // Los fallidos se anotan con el modelo que traduce ahora (si no, se omitirían o reintentarían de más).
            aiId = FailureLog.aiId(next);
            triedAis.add(aiId);
            // cfg es la copia de esta traducción: el modelo elegido se guarda en la configuración real solo si
            // el jugador no cambió de servicio ni de modelo mientras tanto.
            Config live = Config.get();
            if (live.apiUrl.equals(cfg.apiUrl) && (live.model.isBlank() || live.model.equals(old))) {
                live.model = pick;
                live.save();
            }
            TraductorES.LOG.info("[Traductor ES] Modelo elegido automáticamente: {}{}", pick, old.isBlank() ? "" : " (antes: " + old + ")");
            ModelStatus.Info why = failed == null ? null : ModelStatus.get(cfg.apiUrl, failed);
            if (why != null) {
                String msg = (why.state() == ModelStatus.State.EXHAUSTED ? "Se acabó el cupo de " : "No se puede usar ") + failed + ". Sigue con " + pick + ".";
                Minecraft.getInstance().execute(() -> Platform.toast(net.minecraft.network.chat.Component.literal("Traductor ES"),
                        net.minecraft.network.chat.Component.literal(msg)));
            }
        } catch (FatalException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FatalException("Traducción interrumpida", e);
        } catch (IOException e) {
            throw new FatalException("No se pudo elegir un modelo: " + LlmClient.describe(e), e, true);
        }
    }

    /** Si la IA falla una y otra vez sin que sea algo pasajero, se corta en vez de mandar miles de peticiones inútiles. */
    private IOException countFailure(IOException e) throws FatalException {
        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= MAX_CONSECUTIVE_FAILURES) {
            throw new FatalException("La IA falló " + fails + " veces seguidas. Último error: " + LlmClient.describe(e), e);
        }
        return e;
    }

    // ---------- Cambio automático de modelo o servicio ----------

    private final Object switchLock = new Object();
    /** Servicio y modelo ya usados en esta traducción (no se vuelve a ellos). */
    private final Set<String> triedAis = ConcurrentHashMap.newKeySet();
    /** Muestras {tiempo activo en ms, textos hechos} para medir el ritmo reciente. */
    private final java.util.ArrayDeque<long[]> samples = new java.util.ArrayDeque<>();
    /** Desde cuándo (tiempo activo) se mide el ritmo: el arranque o el último cambio. */
    private volatile long windowStartMs;
    /** Ya se buscó a dónde cambiar y no había nada. */
    private volatile boolean noAlternative;
    private static final long SWITCH_WINDOW_MS = 180_000;

    /** Milisegundos traduciendo, sin contar pausas. */
    private long activeMs() {
        return System.currentTimeMillis() - translateStartMs - totalPausedMs();
    }

    /** Textos por minuto de los últimos 3 minutos, o -1 si todavía no hay 3 minutos medidos desde el arranque o el último cambio. */
    public double recentRate() {
        synchronized (samples) {
            if (translateStartMs == 0 || samples.isEmpty()) return -1;
            long now = activeMs();
            if (now - windowStartMs < SWITCH_WINDOW_MS) return -1;
            long[] old = samples.peekFirst();
            for (long[] smp : samples) {
                if (now - smp[0] <= SWITCH_WINDOW_MS) { old = smp; break; }
            }
            long span = now - old[0];
            return span < 60_000 ? -1 : (done - old[1]) * 60_000.0 / span;
        }
    }

    /** Ritmo para mostrar: el reciente si ya hay, si no el de toda la traducción. */
    public double displayRate() {
        double r = recentRate();
        return r >= 0 ? r : ratePerMinute();
    }

    /** Cada tanto: anota el ritmo y, si va muy lenta (o lenta con mucho por delante), cambia sola. */
    private void monitorSpeed() {
        if (state != State.TRANSLATING || isPaused() || applyOnly || translateStartMs == 0 || cancelRequested) return;
        long now = activeMs();
        synchronized (samples) {
            long[] last = samples.peekLast();
            if (last == null || now - last[0] >= 10_000) samples.addLast(new long[]{now, done});
            while (samples.size() > 2 && now - samples.peekFirst()[0] > SWITCH_WINDOW_MS + 30_000) samples.removeFirst();
        }
        if (!autoSwitchOn()) return; // apagado, o la IA está en la PC (cambiar sería mandar los textos a internet)
        double r = recentRate();
        if (r < 0) return;
        int left = total - done;
        String reason = null;
        if (r < SpeedLevel.SLOW.from) reason = "iba muy lenta";
        else if (r < SpeedLevel.NORMAL.from && r > 0 && left / r > 120) reason = "iba lenta y faltaban más de 2 horas";
        if (reason == null) return;
        if (!switchAway(reason + " (~" + Math.round(r) + " textos/min)", null)) {
            noAlternative = true;
            windowStartMs = now; // se vuelve a mirar en 3 minutos
        }
    }

    /**
     * Pasa a otro modelo del mismo servicio o, si no hay, a otro servicio gratis con clave. Nunca a uno de pago ni
     * de una IA en la PC a la nube. failedModel: el que se agotó (null si es por velocidad). Devuelve si cambió.
     */
    boolean switchAway(String reason, String failedModel) {
        return switchAway(reason, failedModel, false);
    }

    /** otherServiceOnly: no probar otro modelo del mismo servicio (ya se sabe que los que quedan no convienen). */
    private boolean switchAway(String reason, String failedModel, boolean otherServiceOnly) {
        synchronized (switchLock) {
            if (cancelRequested) return false; // se está cerrando (cancelada o «Usar este servicio»): no cambiar nada
            Config cur = cfg;
            if (failedModel != null && !failedModel.equals(cur.model)) return true; // otra petición ya cambió
            if (cur.isLocalAi() || modelSwitches.get() >= MAX_MODEL_SWITCHES) return false;
            triedAis.add(FailureLog.aiId(cur));
            Config next = otherServiceOnly ? null : sameServiceAlternative(cur);
            if (next == null) next = otherServiceAlternative(cur);
            if (next == null) {
                TraductorES.LOG.info("[Traductor ES] {}, pero no hay otro modelo ni servicio gratis con clave", reason);
                return false;
            }
            modelSwitches.incrementAndGet();
            String from = aiLabel();
            Config old = cur;
            cfg = next;
            if (!next.apiUrl.equals(old.apiUrl)) client = new LlmClient();
            aiId = FailureLog.aiId(next);
            triedAis.add(aiId);
            noAlternative = false;
            pauseUntil = 0; // la espera era del servicio o modelo anterior
            consecutiveFailures.set(0);
            // Con su propio candado: quien llama puede tener el de la traducción (no se cruzan).
            synchronized (samples) {
                samples.clear();
                windowStartMs = activeMs();
            }
            // La configuración del panel sigue el cambio, si el jugador no la tocó mientras tanto.
            Config live = Config.get();
            if (live.apiUrl.equals(old.apiUrl) && (live.model.isBlank() || live.model.equals(old.model))) {
                if (!live.apiUrl.equals(next.apiUrl)) live.applyPreset(Preset.fromUrl(next.apiUrl));
                live.model = next.model;
                live.save();
            }
            String msg = "Con " + from + " " + reason + ". Sigue con " + aiLabel() + ".";
            TraductorES.LOG.info("[Traductor ES] Cambio automático: {}", msg);
            Minecraft.getInstance().execute(() -> Platform.toast(net.minecraft.network.chat.Component.literal("Traductor ES"),
                    net.minecraft.network.chat.Component.literal(msg)));
            return true;
        }
    }

    /** Otro modelo gratis del mismo servicio que todavía no se usó, o null. */
    private Config sameServiceAlternative(Config cur) {
        Preset p = Preset.fromUrl(cur.apiUrl);
        if (!p.isCloud()) return null;
        String pick = pickModel(cur, p);
        if (pick == null) return null;
        Config next = cur.snapshot();
        next.model = pick;
        return next;
    }

    /** El primer servicio gratis en la nube (del más rápido al más lento) con clave y algún modelo sin usar, o null. */
    private Config otherServiceAlternative(Config cur) {
        Preset from = Preset.fromUrl(cur.apiUrl);
        Config live = Config.get();
        // Las claves compartidas al día: pudieron cargarse en otro modpack después de abrir este.
        Map<String, String> shared = live.shareKeys ? Shared.loadKeys() : Map.of();
        for (Preset p : new Preset[]{Preset.GROQ, Preset.GEMINI, Preset.OPENROUTER}) {
            if (p == from) continue;
            String key = shared.getOrDefault(p.name(), live.apiKeys.getOrDefault(p.name(), cur.apiKeys.getOrDefault(p.name(), "")));
            if (key == null || key.isBlank()) continue;
            Config next = cur.snapshot();
            next.apiUrl = p.url;
            next.apiKey = key;
            next.model = "";
            if (p.parallel > 0) next.parallelRequests = p.parallel;
            String pick = pickModel(next, p);
            if (pick == null) continue;
            next.model = pick;
            return next;
        }
        return null;
    }

    /**
     * Un modelo gratis del servicio que sirva para traducir, sin los ya usados ni los agotados, retirados o sin
     * plan gratis. En OpenRouter, solo los ":free" (nunca se pasa solo a uno de pago).
     */
    private String pickModel(Config c, Preset p) {
        try {
            List<String> models = new ArrayList<>(ModelStatus.usable(c.apiUrl, new LlmClient().listModels(c)));
            models.removeIf(m -> triedAis.contains(c.apiUrl + " " + m) || ModelPicker.weakReason(m) != null
                    || ModelPicker.kind(m) == ModelPicker.Kind.PREVIEW);
            if (p == Preset.OPENROUTER) return ModelPicker.recommendFree(models);
            return models.isEmpty() ? null : ModelPicker.recommend(p, models);
        } catch (Exception e) {
            TraductorES.LOG.info("[Traductor ES] No se pudo ver los modelos de {}: {}", p.label, LlmClient.describe(e));
            return null;
        }
    }

    /** Espera antes de reintentar, mostrando el motivo (corto: entra en el aviso de la esquina). Se puede cancelar mientras tanto. */
    private void backoff(long seconds, String why) {
        long until = System.currentTimeMillis() + seconds * 1000;
        synchronized (pauseLock) {
            if (until > pauseUntil) {
                TraductorES.LOG.info("[Traductor ES] {}: se reintenta en {} s", why, seconds);
                pauseWhy = why;
                pauseUntil = until;
            }
        }
        awaitPause();
    }

    /** Espera a que termine la pausa compartida (límite de la IA) y la del jugador, si hay. */
    private void awaitPause() {
        while (System.currentTimeMillis() < pauseUntil || pauseRequested) {
            // Interrumpida: la traducción se está cerrando (sin esto, la espera giraría sin dormir hasta el final).
            if (cancelRequested || Thread.currentThread().isInterrupted()) throw new Cancelled();
            sleepQuietly(250);
        }
    }

    /** Motivo y segundos de la espera actual antes de reintentar, o null si no se está esperando. */
    public String waitingMessage() {
        long left = pauseUntil - System.currentTimeMillis();
        return left > 0 ? pauseWhy + " · " + (left + 999) / 1000 + " s" : null;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Traduce un lote; lo que falla se reintenta de a uno y, si falla el lote entero, se parte en dos. */
    private void translateBatch(List<Item> batch, int depth) throws FatalException {
        Map<String, String> req = new LinkedHashMap<>();
        for (Item it : batch) req.put(it.requestKey, it.text);
        Map<String, String> result;
        try {
            result = request(req, batch.get(0).modNs, null);
        } catch (IOException e) {
            if (batch.size() > 1) {
                int mid = batch.size() / 2;
                translateBatch(batch.subList(0, mid), depth + 1);
                translateBatch(batch.subList(mid, batch.size()), depth + 1);
            } else {
                translateSingle(batch.get(0), e.getMessage(), 1);
            }
            return;
        }

        List<Item> retry = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Item it : batch) {
            String t = result.get(it.requestKey);
            String why = t == null ? maskErrors.remove(it.requestKey) : null;
            if (why == null) why = Validator.check(it.text, t);
            if (why == null && cfg.retryUnchanged && Validator.looksUntranslated(it.text, t)) why = UNCHANGED;
            if (why == null) accept(it, t);
            else { retry.add(it); reasons.add(why); }
        }
        for (int k = 0; k < retry.size(); k++) translateSingle(retry.get(k), reasons.get(k), 1);
    }

    /** Reintento de un solo texto, explicándole a la IA qué salió mal la vez anterior. */
    private void translateSingle(Item it, String reason, int attempt) throws FatalException {
        if (Validator.NEWLINES.equals(reason) && hasLineBreaks(it.text)) {
            String joined = translateByLines(it);
            if (joined != null) accept(it, joined);
            else fail(it, reason);
            return;
        }
        List<String[]> examples = UNCHANGED.equals(reason) ? similarTranslated(it) : List.of();
        String note = UNCHANGED.equals(reason)
                ? "ATENCIÓN: en el intento anterior devolviste este texto sin traducir. Tradúcelo al " + SpanishTarget.describe(target) + " salvo que sea un nombre propio o una marca; en ese caso devuélvelo igual."
                        + examplesNote(examples)
                : "ATENCIÓN: en el intento anterior la traducción de este texto fue rechazada (" + reason + "). Corrígelo y conserva exactamente los marcadores del original.";
        String t, why;
        try {
            t = request(Map.of(it.requestKey, it.text), it.modNs, note).get(it.requestKey);
            why = t == null ? maskErrors.remove(it.requestKey) : null;
            if (why == null) why = Validator.check(it.text, t);
        } catch (IOException e) {
            t = null;
            why = e.getMessage();
        }
        if (why == null) {
            accept(it, t); // un "sin cambios" en el reintento se acepta: es un nombre propio
            if (!examples.isEmpty()) for (Target tg : it.targets) cache.markReviewed(tg.ns(), tg.key());
        }
        else if (attempt < 2) translateSingle(it, why, attempt + 1);
        else if (hasLineBreaks(it.text) && !Validator.NEWLINES.equals(reason)) {
            String joined = translateByLines(it);
            if (joined != null) accept(it, joined);
            else fail(it, why);
        } else fail(it, why);
    }

    /** Textos ya traducidos del mismo mod que comparten palabras con este (para el reintento de un "sin cambios"). */
    private List<String[]> similarTranslated(Item it) {
        return it.targets.isEmpty() ? List.of() : cache.translatedLike(it.targets.get(0).ns(), it.text, 4);
    }

    private static String examplesNote(List<String[]> examples) {
        if (examples.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" Otros textos de este mismo mod, con las mismas palabras, ya se tradujeron así: ");
        for (int i = 0; i < examples.size(); i++) {
            sb.append(i > 0 ? "; " : "").append('«').append(examples.get(i)[0]).append("» → «").append(examples.get(i)[1]).append('»');
        }
        return sb.append(". Así que no es un nombre propio: tradúcelo de la misma forma.").toString();
    }

    private static boolean hasLineBreaks(String s) {
        return LINE_BREAK.matcher(s).find();
    }

    /** Parte el texto por saltos de línea, traduce cada renglón y lo vuelve a unir con los saltos originales. */
    private String translateByLines(Item it) throws FatalException {
        List<String> segments = new ArrayList<>(), breaks = new ArrayList<>();
        Matcher m = LINE_BREAK.matcher(it.text);
        int last = 0;
        while (m.find()) {
            segments.add(it.text.substring(last, m.start()));
            breaks.add(m.group());
            last = m.end();
        }
        segments.add(it.text.substring(last));

        Map<String, String> req = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            String seg = segments.get(i);
            if (!seg.isBlank() && !Validator.isTrivial(seg)) req.put("linea" + (i + 1), seg.strip());
        }
        Map<String, String> res = new HashMap<>();
        if (!req.isEmpty()) {
            try {
                res.putAll(request(req, it.modNs, "Cada valor es un renglón de un mismo texto. Tradúcelos sin unirlos ni agregar saltos de línea."));
            } catch (IOException ignored) {
                // se reintenta renglón por renglón abajo
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            String seg = segments.get(i), id = "linea" + (i + 1);
            String out = seg;
            if (req.containsKey(id)) {
                String t = res.get(id);
                if (Validator.check(req.get(id), t) != null) {
                    try {
                        t = request(Map.of(id, req.get(id)), it.modNs, null).get(id);
                    } catch (IOException e) {
                        return null;
                    }
                    if (Validator.check(req.get(id), t) != null) return null;
                }
                // conserva los espacios del principio y del final del renglón original
                int lead = seg.length() - seg.stripLeading().length(), trail = seg.length() - seg.stripTrailing().length();
                out = seg.substring(0, lead) + t + seg.substring(seg.length() - trail);
            }
            sb.append(out);
            if (i < breaks.size()) sb.append(breaks.get(i));
        }
        String joined = sb.toString();
        return Validator.check(it.text, joined) == null ? joined : null;
    }

    private synchronized void accept(Item it, String translation) {
        translation = caps.apply(it.text, translation);
        for (Target t : it.targets) {
            failureLog.succeeded(t.ns(), t.key());
            String model = lastUsedModel.get();
            cache.put(t.ns(), t.key(), it.text, translation, Validator.looksUntranslated(it.text, translation), model != null ? model : cfg.model);
            output.computeIfAbsent(t.ns(), k -> new TreeMap<>()).put(t.key(), translation);
            if (it.isName) glossary.addModName(t.ns(), t.key(), it.text, translation);
        }
        perMod.computeIfAbsent(it.modNs, k -> new int[2])[0]++;
        done++;
    }

    private synchronized void fail(Item it, String why) {
        // Cortada al cancelar: no es culpa del texto (si se anotara, se saltearía en las próximas traducciones).
        if (cancelRequested) return;
        failed++;
        done++;
        perMod.computeIfAbsent(it.modNs, k -> new int[2])[1]++;
        for (Target tg : it.targets) failureLog.failed(tg.ns(), tg.key(), it.text, aiId);
        Target t = it.targets.get(0);
        failures.add(t.ns() + ":" + t.key() + " — " + why + " — \"" + abbreviate(it.text) + "\"");
    }

    private void waitWhilePaused() {
        if (!pauseRequested) return;
        state = State.PAUSED;
        cache.flush();
        while (pauseRequested && !cancelRequested) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        state = State.TRANSLATING;
    }

    private void finish(State end, String msg) {
        cache.flush();
        failureLog.save();
        if (prepared) {
            try {
                packChanged |= writePack();
            } catch (IOException e) {
                TraductorES.LOG.error("[Traductor ES] No se pudo escribir el pack", e);
            }
        }
        if (!applyOnly) writeReport(msg); // el informe es de la última traducción, no de aplicar correcciones
        message = msg;
        currentNamespace = "";
        state = end;
        if (end != State.ERROR) TraductorES.LOG.info("[Traductor ES] {}", msg); // los errores ya se registraron
        onFinish.accept(this);
    }

    private void writeReport(String msg) {
        List<String> failures;
        Map<String, int[]> perMod;
        synchronized (this) { // copias: accept y fail los modifican con este mismo candado
            failures = new ArrayList<>(this.failures);
            perMod = new TreeMap<>(this.perMod);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Traductor ES — informe\n");
        sb.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append('\n');
        sb.append("Idioma de destino: ").append(target).append(" (").append(SpanishTarget.describe(target)).append(")\n");
        sb.append("Resultado: ").append(msg).append("\n\n");
        sb.append("Textos con español oficial (no se tocan): ").append(official).append('\n');
        sb.append("Copiados de otra variante del español: ").append(fromVariants).append('\n');
        sb.append("Correcciones manuales aplicadas: ").append(corrected).append('\n');
        sb.append("Reutilizados del caché: ").append(fromCache).append('\n');
        sb.append("Archivos de libros con textos por traducir: ").append(bookFiles).append('\n');
        sb.append("Textos de misiones (FTB Quests) sin español: ").append(questTexts)
                .append(quests == null ? " (el modpack no tiene misiones con archivos de idioma)" : "").append('\n');
        sb.append("Textos únicos enviados a la IA: ").append(done).append(" de ").append(total).append('\n');
        sb.append("Fallidos (quedan en el idioma original): ").append(failed).append('\n');
        sb.append("Omitidos (fallaron ").append(FailureLog.MAX_RUNS).append(" veces con esta IA; se reintentan si cambiás de modelo o de servicio): ")
                .append(skipped).append("\n\n");
        if (!perMod.isEmpty()) {
            sb.append("Por mod (traducidos / fallidos):\n");
            perMod.forEach((ns, c) -> sb.append("  ").append(ns).append(": ").append(c[0]).append(" / ").append(c[1]).append('\n'));
            sb.append('\n');
        }
        if (!failures.isEmpty()) {
            sb.append("Textos fallidos:\n");
            int n = 0;
            for (String l : failures) {
                if (n++ >= 1000) { sb.append("… y ").append(failures.size() - 1000).append(" más\n"); break; }
                sb.append(l).append('\n');
            }
        }
        try {
            Files.createDirectories(Config.DIR);
            Files.writeString(Config.DIR.resolve("informe.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo escribir informe.txt", e);
        }
    }

    private static String abbreviate(String s) {
        String one = s.replace("\n", "\\n");
        return one.length() <= 120 ? one : one.substring(0, 120) + "…";
    }
}
