package com.luchomart.traductores;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mayúsculas como el Minecraft oficial en español: "Mesa de crafteo", no "Mesa de Crafteo". Muchos modelos copian
 * el estilo del inglés ("Basic Redstone Gauge" → "Medidor Básico de Redstone"); acá se corrige sin volver a traducir.
 * Solo en nombres (el inglés en Title Case y sin punto final). Quedan con mayúscula la primera palabra, las que el
 * Minecraft oficial escribe así (Nether, End, Wither), las que aparecen igual en el inglés y no son palabras comunes
 * del español oficial (nombres de mods o materiales inventados), siglas y números romanos.
 */
public final class Capitalization {
    /** Palabras que el español oficial escribe con mayúscula en medio de un nombre, y las que escribe en minúscula. */
    private final Set<String> upper, lower;

    private Capitalization(Set<String> upper, Set<String> lower) {
        this.upper = upper;
        this.lower = lower;
    }

    /** Sin el idioma oficial a mano: lo que se sabe del Minecraft en español. */
    public static final Capitalization DEFAULT = new Capitalization(Set.of("Nether", "End", "Wither"),
            Set.of("redstone", "ender", "magma", "acacia", "lava"));

    /**
     * Aprende de los nombres oficiales de Minecraft en la variante de destino (clave → texto): qué palabras van con
     * mayúscula en medio de un nombre ("Ladrillos del Nether") y cuáles en minúscula ("Polvo de redstone").
     */
    public static Capitalization learn(java.util.Map<String, String> official) {
        Set<String> up = new HashSet<>(DEFAULT.upper), low = new HashSet<>(DEFAULT.lower);
        for (java.util.Map.Entry<String, String> e : official.entrySet()) {
            String k = e.getKey();
            if (!(k.startsWith("block.") || k.startsWith("item.") || k.startsWith("entity.") || k.startsWith("biome."))) continue;
            Matcher m = WORD.matcher(e.getValue());
            boolean first = true;
            while (m.find()) {
                String w = m.group();
                if (!first) {
                    if (Character.isUpperCase(w.charAt(0))) up.add(w);
                    else low.add(w.toLowerCase(Locale.ROOT));
                }
                first = false;
            }
        }
        return new Capitalization(up, low);
    }

    /** Con las reglas por defecto (sin el idioma oficial). */
    public static String fix(String english, String translation) {
        return DEFAULT.apply(english, translation);
    }

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}'’]*");
    /** Palabras chicas del inglés que en Title Case suelen ir en minúscula. */
    private static final Set<String> SMALL = Set.of("a", "an", "the", "of", "and", "or", "for", "to", "in", "on", "at", "by", "with", "from");
    /** Marcadores, colores y texto con formato: no se tocan. */
    private static final Pattern FORMATTED = Pattern.compile("[§&%{}<>$\\\\\\n\\[\\]]");

    /** La traducción con las mayúsculas corregidas, o la misma si no corresponde tocarla. */
    public String apply(String english, String translation) {
        if (english == null || translation == null || !isTitleCaseName(english)) return translation;
        if (FORMATTED.matcher(translation).find()) return translation;
        Set<String> keep = new HashSet<>();
        Matcher em = WORD.matcher(english);
        while (em.find()) {
            keep.add(em.group());
            keep.add(em.group().split("['’]")[0]); // "Thor's" → también "Thor"
        }

        StringBuilder out = new StringBuilder(translation.length());
        Matcher m = WORD.matcher(translation);
        int last = 0;
        boolean first = true;
        while (m.find()) {
            String w = m.group();
            out.append(translation, last, m.start());
            boolean sentenceStart = first || endsPhrase(out);
            out.append(sentenceStart || keepAsIs(w, keep) ? w : Character.toLowerCase(w.charAt(0)) + w.substring(1));
            last = m.end();
            first = false;
        }
        out.append(translation.substring(last));
        return out.toString();
    }

    /** Después de ":" o de un guion separador empieza otra frase ("Modo: Automático"). */
    private static boolean endsPhrase(StringBuilder sb) {
        String s = sb.toString().stripTrailing();
        return s.endsWith(":") || s.endsWith(" -") || s.endsWith("—") || s.endsWith("–") || s.endsWith("(");
    }

    private boolean keepAsIs(String w, Set<String> english) {
        if (!Character.isUpperCase(w.charAt(0))) return true;
        String lw = w.toLowerCase(Locale.ROOT);
        if (upper.contains(w) && !lower.contains(lw)) return true;   // como el oficial: "del Nether", "del End"
        if (lower.contains(lw)) return false;                        // como el oficial: "de redstone", "de acacia"
        if (english.contains(w)) return true;                       // nombre propio o sin traducir
        if (w.length() > 1 && w.equals(w.toUpperCase(Locale.ROOT))) return true; // sigla o número romano
        for (int i = 1; i < w.length(); i++) if (Character.isUpperCase(w.charAt(i))) return true; // "McDonald", "iPhone"
        return false;
    }

    /**
     * Nombre en Title Case: sin puntuación de oración al final, pocas palabras y casi todas las importantes con
     * mayúscula ("Basic Redstone Gauge", "Block of Iron"). No: "Right-click to open", "Stores energy."
     */
    static boolean isTitleCaseName(String english) {
        String s = english.strip();
        if (s.isEmpty() || s.endsWith(".") || s.endsWith("!") || s.endsWith("?")) return false;
        int words = 0, capitalized = 0;
        Matcher m = WORD.matcher(s);
        while (m.find()) {
            String w = m.group();
            if (m.start() > 0 && Character.isDigit(s.charAt(m.start() - 1))) continue; // "16k", "64B"
            if (SMALL.contains(w.toLowerCase(Locale.ROOT))) continue;
            words++;
            if (Character.isUpperCase(w.charAt(0))) capitalized++;
        }
        return words >= 2 && words <= 8 && capitalized == words;
    }
}
