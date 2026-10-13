package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Misiones de FTB Quests en el formato con archivos de idioma (config/ftbquests/quests/lang/en_us.snbt, es_es.snbt…).
 * Cada valor es un texto o una lista de renglones (las descripciones). FTB Quests usa el archivo del idioma del juego
 * y, si falta una clave, el inglés: alcanza con escribir lang/&lt;variante&gt;.snbt.
 * <p>
 * Si el modpack ya trae ese archivo, se conserva todo lo suyo y solo se agregan las claves que faltan. El original se
 * guarda aparte para poder rearmar el archivo en cada traducción (y se renueva si el modpack lo actualiza).
 */
public final class FtbQuests {
    /** Namespace para el caché y la salida ("@" para que no choque con un mod). */
    public static final String NS = "@ftbquests";
    /** Renglones que no son texto: saltos de página e imágenes. */
    private static final Pattern SPECIAL = Pattern.compile("^\\{@?[a-z_]+(?::[^}]*)?}$");
    private static final Pattern TAG = Pattern.compile("<c(\\d+)>(.*?)</c\\1>", Pattern.DOTALL);
    /** Propiedades de estilo que se copian tal cual; con otras (translate, extra…) el renglón no se toca. */
    private static final Set<String> STYLE = Set.of("text", "color", "bold", "italic", "underlined", "strikethrough",
            "obfuscated", "font", "insertion", "clickEvent", "hoverEvent");
    private static final Gson GSON = new Gson();

    private FtbQuests() {}

    /** Las misiones de un modpack, listas para traducir. */
    public static final class Quests {
        final Path langDir;
        final String target;
        /** Inglés, en el orden del archivo: clave → String o List&lt;String&gt;. */
        final Map<String, Object> english;
        /** Lo que ya está en el español de destino (oficial) y no se toca. */
        final Map<String, Object> base;
        /** Claves que faltan en base pero existen en otra variante del español. */
        final Map<String, Object> variant;

        Quests(Path langDir, String target, Map<String, Object> english, Map<String, Object> base, Map<String, Object> variant) {
            this.langDir = langDir;
            this.target = target;
            this.english = english;
            this.base = base;
            this.variant = variant;
        }

        public int official() { return base.size(); }
        public int fromVariant() { return variant.size(); }
    }

    /** Carpeta de idiomas de las misiones, si el modpack usa ese formato. */
    static Path langDir() {
        return Platform.configDir().resolve("ftbquests/quests/lang");
    }

    /** Lee las misiones. Devuelve null si el modpack no tiene misiones en el formato con archivos de idioma. */
    public static Quests scan(String target, List<String> fallbacks) throws IOException {
        Path dir = langDir();
        Path en = dir.resolve("en_us.snbt");
        if (!Files.isRegularFile(en)) return null;
        Map<String, Object> english = parse(Files.readString(en, StandardCharsets.UTF_8));
        Map<String, Object> base = parse(officialText(dir, target));
        Map<String, Object> variant = new LinkedHashMap<>();
        for (String v : fallbacks) {
            Path f = dir.resolve(v + ".snbt");
            if (!Files.isRegularFile(f)) continue;
            parse(Files.readString(f, StandardCharsets.UTF_8)).forEach((k, val) -> {
                if (english.containsKey(k) && !base.containsKey(k)) variant.putIfAbsent(k, val);
            });
        }
        base.keySet().retainAll(english.keySet());
        return new Quests(dir, target, english, base, variant);
    }

    /**
     * El archivo de destino tal como lo trae el modpack. Si el que está en la carpeta lo escribió este mod, se usa
     * la copia del original; si no (no existe, o el modpack lo cambió), lo que haya en la carpeta.
     */
    private static String officialText(Path dir, String target) throws IOException {
        Path file = dir.resolve(target + ".snbt");
        Path backup = backupFile(target), mark = markFile(target);
        if (!Files.isRegularFile(file)) {
            Files.deleteIfExists(backup);
            return "";
        }
        String current = Files.readString(file, StandardCharsets.UTF_8);
        boolean ours = Files.isRegularFile(mark) && Files.readString(mark).trim().equals(hash(current));
        if (ours) return Files.isRegularFile(backup) ? Files.readString(backup, StandardCharsets.UTF_8) : "";
        Files.createDirectories(backup.getParent());
        Files.writeString(backup, current, StandardCharsets.UTF_8);
        return current;
    }

    private static Path backupFile(String target) {
        return Config.DIR.resolve("ftbquests/original_" + target + ".snbt");
    }

    private static Path markFile(String target) {
        return Config.DIR.resolve("ftbquests/escrito_" + target + ".txt");
    }

    /**
     * Recorre los textos a traducir (los que no están en el oficial ni en otra variante).
     * La clave de cada unidad es la clave del archivo, con "#n" para el renglón n de una lista.
     */
    public static void extract(Quests q, BiConsumer<String, String> out) {
        q.english.forEach((key, value) -> {
            if (q.base.containsKey(key) || q.variant.containsKey(key)) return;
            if (value instanceof String s) {
                String t = translatable(s);
                if (t != null) out.accept(key, t);
            } else {
                @SuppressWarnings("unchecked") List<String> lines = (List<String>) value;
                for (int i = 0; i < lines.size(); i++) {
                    String t = translatable(lines.get(i));
                    if (t != null) out.accept(key + "#" + i, t);
                }
            }
        });
    }

    /**
     * Escribe lang/&lt;variante&gt;.snbt: lo oficial, lo de otras variantes y lo traducido (lo que falte queda en
     * inglés). Devuelve true si el archivo cambió.
     */
    public static boolean write(Quests q, Map<String, String> translations) throws IOException {
        Map<String, Object> merged = new LinkedHashMap<>();
        boolean any = !q.base.isEmpty() || !q.variant.isEmpty();
        for (Map.Entry<String, Object> e : q.english.entrySet()) {
            String key = e.getKey();
            if (q.base.containsKey(key)) { merged.put(key, q.base.get(key)); continue; }
            if (q.variant.containsKey(key)) { merged.put(key, q.variant.get(key)); continue; }
            if (e.getValue() instanceof String s) {
                String t = translations.get(key);
                if (t != null) { merged.put(key, restore(s, t)); any = true; }
            } else {
                @SuppressWarnings("unchecked") List<String> lines = (List<String>) e.getValue();
                List<String> out = new ArrayList<>(lines.size());
                boolean translated = false;
                for (int i = 0; i < lines.size(); i++) {
                    String t = translations.get(key + "#" + i);
                    if (t != null) translated = true;
                    out.add(t == null ? lines.get(i) : restore(lines.get(i), t));
                }
                if (translated) { merged.put(key, out); any = true; }
            }
        }
        // Claves propias del oficial que ya no están en inglés: se conservan.
        q.base.forEach(merged::putIfAbsent);
        if (!any) return false;

        String text = format(merged);
        Path file = q.langDir.resolve(q.target + ".snbt");
        if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(text)) return false;
        Files.writeString(file, text, StandardCharsets.UTF_8);
        Path mark = markFile(q.target);
        Files.createDirectories(mark.getParent());
        Files.writeString(mark, hash(text));
        return true;
    }

    // ---------- Renglones ----------

    /**
     * El texto para la IA, o null si el renglón no se traduce (vacío, salto de página, imagen o JSON raro).
     * Los renglones JSON ({"text": "…", "color": …}) se pasan como texto con etiquetas &lt;cN&gt;…&lt;/cN&gt; para que
     * la IA pueda reordenar las partes; al volver se rearma el JSON con el mismo estilo.
     */
    static String translatable(String line) {
        String s = line.strip();
        if (s.isEmpty() || SPECIAL.matcher(s).matches()) return null;
        if (!isJson(s)) return line;
        List<JsonElement> parts = jsonParts(s);
        if (parts == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JsonElement p = parts.get(i);
            if (p.isJsonPrimitive()) sb.append(p.getAsString());
            else sb.append("<c").append(i).append('>').append(p.getAsJsonObject().get("text").getAsString()).append("</c").append(i).append('>');
        }
        String text = sb.toString();
        return text.isBlank() ? null : text;
    }

    /** El renglón final a partir del original y la traducción (rearma el JSON si hacía falta). */
    static String restore(String original, String translated) {
        String s = original.strip();
        if (!isJson(s)) return translated;
        List<JsonElement> parts = jsonParts(s);
        if (parts == null) return original;
        JsonArray out = new JsonArray();
        Matcher m = TAG.matcher(translated);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(new JsonPrimitive(translated.substring(last, m.start())));
            int i = Integer.parseInt(m.group(1));
            if (i >= parts.size() || !parts.get(i).isJsonObject()) return original;
            JsonObject styled = parts.get(i).getAsJsonObject().deepCopy();
            styled.addProperty("text", m.group(2));
            out.add(styled);
            last = m.end();
        }
        if (last < translated.length()) out.add(new JsonPrimitive(translated.substring(last)));
        if (s.startsWith("{") && out.size() == 1 && out.get(0).isJsonObject()) return GSON.toJson(out.get(0));
        return GSON.toJson(out);
    }

    /** Si el renglón es un texto JSON de Minecraft. "[Nota] …" empieza con corchete pero es texto común. */
    private static boolean isJson(String s) {
        // Sin comillas no es JSON de Minecraft (Gson aceptaría "[Nota]" como lista de una palabra).
        if (!s.startsWith("[") && !s.startsWith("{") || !s.contains("\"")) return false;
        try {
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonArray() || e.isJsonObject();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Las partes de un renglón JSON (textos sueltos y objetos con "text"), o null si tiene algo que no se sabe rearmar. */
    private static List<JsonElement> jsonParts(String s) {
        JsonElement root = JsonParser.parseString(s);
        List<JsonElement> parts = new ArrayList<>();
        if (root.isJsonObject()) parts.add(root);
        else if (root.isJsonArray()) root.getAsJsonArray().forEach(parts::add);
        else return null;
        for (JsonElement p : parts) {
            if (p.isJsonPrimitive() && p.getAsJsonPrimitive().isString()) continue;
            if (!p.isJsonObject()) return null;
            JsonObject o = p.getAsJsonObject();
            if (!o.has("text") || !o.get("text").isJsonPrimitive()) return null;
            for (String k : o.keySet()) if (!STYLE.contains(k)) return null;
        }
        return parts;
    }

    // ---------- SNBT ----------

    /** Lee un archivo de idioma: { clave: "texto", clave: ["renglón", …] }. */
    static Map<String, Object> parse(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        Reader r = new Reader(text);
        r.skip();
        if (!r.eat('{')) return out;
        while (true) {
            r.skip();
            if (r.eof() || r.eat('}')) break;
            String key = r.peek() == '"' || r.peek() == '\'' ? r.string() : r.bare();
            r.skip();
            if (!r.eat(':')) throw new IllegalArgumentException("Falta ':' después de " + key);
            r.skip();
            if (r.eat('[')) {
                List<String> lines = new ArrayList<>();
                while (true) {
                    r.skip();
                    if (r.eat(']')) break;
                    lines.add(r.string());
                }
                out.put(key, lines);
            } else {
                out.put(key, r.string());
            }
        }
        return out;
    }

    /** Escribe en el mismo formato que FTB Quests (tabulaciones, una clave por renglón). */
    static String format(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("{\n");
        values.forEach((key, value) -> {
            sb.append('\t').append(key).append(": ");
            if (value instanceof String s) {
                sb.append(quote(s));
            } else {
                @SuppressWarnings("unchecked") List<String> lines = (List<String>) value;
                if (lines.size() == 1) sb.append('[').append(quote(lines.get(0))).append(']');
                else {
                    sb.append("[\n");
                    for (String l : lines) sb.append("\t\t").append(quote(l)).append('\n');
                    sb.append("\t]");
                }
            }
            sb.append('\n');
        });
        return sb.append("}\n").toString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lector mínimo de SNBT: claves sueltas o entre comillas, textos, listas, comas y espacios. */
    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) { this.s = s; }

        boolean eof() { return i >= s.length(); }

        char peek() { return eof() ? 0 : s.charAt(i); }

        boolean eat(char c) {
            if (peek() != c) return false;
            i++;
            return true;
        }

        void skip() {
            while (!eof() && (Character.isWhitespace(peek()) || peek() == ',')) i++;
        }

        String bare() {
            int start = i;
            while (!eof() && peek() != ':' && !Character.isWhitespace(peek())) i++;
            return s.substring(start, i);
        }

        String string() {
            char q = peek();
            if (q != '"' && q != '\'') throw new IllegalArgumentException("Se esperaba un texto en la posición " + i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = s.charAt(i++);
                if (c == '\\' && !eof()) {
                    // Solo \" y \\ son escapes; "\&" (un & literal de FTB Quests) se conserva tal cual.
                    char n = s.charAt(i++);
                    if (n != '"' && n != '\'' && n != '\\') sb.append('\\');
                    sb.append(n);
                }
                else if (c == q) return sb.toString();
                else sb.append(c);
            }
            throw new IllegalArgumentException("Texto sin cerrar");
        }
    }
}
