package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Qué textos quedaron en inglés en la última pasada en modo "solo comunidad" (sin IA), con su clave: el editor de
 * correcciones los muestra en la pestaña "Faltan" para traducirlos a mano. Se rehace entero en cada pasada
 * (en compartidas/faltan-es_ar.json…).
 */
public final class MissingTexts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** namespace → clave → texto original en inglés. */
    private final Map<String, Map<String, String>> data = new TreeMap<>();

    /** El archivo de una variante, dentro de la carpeta de las compartidas. */
    public static Path file(Path sharedDir, String target) {
        return sharedDir.resolve("faltan-" + target + ".json");
    }

    public synchronized void add(String ns, String key, String english) {
        data.computeIfAbsent(ns, k -> new TreeMap<>()).put(key, english);
    }

    public synchronized int size() {
        return data.values().stream().mapToInt(Map::size).sum();
    }

    /** Reemplaza el archivo (si no falta nada, lo borra). */
    public synchronized void write(Path file) throws IOException {
        if (data.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        JsonFiles.writeAtomic(file, GSON.toJson(data));
    }

    /** Lo anotado en la última pasada; vacío si no hay archivo o está dañado. */
    public static Map<String, Map<String, String>> read(Path file) {
        if (!Files.exists(file)) return Map.of();
        try {
            Map<String, Map<String, String>> m = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType());
            if (m == null) return Map.of();
            m.values().removeIf(byKey -> byKey == null);
            m.values().forEach(byKey -> byKey.values().removeIf(t -> t == null || t.isBlank()));
            return m;
        } catch (Exception e) {
            // Sin log: los tests no tienen el mod cargado, y la próxima pasada rehace el archivo igual.
            return Map.of();
        }
    }
}
