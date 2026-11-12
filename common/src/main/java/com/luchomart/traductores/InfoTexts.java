package com.luchomart.traductores;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos que el modpack escribe directo en un script (fichas de información de REI armadas con KubeJS): no tienen
 * clave de idioma, así que se capturan cuando REI los registra y se cambian en memoria al mostrarlos. Solo existen
 * dentro de un mundo: lo capturado se guarda para traducirlo en la pasada siguiente.
 * <p>
 * Los números se separan para no traducir una línea por cada valor: "Size: 60" y "Size: 40" son "Size: {0}".
 */
public final class InfoTexts {
    /** Namespace para el caché y la salida ("@" para que no vaya al pack). */
    public static final String NS = "@rei";

    /** Un número suelto: no el de "Mk2", "§4", "%1$s" o "#123". */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_.,§&%#$-])-?\\d+(?:\\.\\d+)?(?![\\p{L}\\p{N}_])");
    private static final Pattern SLOT = Pattern.compile("\\{(\\d+)}");

    private static final Set<String> captured = ConcurrentHashMap.newKeySet();
    private static boolean loaded;
    private static volatile boolean hasNew;

    /** Las traducciones del idioma cargado (plantilla → español), para no leer el archivo en cada ficha. */
    private record Loaded(String lang, Map<String, String> texts) {}
    private static volatile Loaded current;

    private InfoTexts() {}

    // ---------- Plantillas ----------

    /** "Range: -59 &lt; y &lt; 16" → "Range: {0} &lt; y &lt; {1}". Si el texto ya trae llaves, queda como está. */
    static String template(String line) {
        if (line.indexOf('{') >= 0) return line;
        Matcher m = NUMBER.matcher(line);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("{" + n++ + "}"));
        m.appendTail(sb);
        return sb.toString();
    }

    /** Los números de la línea, en el orden de las fichas de {@link #template}. */
    static List<String> numbers(String line) {
        List<String> out = new ArrayList<>();
        if (line.indexOf('{') >= 0) return out;
        Matcher m = NUMBER.matcher(line);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** Pone los números en la plantilla traducida. */
    static String fill(String translated, List<String> numbers) {
        if (numbers.isEmpty()) return translated;
        Matcher m = SLOT.matcher(translated);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(i < numbers.size() ? numbers.get(i) : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---------- Captura ----------

    /** Anota una línea en inglés. Puede llamarse desde el hilo de carga de REI. */
    public static synchronized void capture(String line) {
        if (Validator.isTrivial(line)) return;
        loadCaptured();
        if (captured.add(template(line))) hasNew = true;
    }

    /** Hay líneas que todavía no pasaron por una traducción. */
    public static synchronized boolean hasNew() {
        return hasNew;
    }

    /**
     * Las plantillas capturadas (en esta sesión y en las anteriores), clave → plantilla, para traducirlas. Las guarda
     * para la próxima sesión.
     */
    static synchronized Map<String, String> known() {
        loadCaptured();
        save();
        hasNew = false;
        Map<String, String> out = new TreeMap<>();
        for (String t : captured) out.put("line." + FtbQuests.hash(t).substring(0, 16), t);
        return out;
    }

    /** Guarda lo capturado sin traducir nada (al cerrar el juego). */
    public static synchronized void save() {
        if (captured.isEmpty()) return;
        try {
            Path file = capturedFile();
            Files.createDirectories(file.getParent());
            JsonArray out = new JsonArray();
            new TreeSet<>(captured).forEach(out::add);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudieron guardar los textos de REI: {}", e.toString());
        }
    }

    private static void loadCaptured() {
        if (loaded) return;
        loaded = true;
        Path file = capturedFile();
        if (!Files.isRegularFile(file)) return;
        try {
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray()
                    .forEach(e -> captured.add(e.getAsString()));
        } catch (IOException | RuntimeException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudieron leer los textos de REI: {}", e.toString());
        }
    }

    // ---------- Traducciones ----------

    private static Path capturedFile() {
        return Config.DIR.resolve("rei/lineas.json");
    }

    private static Path textsFile(String lang) {
        return Config.DIR.resolve("rei/textos_" + lang + ".json");
    }

    /** Guarda plantilla → traducción y hace que se vea en la próxima ficha que se abra. */
    static void write(String target, Map<String, String> lines, Map<String, String> translations) throws IOException {
        if (FtbQuests.writeOld(textsFile(target), lines, translations)) current = null;
    }

    /** La línea en español, o null si no hay traducción o no corresponde aplicarla con este idioma y estas opciones. */
    public static String translate(String line, String gameLanguage) {
        Config cfg = Config.get();
        if (!FtbQuests.shouldApplyOld(cfg.targetLanguage, gameLanguage, cfg.translateBooks)) return null;
        Loaded l = current;
        if (l == null || !l.lang().equals(gameLanguage)) current = l = new Loaded(gameLanguage, read(textsFile(gameLanguage)));
        String t = l.texts().get(template(line));
        return t == null ? null : fill(t, numbers(line));
    }

    private static Map<String, String> read(Path file) {
        Map<String, String> out = new HashMap<>();
        if (!Files.isRegularFile(file)) return out;
        try {
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        } catch (IOException | RuntimeException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudieron leer los textos de REI traducidos: {}", e.toString());
        }
        return out;
    }
}
