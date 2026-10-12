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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traducciones hechas por la IA, una carpeta por namespace en config/traductor_es/cache.
 * Cada entrada guarda el texto original: si el mod cambia ese texto, la entrada deja de valer.
 */
public class TranslationCache {
    public static final Path DIR = Config.DIR.resolve("cache");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** o = original, t = traducción. */
    public static final class Entry {
        public String o;
        public String t;
        /** true si se aceptó sin cambios a propósito (nombre propio confirmado en un reintento). */
        public Boolean r;
        public Entry() {}
        public Entry(String o, String t, boolean confirmed) { this.o = o; this.t = t; this.r = confirmed ? Boolean.TRUE : null; }
    }

    private final Map<String, Map<String, Entry>> data = new ConcurrentHashMap<>();
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

    public void put(String ns, String key, String source, String translation, boolean confirmed) {
        namespace(ns).put(key, new Entry(source, translation, confirmed));
        dirty.add(ns);
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

    /** Los libros usan namespaces con "/" ("@patchouli/mod/libro"): se aplanan para el nombre de archivo. */
    private static Path fileFor(String ns) {
        return DIR.resolve(ns.replace("/", "__") + ".json");
    }

    /** Escribe a disco los namespaces modificados. */
    public void flush() {
        for (String ns : new HashSet<>(dirty)) {
            dirty.remove(ns);
            try {
                Files.createDirectories(DIR);
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
