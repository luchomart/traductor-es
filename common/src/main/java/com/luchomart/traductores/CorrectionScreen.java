package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Editor de correcciones: buscar entre lo que tradujo la IA (objetos, libros y misiones), elegir un texto y
 * corregirlo. Se guarda en correcciones.json y se aplica enseguida, sin usar la IA.
 */
public class CorrectionScreen extends BaseScreen {
    /** Un texto traducido. corrected es null si no tiene corrección. */
    private static final class Entry {
        final String ns, key, original, ai;
        String corrected;
        final String haystack, sortKey;

        Entry(String ns, String key, String original, String ai, String corrected) {
            this.ns = ns; this.key = key; this.original = original; this.ai = ai; this.corrected = corrected;
            this.sortKey = SORT_PREFIX.matcher(original).replaceFirst("").toLowerCase(Locale.ROOT);
            this.haystack = (original + "\n" + ai + "\n" + (corrected == null ? "" : corrected) + "\n" + ns + ":" + key).toLowerCase(Locale.ROOT);
        }

        String current() { return corrected != null ? corrected : ai; }
    }

    private static final int ROW = 24, TOP = 52;
    private static final java.util.regex.Pattern SECTION = java.util.regex.Pattern.compile("§([0-9a-fk-orA-FK-OR])");
    private static final java.util.regex.Pattern AMP_CODE = java.util.regex.Pattern.compile("&[0-9a-fk-orA-FK-OR]");
    /** Cómo se muestran los códigos § en el editor: "&" (como en los servidores), o "¤" si el texto ya usa & propios. */
    private String codeMark = "&";
    /** Lo que se saltea al ordenar: espacios, símbolos y códigos de color al principio. */
    private static final java.util.regex.Pattern SORT_PREFIX = java.util.regex.Pattern.compile("^(?:[§&][0-9a-fk-orA-FK-OR]|[^\\p{L}\\p{N}])+");

    private final Screen parent;
    private volatile List<Entry> entries;
    private volatile String loadError;
    private final List<Entry> matches = new ArrayList<>();
    private String query;
    private int page;
    private int W, left, perPage;

    private EditBox search;
    private final List<Button> rows = new ArrayList<>();
    private Button prev, next;

    /** Texto que se está editando (null = viendo la lista). */
    private Entry editing;
    private MultiLineEditBox editor;
    private String warning;
    /** Se tocó Guardar con un problema de marcadores: el segundo toque guarda igual. */
    private boolean confirmWarning;
    private String status;

    public CorrectionScreen(Screen parent, String query) {
        super(Component.literal("Corregir traducciones"));
        this.parent = parent;
        this.query = query == null ? "" : query;
        load();
    }

    private void load() {
        Minecraft mc = Minecraft.getInstance();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        if (target == null) {
            loadError = "El juego no está en español: elegí tu español en Opciones → Idioma.";
            entries = List.of();
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Map<String, Map<String, String>> corr = Corrections.load();
                List<Entry> out = new ArrayList<>();
                new TranslationCache(target).loadAll().forEach((ns, m) -> m.forEach((key, e) -> {
                    // Sin letras (espacios, símbolos) no hay nada que corregir.
                    if (Validator.isTrivial(e.o)) return;
                    out.add(new Entry(ns, key, e.o, e.t, corr.getOrDefault(ns, Map.of()).get(key)));
                }));
                // Correcciones escritas a mano para textos que la IA no tradujo (oficiales, por ejemplo).
                corr.forEach((ns, m) -> m.forEach((key, text) -> {
                    if (out.stream().noneMatch(e -> e.ns.equals(ns) && e.key.equals(key))) out.add(new Entry(ns, key, "", "", text));
                }));
                // Por la primera letra: "  - Potions" va con la P, no antes de todo por el espacio.
                out.sort(java.util.Comparator.comparing((Entry e) -> e.sortKey));
                entries = out;
            } catch (Exception e) {
                loadError = "No se pudo leer lo traducido: " + e.getMessage();
                entries = List.of();
            }
            mc.execute(this::refresh);
        }, "Traductor ES - correcciones");
        t.setDaemon(true);
        t.start();
    }

    // ---------- Construcción ----------

    @Override
    protected void init() {
        W = Math.min(width - 24, 460);
        left = (width - W) / 2;
        rows.clear();
        if (editing != null) initEditor();
        else initList();
    }

    private void initList() {
        search = new EditBox(font, left, 26, W, 20, Component.literal("Buscar"));
        search.setMaxLength(200);
        search.setValue(query);
        search.setHint(Component.literal("Buscá en inglés o en español (nombre, descripción, mod…)").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> {
            query = s;
            page = 0;
            refresh();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        perPage = Math.max(3, (height - TOP - 34) / ROW);
        int by = height - 28;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); }).bounds(left, by, 20, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); }).bounds(left + 24, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose()).bounds(left + W - 100, by, 100, 20).build());
        refresh();
    }

    private void initEditor() {
        Entry e = editing;
        int boxTop = originalBottom() + 16;
        int by = height - 28;
        int boxH = Math.max(40, by - 22 - boxTop);
        editor = new MultiLineEditBox(font, left, boxTop, W, boxH, Component.literal("Escribí la traducción correcta"), Component.literal("Traducción"));
        editor.setCharacterLimit(10000);
        // Minecraft dibuja los códigos § como colores y no se ven: en el editor aparecen como &9, &r…
        codeMark = AMP_CODE.matcher(e.original + e.current()).find() ? "¤" : "&";
        editor.setValue(visibleCodes(e.current()));
        addRenderableWidget(editor);
        setInitialFocus(editor);
        Button save = addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(left + W - 90, by, 90, 20).build());
        save.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Se guarda en correcciones.json y se aplica enseguida.")));
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> closeEditor(null)).bounds(left + W - 184, by, 90, 20).build());
        if (e.corrected != null && !e.ai.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Volver a la de la IA"), b -> restore()).bounds(left, by, 130, 20).build());
        }
    }

    /** Dónde termina el bloque del original en el editor (hasta 6 renglones). */
    private int originalBottom() {
        int lines = Math.min(6, Math.max(1, font.split(Component.literal(editing.original), W).size()));
        return 46 + lines * (font.lineHeight + 1);
    }

    /** Vuelve a filtrar y arma las filas de la página actual. */
    private void refresh() {
        if (editing != null || search == null) return;
        for (Button b : rows) removeWidget(b);
        rows.clear();
        List<Entry> all = entries;
        matches.clear();
        if (all == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Entry e : all) if (q.isEmpty() || e.haystack.contains(q)) matches.add(e);
        int pages = Math.max(1, (matches.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int y = TOP;
        for (int i = page * perPage; i < Math.min(matches.size(), (page + 1) * perPage); i++) {
            Entry e = matches.get(i);
            rows.add(addRenderableWidget(new Row(left, y, W, ROW - 2, e, b -> openEditor(e))));
            y += ROW;
        }
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    // ---------- Acciones ----------

    private void openEditor(Entry e) {
        editing = e;
        warning = null;
        confirmWarning = false;
        status = null;
        rebuildWidgets();
    }

    private void closeEditor(String message) {
        editing = null;
        editor = null;
        status = message;
        rebuildWidgets();
    }

    private String visibleCodes(String s) {
        return SECTION.matcher(s).replaceAll(java.util.regex.Matcher.quoteReplacement(codeMark) + "$1");
    }

    private String realCodes(String s) {
        return s.replaceAll(java.util.regex.Pattern.quote(codeMark) + "([0-9a-fk-orA-FK-OR])", "§$1");
    }

    private void save() {
        Entry e = editing;
        String text = realCodes(editor.getValue());
        if (text.isBlank()) {
            warning = "La traducción está vacía. Si querés volver a la de la IA, usá «Volver a la de la IA».";
            return;
        }
        String problem = e.original.isEmpty() ? null : Validator.check(e.original, text);
        if (problem != null && !confirmWarning) {
            warning = "Ojo: " + problem + " respecto del original (colores, %s, {0}…). Puede verse mal. Tocá Guardar otra vez para guardarla igual.";
            confirmWarning = true;
            return;
        }
        try {
            if (text.equals(e.ai)) Corrections.remove(e.ns, e.key);
            else Corrections.put(e.ns, e.key, text);
            e.corrected = text.equals(e.ai) ? null : text;
            TraductorES.LOG.info("[Traductor ES] Corrección guardada: {}:{}", e.ns, e.key);
            closeEditor(applied(e));
        } catch (Exception ex) {
            warning = "No se pudo guardar: " + ex.getMessage();
        }
    }

    private void restore() {
        Entry e = editing;
        try {
            Corrections.remove(e.ns, e.key);
            e.corrected = null;
            closeEditor(applied(e));
        } catch (Exception ex) {
            warning = "No se pudo guardar: " + ex.getMessage();
        }
    }

    /** Aplica las correcciones y devuelve el mensaje para el jugador. */
    private static String applied(Entry e) {
        if (!Traductor.applyCorrections()) return "Guardado. Se aplica cuando termine la traducción en curso.";
        if (e.ns.equals(FtbQuests.NS)) return "Guardado. Las misiones se ven al volver a entrar al mundo.";
        return "Guardado y aplicado.";
    }

    @Override
    public void onClose() {
        if (editing != null) closeEditor(null);
        else minecraft.setScreen(parent);
    }

    // ---------- Dibujo ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (editing != null) {
            renderEditor(g);
            return;
        }
        g.drawString(font, "Corregir traducciones", left, 10, UiKit.GOLD, true);
        List<Entry> all = entries;
        String info;
        if (loadError != null) info = loadError;
        else if (all == null) info = "Cargando…";
        else if (all.isEmpty()) info = "Todavía no hay nada traducido por la IA.";
        else if (matches.isEmpty()) info = "Nada coincide con la búsqueda.";
        else {
            int pages = Math.max(1, (matches.size() + perPage - 1) / perPage);
            info = Traductor.fmt(matches.size()) + " textos · página " + (page + 1) + " de " + pages;
        }
        g.drawString(font, font.plainSubstrByWidth(info, W - 160), left + 50, height - 22, UiKit.MUTED, false);
        if (status != null) g.drawString(font, font.plainSubstrByWidth(status, W - 120), left + 150, 10, UiKit.GREEN, false);
        if (all != null && all.isEmpty() && loadError == null) {
            UiKit.wrappedCentered(g, font, Component.literal("Cuando la IA traduzca algo, aparece acá para corregirlo."), width / 2, TOP + 10, W, UiKit.MUTED);
        }
    }

    private void renderEditor(GuiGraphics g) {
        Entry e = editing;
        g.drawString(font, "Corregir · " + sourceLabel(e.ns), left, 10, UiKit.GOLD, true);
        g.drawString(font, font.plainSubstrByWidth(e.ns + ":" + e.key, W), left, 22, UiKit.DIM, false);
        g.drawString(font, "Original", left, 36, UiKit.MUTED, false);
        int y = 46, shown = 0;
        for (var line : font.split(Component.literal(e.original.isEmpty() ? "(texto oficial, sin original guardado)" : visibleCodes(e.original)), W)) {
            if (++shown > 6) break;
            g.drawString(font, line, left, y, UiKit.WHITE, false);
            y += font.lineHeight + 1;
        }
        String label = e.corrected != null ? "Tu corrección" : "Traducción de la IA";
        if (SECTION.matcher(e.original + e.current()).find()) label += "  ·  " + codeMark + "9, " + codeMark + "r… son colores: dejalos en su lugar";
        g.drawString(font, font.plainSubstrByWidth(label, W), left, originalBottom() + 4, UiKit.MUTED, false);
        if (warning != null) {
            UiKit.wrapped(g, font, Component.literal(warning), left, height - 48, W, UiKit.YELLOW);
        }
    }

    /** "Misiones", "Libro: nombre" o el mod. */
    static String sourceLabel(String ns) {
        if (ns.equals(FtbQuests.NS)) return "Misiones";
        if (ns.startsWith(PatchouliBooks.PREFIX)) return "Libro: " + ns.substring(ns.lastIndexOf('/') + 1);
        return ns;
    }

    /** Fila de la lista: original arriba, traducción abajo y de dónde es a la derecha. */
    private class Row extends Button {
        private final Entry e;

        Row(int x, int y, int w, int h, Entry e, OnPress onPress) {
            super(x, y, w, h, Component.literal(e.original), onPress, DEFAULT_NARRATION);
            this.e = e;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, hover ? 0xFF8A8A8A : 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF33363C : 0xFF24262B);
            Font font = Minecraft.getInstance().font;
            String tag = (e.corrected != null ? "★ " : "") + sourceLabel(e.ns);
            int tagW = Math.min(110, font.width(tag));
            int textW = w - tagW - 18;
            g.drawString(font, font.plainSubstrByWidth(oneLine(e.original), textW), x + 5, y + 3, UiKit.DIM, false);
            g.drawString(font, font.plainSubstrByWidth(oneLine(e.current()), textW), x + 5, y + 13, e.corrected != null ? UiKit.GOLD : UiKit.WHITE, false);
            g.drawString(font, font.plainSubstrByWidth(tag, tagW), x + w - tagW - 5, y + 8, e.corrected != null ? UiKit.GOLD : UiKit.MUTED, false);
        }

        private String oneLine(String s) {
            return s.replace('\n', ' ').replace("\\n", " ");
        }
    }
}
