package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Correcciones manuales en config/traductor_es/correcciones.json, con formato
 * {"namespace:clave": "texto"}. Tienen prioridad sobre todo, incluso sobre el español oficial.
 */
public final class Corrections {
    public static final Path FILE = Config.DIR.resolve("correcciones.json");

    private Corrections() {}

    /** namespace → clave → texto corregido. */
    public static Map<String, Map<String, String>> load() {
        Map<String, Map<String, String>> out = new HashMap<>();
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(Config.DIR);
                Map<String, String> example = new LinkedHashMap<>();
                example.put("_comentario", "Formato: \"namespace:clave\": \"texto\". Ejemplo: \"pneumaticcraft:block.pneumaticcraft.solar_compressor\": \"Compresor solar\". Las claves que empiezan con _ se ignoran.");
                Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(example), StandardCharsets.UTF_8);
                return out;
            }
            Map<String, String> m = new Gson().fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, String>>() {}.getType());
            if (m == null) return out;
            for (Map.Entry<String, String> e : m.entrySet()) {
                int sep = e.getKey().indexOf(':');
                if (e.getKey().startsWith("_") || sep <= 0 || e.getValue() == null) continue;
                out.computeIfAbsent(e.getKey().substring(0, sep), k -> new HashMap<>()).put(e.getKey().substring(sep + 1), e.getValue());
            }
        } catch (Exception e) {
            TraductorES.LOG.error("[Traductor ES] correcciones.json inválido, se ignora", e);
        }
        return out;
    }
}
