package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Arma lo que este jugador puede aportar al repo de traducciones de la comunidad, en su formato:
 * &lt;variante&gt;/&lt;archivo del caché&gt;.json = {"revisadas": {inglés: español}, "ia": {inglés: español}}.
 * <p>
 * Cada archivo sale de juntar lo último del repo (lo que el mod ya bajó en compartidas/) con el caché propio y las
 * correcciones: así, subirlo entero nunca borra lo que ya estaba. Mismas reglas que tools/compartir.py: lo del repo se
 * respeta, una revisada le gana a una de la IA, y misiones y fichas de REI no van (son de cada modpack).
 * No usa nada de Minecraft: se prueba en los tests.
 */
public final class CommunityExport {
    private static final Set<String> VARIANTS = Set.copyOf(SpanishTarget.CODES);
    private static final List<String> EXCLUDED = List.of("@ftbquests", "@rei");

    /**
     * files: ruta relativa ("es_ar/ae2.json") → contenido listo para subir. Solo archivos con algo nuevo.
     * added: por archivo, cuántas traducciones nuevas trae {de la IA, revisadas}.
     */
    public record Result(Map<String, String> files, Map<String, int[]> added) {
        public int newAi() { return added.values().stream().mapToInt(a -> a[0]).sum(); }
        public int newRevised() { return added.values().stream().mapToInt(a -> a[1]).sum(); }
        public int total() { return newAi() + newRevised(); }
        public boolean isEmpty() { return files.isEmpty(); }

        /** Sin los archivos que ya se mandaron tal cual (por su hash): así no se manda dos veces lo mismo. */
        public Result withoutSent(Set<String> sentHashes) {
            Map<String, String> f = new TreeMap<>();
            Map<String, int[]> a = new TreeMap<>();
            files.forEach((path, content) -> {
                if (sentHashes.contains(hash(content))) return;
                f.put(path, content);
                a.put(path, added.get(path));
            });
            return new Result(f, a);
        }
    }

    /**
     * Título fijo para el pull request, igual en los dos caminos: "Traducciones es_ar: 340 nuevas (ae2, create y 10 más)".
     * Los mods van ordenados por cuántas traducciones nuevas traen.
     */
    public static String title(Result r) {
        List<String> variants = r.files().keySet().stream().map(p -> p.substring(0, p.indexOf('/'))).distinct().toList();
        List<String> mods = r.added().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]))
                .map(e -> modName(e.getKey())).distinct().toList();
        String shown = mods.size() <= 3 ? String.join(", ", mods)
                : String.join(", ", mods.subList(0, 2)) + " y " + (mods.size() - 2) + " más";
        return "Traducciones " + String.join(", ", variants) + ": " + r.total() + (r.total() == 1 ? " nueva" : " nuevas")
                + " (" + shown + ")";
    }

    /** "es_ar/ae2.json" → "ae2"; "es_ar/@patchouli__ae2__guide.json" → "libro ae2". */
    private static String modName(String path) {
        String name = path.substring(path.indexOf('/') + 1, path.length() - ".json".length());
        if (!name.startsWith("@")) return name;
        String[] parts = name.substring(1).split("__");
        return "libro " + (parts.length > 1 ? parts[1] : parts[0]);
    }

    public static String hash(String content) {
        return SharedTranslations.hash(content.getBytes(StandardCharsets.UTF_8));
    }

    private CommunityExport() {}

    /**
     * @param cacheRoot   config/traductor_es/cache (una carpeta por variante)
     * @param sharedDir   config/traductor_es/compartidas (lo bajado del repo)
     * @param corrections namespace → clave → texto; se aplican solo a la variante de destino (están escritas en ella)
     * @param target      variante con la que juega
     */
    public static Result build(Path cacheRoot, Path sharedDir, Map<String, Map<String, String>> corrections, String target) throws IOException {
        Map<String, String> files = new TreeMap<>();
        Map<String, int[]> addedPerFile = new TreeMap<>();
        if (!Files.isDirectory(cacheRoot)) return new Result(files, addedPerFile);
        List<Path> variantDirs;
        try (Stream<Path> s = Files.list(cacheRoot)) {
            variantDirs = s.filter(p -> Files.isDirectory(p) && VARIANTS.contains(p.getFileName().toString())).sorted().toList();
        }
        for (Path vdir : variantDirs) {
            String variant = vdir.getFileName().toString();
            List<Path> cacheFiles;
            try (Stream<Path> s = Files.list(vdir)) {
                cacheFiles = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            }
            for (Path f : cacheFiles) {
                String name = f.getFileName().toString();
                if (EXCLUDED.stream().anyMatch(name::startsWith)) continue;
                String ns = name.substring(0, name.length() - ".json".length()).replace("__", "/");
                JsonObject cache = readObject(f);
                if (cache == null) continue;

                Map<String, String> revised = new TreeMap<>(), ai = new TreeMap<>();
                JsonObject repo = readObject(sharedDir.resolve(variant).resolve(name));
                if (repo != null) {
                    revised.putAll(section(repo, "revisadas"));
                    ai.putAll(section(repo, "ia"));
                    ai.keySet().removeAll(revised.keySet());
                }
                int[] added = new int[2]; // [ia, revisadas]
                Map<String, String> corr = variant.equals(target) ? corrections.getOrDefault(ns, Map.of()) : Map.of();
                // Primero las correcciones (revisadas): el inglés sale del caché.
                for (Map.Entry<String, String> c : corr.entrySet()) {
                    String english = original(cache, c.getKey());
                    if (english != null) add(revised, ai, english, c.getValue(), true, added);
                }
                for (Map.Entry<String, JsonElement> e : cache.entrySet()) {
                    if (corr.containsKey(e.getKey()) || !e.getValue().isJsonObject()) continue;
                    JsonObject entry = e.getValue().getAsJsonObject();
                    String o = string(entry, "o"), t = string(entry, "t");
                    if (o != null && t != null) add(revised, ai, o, t, false, added);
                }
                if (added[0] + added[1] == 0) continue;
                files.put(variant + "/" + name, toJson(revised, ai));
                addedPerFile.put(variant + "/" + name, added);
            }
        }
        return new Result(files, addedPerFile);
    }

    private static void add(Map<String, String> revised, Map<String, String> ai, String english, String spanish,
                            boolean isRevised, int[] added) {
        if (english.isBlank() || spanish.isBlank()) return;
        if (isRevised) {
            if (revised.containsKey(english)) return; // ya revisada en el repo: se respeta
            revised.put(english, spanish);
            ai.remove(english);
            added[1]++;
        } else {
            if (revised.containsKey(english) || ai.containsKey(english)) return;
            ai.put(english, spanish);
            added[0]++;
        }
    }

    private static String original(JsonObject cache, String key) {
        JsonElement e = cache.get(key);
        return e != null && e.isJsonObject() ? string(e.getAsJsonObject(), "o") : null;
    }

    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static Map<String, String> section(JsonObject o, String name) {
        Map<String, String> out = new TreeMap<>();
        JsonElement e = o.get(name);
        if (e == null || !e.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> kv : e.getAsJsonObject().entrySet()) {
            if (kv.getValue().isJsonPrimitive()) out.put(kv.getKey(), kv.getValue().getAsString());
        }
        return out;
    }

    private static JsonObject readObject(Path f) {
        if (!Files.exists(f)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            TraductorES.LOG.warn("[Traductor ES] {} dañado, se ignora: {}", f, e.toString());
            return null;
        }
    }

    /** Escribe cada archivo en la carpeta (para subirlos a mano). */
    public static void writeTo(Path dir, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path f = dir.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Igual que json.dumps(..., ensure_ascii=False, indent=1) de Python con las claves ordenadas: así los cambios de un
     * pull request se ven línea por línea y no como el archivo entero reescrito.
     */
    static String toJson(Map<String, String> revised, Map<String, String> ai) {
        StringBuilder sb = new StringBuilder("{\n");
        section(sb, "revisadas", revised);
        sb.append(",\n");
        section(sb, "ia", ai);
        return sb.append("\n}\n").toString();
    }

    private static void section(StringBuilder sb, String name, Map<String, String> m) {
        sb.append(' ');
        quote(sb, name);
        sb.append(": ");
        if (m.isEmpty()) { sb.append("{}"); return; }
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<String, String> e : new TreeMap<>(m).entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  ");
            quote(sb, e.getKey());
            sb.append(": ");
            quote(sb, e.getValue());
        }
        sb.append("\n }");
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
