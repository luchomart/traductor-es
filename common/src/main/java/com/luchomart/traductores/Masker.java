package com.luchomart.traductores;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Antes de mandar un texto a la IA, reemplaza los marcadores (%s, §a, {0}, $(item), <item>, \n literal…)
 * por fichas ⟦1⟧, ⟦2⟧… que la IA solo tiene que copiar. Al volver, las fichas se reemplazan por los originales.
 */
public final class Masker {
    // El orden importa: {item:<modid>} tiene que tomarse entero como llave antes que como etiqueta <…>.
    private static final Pattern MARKER = Pattern.compile(
            "\\{[^{}\\s]{1,40}}"                                  // {0}, {player}
            + "|\\$\\([^)]{0,60}\\)"                              // $(item), $(l:ruta), $()
            + "|%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[sdfxXcbeEgGo%]" // %s, %1$s, %%
            + "|§[0-9a-fk-orA-FK-OR]"                             // §a, §r
            + "|</?[a-zA-Z][a-zA-Z0-9_:#-]{0,30}>"                 // <item>, <r>, <n>, </b>
            + "|\\\\n"                                             // \n escrito como texto
            + "|" + Validator.HIGHLIGHT.pattern());                // _resaltado_ de Create (no el _ de stone_bricks)
    private static final Pattern TOKEN = Pattern.compile("⟦(\\d+)⟧");

    /** Texto con fichas y la lista de marcadores originales (la ficha ⟦n⟧ es tokens.get(n-1)). */
    public record Masked(String text, List<String> tokens) {}

    private Masker() {}

    /** Marcadores seguidos (como "§a%s§r") se agrupan en una sola ficha. */
    public static Masked mask(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = MARKER.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() == last && !tokens.isEmpty() && sb.toString().endsWith("⟧")) {
                int i = tokens.size() - 1;
                tokens.set(i, tokens.get(i) + m.group());
            } else {
                sb.append(text, last, m.start());
                tokens.add(m.group());
                sb.append('⟦').append(tokens.size()).append('⟧');
            }
            last = m.end();
        }
        sb.append(text.substring(last));
        return new Masked(sb.toString(), tokens);
    }

    /**
     * Restituye los marcadores. Devuelve null si la IA perdió, repitió o inventó alguna ficha.
     */
    public static String unmask(String translated, List<String> tokens) {
        if (translated == null) return null;
        if (tokens.isEmpty()) return translated.contains("⟦") ? null : translated;
        boolean[] seen = new boolean[tokens.size()];
        StringBuilder sb = new StringBuilder();
        Matcher m = TOKEN.matcher(translated);
        int last = 0;
        while (m.find()) {
            int n;
            try {
                n = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (n < 1 || n > tokens.size() || seen[n - 1]) return null;
            seen[n - 1] = true;
            sb.append(translated, last, m.start()).append(tokens.get(n - 1));
            last = m.end();
        }
        sb.append(translated.substring(last));
        for (boolean s : seen) if (!s) return null;
        String out = sb.toString();
        return out.contains("⟦") || out.contains("⟧") ? null : out;
    }
}
