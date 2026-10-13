package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Textos que la IA no pudo traducir bien, con cuántas traducciones seguidas fallaron y con qué modelo.
 * Después de {@link #MAX_RUNS} fallas con el mismo modelo se dejan de mandar (quedan en el idioma original) hasta
 * que cambie el modelo, el servicio o el texto. Así no se gasta tiempo en cada arranque en algo que siempre falla.
 */
public final class FailureLog {
    public static final int MAX_RUNS = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** o: texto original; ai: servicio y modelo; n: traducciones seguidas en las que falló. */
    private static final class Entry {
        String o, ai;
        int n;
    }

    private final Path file;
    private final Map<String, Entry> entries;
    private boolean dirty;

    public FailureLog(String target) {
        file = Config.DIR.resolve("fallidos_" + target + ".json");
        Map<String, Entry> loaded = null;
        try {
            if (Files.exists(file)) {
                loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<TreeMap<String, Entry>>() {}.getType());
            }
        } catch (Exception e) {
            TraductorES.LOG.warn("[Traductor ES] {} dañado, se empieza de cero", file.getFileName());
        }
        entries = loaded != null ? loaded : new TreeMap<>();
        entries.values().removeIf(e -> e == null || e.o == null || e.ai == null);
    }

    /** Identifica la IA: otro servicio u otro modelo vuelve a intentar los fallidos. */
    public static String aiId(Config cfg) {
        return cfg.apiUrl + " " + cfg.model;
    }

    /** Si ya falló demasiadas veces con esta IA (y el original no cambió). */
    public synchronized boolean shouldSkip(String ns, String key, String text, String ai) {
        Entry e = entries.get(ns + ":" + key);
        return e != null && e.n >= MAX_RUNS && e.o.equals(text) && e.ai.equals(ai);
    }

    public synchronized void failed(String ns, String key, String text, String ai) {
        Entry e = entries.get(ns + ":" + key);
        if (e == null || !e.o.equals(text) || !e.ai.equals(ai)) {
            e = new Entry();
            e.o = text;
            e.ai = ai;
            entries.put(ns + ":" + key, e);
        }
        e.n++;
        dirty = true;
    }

    public synchronized void succeeded(String ns, String key) {
        if (entries.remove(ns + ":" + key) != null) dirty = true;
    }

    public synchronized void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(entries), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo guardar {}", file.getFileName(), e);
        }
    }
}
