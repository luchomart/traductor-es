package com.luchomart.traductores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Comprueba que una traducción conserva los marcadores del texto original. */
public final class Validator {
    private Validator() {}

    // %s, %d, %1$s, %.2f, %% (sin el flag de espacio, para no confundir "50% de" con "% d")
    private static final Pattern PRINTF = Pattern.compile("%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[sdfxXcbeEgGo%]");
    private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern AMP_FORMAT = Pattern.compile("&[0-9a-fk-orA-FK-OR]");
    private static final Pattern BRACES = Pattern.compile("\\{[^{}\\s]{1,40}}");
    private static final Pattern PATCHOULI = Pattern.compile("\\$\\([^)]{0,60}\\)");
    private static final Pattern TAGS = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9_:#-]{0,30}>");
    /** Guion bajo de resaltado de Create (_Palabra_): el que está al borde de una palabra, no el de stone_bricks. */
    static final Pattern HIGHLIGHT = Pattern.compile("(?<![\\p{L}\\p{N}])_(?=[\\p{L}\\p{N}])|(?<=[\\p{L}\\p{N}.!?)])_(?![\\p{L}\\p{N}])");

    /** Devuelve null si es válida, o el motivo del rechazo. */
    public static String check(String source, String translated) {
        if (translated == null) return "sin traducción";
        if (translated.isBlank() && !source.isBlank()) return "traducción vacía";
        String why = compare("marcadores %", PRINTF, source, translated);
        if (why == null) why = compare("códigos §", FORMAT, source, translated);
        if (why == null) why = compare("códigos &", AMP_FORMAT, source, translated);
        if (why == null) why = compare("marcadores {}", BRACES, source, translated);
        if (why == null) why = compare("marcadores $()", PATCHOULI, source, translated);
        if (why == null) why = compare("etiquetas <>", TAGS, source, translated);
        if (why == null) why = compare("resaltados _ _", HIGHLIGHT, source, translated);
        if (why == null && count(source, '\n') != count(translated, '\n')) why = NEWLINES;
        if (why == null && literalNewlines(source) != literalNewlines(translated)) why = NEWLINES;
        return why;
    }

    public static final String NEWLINES = "saltos de línea distintos";

    /** Cantidad de "\n" escritos como texto (barra + n), que algunos mods interpretan como salto. */
    private static int literalNewlines(String s) {
        int n = 0;
        for (int i = s.indexOf("\\n"); i >= 0; i = s.indexOf("\\n", i + 2)) n++;
        return n;
    }

    /**
     * Un "español oficial" que en realidad es el inglés copiado (muchos mods traen es_es.json sin traducir).
     * Los nombres de una sola palabra cuentan como traducidos: suelen ser nombres propios.
     */
    public static boolean isEnglishCopy(String english, String spanish) {
        return spanish != null && looksUntranslated(english, spanish);
    }

    /** Un texto de varias palabras devuelto sin cambios probablemente quedó sin traducir. */
    public static boolean looksUntranslated(String source, String translated) {
        if (!source.equals(translated)) return false;
        String words = PATCHOULI.matcher(FORMAT.matcher(PRINTF.matcher(source).replaceAll(" ")).replaceAll(" ")).replaceAll(" ");
        int count = 0;
        for (String w : words.trim().split("\\s+")) if (w.codePoints().filter(Character::isLetter).count() >= 3) count++;
        return count >= 2;
    }

    private static String compare(String label, Pattern p, String a, String b) {
        List<String> ta = tokens(p, a), tb = tokens(p, b);
        return ta.equals(tb) ? null : label + " distintos: " + ta + " → " + tb;
    }

    private static List<String> tokens(Pattern p, String s) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) out.add(m.group());
        Collections.sort(out);
        return out;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /** Cosas que no son palabras: links, ids (mod:objeto), identificadores de código y palabras con números (00ccff, 1st, MP3). */
    private static final Pattern NOT_WORDS = Pattern.compile(
            "https?://\\S+"
            + "|&[0-9a-fk-orA-FK-OR]"
            + "|\\b[a-z0-9_.-]+:[a-z0-9_/.-]+\\b"
            + "|\\b[a-z0-9]+(?:[_.][a-z0-9]+)+\\b"
            + "|\\b[a-z]+[A-Z]\\w*(?:\\(\\))?"
            + "|\\b(?=\\w*\\d)\\w+\\b");
    private static final Pattern ROMAN = Pattern.compile("^M{0,4}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})$");
    /** Una palabra de dos letras o más, o una sigla con puntos (R.I.P.). */
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}|(?:\\p{Lu}\\.){2,}");

    /**
     * Texto que no hace falta mandar a la IA: sin ninguna palabra de verdad. Vacío, números, símbolos, marcadores,
     * códigos de color (00ccff), números romanos (CXXIX), letras sueltas (x%s, Ⱎ), links o identificadores de código.
     */
    public static boolean isTrivial(String s) {
        String rest = PATCHOULI.matcher(BRACES.matcher(FORMAT.matcher(PRINTF.matcher(s).replaceAll("")).replaceAll("")).replaceAll("")).replaceAll("");
        rest = NOT_WORDS.matcher(rest).replaceAll(" ").strip();
        // Una mayúscula sola es la sigla de un botón (B de negrita, I de cursiva): sí se traduce.
        if (rest.length() == 1 && rest.charAt(0) >= 'A' && rest.charAt(0) <= 'Z') return false;
        if (ROMAN.matcher(rest).matches()) return true;
        return !WORD.matcher(rest).find();
    }

    private static final Pattern LONG_WORD = Pattern.compile("\\p{L}{4,}");
    /** Palabras demasiado comunes para decir que dos textos hablan de lo mismo. */
    private static final java.util.Set<String> COMMON = java.util.Set.of("with", "from", "this", "that", "your", "have", "when", "will", "into", "more", "than");

    /** Comparten alguna palabra de 4 letras o más que no sea de las muy comunes. */
    public static boolean related(String a, String b) {
        java.util.Set<String> words = words(a);
        words.retainAll(words(b));
        return !words.isEmpty();
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        Matcher m = LONG_WORD.matcher(s);
        while (m.find()) {
            String w = m.group().toLowerCase(java.util.Locale.ROOT);
            if (!COMMON.contains(w)) out.add(w);
        }
        return out;
    }
}
