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

import java.io.BufferedReader;
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
 * Libros dentro del juego, en dos formatos:
 * <ul>
 *   <li>Patchouli: assets/&lt;ns&gt;/patchouli_books/&lt;libro&gt;/&lt;idioma&gt;/....json.</li>
 *   <li>Mantle (los de Tinkers' Construct): assets/&lt;ns&gt;/book/&lt;libro&gt;/&lt;idioma&gt;/....json, más un language.lang
 *       con los nombres de las secciones. El libro tiene un index.json fuera de las carpetas de idioma.</li>
 *   <li>Citadel (el diccionario de Alex's Mobs): assets/&lt;ns&gt;/book/&lt;libro&gt;/&lt;idioma&gt;/....txt, con un root.json
 *       en el libro.</li>
 *   <li>Manual de Immersive Engineering: assets/&lt;ns&gt;/manual/&lt;idioma&gt;/&lt;entrada&gt;.txt, con
 *       manual/&lt;entrada&gt;.json al lado.</li>
 *   <li>GuideME (AE2 y sus agregados, Powah): assets/&lt;ns&gt;/&lt;carpeta de la guía&gt;/....md en inglés y
 *       .../_&lt;idioma&gt;/....md las traducciones.</li>
 * </ul>
 * Cada texto se traduce como una clave "archivo#/puntero" dentro de un namespace propio del libro (en los .txt y
 * .md, el número de pedazo: ver {@link TextPages}). Todos buscan cada archivo primero en el idioma del juego y si no está, en inglés:
 * alcanza con escribir los archivos traducidos.
 */
public final class Books {
    /** Lo que distingue a cada formato. arrayFields: campos que son listas de textos (["Mucho daño", "Poca durabilidad"]). */
    public enum Format {
        PATCHOULI("patchouli_books", "@patchouli/", "json", null,
                Set.of("name", "title", "text", "description", "subtitle", "link_text", "header"), Set.of("pages")),
        MANTLE("book", "@mantle/", "json|lang", "index.json",
                Set.of("title", "text", "description", "subText"), Set.of("effects", "properties", "entity", "block", "tooltip")),
        CITADEL("book", "@citadel/", "txt", "root.json", Set.of(), Set.of()),
        /** Sin carpeta por libro: el "libro" es siempre "manual". */
        IE_MANUAL("manual", "@manual/", "txt", null, Set.of(), Set.of()),
        /** El "libro" es la carpeta de la guía: ae2guide (AE2 y sus agregados) o guides/&lt;mod&gt;/&lt;guía&gt;. */
        GUIDEME("ae2guide|guides", "@guideme/", "md", null, Set.of(), Set.of());

        final List<String> folders;
        final String prefix;
        /** Archivo que tiene que estar en la carpeta del libro para que sea de este formato ("book" lo usan varios). */
        final String marker;
        final Set<String> textFields, arrayFields;
        /** Grupos: libro, idioma (en GuideME falta en los de inglés) y archivo. */
        final Pattern path;

        Format(String folders, String prefix, String extensions, String marker, Set<String> textFields, Set<String> arrayFields) {
            this.folders = List.of(folders.split("\\|"));
            this.prefix = prefix;
            this.marker = marker;
            this.textFields = textFields;
            this.arrayFields = arrayFields;
            String lang = "([a-z]{2,3}_[a-z]{2,3})";
            this.path = Pattern.compile(switch (extensions) {
                case "md" -> "(ae2guide|guides/[^/]+/[^/]+)/(?:_" + lang + "/)?((?!_)[^/].*\\.md)";
                case "txt" -> folders + "/" + (folders.equals("manual") ? "()" : "([^/]+)/") + lang + "/([^/]+\\.txt)";
                default -> folders + "/([^/]+)/" + lang + "/(.+\\.(?:" + extensions + "))";
            });
        }

        /** Carpeta del idioma dentro de assets/&lt;ns&gt;/. */
        String dir(String book, String lang) {
            return switch (this) {
                case GUIDEME -> book + "/_" + lang;
                case IE_MANUAL -> "manual/" + lang;
                default -> folders.get(0) + "/" + book + "/" + lang;
            };
        }

        TextPages.Kind textKind() {
            return switch (this) {
                case IE_MANUAL -> TextPages.Kind.IE;
                case GUIDEME -> TextPages.Kind.MARKDOWN;
                default -> TextPages.Kind.LINES;
            };
        }
    }

    /** Todos los namespaces de libros empiezan con "@". */
    public static final String PREFIX = "@";
    /** Archivo de Mantle con los nombres de las secciones (clave=valor). Se guarda como un JSON plano. */
    static final String MANTLE_LANG = "language.lang";
    /** Valores que son claves de traducción (libros con i18n) o identificadores ("tconstruct:pickaxe"). */
    private static final Pattern KEY_LIKE = Pattern.compile("^[a-z0-9_.:/-]+$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Book {
        public final Format format;
        public final String ns, book;
        /** Archivos en inglés que necesitan traducción (ruta relativa → contenido). */
        public final Map<String, JsonObject> english = new TreeMap<>();
        /** Archivos que se copian tal cual desde otra variante del español. */
        public final Map<String, JsonElement> fromVariant = new TreeMap<>();
        public int official;

        Book(Format format, String ns, String book) { this.format = format; this.ns = ns; this.book = book; }

        public String pseudoNamespace() { return format.prefix + ns + "/" + book; }
    }

    private Books() {}

    private static final Pattern DATA_BOOK = Pattern.compile("data/([a-z0-9_.-]+)/patchouli_books/[^/]+/book\\.json");
    /** Textos de book.json que Patchouli pasa por el traductor del juego cuando el libro tiene "i18n": true. */
    private static final List<String> BOOK_FIELDS = List.of("name", "landing_text", "subtitle");

    private static final Pattern DATA_CONTENT = Pattern.compile("data/([a-z0-9_.-]+)/patchouli_books/([^/]+)/([a-z]{2,3}_[a-z]{2,3})/(.+\\.json)");
    /** Mods dentro de otro .jar: Fabric (META-INF/jars) y Forge (META-INF/jarjar). */
    private static final Pattern NESTED_JAR = Pattern.compile("META-INF/(?:jars|jarjar)/.+\\.jar");
    private static final int MAX_NESTED = 3;

    /**
     * Los archivos de libros de Patchouli que están en data/ dentro de los .jar, también los anidados: ruta → contenido.
     * Esos archivos no son recursos del juego, así que hay que leerlos de ahí.
     */
    public static Map<String, byte[]> readJarBooks(Path modsDir) {
        Map<String, byte[]> out = new TreeMap<>();
        if (!Files.isDirectory(modsDir)) return out;
        try (Stream<Path> jars = Files.list(modsDir)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                    for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                        java.util.zip.ZipEntry entry = en.nextElement();
                        if (isBookFile(entry.getName())) {
                            try (java.io.InputStream in = zip.getInputStream(entry)) { out.putIfAbsent(entry.getName(), in.readAllBytes()); }
                        } else if (NESTED_JAR.matcher(entry.getName()).matches()) {
                            try (java.io.InputStream in = zip.getInputStream(entry)) { readNestedJar(in, out, 1); }
                        }
                    }
                } catch (Exception e) {
                    TraductorES.LOG.debug("[Traductor ES] No se pudo revisar {}: {}", jar.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo leer la carpeta de mods: {}", e.toString());
        }
        return out;
    }

    private static void readNestedJar(java.io.InputStream jar, Map<String, byte[]> out, int depth) throws IOException {
        java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(jar);
        for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
            if (isBookFile(entry.getName())) out.putIfAbsent(entry.getName(), zip.readAllBytes());
            else if (depth < MAX_NESTED && NESTED_JAR.matcher(entry.getName()).matches()) {
                readNestedJar(new java.io.ByteArrayInputStream(zip.readAllBytes()), out, depth + 1);
            }
        }
    }

    private static boolean isBookFile(String name) {
        if (DATA_BOOK.matcher(name).matches()) return true;
        Matcher m = DATA_CONTENT.matcher(name);
        return m.matches() && (m.group(3).equals("en_us") || m.group(3).startsWith("es_"));
    }

    /**
     * Textos sueltos de los book.json (portada del libro). Con "i18n": true, Patchouli busca el texto como clave de
     * idioma: si no es una clave (por ejemplo, la frase de la portada escrita tal cual), alcanza con agregarla en
     * nuestro pack con ese texto como clave. jarBooks: lo que devuelve readJarBooks. Devuelve namespace → textos.
     */
    public static Map<String, Set<String>> bookTexts(Map<String, byte[]> jarBooks) {
        Map<String, Set<String>> out = new TreeMap<>();
        jarBooks.forEach((name, content) -> {
            Matcher m = DATA_BOOK.matcher(name);
            if (!m.matches()) return;
            JsonElement el = parseJson(content);
            if (el == null || !el.isJsonObject()) return;
            JsonObject book = el.getAsJsonObject();
            if (!book.has("i18n") || !book.get("i18n").getAsBoolean()) return;
            for (String field : BOOK_FIELDS) {
                JsonElement v = book.get(field);
                if (v == null || !v.isJsonPrimitive()) continue;
                String text = v.getAsString();
                if (!text.isBlank() && !KEY_LIKE.matcher(text).matches()) out.computeIfAbsent(m.group(1), k -> new java.util.TreeSet<>()).add(text);
            }
        });
        return out;
    }

    /**
     * Libros de Patchouli con el contenido en data/: un pack no los reemplaza. La traducción se escribe en el pack como
     * la de cualquier libro y un mixin de la plataforma se la entrega a Patchouli (ver Platform.readsDataBooks).
     */
    public static List<Book> scanDataBooks(Map<String, byte[]> jarBooks, String target, List<String> variants) {
        // ns/libro → idioma → archivo → contenido
        Map<String, Map<String, Map<String, byte[]>>> index = new TreeMap<>();
        jarBooks.forEach((name, content) -> {
            Matcher m = DATA_CONTENT.matcher(name);
            if (!m.matches()) return;
            index.computeIfAbsent(m.group(1) + "/" + m.group(2), k -> new TreeMap<>())
                    .computeIfAbsent(m.group(3), k -> new TreeMap<>()).put(m.group(4), content);
        });
        List<Book> books = new ArrayList<>();
        index.forEach((id, byLang) -> {
            String[] parts = id.split("/", 2);
            // Con "use_resource_pack", el contenido está en assets/ y ya lo encuentra el escaneo de recursos.
            JsonElement meta = parseJson(jarBooks.getOrDefault("data/" + parts[0] + "/patchouli_books/" + parts[1] + "/book.json", new byte[0]));
            if (meta != null && meta.isJsonObject() && meta.getAsJsonObject().has("use_resource_pack")
                    && meta.getAsJsonObject().get("use_resource_pack").getAsBoolean()) return;
            Book book = classify(Format.PATCHOULI, parts[0], parts[1], byLang, target, variants, (content, rel) -> parseJson(content));
            if (book != null) books.add(book);
        });
        return books;
    }

    private static JsonElement parseJson(byte[] content) {
        if (content.length == 0) return null;
        try {
            return JsonParser.parseReader(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(content), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Decide qué hacer con cada archivo en inglés: oficial (no se toca), copia de otra variante o por traducir.
     * Devuelve null si no hay nada que hacer.
     */
    private static <T> Book classify(Format format, String ns, String name, Map<String, Map<String, T>> byLang, String target,
                                     List<String> variants, java.util.function.BiFunction<T, String, JsonElement> read) {
        Map<String, T> en = byLang.get("en_us");
        if (en == null) return null;
        Book book = new Book(format, ns, name);
        Map<String, T> es = byLang.getOrDefault(target, Map.of());
        for (Map.Entry<String, T> file : en.entrySet()) {
            String rel = file.getKey();
            // Las plantillas de Patchouli tienen variables ("#text"), no textos.
            if (format == Format.PATCHOULI && rel.startsWith("templates/")) continue;
            if (es.containsKey(rel)) { book.official++; continue; }
            T variant = null;
            for (String v : variants) {
                variant = byLang.getOrDefault(v, Map.of()).get(rel);
                if (variant != null) break;
            }
            JsonElement parsed = read.apply(variant != null ? variant : file.getValue(), rel);
            if (parsed == null) continue;
            if (variant != null) book.fromVariant.put(rel, parsed);
            else if (parsed.isJsonObject()) book.english.put(rel, parsed.getAsJsonObject());
        }
        return book.english.isEmpty() && book.fromVariant.isEmpty() ? null : book;
    }

    public static List<Book> scan(ResourceManager rm, String target, List<String> variants) {
        List<Book> books = new ArrayList<>();
        for (Format f : Format.values()) books.addAll(scan(rm, f, target, variants));
        return books;
    }

    private static List<Book> scan(ResourceManager rm, Format format, String target, List<String> variants) {
        Map<ResourceLocation, List<Resource>> stacks = new java.util.HashMap<>();
        for (String folder : format.folders) {
            stacks.putAll(rm.listResourceStacks(folder, rl -> rl.getPath().endsWith(".json") || rl.getPath().endsWith(".lang")
                    || rl.getPath().endsWith(".txt") || rl.getPath().endsWith(".md")));
        }
        Set<String> paths = new HashSet<>();
        for (ResourceLocation rl : stacks.keySet()) paths.add(rl.getNamespace() + ":" + rl.getPath());
        // ns/libro → idioma → archivo → recurso de más arriba (sin contar nuestro pack)
        Map<String, Map<String, Map<String, Resource>>> index = new TreeMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> e : stacks.entrySet()) {
            Matcher m = format.path.matcher(e.getKey().getPath());
            if (!m.matches()) continue;
            String ns = e.getKey().getNamespace();
            String bookName = m.group(1).isEmpty() ? "manual" : m.group(1), rel = m.group(3);
            String lang = m.group(2) == null ? "en_us" : m.group(2);
            if (!belongs(format, paths, ns, bookName, rel)) continue;
            String id = ns + "/" + bookName;
            if (!lang.equals("en_us") && !lang.startsWith("es_")) continue;
            Resource top = null;
            for (Resource r : e.getValue()) if (!PackWriter.PACK_ID.equals(r.sourcePackId())) top = r;
            if (top == null) continue;
            Map<String, Resource> files = index.computeIfAbsent(id, k -> new TreeMap<>()).computeIfAbsent(lang, k -> new TreeMap<>());
            // GuideME: si hay una versión en _en_us/, es la que ve un jugador en inglés (gana a la de la raíz).
            if (m.group(2) == null && files.containsKey(rel)) continue;
            files.put(rel, top);
        }

        List<Book> books = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Resource>>> e : index.entrySet()) {
            String[] parts = e.getKey().split("/", 2);
            Book book = classify(format, parts[0], parts[1], e.getValue(), target, variants, (r, rel) -> read(format, r, rel));
            if (book != null) books.add(book);
        }
        return books;
    }

    /** "book" y "manual" son nombres comunes: cada formato se reconoce por un archivo que solo tienen sus libros. */
    private static boolean belongs(Format format, Set<String> paths, String ns, String book, String rel) {
        return switch (format) {
            case PATCHOULI, GUIDEME -> true;
            case MANTLE, CITADEL -> paths.contains(ns + ":book/" + book + "/" + format.marker);
            // Alguna entrada tiene su .json al lado (no todas: engineered_bricks no lo tiene).
            case IE_MANUAL -> paths.stream().anyMatch(p -> p.startsWith(ns + ":manual/") && p.endsWith(".json") && p.indexOf('/', ns.length() + 8) < 0);
        };
    }

    private static JsonElement read(Format format, Resource r, String rel) {
        try (Reader reader = r.openAsReader()) {
            // El lector de Gson acepta los comentarios // y /* */ que usan algunos libros de Mantle.
            if (rel.endsWith(".txt") || rel.endsWith(".md")) return TextPages.parse(reader, format.textKind());
            return rel.endsWith(".lang") ? parseLang(reader) : JsonParser.parseReader(reader);
        } catch (Exception e) {
            return null;
        }
    }

    /** language.lang de Mantle: "clave=valor" por línea; se saltean las vacías y las que empiezan con "//". */
    static JsonObject parseLang(Reader reader) throws IOException {
        JsonObject out = new JsonObject();
        BufferedReader r = new BufferedReader(reader);
        for (String line; (line = r.readLine()) != null; ) {
            if (line.startsWith("//")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            out.addProperty(line.substring(0, eq), line.substring(eq + 1));
        }
        return out;
    }

    static String toLang(JsonElement el) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue().getAsString().replace("\n", " ")).append('\n');
        }
        return sb.toString();
    }

    /** Recorre el archivo y entrega (puntero, texto) por cada campo traducible. */
    public static void extract(Format format, String file, JsonElement el, BiConsumer<String, String> out) {
        if (file.endsWith(".lang")) {
            // Todos los valores son textos; las claves con "/" romperían el puntero (no se usan en la práctica).
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String s = e.getValue().getAsString();
                if (!e.getKey().contains("/") && !s.isBlank()) out.accept("/" + e.getKey(), s);
            }
            return;
        }
        if (file.endsWith(".txt") || file.endsWith(".md")) {
            TextPages.extract(el, out);
            return;
        }
        extract(format, el, "", out);
    }

    private static void extract(Format format, JsonElement el, String pointer, BiConsumer<String, String> out) {
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String p = pointer + "/" + e.getKey();
                JsonElement v = e.getValue();
                if (format.textFields.contains(e.getKey()) && isString(v)) {
                    accept(p, v.getAsString(), out);
                } else if (format.arrayFields.contains(e.getKey()) && v.isJsonArray()) {
                    JsonArray a = v.getAsJsonArray();
                    for (int i = 0; i < a.size(); i++) {
                        if (isString(a.get(i))) accept(p + "/" + i, a.get(i).getAsString(), out);
                        else extract(format, a.get(i), p + "/" + i, out);
                    }
                } else {
                    extract(format, v, p, out);
                }
            }
        } else if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) extract(format, a.get(i), pointer + "/" + i, out);
        }
    }

    private static boolean isString(JsonElement v) {
        return v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
    }

    private static void accept(String pointer, String s, BiConsumer<String, String> out) {
        if (!s.isBlank() && !KEY_LIKE.matcher(s).matches()) out.accept(pointer, s);
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
    public static boolean write(List<Book> books, Map<String, Map<String, String>> output, String target) throws IOException {
        boolean changed = false;
        Set<Path> written = new HashSet<>();
        for (Book book : books) {
            Path base = PackWriter.DIR.resolve("assets").resolve(book.ns).resolve(book.format.dir(book.book, target));
            for (Map.Entry<String, JsonElement> v : book.fromVariant.entrySet()) {
                changed |= writeIfChanged(base.resolve(v.getKey()), serialize(v.getKey(), v.getValue()), written);
            }
            Map<String, String> done = output.getOrDefault(book.pseudoNamespace(), Map.of());
            for (Map.Entry<String, JsonObject> file : book.english.entrySet()) {
                Map<String, String> perPointer = new TreeMap<>();
                String prefix = file.getKey() + "#";
                for (Map.Entry<String, String> t : done.entrySet()) {
                    if (t.getKey().startsWith(prefix)) perPointer.put(t.getKey().substring(prefix.length()), t.getValue());
                }
                if (perPointer.isEmpty()) continue;
                // language.lang, los .txt y los .md reemplazan entero al inglés: lo que falta traducir queda en inglés dentro del mismo archivo.
                changed |= writeIfChanged(base.resolve(file.getKey()), serialize(file.getKey(), apply(file.getValue(), perPointer)), written);
            }
        }
        // Borra lo que quedó de libros o archivos que ya no se generan.
        Path assets = PackWriter.DIR.resolve("assets");
        if (Files.isDirectory(assets)) {
            String t = Pattern.quote(target);
            Pattern ours = Pattern.compile("[^/]+/(?:(?:patchouli_books|book)/[^/]+/" + t + "|manual/" + t + "|(?:ae2guide|guides/[^/]+/[^/]+)/_" + t + ")/.+");
            try (Stream<Path> files = Files.walk(assets)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    String s = assets.relativize(p).toString().replace('\\', '/');
                    // Solo los del idioma actual: los de otras variantes del español se conservan.
                    if (ours.matcher(s).matches() && !written.contains(p)) {
                        Files.delete(p);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static String serialize(String file, JsonElement el) {
        if (file.endsWith(".txt") || file.endsWith(".md")) return TextPages.write(el);
        return file.endsWith(".lang") ? toLang(el) : GSON.toJson(el);
    }

    private static boolean writeIfChanged(Path file, String json, Set<Path> written) throws IOException {
        written.add(file);
        if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).equals(json)) return false;
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return true;
    }
}
