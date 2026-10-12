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
    private static final Pattern BRACES = Pattern.compile("\\{[^{}\\s]{1,40}}");
    private static final Pattern PATCHOULI = Pattern.compile("\\$\\([^)]{0,60}\\)");
    private static final Pattern TAGS = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9_:#-]{0,30}>");

    /** Devuelve null si es válida, o el motivo del rechazo. */
    public static String check(String source, String translated) {
        if (translated == null) return "sin traducción";
        if (translated.isBlank() && !source.isBlank()) return "traducción vacía";
        String why = compare("marcadores %", PRINTF, source, translated);
        if (why == null) why = compare("códigos §", FORMAT, source, translated);
        if (why == null) why = compare("marcadores {}", BRACES, source, translated);
        if (why == null) why = compare("marcadores $()", PATCHOULI, source, translated);
        if (why == null) why = compare("etiquetas <>", TAGS, source, translated);
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

    /** Texto que no hace falta mandar a la IA: vacío, solo números, símbolos o marcadores. */
    public static boolean isTrivial(String s) {
        String rest = PATCHOULI.matcher(BRACES.matcher(FORMAT.matcher(PRINTF.matcher(s).replaceAll("")).replaceAll("")).replaceAll("")).replaceAll("");
        return rest.codePoints().noneMatch(Character::isLetter);
    }
}
