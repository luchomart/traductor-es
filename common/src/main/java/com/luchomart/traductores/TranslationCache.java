package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traducciones hechas por la IA, una carpeta por namespace en config/traductor_es/cache.
 * Cada entrada guarda el texto original: si el mod cambia ese texto, la entrada deja de valer.
 */
public class TranslationCache {
    public static final Path ROOT = Config.DIR.resolve("cache");
    /** Carpeta del idioma de destino: cache/es_es, cache/es_mx… */
    private final Path dir;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** o = original, t = traducción. */
    public static final class Entry {
        public String o;
        public String t;
        /** true si se aceptó sin cambios a propósito (nombre propio confirmado en un reintento). */
        public Boolean r;
        /** Modelo que la tradujo (para poder rehacer lo de un modelo que tradujo mal). Falta en las anteriores. */
        public String m;
        public Entry() {}
        public Entry(String o, String t, boolean confirmed, String model) {
            this.o = o;
            this.t = t;
            this.r = confirmed ? Boolean.TRUE : null;
            this.m = model == null || model.isBlank() ? null : model;
        }
    }

    private final Map<String, Map<String, Entry>> data = new ConcurrentHashMap<>();

    public TranslationCache(String target) {
        dir = ROOT.resolve(target);
        migrateLegacy(target);
    }

    /** Las versiones anteriores guardaban el caché (solo es_es) directo en cache/: se mueve a cache/es_es. */
    private static void migrateLegacy(String target) {
        if (!"es_es".equals(target)) return;
        try (java.util.stream.Stream<Path> files = Files.list(ROOT)) {
            List<Path> legacy = files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).toList();
            if (legacy.isEmpty()) return;
            Path dest = ROOT.resolve("es_es");
            Files.createDirectories(dest);
            for (Path p : legacy) Files.move(p, dest.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            TraductorES.LOG.info("[Traductor ES] Caché anterior movido a {}", dest);
        } catch (IOException e) {
            if (Files.isDirectory(ROOT)) TraductorES.LOG.error("[Traductor ES] No se pudo mover el caché anterior", e);
        }
    }
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();

    public String get(String ns, String key, String source) {
        Entry e = namespace(ns).get(key);
        return e != null && source.equals(e.o) ? e.t : null;
    }

    /** true si la entrada está marcada como "sin cambios a propósito". */
    public boolean isConfirmed(String ns, String key) {
        Entry e = namespace(ns).get(key);
        return e != null && Boolean.TRUE.equals(e.r);
    }

    public void put(String ns, String key, String source, String translation, boolean confirmed, String model) {
        namespace(ns).put(key, new Entry(source, translation, confirmed, model));
        dirty.add(ns);
    }

    /**
     * Saca del caché todo lo que tradujo ese modelo (así se vuelve a traducir) y lo guarda en un archivo de
     * respaldo dentro de config/traductor_es/respaldos. Devuelve cuántas traducciones sacó.
     */
    public int removeByModel(String model) throws IOException {
        Map<String, Map<String, Entry>> removed = new TreeMap<>();
        for (Map.Entry<String, Map<String, Entry>> ns : loadAll().entrySet()) {
            Map<String, Entry> out = new TreeMap<>();
            ns.getValue().entrySet().removeIf(e -> {
                if (!model.equals(e.getValue().m)) return false;
                out.put(e.getKey(), e.getValue());
                return true;
            });
            if (!out.isEmpty()) {
                removed.put(ns.getKey(), out);
                dirty.add(ns.getKey());
            }
        }
        if (removed.isEmpty()) return 0;
        Path backups = Config.DIR.resolve("respaldos");
        Files.createDirectories(backups);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        Path f = backups.resolve(dir.getFileName() + "_" + model.replaceAll("[^A-Za-z0-9._-]", "_") + "_" + stamp + ".json");
        Files.writeString(f, GSON.toJson(removed), StandardCharsets.UTF_8);
        flush();
        int n = removed.values().stream().mapToInt(Map::size).sum();
        TraductorES.LOG.info("[Traductor ES] Se sacaron {} traducciones de {} (respaldo en {})", n, model, f.getFileName());
        return n;
    }

    /** Cuántas traducciones hizo cada modelo ("" = de antes de que se guardara el modelo). */
    public Map<String, Integer> countByModel() {
        Map<String, Integer> out = new TreeMap<>();
        for (Map<String, Entry> ns : loadAll().values()) {
            for (Entry e : ns.values()) out.merge(e.m == null ? "" : e.m, 1, Integer::sum);
        }
        return out;
    }

    private Map<String, Entry> namespace(String ns) {
        return data.computeIfAbsent(ns, this::load);
    }

    private Map<String, Entry> load(String ns) {
        Path f = fileFor(ns);
        if (Files.exists(f)) {
            try {
                Map<String, Entry> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<TreeMap<String, Entry>>() {}.getType());
                if (m != null) {
                    m.values().removeIf(e -> e == null || e.o == null || e.t == null);
                    return new ConcurrentHashMap<>(m);
                }
            } catch (Exception e) {
                TraductorES.LOG.error("[Traductor ES] Caché dañado para {}, se ignora", ns, e);
            }
        }
        return new ConcurrentHashMap<>();
    }

    /** Todo lo traducido para esta variante: namespace → clave → entrada (para el editor de correcciones). */
    public Map<String, Map<String, Entry>> loadAll() {
        Map<String, Map<String, Entry>> out = new TreeMap<>();
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = p.getFileName().toString();
                String ns = name.substring(0, name.length() - ".json".length()).replace("__", "/");
                out.put(ns, namespace(ns));
            }
        } catch (IOException e) {
            if (Files.isDirectory(dir)) TraductorES.LOG.error("[Traductor ES] No se pudo leer el caché", e);
        }
        return out;
    }

    /** Los libros usan namespaces con "/" ("@patchouli/mod/libro"): se aplanan para el nombre de archivo. */
    private Path fileFor(String ns) {
        return dir.resolve(ns.replace("/", "__") + ".json");
    }

    /** Escribe a disco los namespaces modificados. */
    public void flush() {
        for (String ns : new HashSet<>(dirty)) {
            dirty.remove(ns);
            try {
                Files.createDirectories(dir);
                Path f = fileFor(ns), tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.writeString(tmp, GSON.toJson(new TreeMap<>(namespace(ns))), StandardCharsets.UTF_8);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                dirty.add(ns);
                TraductorES.LOG.error("[Traductor ES] No se pudo guardar el caché de {}", ns, e);
            }
        }
    }
}
