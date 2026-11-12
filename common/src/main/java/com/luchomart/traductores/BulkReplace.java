package com.luchomart.traductores;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reemplazo masivo del editor de correcciones ("Mena de" → "Mineral de" en todos los textos). Busca la palabra o frase
 * entera, sin importar mayúsculas. Escrito en minúscula, el reemplazo toma la mayúscula inicial de cada lugar.
 */
public final class BulkReplace {
    /** Un texto donde buscar: current es lo que se ve hoy; base, la traducción sin corrección (IA o comunidad). */
    public record Target(String ns, String key, String original, String current, String base) {}

    /**
     * Cómo queda un texto. problem != null: el cambio rompería colores o valores (%s…) y no se aplica.
     * firstMatch: dónde empieza el primer cambio en el texto nuevo (para mostrar ese pedazo en la vista previa).
     */
    public record Change(Target target, String after, String problem, int firstMatch) {
        public boolean ok() { return problem == null; }
    }

    private BulkReplace() {}

    /** Los textos que cambian (los que no tienen la frase no aparecen). Vacío si no hay nada que buscar. */
    public static List<Change> preview(List<Target> targets, String find, String replacement) {
        List<Change> out = new ArrayList<>();
        Pattern p = pattern(find);
        if (p == null) return out;
        for (Target t : targets) {
            Matcher m = p.matcher(t.current());
            StringBuilder sb = new StringBuilder();
            int first = -1;
            while (m.find()) {
                m.appendReplacement(sb, "");
                if (first < 0) first = sb.length();
                sb.append(matchCase(m.group(), replacement));
            }
            if (first < 0) continue;
            m.appendTail(sb);
            String after = sb.toString();
            if (after.equals(t.current())) continue;
            String problem = null;
            if (!t.original().isEmpty() && Validator.check(t.original(), t.current()) == null) {
                problem = Validator.check(t.original(), after);
            }
            out.add(new Change(t, after, problem, first));
        }
        return out;
    }

    /** La frase como palabra entera: no se busca dentro de otras palabras ("mena" no toca "Almenara"). */
    static Pattern pattern(String find) {
        String f = find == null ? "" : find.strip();
        if (f.isEmpty()) return null;
        String before = Character.isLetterOrDigit(f.codePointAt(0)) ? "(?<![\\p{L}\\p{N}])" : "";
        String after = Character.isLetterOrDigit(f.codePointBefore(f.length())) ? "(?![\\p{L}\\p{N}])" : "";
        return Pattern.compile(before + Pattern.quote(f) + after, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Si el texto encontrado empieza en mayúscula y el reemplazo se escribió en minúscula, se pone la inicial en
     * mayúscula ("Mena de" → "Mineral de" escribiendo "mineral de"). En cualquier otro caso queda como se escribió:
     * así "rf" → "RF" o "hierro" → "Hierro" funcionan.
     */
    static String matchCase(String found, String replacement) {
        if (replacement.isEmpty() || found.isEmpty()) return replacement;
        int f = found.codePointAt(0), r = replacement.codePointAt(0);
        if (!Character.isUpperCase(f) || !Character.isLowerCase(r)) return replacement;
        return new StringBuilder().appendCodePoint(Character.toUpperCase(r)).append(replacement.substring(Character.charCount(r))).toString();
    }
}
