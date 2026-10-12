package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Escribe el resource pack generado en config/traductor_es/pack. */
public final class PackWriter {
    public static final Path DIR = Config.DIR.resolve("pack");
    /** Id del pack registrado: se usa para no leer nuestras propias traducciones como si fueran oficiales. */
    public static final String PACK_ID = TraductorES.MODID + "/traducciones";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PackWriter() {}

    /** Crea la carpeta y el pack.mcmeta si faltan: el pack debe existir antes de que el juego busque packs. */
    public static void ensureExists() {
        try {
            Files.createDirectories(DIR.resolve("assets"));
            Path meta = DIR.resolve("pack.mcmeta");
            String json = "{\n  \"pack\": {\n    \"pack_format\": " + Platform.PACK_FORMAT
                    + ",\n    \"description\": \"Traducciones al español generadas por Traductor ES\"\n  }\n}\n";
            if (!Files.exists(meta) || !Files.readString(meta, StandardCharsets.UTF_8).equals(json)) {
                Files.writeString(meta, json, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo crear la carpeta del pack", e);
        }
    }

    /**
     * Reemplaza el contenido del pack: un es_es.json por namespace.
     * Borra los de namespaces que ya no tienen nada (por ejemplo, un mod desinstalado).
     * Devuelve true si cambió algún archivo.
     */
    public static boolean write(Map<String, Map<String, String>> byNamespace) throws IOException {
        ensureExists();
        boolean changed = false;
        Path assets = DIR.resolve("assets");
        for (Map.Entry<String, Map<String, String>> e : byNamespace.entrySet()) {
            if (e.getValue().isEmpty() || e.getKey().startsWith(PatchouliBooks.PREFIX)) continue;
            Path lang = assets.resolve(e.getKey()).resolve("lang");
            Path file = lang.resolve("es_es.json");
            String json = GSON.toJson(new TreeMap<>(e.getValue()));
            if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).equals(json)) continue;
            Files.createDirectories(lang);
            Path tmp = lang.resolve("es_es.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            changed = true;
        }
        try (Stream<Path> dirs = Files.list(assets)) {
            for (Path nsDir : dirs.toList()) {
                Map<String, String> content = byNamespace.get(nsDir.getFileName().toString());
                if (content == null || content.isEmpty()) {
                    changed |= Files.deleteIfExists(nsDir.resolve("lang").resolve("es_es.json"));
                }
            }
        }
        return changed;
    }
}
