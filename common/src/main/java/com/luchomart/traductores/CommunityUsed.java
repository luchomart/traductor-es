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
 * Qué textos tomó la última pasada de las traducciones de la comunidad, con su clave: las compartidas van por texto en
 * inglés y no quedan en el caché, así que sin esto el editor de correcciones no las puede mostrar. Se rehace entero en
 * cada pasada (en compartidas/usadas-es_ar.json…).
 */
public final class CommunityUsed {
    /** o = original en inglés, t = traducción, r = true si la revisó una persona. Clase y no record: Gson 2.8.9. */
    public static final class Entry {
        public String o;
        public String t;
        public Boolean r;

        public Entry() {}

        Entry(String o, String t, boolean revised) {
            this.o = o;
            this.t = t;
            this.r = revised ? Boolean.TRUE : null;
        }

        public boolean revised() { return Boolean.TRUE.equals(r); }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** namespace → clave → texto usado. */
    private final Map<String, Map<String, Entry>> data = new TreeMap<>();

    /** El archivo de una variante, dentro de la carpeta de las compartidas. */
    public static Path file(Path sharedDir, String target) {
        return sharedDir.resolve("usadas-" + target + ".json");
    }

    public synchronized void add(String ns, String key, String english, String text, boolean revised) {
        data.computeIfAbsent(ns, k -> new TreeMap<>()).put(key, new Entry(english, text, revised));
    }

    public synchronized boolean isEmpty() {
        return data.isEmpty();
    }

    /** Reemplaza el archivo (si no se usó nada, lo borra). */
    public synchronized void write(Path file) throws IOException {
        if (data.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Lo anotado en la última pasada; vacío si no hay archivo o está dañado. */
    public static Map<String, Map<String, Entry>> read(Path file) {
        if (!Files.exists(file)) return Map.of();
        try {
            Map<String, Map<String, Entry>> m = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Map<String, Entry>>>() {}.getType());
            if (m == null) return Map.of();
            // Sin original o sin texto no sirve para el editor.
            m.values().forEach(byKey -> byKey.values().removeIf(e -> e == null || e.o == null || e.t == null));
            return m;
        } catch (Exception e) {
            // Sin log: los tests no tienen el mod cargado, y la próxima pasada rehace el archivo igual.
            return Map.of();
        }
    }
}
