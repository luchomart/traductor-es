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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    /** Una clave concreta que recibe una traducción. ns puede ser un mod o un libro ("@patchouli/mod/libro"). */
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
        FatalException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** Cancelación pedida mientras se esperaba (para salir de lo que se esté haciendo). */
    private static class Cancelled extends RuntimeException {}

    private static final int CONNECT_RETRIES = 6;          // ~2 minutos esperando a que vuelva la conexión
    private static final int TRANSIENT_RETRIES = 5;
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    /** Lo que se escaneó de los recursos del juego. */
    private record Scan(Map<String, Map<String, Map<String, String>>> lang, List<PatchouliBooks.Book> books) {}

    // Estado visible desde la interfaz.
    public volatile State state = State.SCANNING;
    public volatile String message = "Buscando textos…";
    public volatile String currentNamespace = "";
    public volatile int total, done, failed;
    public volatile int official, fromVariants, fromCache, corrected, bookFiles;
    public volatile long translateStartMs, pausedMs;
    public volatile boolean packChanged;
    /** Motivo de la espera actual antes de reintentar (null si no se está esperando). */
    public volatile String waitingMessage;

    private volatile boolean pauseRequested, cancelRequested;

    private final Config cfg;
    private final Consumer<TranslationJob> onFinish;
    private final LlmClient client = new LlmClient();
    private final TranslationCache cache = new TranslationCache();
    private final Map<String, Map<String, String>> output = new TreeMap<>();
    private final List<String> failures = new ArrayList<>();
    private final Map<String, int[]> perMod = new TreeMap<>(); // [traducidos, fallidos]
    private List<PatchouliBooks.Book> books = List.of();
    private Glossary glossary;
    private long lastFlushMs;
    /** Motivo por el que no se pudo restituir una ficha, por clave de petición (para el reintento). */
    private final Map<String, String> maskErrors = new HashMap<>();

    private int consecutiveFailures;

    public TranslationJob(Config cfg, Consumer<TranslationJob> onFinish) {
        this.cfg = cfg;
        this.onFinish = onFinish;
    }

    public void pause() { pauseRequested = true; }
    public void resume() { pauseRequested = false; }
    public void cancel() { cancelRequested = true; pauseRequested = false; }
    public boolean isPauseRequested() { return pauseRequested; }
    public boolean isRunning() { return state == State.SCANNING || state == State.TRANSLATING || state == State.PAUSED; }

    /** Guarda el caché a disco (por ejemplo, al cerrar el juego). */
    public void flushCache() { cache.flush(); }

    /** Textos por minuto desde que empezó la traducción, sin contar pausas. */
    public double ratePerMinute() {
        long elapsed = System.currentTimeMillis() - translateStartMs - pausedMs;
        return translateStartMs == 0 || elapsed < 1000 ? 0 : done * 60000.0 / elapsed;
    }

    @Override
    public void run() {
        try {
            List<Item> pending = prepare();
            if (cancelRequested) { finish(State.CANCELLED, "Cancelado"); return; }
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
            TraductorES.LOG.error("[Traductor ES] {}", e.getMessage(), e.getCause());
            finish(State.ERROR, e.getMessage());
        } catch (Throwable t) {
            TraductorES.LOG.error("[Traductor ES] Error inesperado", t);
            finish(State.ERROR, "Error inesperado: " + t);
        }
    }

    // ---------- Preparación ----------

    private final Map<String, Item> byText = new LinkedHashMap<>();
    private final Set<String> usedRequestKeys = new HashSet<>();
    private int bookCounter;

    /** Decide la fuente de cada texto y devuelve lo que hay que mandar a la IA. */
    private List<Item> prepare() throws IOException {
        state = State.SCANNING;
        message = "Buscando textos en los mods…";
        Scan scan = scanOnMainThread();
        Map<String, Map<String, Map<String, String>>> all = scan.lang();
        Map<String, Map<String, String>> corrections = Corrections.load();

        Map<String, Map<String, String>> vanilla = all.getOrDefault("minecraft", Map.of());
        glossary = Glossary.create();
        glossary.addVanilla(vanilla.getOrDefault("en_us", Map.of()), vanilla.getOrDefault("es_es", Map.of()));

        Set<String> excluded = new HashSet<>(cfg.excludedNamespaces);
        for (Map.Entry<String, Map<String, Map<String, String>>> nsEntry : all.entrySet()) {
            String ns = nsEntry.getKey();
            if (excluded.contains(ns)) continue;
            Map<String, Map<String, String>> langs = nsEntry.getValue();
            Map<String, String> source = sourceLanguage(langs);
            Map<String, String> es = langs.getOrDefault("es_es", Map.of());
            Map<String, String> corr = corrections.getOrDefault(ns, Map.of());
            Map<String, String> nsOut = output.computeIfAbsent(ns, k -> new TreeMap<>());
            nsOut.putAll(corr);
            corrected += corr.size();

            for (Map.Entry<String, String> kv : new TreeMap<>(source).entrySet()) {
                String key = kv.getKey(), text = kv.getValue();
                if (corr.containsKey(key)) { glossary.addModName(ns, key, text, corr.get(key)); continue; }
                if (es.containsKey(key)) { official++; glossary.addModName(ns, key, text, es.get(key)); continue; }
                String variant = cfg.useSpanishVariants ? fromVariant(langs, key) : null;
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

        if (cfg.translateBooks) prepareBooks(scan.books());
        glossary.sort();

        packChanged |= writePack();
        List<Item> pending = new ArrayList<>(byText.values());
        // Primero los nombres: después se usan como glosario en descripciones y libros.
        pending.sort(Comparator.comparing((Item it) -> !it.isName));
        total = pending.size();
        TraductorES.LOG.info("[Traductor ES] Glosario: {} términos. Oficiales: {}, variantes: {}, caché: {}, correcciones: {}, archivos de libros: {}, por traducir: {}",
                glossary.size(), official, fromVariants, fromCache, corrected, bookFiles, total);
        return pending;
    }

    private void prepareBooks(List<PatchouliBooks.Book> scanned) {
        books = scanned;
        for (PatchouliBooks.Book book : books) {
            String pseudo = book.pseudoNamespace();
            Map<String, String> bookOut = output.computeIfAbsent(pseudo, k -> new TreeMap<>());
            fromVariants += book.fromVariant.size();
            official += book.official;
            bookFiles += book.english.size();
            for (Map.Entry<String, com.google.gson.JsonObject> file : book.english.entrySet()) {
                PatchouliBooks.extract(file.getValue(), "", (pointer, text) -> {
                    if (Validator.isTrivial(text)) return;
                    String key = file.getKey() + "#" + pointer;
                    String cached = usableCache(pseudo, key, text);
                    if (cached != null) { bookOut.put(key, cached); fromCache++; return; }
                    String field = pointer.substring(pointer.lastIndexOf('/') + 1);
                    enqueue(pseudo, book.ns, key, "patchouli." + book.book + "." + field + "." + (++bookCounter), text, false);
                });
            }
        }
    }

    /** Traducción del caché si sigue valiendo: mismo original, marcadores bien y no "olvidada" sin traducir. */
    private String usableCache(String ns, String key, String text) {
        String cached = cache.get(ns, key, text);
        if (cached == null || Validator.check(text, cached) != null) return null;
        if (cfg.retryUnchanged && Validator.looksUntranslated(text, cached) && !cache.isConfirmed(ns, key)) return null;
        return cached;
    }

    private void enqueue(String ns, String modNs, String key, String requestKey, String text, boolean isName) {
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
                            cfg.translateBooks ? PatchouliBooks.scan(rm, cfg.spanishVariants) : List.of()));
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

    private String fromVariant(Map<String, Map<String, String>> langs, String key) {
        for (String v : cfg.spanishVariants) {
            Map<String, String> m = langs.get(v);
            if (m != null && m.containsKey(key)) return m.get(key);
        }
        return null;
    }

    private boolean writePack() throws IOException {
        boolean changed = PackWriter.write(output);
        changed |= PatchouliBooks.write(books, output);
        return changed;
    }

    // ---------- Traducción ----------

    private void translateAll(List<Item> pending) throws FatalException, IOException {
        state = State.TRANSLATING;
        translateStartMs = System.currentTimeMillis();
        lastFlushMs = translateStartMs;
        boolean namesPhase = true;
        int i = 0;
        while (i < pending.size()) {
            waitWhilePaused();
            if (cancelRequested) break;
            if (namesPhase && !pending.get(i).isName) {
                namesPhase = false;
                glossary.sort(); // los nombres recién traducidos pasan a estar ordenados en el glosario
            }

            List<Item> batch = new ArrayList<>();
            int chars = 0;
            while (i < pending.size() && batch.size() < cfg.batchSize) {
                Item next = pending.get(i);
                if (!batch.isEmpty() && (chars + next.text.length() > cfg.batchMaxChars
                        || !next.modNs.equals(batch.get(0).modNs) || next.isName != batch.get(0).isName)) break;
                batch.add(next);
                chars += next.text.length();
                i++;
            }
            currentNamespace = batch.get(0).modNs;
            message = "Traduciendo";
            translateBatch(batch, 0);

            if (System.currentTimeMillis() - lastFlushMs > 60_000) {
                cache.flush();
                packChanged |= writePack();
                lastFlushMs = System.currentTimeMillis();
            }
        }
    }

    /** Una petición a la IA. Los errores de conexión o de modelo inexistente detienen todo. */
    private Map<String, String> request(Map<String, String> req, String modNs, String note) throws FatalException, IOException {
        String prompt = Prompts.withGlossary(cfg.effectivePrompt(), glossary.relevant(req.values(), modNs));
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
        int connectFails = 0, transientFails = 0;
        while (true) {
            try {
                raw = client.translate(cfg, prompt, masked);
                waitingMessage = null;
                consecutiveFailures = 0;
                break;
            } catch (ConnectException | HttpConnectTimeoutException e) {
                // Ollama cerrado o reiniciándose: se espera un rato antes de rendirse.
                if (++connectFails > CONNECT_RETRIES) {
                    throw new FatalException("No se pudo conectar con la IA en " + cfg.apiUrl + ". ¿Está Ollama encendido?", e);
                }
                backoff(Math.min(30, 5L << (connectFails - 1)), "No hay conexión con la IA, reintentando");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FatalException("Traducción interrumpida", e);
            } catch (LlmClient.LlmException e) {
                if (e.isModelNotFound()) {
                    throw new FatalException("El servidor respondió 404: revisá que el modelo \"" + cfg.model + "\" exista y que la URL sea correcta", e);
                }
                if (e.status == 401 || e.status == 403) {
                    throw new FatalException("La API rechazó la clave (HTTP " + e.status + "). Revisá el campo Clave", e);
                }
                if (e.isTransient() && ++transientFails <= TRANSIENT_RETRIES) {
                    long wait = e.retryAfterSeconds > 0 ? Math.min(e.retryAfterSeconds, 120) : Math.min(60, 5L << (transientFails - 1));
                    backoff(wait, e.status == 429 ? "La IA pidió bajar el ritmo" : "La IA está ocupada");
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

    /** Si la IA falla una y otra vez sin que sea algo pasajero, se corta en vez de mandar miles de peticiones inútiles. */
    private IOException countFailure(IOException e) throws FatalException {
        waitingMessage = null;
        if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            throw new FatalException("La IA falló " + consecutiveFailures + " veces seguidas. Último error: " + LlmClient.describe(e), e);
        }
        return e;
    }

    /** Espera antes de reintentar, mostrando el motivo. Se puede cancelar mientras tanto. */
    private void backoff(long seconds, String why) {
        long until = System.currentTimeMillis() + seconds * 1000;
        while (System.currentTimeMillis() < until) {
            if (cancelRequested) throw new Cancelled();
            waitingMessage = why + " (" + Math.max(1, (until - System.currentTimeMillis() + 999) / 1000) + " s)";
            sleepQuietly(250);
        }
        waitingMessage = null;
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
        String note = UNCHANGED.equals(reason)
                ? "ATENCIÓN: en el intento anterior devolviste este texto sin traducir. Tradúcelo al español de España salvo que sea un nombre propio o una marca; en ese caso devuélvelo igual."
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
        if (why == null) accept(it, t); // un "sin cambios" en el reintento se acepta: es un nombre propio
        else if (attempt < 2) translateSingle(it, why, attempt + 1);
        else if (hasLineBreaks(it.text) && !Validator.NEWLINES.equals(reason)) {
            String joined = translateByLines(it);
            if (joined != null) accept(it, joined);
            else fail(it, why);
        } else fail(it, why);
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
        try {
            res.putAll(request(req, it.modNs, "Cada valor es un renglón de un mismo texto. Tradúcelos sin unirlos ni agregar saltos de línea."));
        } catch (IOException ignored) {
            // se reintenta renglón por renglón abajo
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

    private void accept(Item it, String translation) {
        for (Target t : it.targets) {
            cache.put(t.ns(), t.key(), it.text, translation, Validator.looksUntranslated(it.text, translation));
            output.computeIfAbsent(t.ns(), k -> new TreeMap<>()).put(t.key(), translation);
            if (it.isName) glossary.addModName(t.ns(), t.key(), it.text, translation);
        }
        perMod.computeIfAbsent(it.modNs, k -> new int[2])[0]++;
        done++;
    }

    private void fail(Item it, String why) {
        failed++;
        done++;
        perMod.computeIfAbsent(it.modNs, k -> new int[2])[1]++;
        Target t = it.targets.get(0);
        failures.add(t.ns() + ":" + t.key() + " — " + why + " — \"" + abbreviate(it.text) + "\"");
    }

    private void waitWhilePaused() {
        if (!pauseRequested) return;
        state = State.PAUSED;
        long start = System.currentTimeMillis();
        cache.flush();
        while (pauseRequested && !cancelRequested) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        pausedMs += System.currentTimeMillis() - start;
        state = State.TRANSLATING;
    }

    private void finish(State end, String msg) {
        cache.flush();
        try {
            packChanged |= writePack();
        } catch (IOException e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo escribir el pack", e);
        }
        writeReport(msg);
        message = msg;
        currentNamespace = "";
        state = end;
        TraductorES.LOG.info("[Traductor ES] {}", msg);
        onFinish.accept(this);
    }

    private void writeReport(String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Traductor ES — informe\n");
        sb.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append('\n');
        sb.append("Resultado: ").append(msg).append("\n\n");
        sb.append("Textos con español oficial (no se tocan): ").append(official).append('\n');
        sb.append("Copiados de otra variante del español: ").append(fromVariants).append('\n');
        sb.append("Correcciones manuales aplicadas: ").append(corrected).append('\n');
        sb.append("Reutilizados del caché: ").append(fromCache).append('\n');
        sb.append("Archivos de libros con textos por traducir: ").append(bookFiles).append('\n');
        sb.append("Textos únicos enviados a la IA: ").append(done).append(" de ").append(total).append('\n');
        sb.append("Fallidos (quedan en el idioma original): ").append(failed).append("\n\n");
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
