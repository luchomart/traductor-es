package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Traducciones que otros jugadores ya hicieron, del repo luchomart/traductor-es-traducciones.
 * Se bajan a config/traductor_es/compartidas/&lt;variante&gt;/&lt;mod&gt;.json, solo las de los mods instalados y solo si
 * cambiaron (el index.json del repo trae un hash por archivo). Sin internet se usa lo que ya se había bajado.
 * <p>
 * Formato de cada archivo: {"revisadas": {inglés: español}, "ia": {inglés: español}}. La clave es el texto en inglés,
 * así sirve aunque el mod cambie sus claves. No usa nada de Minecraft: se prueba en los tests.
 */
public final class SharedTranslations {
    public static final String BASE_URL = "https://raw.githubusercontent.com/luchomart/traductor-es-traducciones/main/";
    /** De otra variante solo se usan textos cortos (nombres): los largos cambian con el voseo o el tú. */
    public static final int SHORT_MAX = 40;
    /** Tope por archivo: el más grande del repo ronda los 300 KB. */
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    /** Baja una URL. En el juego usa HTTP; en los tests, un mapa. */
    @FunctionalInterface
    public interface Downloader {
        byte[] get(String url) throws IOException;
    }

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build();

    /** Descarga real, con 15 segundos de tope por archivo. */
    public static final Downloader HTTP = url -> {
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15)).GET().build();
        try {
            java.net.http.HttpResponse<byte[]> r = CLIENT.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrumpido", e);
        }
    };

    /** Una traducción encontrada: revisada por una persona, y si es de la variante de destino o de otra. */
    public record Hit(String text, boolean revised, boolean sameVariant) {}

    /** Resultado de una actualización, para el log. */
    public record Update(int downloaded, int upToDate, String error) {}

    private record Data(Map<String, String> revised, Map<String, String> ai) {
        static final Data EMPTY = new Data(Map.of(), Map.of());
    }

    private final Path dir;
    private final String target;
    /** Variante de destino primero, después las otras en el orden de SpanishTarget.fallbacks. */
    private final List<String> variants;
    private final Map<String, Data> loaded = new java.util.concurrent.ConcurrentHashMap<>();

    /** @param others otras variantes a consultar para textos cortos (vacía = solo la de destino). */
    public SharedTranslations(Path dir, String target, List<String> others) {
        this.dir = dir;
        this.target = target;
        this.variants = new ArrayList<>();
        variants.add(target);
        for (String v : others) if (!variants.contains(v)) variants.add(v);
    }

    /** Nombre del archivo de un namespace (o pseudo-namespace de libro): el mismo que usa el caché. */
    public static String fileName(String ns) {
        return ns.replace("/", "__") + ".json";
    }

    /**
     * Baja los archivos de estos namespaces que falten o hayan cambiado. Nunca lanza: si algo falla, se sigue con lo
     * que haya en la carpeta.
     */
    public Update update(Collection<String> namespaces, String baseUrl, Downloader http) {
        return update(namespaces, baseUrl, http, (done, total) -> {});
    }

    /** Avance de la descarga: archivos bajados (o fallidos) de los que hay que bajar. */
    @FunctionalInterface
    public interface Progress {
        void update(int done, int total);
    }

    /** Archivos que se bajan a la vez: GitHub responde rápido, pero de a uno 200 archivos tardan más de un minuto. */
    static final int PARALLEL = 6;

    /**
     * Como {@link #update(Collection, String, Downloader)}, avisando el avance. Primero se ve qué falta bajar (así se
     * sabe el total) y después se baja de a {@link #PARALLEL} archivos.
     */
    public Update update(Collection<String> namespaces, String baseUrl, Downloader http, Progress progress) {
        JsonObject files;
        try {
            JsonObject index = JsonParser.parseString(new String(http.get(baseUrl + "index.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            files = index.getAsJsonObject("archivos");
            if (files == null) return new Update(0, 0, "index.json sin \"archivos\"");
        } catch (Exception e) {
            return new Update(0, 0, "no se pudo bajar el índice: " + e);
        }
        List<String> pending = new ArrayList<>();
        int upToDate = 0;
        for (String v : variants) {
            for (String ns : namespaces) {
                String rel = v + "/" + fileName(ns);
                JsonElement info = files.get(rel);
                if (info == null || !info.isJsonObject() || !info.getAsJsonObject().has("hash")) continue;
                Path local = dir.resolve(rel);
                try {
                    if (Files.exists(local) && info.getAsJsonObject().get("hash").getAsString().equals(hash(Files.readAllBytes(local)))) {
                        upToDate++;
                        continue;
                    }
                } catch (IOException e) {
                    // si no se puede leer, se vuelve a bajar
                }
                pending.add(rel);
            }
        }
        if (pending.isEmpty()) return new Update(0, upToDate, null);

        AtomicInteger downloaded = new AtomicInteger(), finished = new AtomicInteger();
        AtomicReference<String> error = new AtomicReference<>();
        progress.update(0, pending.size());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLEL, pending.size()), r -> {
            Thread t = new Thread(r, "Traductor ES - descarga");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (String rel : pending) {
                tasks.add(pool.submit(() -> {
                    try {
                        byte[] body = http.get(baseUrl + rel);
                        if (body.length > MAX_BYTES) throw new IOException("archivo demasiado grande");
                        parse(body); // que sea válido antes de reemplazar lo que había
                        Path local = dir.resolve(rel);
                        Files.createDirectories(local.getParent());
                        // Temporal propio: el asistente y la traducción pueden bajar el mismo archivo a la vez, y con un
                        // .tmp compartido uno podría mover el del otro a medio escribir.
                        Path tmp = Files.createTempFile(local.getParent(), local.getFileName().toString(), ".tmp");
                        try {
                            Files.write(tmp, body);
                            // Atómico: con REPLACE_EXISTING Java borra el destino antes de renombrar, y si dos
                            // descargas terminan a la vez la segunda falla con NoSuchFileException.
                            Files.move(tmp, local, StandardCopyOption.ATOMIC_MOVE);
                        } finally {
                            Files.deleteIfExists(tmp);
                        }
                        loaded.remove(rel);
                        downloaded.incrementAndGet();
                    } catch (Exception e) {
                        error.compareAndSet(null, rel + ": " + e);
                    }
                    progress.update(finished.incrementAndGet(), pending.size());
                }));
            }
            for (Future<?> f : tasks) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    error.compareAndSet(null, e.getCause().toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return new Update(downloaded.get(), upToDate, error.get());
    }

    /**
     * La mejor traducción compartida de un texto: primero la variante de destino (revisada, después de la IA); si no hay,
     * otra variante, solo para textos cortos. null si no hay ninguna.
     */
    public Hit get(String ns, String english) {
        Data own = data(target, ns);
        String t = own.revised.get(english);
        if (t != null) return new Hit(t, true, true);
        t = own.ai.get(english);
        if (t != null) return new Hit(t, false, true);
        if (!isShort(english)) return null;
        for (int i = 1; i < variants.size(); i++) {
            Data d = data(variants.get(i), ns);
            t = d.revised.get(english);
            if (t != null) return new Hit(t, true, false);
            t = d.ai.get(english);
            if (t != null) return new Hit(t, false, false);
        }
        return null;
    }

    /** Corto = cabe en un nombre: sin saltos de línea, sin punto final y de hasta {@link #SHORT_MAX} caracteres. */
    static boolean isShort(String text) {
        String s = text.strip();
        return s.length() <= SHORT_MAX && !s.contains("\n") && !s.endsWith(".");
    }

    private Data data(String variant, String ns) {
        return loaded.computeIfAbsent(variant + "/" + fileName(ns), rel -> {
            Path f = dir.resolve(rel);
            if (!Files.exists(f)) return Data.EMPTY;
            try {
                return parse(Files.readAllBytes(f));
            } catch (Exception e) {
                TraductorES.LOG.warn("[Traductor ES] Traducciones compartidas dañadas en {}, se ignoran: {}", f, e.toString());
                return Data.EMPTY;
            }
        });
    }

    private static Data parse(byte[] body) {
        JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        return new Data(section(o, "revisadas"), section(o, "ia"));
    }

    private static Map<String, String> section(JsonObject o, String name) {
        Map<String, String> out = new HashMap<>();
        JsonElement e = o.get(name);
        if (e == null || !e.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> kv : e.getAsJsonObject().entrySet()) {
            if (kv.getValue().isJsonPrimitive() && !kv.getValue().getAsString().isBlank()) out.put(kv.getKey(), kv.getValue().getAsString());
        }
        return out;
    }

    /** Los primeros 16 caracteres del SHA-256 en hexadecimal, igual que el index.json del repo. */
    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
