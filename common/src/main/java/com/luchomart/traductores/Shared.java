package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.TreeMap;

/**
 * Carpeta del usuario compartida por todos los modpacks (~/.traductor_es): las claves de los servicios y lo que se
 * aprendió de los modelos dependen de la cuenta, no del modpack. Así se cargan una sola vez.
 */
public final class Shared {
    private Shared() {}

    public static final Path DIR = Path.of(System.getProperty("user.home"), ".traductor_es");
    private static final Path KEYS = DIR.resolve("claves.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Servicio (nombre del Preset) → clave. Vacío si no hay o no se pudo leer. */
    static synchronized Map<String, String> loadKeys() {
        try {
            if (Files.exists(KEYS)) {
                Map<String, String> m = GSON.fromJson(Files.readString(KEYS, StandardCharsets.UTF_8), new TypeToken<TreeMap<String, String>>() {}.getType());
                if (m != null) return m;
            }
        } catch (Exception e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo leer {}: {}", KEYS, e.toString());
        }
        return new TreeMap<>();
    }

    /** Guarda las claves de este modpack: las escritas se suman, las borradas a propósito se sacan. */
    static synchronized void saveKeys(Map<String, String> local) {
        Map<String, String> all = loadKeys();
        boolean changed = false;
        for (Map.Entry<String, String> e : local.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue().trim();
            String old = all.get(e.getKey());
            if (v.isEmpty() ? old != null : !v.equals(old)) {
                if (v.isEmpty()) all.remove(e.getKey());
                else all.put(e.getKey(), v);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            Files.createDirectories(DIR);
            Files.writeString(KEYS, GSON.toJson(all), StandardCharsets.UTF_8);
            // Solo el usuario puede leerlas (en Linux y Mac; en Windows la carpeta del usuario ya es privada).
            try {
                Files.setPosixFilePermissions(KEYS, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
            }
        } catch (Exception e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo guardar {}: {}", KEYS, e.toString());
        }
    }
}
