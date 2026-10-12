package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Libros de Patchouli: assets/<ns>/patchouli_books/<libro>/<idioma>/....json.
 * Cada texto se traduce como una clave "archivo#/puntero/json" dentro de un namespace propio del libro.
 */
public final class PatchouliBooks {
    public static final String PREFIX = "@patchouli/";
    private static final Pattern PATH = Pattern.compile("patchouli_books/([^/]+)/([a-z0-9_]+)/(.+\\.json)");
    private static final Set<String> TEXT_FIELDS = Set.of("name", "title", "text", "description", "subtitle", "link_text", "header");
    /** Valores que son claves de traducción (libros con i18n): ya se traducen por los archivos de idioma. */
    private static final Pattern KEY_LIKE = Pattern.compile("^[a-z0-9_.:/-]+$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Book {
        public final String ns, book;
        /** Archivos en inglés que necesitan traducción (ruta relativa → contenido). */
        public final Map<String, JsonObject> english = new TreeMap<>();
        /** Archivos que se copian tal cual desde otra variante del español. */
        public final Map<String, JsonElement> fromVariant = new TreeMap<>();
        public int official;

        Book(String ns, String book) { this.ns = ns; this.book = book; }

        public String pseudoNamespace() { return PREFIX + ns + "/" + book; }
    }

    private PatchouliBooks() {}

    public static List<Book> scan(ResourceManager rm, List<String> variants) {
        Map<ResourceLocation, List<Resource>> stacks = rm.listResourceStacks("patchouli_books", rl -> rl.getPath().endsWith(".json"));
        // ns/libro → idioma → archivo → recurso de más arriba (sin contar nuestro pack)
        Map<String, Map<String, Map<String, Resource>>> index = new TreeMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> e : stacks.entrySet()) {
            Matcher m = PATH.matcher(e.getKey().getPath());
            if (!m.matches()) continue;
            String lang = m.group(2);
            if (!lang.equals("en_us") && !lang.startsWith("es_")) continue;
            Resource top = null;
            for (Resource r : e.getValue()) if (!PackRegistration.PACK_ID.equals(r.sourcePackId())) top = r;
            if (top == null) continue;
            index.computeIfAbsent(e.getKey().getNamespace() + "/" + m.group(1), k -> new TreeMap<>())
                    .computeIfAbsent(lang, k -> new TreeMap<>()).put(m.group(3), top);
        }

        List<Book> books = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Resource>>> e : index.entrySet()) {
            Map<String, Resource> en = e.getValue().get("en_us");
            if (en == null) continue;
            String[] parts = e.getKey().split("/", 2);
            Book book = new Book(parts[0], parts[1]);
            Map<String, Resource> es = e.getValue().getOrDefault("es_es", Map.of());
            for (Map.Entry<String, Resource> file : en.entrySet()) {
                String rel = file.getKey();
                if (es.containsKey(rel)) { book.official++; continue; }
                Resource variant = null;
                for (String v : variants) {
                    variant = e.getValue().getOrDefault(v, Map.of()).get(rel);
                    if (variant != null) break;
                }
                JsonElement parsed = read(variant != null ? variant : file.getValue());
                if (parsed == null) continue;
                if (variant != null) book.fromVariant.put(rel, parsed);
                else if (parsed.isJsonObject()) book.english.put(rel, parsed.getAsJsonObject());
            }
            if (!book.english.isEmpty() || !book.fromVariant.isEmpty()) books.add(book);
        }
        return books;
    }

    private static JsonElement read(Resource r) {
        try (Reader reader = r.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            return null;
        }
    }

    /** Recorre el JSON y entrega (puntero, texto) por cada campo traducible. */
    public static void extract(JsonElement el, String pointer, BiConsumer<String, String> out) {
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String p = pointer + "/" + e.getKey();
                JsonElement v = e.getValue();
                if (TEXT_FIELDS.contains(e.getKey()) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String s = v.getAsString();
                    if (!s.isBlank() && !KEY_LIKE.matcher(s).matches()) out.accept(p, s);
                } else {
                    extract(v, p, out);
                }
            }
        } else if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) extract(a.get(i), pointer + "/" + i, out);
        }
    }

    /** Copia del JSON con los textos reemplazados (puntero → traducción). */
    static JsonElement apply(JsonObject original, Map<String, String> translations) {
        JsonElement copy = original.deepCopy();
        for (Map.Entry<String, String> t : translations.entrySet()) {
            String[] parts = t.getKey().substring(1).split("/");
            JsonElement cur = copy;
            for (int i = 0; i < parts.length - 1 && cur != null; i++) {
                cur = cur.isJsonObject() ? cur.getAsJsonObject().get(parts[i])
                        : cur.isJsonArray() ? cur.getAsJsonArray().get(Integer.parseInt(parts[i])) : null;
            }
            String last = parts[parts.length - 1];
            if (cur != null && cur.isJsonObject()) cur.getAsJsonObject().add(last, new JsonPrimitive(t.getValue()));
            else if (cur != null && cur.isJsonArray()) cur.getAsJsonArray().set(Integer.parseInt(last), new JsonPrimitive(t.getValue()));
        }
        return copy;
    }

    /**
     * Escribe los libros traducidos en el pack. output contiene, por pseudo-namespace del libro,
     * "archivo#puntero" → traducción. Borra archivos de libros que ya no corresponden.
     */
    public static boolean write(List<Book> books, Map<String, Map<String, String>> output) throws IOException {
        boolean changed = false;
        Set<Path> written = new HashSet<>();
        for (Book book : books) {
            Path base = PackWriter.DIR.resolve("assets").resolve(book.ns).resolve("patchouli_books").resolve(book.book).resolve("es_es");
            for (Map.Entry<String, JsonElement> v : book.fromVariant.entrySet()) {
                changed |= writeIfChanged(base.resolve(v.getKey()), GSON.toJson(v.getValue()), written);
            }
            Map<String, String> done = output.getOrDefault(book.pseudoNamespace(), Map.of());
            for (Map.Entry<String, JsonObject> file : book.english.entrySet()) {
                Map<String, String> perPointer = new TreeMap<>();
                String prefix = file.getKey() + "#";
                for (Map.Entry<String, String> t : done.entrySet()) {
                    if (t.getKey().startsWith(prefix)) perPointer.put(t.getKey().substring(prefix.length()), t.getValue());
                }
                if (perPointer.isEmpty()) continue;
                changed |= writeIfChanged(base.resolve(file.getKey()), GSON.toJson(apply(file.getValue(), perPointer)), written);
            }
        }
        // Borra lo que quedó de libros o archivos que ya no se generan.
        Path assets = PackWriter.DIR.resolve("assets");
        if (Files.isDirectory(assets)) {
            try (Stream<Path> files = Files.walk(assets)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    String s = assets.relativize(p).toString().replace('\\', '/');
                    if (s.contains("/patchouli_books/") && !written.contains(p)) {
                        Files.delete(p);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean writeIfChanged(Path file, String json, Set<Path> written) throws IOException {
        written.add(file);
        if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).equals(json)) return false;
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return true;
    }
}
