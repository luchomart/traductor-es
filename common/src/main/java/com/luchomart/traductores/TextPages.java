package com.luchomart.traductores;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Páginas de libros escritas como texto: los .txt de Citadel y de Immersive Engineering (una línea, un texto) y los .md
 * de GuideME (un párrafo, un texto). El archivo se guarda como JSON con los pedazos en orden ("0", "1"…) para usar lo
 * mismo que con los libros JSON:
 * <ul>
 *   <li>"#sep": lo que va después de cada pedazo (un salto de línea, o nada si sigue en la misma línea).</li>
 *   <li>"#fixed": los pedazos que se copian tal cual (etiquetas, escenas, código, separadores de tabla).</li>
 *   <li>"#tags": las marcas propias de cada formato, que en el texto se cambian por otras simples que ya se protegen
 *       al traducir: un link queda &lt;l1&gt;texto&lt;/l1&gt; (el texto visible se traduce) y el resto &lt;t2&gt;.</li>
 * </ul>
 */
final class TextPages {
    enum Kind { LINES, IE, MARKDOWN }

    private static final String TAGS = "#tags", FIXED = "#fixed", SEP = "#sep";

    // Immersive Engineering: <link;destino;texto;ancla>, <&ancla>, <config;…>, <keybind;…> y la barra que une líneas.
    private static final Pattern IE_LINK = Pattern.compile("(<link;[^;>]*;)([^;>]*)((?:;[^;>]*)?>)");
    private static final Pattern IE_OTHER = Pattern.compile("<&[^>]*>|<(?:config|keybind|dim|item);[^>]*>|\\\\$");
    // Markdown: imágenes, código, etiquetas (<ItemLink id="…" />) y caracteres escapados; los links son [texto](destino).
    private static final Pattern MD_LINK = Pattern.compile("(\\[)([^\\]]+)(\\]\\([^)]*\\))");
    private static final Pattern MD_OTHER = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)|`[^`]+`|</?[A-Za-z][^<>]*>|\\\\[\\\\`*_{}\\[\\]()#+.!|-]");
    private static final Pattern FRONT_TITLE = Pattern.compile("(\\s*title:\\s*[\"']?)(.*?)([\"']?\\s*)");
    private static final Pattern HEADING = Pattern.compile("(\\s*#{1,6}\\s+)(.*)");
    private static final Pattern ITEM = Pattern.compile("(\\s*(?:[-*+]|\\d+[.)]|>)\\s+)(.*)");
    private static final Pattern INDENT = Pattern.compile("(\\s*)(.*)");
    private static final Pattern TABLE_RULE = Pattern.compile("\\s*\\|?[\\s:|-]+\\|?\\s*");
    private static final Pattern OPEN_TAG = Pattern.compile("</?[A-Za-z]");

    private static final Pattern LINK_BACK = Pattern.compile("<l(\\d+)>(.*?)</l\\1>");
    private static final Pattern TAG_BACK = Pattern.compile("</?[lt](\\d+)>");
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");

    private TextPages() {}

    /** Arma el JSON pedazo por pedazo. */
    private static final class Builder {
        final JsonObject out = new JsonObject();
        final JsonArray fixed = new JsonArray(), sep = new JsonArray(), tags = new JsonArray();
        final Pattern other, link;
        int n;

        Builder(Pattern other, Pattern link) { this.other = other; this.link = link; }

        void add(String s, boolean isFixed, String after) {
            if (s.isEmpty() && after.isEmpty()) return;
            out.addProperty(String.valueOf(n), s);
            if (isFixed) fixed.add(n);
            sep.add(after);
            n++;
        }

        void fixed(String s) { add(s, true, "\n"); }
        void fixed(String s, String after) { add(s, true, after); }
        void text(String s, String after) { add(other == null ? s : protect(s), false, after); }

        /** Cierra el párrafo abierto (si hay) y devuelve null para dejarlo cerrado. */
        String flush(String para) {
            if (para != null) text(para, "\n");
            return null;
        }

        /** Cambia las marcas propias por &lt;tN&gt; y los links por &lt;lN&gt;texto&lt;/lN&gt;, guardando las originales. */
        String protect(String s) {
            StringBuilder sb = new StringBuilder();
            Matcher m = other.matcher(s);
            while (m.find()) {
                tags.add(m.group());
                m.appendReplacement(sb, "<t" + tags.size() + ">");
            }
            m.appendTail(sb);
            s = sb.toString();
            sb.setLength(0);
            m = link.matcher(s);
            while (m.find()) {
                tags.add(m.group(1) + "\u0000" + m.group(3));
                m.appendReplacement(sb, Matcher.quoteReplacement("<l" + tags.size() + ">" + m.group(2) + "</l" + tags.size() + ">"));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        /** Fila de tabla: cada celda con texto es un pedazo; las barras y los espacios quedan fijos. */
        void tableRow(String line) {
            if (TABLE_RULE.matcher(line).matches()) { fixed(line); return; }
            String[] cells = line.split("\\|", -1);
            StringBuilder pending = new StringBuilder();
            for (int k = 0; k < cells.length; k++) {
                if (k > 0) pending.append('|');
                String cell = cells[k];
                if (!hasLetters(cell)) { pending.append(cell); continue; }
                Matcher m = Pattern.compile("(\\s*)(.*?)(\\s*)").matcher(cell);
                m.matches();
                fixed(pending.append(m.group(1)).toString(), "");
                pending.setLength(0);
                text(m.group(2), "");
                pending.append(m.group(3));
            }
            fixed(pending.toString());
        }

        JsonObject build() {
            out.add(SEP, sep);
            if (!fixed.isEmpty()) out.add(FIXED, fixed);
            if (!tags.isEmpty()) out.add(TAGS, tags);
            return out;
        }
    }

    static JsonObject parse(Reader reader, Kind kind) throws IOException {
        List<String> lines = new BufferedReader(reader).lines().toList();
        if (kind == Kind.MARKDOWN) return markdown(lines);
        Builder b = new Builder(kind == Kind.IE ? IE_OTHER : null, IE_LINK);
        for (String line : lines) b.text(line, "\n");
        return b.build();
    }

    private static JsonObject markdown(List<String> lines) {
        Builder b = new Builder(MD_OTHER, MD_LINK);
        String para = null;
        boolean front = false, fence = false, inTag = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i), trimmed = line.trim();
            if (i == 0 && line.equals("---")) { front = true; b.fixed(line); continue; }
            if (front) {
                // Encabezado YAML: solo el título de la navegación es texto.
                if (line.equals("---")) front = false;
                Matcher m = FRONT_TITLE.matcher(line);
                if (front && m.matches() && hasLetters(m.group(2))) {
                    b.fixed(m.group(1), "");
                    b.add(m.group(2), false, "");
                    b.fixed(m.group(3));
                } else {
                    b.fixed(line);
                }
                continue;
            }
            if (fence || trimmed.startsWith("```")) {
                para = b.flush(para);
                if (trimmed.startsWith("```")) fence = !fence;
                b.fixed(line);
                continue;
            }
            if (inTag) {
                b.fixed(line);
                if (line.indexOf('>') >= 0) inTag = false;
                continue;
            }
            if (trimmed.isEmpty()) { para = b.flush(para); b.fixed(line); continue; }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) { para = b.flush(para); b.fixed(h.group(1), ""); b.text(h.group(2), "\n"); continue; }
            if (trimmed.startsWith("|")) { para = b.flush(para); b.tableRow(line); continue; }
            // Etiqueta que sigue en las líneas de abajo (<RecipeFor\n  id="…"\n/>).
            if (OPEN_TAG.matcher(trimmed).lookingAt() && trimmed.indexOf('>') < 0) { para = b.flush(para); inTag = true; b.fixed(line); continue; }
            // Línea sin texto: solo etiquetas (<GameScene …>, <Row>, <ItemImage … />).
            if (!hasLetters(line)) { para = b.flush(para); b.fixed(line); continue; }
            Matcher li = ITEM.matcher(line);
            if (li.matches()) { para = b.flush(para); b.fixed(li.group(1), ""); para = li.group(2); continue; }
            if (para == null) {
                Matcher ind = INDENT.matcher(line);
                ind.matches();
                b.fixed(ind.group(1), "");
                para = ind.group(2);
            } else {
                // Las líneas cortadas de un mismo párrafo se juntan: en Markdown se ven igual y la IA traduce la oración entera.
                para += " " + trimmed;
            }
        }
        b.flush(para);
        return b.build();
    }

    /** Tiene letras fuera de las etiquetas (&lt;NEWLINE&gt;, &lt;np&gt; y &lt;Row&gt; solas no son texto). */
    static boolean hasLetters(String s) {
        return ANY_TAG.matcher(s).replaceAll("").chars().anyMatch(Character::isLetter);
    }

    static void extract(JsonElement el, BiConsumer<String, String> out) {
        JsonObject o = el.getAsJsonObject();
        Set<Integer> fixed = fixed(o);
        for (int i = 0; o.has(String.valueOf(i)); i++) {
            String s = o.get(String.valueOf(i)).getAsString();
            if (!fixed.contains(i) && hasLetters(s)) out.accept("/" + i, s);
        }
    }

    static String write(JsonElement el) {
        JsonObject o = el.getAsJsonObject();
        Set<Integer> fixed = fixed(o);
        JsonArray tags = o.has(TAGS) ? o.getAsJsonArray(TAGS) : new JsonArray();
        JsonArray sep = o.has(SEP) ? o.getAsJsonArray(SEP) : new JsonArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; o.has(String.valueOf(i)); i++) {
            String s = o.get(String.valueOf(i)).getAsString();
            if (!fixed.contains(i)) s = restore(s.replace("\n", " "), tags);
            sb.append(s).append(i < sep.size() ? sep.get(i).getAsString() : "\n");
        }
        return sb.toString();
    }

    private static Set<Integer> fixed(JsonObject o) {
        Set<Integer> out = new HashSet<>();
        if (o.has(FIXED)) for (JsonElement e : o.getAsJsonArray(FIXED)) out.add(e.getAsInt());
        return out;
    }

    private static String restore(String s, JsonArray tags) {
        if (tags.isEmpty()) return s;
        StringBuilder b = new StringBuilder();
        Matcher m = LINK_BACK.matcher(s);
        while (m.find()) {
            String[] parts = tag(tags, m.group(1)).split("\u0000", -1);
            m.appendReplacement(b, Matcher.quoteReplacement(parts.length == 2 ? parts[0] + m.group(2) + parts[1] : m.group(2)));
        }
        m.appendTail(b);
        s = b.toString();
        b.setLength(0);
        // <tN> vuelve a ser la marca; un <lN> o </lN> suelto (la IA los separó) se quita.
        m = TAG_BACK.matcher(s);
        while (m.find()) {
            String t = m.group().startsWith("<t") ? tag(tags, m.group(1)) : "";
            m.appendReplacement(b, Matcher.quoteReplacement(t.contains("\u0000") ? "" : t));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String tag(JsonArray tags, String number) {
        int n = Integer.parseInt(number);
        return n >= 1 && n <= tags.size() ? tags.get(n - 1).getAsString() : "";
    }
}
