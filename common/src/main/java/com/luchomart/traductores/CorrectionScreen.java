package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Editor de correcciones: buscar entre lo que tradujo la IA o se bajó de la comunidad (objetos, libros y misiones), elegir un texto y
 * corregirlo. Se guarda en correcciones.json y se aplica enseguida, sin usar la IA.
 */
public class CorrectionScreen extends BaseScreen {
    /** De dónde sale la traducción que se ve (sin contar la corrección del jugador). */
    /** De dónde viene la traducción de base: cómo se muestra y cómo se vuelve a ella, en un solo lugar. */
    private enum Origin {
        AI("IA", "Traducción de la IA", "Volver a la de la IA", "Volver a la IA", UiKit.AQUA),
        COMMUNITY("Comunidad", "Traducción de la comunidad (de una IA)", "Volver a la de la comunidad", "Volver a la comunidad", UiKit.GREEN),
        COMMUNITY_REVISED("Comunidad ✓", "Traducción de la comunidad (revisada)", "Volver a la de la comunidad", "Volver a la comunidad", UiKit.GREEN),
        MISSING("En inglés", "Sin traducir todavía: escribilo en español (después compartilo en Herramientas → Compartir…)",
                "Volver al inglés", "Volver al inglés", UiKit.YELLOW),
        /** Corrección de un texto que no tradujo nadie (oficial, por ejemplo): siempre tiene corrección. */
        NONE("IA", "Traducción de la IA", "Volver a la de la IA", "Volver a la IA", UiKit.AQUA);

        /** Etiqueta en la fila, rótulo arriba del editor, texto del botón de volver (largo y corto) y color. */
        final String tag, label, restore, restoreShort;
        final int color;

        Origin(String tag, String label, String restore, String restoreShort, int color) {
            this.tag = tag; this.label = label; this.restore = restore; this.restoreShort = restoreShort; this.color = color;
        }
    }

    /**
     * Un texto traducido. ai es la traducción de base (de la IA o de la comunidad, según origin); corrected es null si
     * no tiene corrección.
     */
    private static final class Entry {
        final String ns, key, original, ai;
        final Origin origin;
        String corrected;
        /** Dónde busca el buscador: se rehace al cambiar la corrección. */
        String haystack;
        final String sortKey;
        /** El validador cree que quedó sin traducir y nadie lo confirmó. */
        final boolean doubtful;

        Entry(String ns, String key, String original, String ai, Origin origin, String corrected, boolean doubtful) {
            this.ns = ns; this.key = key; this.original = original; this.ai = ai; this.origin = origin; this.corrected = corrected;
            this.doubtful = doubtful;
            this.sortKey = SORT_PREFIX.matcher(original).replaceFirst("").toLowerCase(Locale.ROOT);
            this.haystack = haystack();
        }

        void setCorrected(String text) {
            corrected = text;
            haystack = haystack();
        }

        private String haystack() {
            return (original + "\n" + ai + "\n" + (corrected == null ? "" : corrected) + "\n" + ns + ":" + key).toLowerCase(Locale.ROOT);
        }

        String current() { return corrected != null ? corrected : ai; }

        boolean fromCommunity() { return origin == Origin.COMMUNITY || origin == Origin.COMMUNITY_REVISED; }
    }

    /** Pestañas de arriba de la lista. */
    private enum Filter {
        ALL("Todo", "Todos los textos."),
        PENDING("Sin corregir", "Lo que todavía no corregiste."),
        CORRECTED("Corregidas", "Lo que ya corregiste (★)."),
        DOUBTFUL("Dudosas", "Lo que parece haber quedado en inglés: buen lugar para empezar a revisar."),
        COMMUNITY("Comunidad", "Lo que se bajó de las traducciones de la comunidad (✓ = revisado por una persona)."),
        MISSING("Faltan", "Lo que quedó en inglés porque la comunidad todavía no lo tiene. Traducilo con «Guardar y siguiente» "
                + "y compartilo en Herramientas → Compartir…: así le sirve a todos.");

        final String label, help;

        Filter(String label, String help) { this.label = label; this.help = help; }

        boolean test(Entry e) {
            return switch (this) {
                case ALL -> true;
                case PENDING -> e.corrected == null;
                case CORRECTED -> e.corrected != null;
                case DOUBTFUL -> e.corrected == null && e.doubtful;
                case COMMUNITY -> e.fromCommunity();
                case MISSING -> e.origin == Origin.MISSING && e.corrected == null;
            };
        }
    }

    /** Un mod (o libro, misiones…) en el selector, con cuántos textos tiene con el filtro actual. */
    private record ModItem(String ns, String label, int count) {}

    private static final int ROW = 24, TOP = 74;
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
    private Filter filter = Filter.ALL;
    /** Hay textos anotados como faltantes (modo "solo comunidad"): recién ahí se muestra la pestaña "Faltan". */
    private boolean showMissingTab;
    /** Mod elegido (null = todos). */
    private String mod;
    /** Se está eligiendo un mod: la lista muestra mods y el buscador los filtra. */
    private boolean pickingMod;
    /** La búsqueda de textos, guardada mientras se elige un mod. */
    private String savedQuery;
    private final List<ModItem> modMatches = new ArrayList<>();
    /** Primera fila visible de la lista (se scrollea de a filas enteras). */
    private int scroll;
    private int W, left, perPage;
    /** Se está arrastrando la barra de scroll. */
    private boolean dragging;

    private EditBox search;
    private final List<Button> rows = new ArrayList<>();

    /** Texto que se está editando (null = viendo la lista). */
    private Entry editing;
    private MultiLineEditBox editor;
    private int editorBottom;
    private String warning;
    /** Lo escrito en el editor, para no perderlo al rearmar la pantalla (por ejemplo, al mostrar un aviso). */
    private String draft;
    /** Se tocó Guardar con un problema de marcadores: el segundo toque guarda igual. */
    private boolean confirmWarning;
    private String status;
    /** Error al guardar un reemplazo masivo (en rojo, arriba de la lista). */
    private String warningInList;
    /** Hay correcciones guardadas con "Guardar y siguiente" sin aplicar: se aplican todas juntas al volver a la lista. */
    private boolean pendingApply, pendingQuests;

    public CorrectionScreen(Screen parent, String query) {
        super(Component.literal("Corregir traducciones"));
        this.parent = parent;
        this.query = query == null ? "" : query;
        load();
    }

    /** El editor abierto en la pestaña "Faltan" (desde el aviso de fin de pasada). */
    public static CorrectionScreen missing(Screen parent) {
        CorrectionScreen s = new CorrectionScreen(parent, "");
        if (s.showMissingTab) s.filter = Filter.MISSING;
        return s;
    }

    private void load() {
        Minecraft mc = Minecraft.getInstance();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        if (target == null) {
            loadError = "El juego no está en español: elegí tu español en Opciones → Idioma.";
            entries = List.of();
            return;
        }
        Path missingFile = MissingTexts.file(Config.DIR.resolve("compartidas"), target);
        showMissingTab = Files.exists(missingFile);
        Thread t = new Thread(() -> {
            try {
                Map<String, Map<String, String>> corr = Corrections.load();
                // Por "ns\0clave": lo de la comunidad reemplaza al caché (es lo que se ve en el juego).
                Map<String, Entry> byKey = new java.util.LinkedHashMap<>();
                new TranslationCache(target).loadAll().forEach((ns, m) -> m.forEach((key, e) -> {
                    // Sin letras (espacios, símbolos) no hay nada que corregir.
                    if (Validator.isTrivial(e.o)) return;
                    boolean doubtful = Validator.looksUntranslated(e.o, e.t) && e.r == null && e.s == null;
                    byKey.put(ns + "\0" + key, new Entry(ns, key, e.o, e.t, Origin.AI, corr.getOrDefault(ns, Map.of()).get(key), doubtful));
                }));
                CommunityUsed.read(CommunityUsed.file(Config.DIR.resolve("compartidas"), target)).forEach((ns, m) -> m.forEach((key, e) -> {
                    if (Validator.isTrivial(e.o)) return;
                    Origin origin = e.revised() ? Origin.COMMUNITY_REVISED : Origin.COMMUNITY;
                    boolean doubtful = !e.revised() && Validator.looksUntranslated(e.o, e.t);
                    byKey.put(ns + "\0" + key, new Entry(ns, key, e.o, e.t, origin, corr.getOrDefault(ns, Map.of()).get(key), doubtful));
                }));
                // Lo que quedó en inglés sin IA: el "de base" es el mismo original (corregirlo es traducirlo).
                MissingTexts.read(missingFile).forEach((ns, m) -> m.forEach((key, english) ->
                        // put: si el caché tiene una traducción de un original viejo, lo que vale es que hoy falta.
                        byKey.put(ns + "\0" + key, new Entry(ns, key, english, english, Origin.MISSING,
                                corr.getOrDefault(ns, Map.of()).get(key), false))));
                // Correcciones escritas a mano para textos que no tradujo ni la IA ni la comunidad (oficiales, por ejemplo).
                corr.forEach((ns, m) -> m.forEach((key, text) ->
                        byKey.putIfAbsent(ns + "\0" + key, new Entry(ns, key, "", "", Origin.NONE, text, false))));
                List<Entry> out = new ArrayList<>(byKey.values());
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
        // Pestañas a la izquierda y el selector de mod a la derecha.
        int modW = Math.min(140, Math.max(90, W - 300));
        int tabsW = W - modW - 6;
        Filter[] fs = java.util.Arrays.stream(Filter.values()).filter(f -> f != Filter.MISSING || showMissingTab).toArray(Filter[]::new);
        // Cada pestaña, del ancho de su texto; lo que sobra se reparte.
        int textSum = 0;
        for (Filter f : fs) textSum += font.width(f.label);
        int extra = Math.max(0, tabsW - textSum) / fs.length;
        int tx = left;
        for (int i = 0; i < fs.length; i++) {
            Filter f = fs[i];
            int tw = i == fs.length - 1 ? left + tabsW - tx : font.width(f.label) + extra;
            Button tab = addRenderableWidget(new TabButton(tx, 24, tw, 18, Component.literal(f.label), b -> setFilter(f), () -> filter == f && !pickingMod));
            Widgets.tooltip(tab, Component.literal(f.help));
            tx += tw;
        }
        String modLabel = pickingMod ? "Elegí un mod ▴" : mod == null ? "Mod: todos ▾" : "Mod: " + sourceLabel(mod) + " ▾";
        Button modButton = addRenderableWidget(Widgets.button(Component.literal(font.plainSubstrByWidth(modLabel, modW - 8)), b -> toggleModPicker())
                .bounds(left + W - modW, 23, modW, 20).build());
        Widgets.tooltip(modButton, Component.literal(mod == null ? "Ver los textos de un solo mod, libro o las misiones." : "Elegir otro mod, o «Todos»."));

        search = new EditBox(font, left, 48, W, 20, Component.literal("Buscar"));
        search.setMaxLength(200);
        search.setValue(query);
        Widgets.hint(search, Component.literal(pickingMod ? "Buscá el mod por nombre…" : "Buscá en inglés o en español (nombre, descripción, mod…)").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            refresh();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        perPage = Math.max(3, (height - TOP - 34) / ROW);
        int by = height - 28;
        addRenderableWidget(Widgets.button(Component.literal("Volver"), b -> onClose()).bounds(left + W - 100, by, 100, 20).build());
        if (!pickingMod) {
            Button replace = addRenderableWidget(Widgets.button(Component.literal("Reemplazar…"), b -> openReplace()).bounds(left + W - 204, by, 100, 20).build());
            Widgets.tooltip(replace, Component.literal("Cambiar una palabra o frase en todos los textos de la lista (con el filtro y la búsqueda de ahora), viendo antes cómo queda cada uno."));
        }
        refresh();
    }

    private void initEditor() {
        Entry e = editing;
        int boxTop = originalBottom() + 16;
        int by = height - 28;
        // Con un aviso, el cuadro se achica para que el aviso entre entre el contador y los botones.
        int boxH = Math.max(40, by - 22 - boxTop - (warning != null ? 34 : 0));
        editorBottom = boxTop + boxH;
        editor = new MultiLineEditBox(font, left, boxTop, W, boxH, Component.literal("Escribí la traducción correcta"), Component.literal("Traducción"));
        editor.setCharacterLimit(10000);
        // Minecraft dibuja los códigos § como colores y no se ven: en el editor aparecen como &9, &r…
        codeMark = AMP_CODE.matcher(e.original + e.current()).find() ? "¤" : "&";
        editor.setValue(draft != null ? draft : visibleCodes(e.current()));
        addRenderableWidget(editor);
        setInitialFocus(editor);
        // Abajo a la derecha: Cancelar · Guardar · Guardar y siguiente. A la izquierda, volver a la de la IA.
        boolean narrow = W < 400;
        int nextW = narrow ? 84 : 120, btnW = narrow ? 62 : 74;
        Button saveNext = addRenderableWidget(Widgets.button(Component.literal(narrow ? "Guardar ▸" : "Guardar y siguiente"), b -> save(true))
                .bounds(left + W - nextW, by, nextW, 20).build());
        saveNext.active = nextEntry() != null;
        Widgets.tooltip(saveNext, Component.literal("Guarda y abre el texto siguiente de la lista (Ctrl+Enter). "
                + "Lo corregido se aplica todo junto al volver a la lista, así el juego no recarga a cada rato."));
        Button save = addRenderableWidget(Widgets.button(Component.literal("Guardar"), b -> save(false)).bounds(left + W - nextW - 4 - btnW, by, btnW, 20).build());
        Widgets.tooltip(save, Component.literal("Se guarda en correcciones.json y se aplica enseguida."));
        addRenderableWidget(Widgets.button(Component.literal("Cancelar"), b -> closeEditor(null)).bounds(left + W - nextW - 8 - 2 * btnW, by, btnW, 20).build());
        if (e.corrected != null && !e.ai.isEmpty()) {
            int rw = Math.min(150, W - nextW - 12 - 2 * btnW);
            String full = e.origin.restore, brief = e.origin.restoreShort;
            addRenderableWidget(Widgets.button(Component.literal(font.plainSubstrByWidth(font.width(full) + 10 <= rw ? full : brief, rw - 6)), b -> restore())
                    .bounds(left, by, rw, 20).build());
        }
    }

    /** El texto que sigue al que se está editando en la lista filtrada, o null si es el último. */
    private Entry nextEntry() {
        int i = matches.indexOf(editing);
        return i >= 0 && i + 1 < matches.size() ? matches.get(i + 1) : null;
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
        if (pickingMod) {
            refreshMods(all, q);
            return;
        }
        for (Entry e : all) {
            if (filter.test(e) && (mod == null || e.ns.equals(mod)) && (q.isEmpty() || e.haystack.contains(q))) matches.add(e);
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        int y = TOP;
        for (int i = scroll; i < Math.min(matches.size(), scroll + perPage); i++) {
            Entry e = matches.get(i);
            rows.add(addRenderableWidget(new Row(left, y, W - BAR - 3, ROW - 2, e, b -> openEditor(e))));
            y += ROW;
        }
    }

    /** El selector de mods: "Todos" y cada mod con textos según la pestaña elegida, del que más tiene al que menos. */
    private void refreshMods(List<Entry> all, String q) {
        modMatches.clear();
        Map<String, Integer> counts = new java.util.HashMap<>();
        int total = 0;
        for (Entry e : all) {
            if (!filter.test(e)) continue;
            counts.merge(e.ns, 1, Integer::sum);
            total++;
        }
        if (q.isEmpty()) modMatches.add(new ModItem(null, "Todos", total));
        List<ModItem> mods = new ArrayList<>();
        counts.forEach((ns, n) -> {
            String label = sourceLabel(ns);
            if (q.isEmpty() || label.toLowerCase(Locale.ROOT).contains(q) || ns.toLowerCase(Locale.ROOT).contains(q)) mods.add(new ModItem(ns, label, n));
        });
        mods.sort(java.util.Comparator.comparingInt((ModItem m) -> -m.count()).thenComparing(ModItem::label));
        modMatches.addAll(mods);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        int y = TOP;
        for (int i = scroll; i < Math.min(modMatches.size(), scroll + perPage); i++) {
            ModItem m = modMatches.get(i);
            rows.add(addRenderableWidget(new ModRow(left, y, W - BAR - 3, ROW - 2, m, b -> pickMod(m.ns()))));
            y += ROW;
        }
    }

    /** Abre el reemplazo masivo sobre lo que muestra la lista ahora. */
    private void openReplace() {
        List<BulkReplace.Target> targets = new ArrayList<>();
        Map<String, Entry> byKey = new java.util.HashMap<>();
        for (Entry e : matches) {
            // Lo que sigue en inglés no se reemplaza: quedaría a medio traducir y guardado como corrección.
            if (e.current().isEmpty() || (e.origin == Origin.MISSING && e.corrected == null)) continue;
            targets.add(new BulkReplace.Target(e.ns, e.key, e.original, e.current(), e.ai));
            byKey.put(e.ns + ":" + e.key, e);
        }
        String scope = filter.label + (mod != null ? " · " + sourceLabel(mod) : "") + (query.isBlank() ? "" : " · «" + query.strip() + "»");
        minecraft.setScreen(new ReplaceScreen(this, targets, scope, changes -> applyReplace(changes, byKey)));
    }

    /** Guarda los reemplazos como correcciones, en una sola escritura, y los aplica una vez. */
    private void applyReplace(List<BulkReplace.Change> changes, Map<String, Entry> byKey) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        boolean quests = false;
        for (BulkReplace.Change c : changes) {
            BulkReplace.Target t = c.target();
            // Si quedó igual a la de base, no hace falta corrección.
            out.put(t.ns() + ":" + t.key(), c.after().equals(t.base()) ? null : c.after());
            quests |= t.ns().equals(FtbQuests.NS);
        }
        try {
            Corrections.putAll(out);
        } catch (Exception ex) {
            status = null;
            minecraft.setScreen(this);
            warningInList = "No se pudo guardar: " + ex.getMessage();
            return;
        }
        out.forEach((k, v) -> {
            Entry e = byKey.get(k);
            if (e != null) e.setCorrected(v);
        });
        TraductorES.LOG.info("[Traductor ES] Reemplazo masivo: {} textos", out.size());
        String applied = applyAll(quests);
        warningInList = null;
        status = Traductor.fmt(out.size()) + " reemplazados. " + applied;
        minecraft.setScreen(this);
    }

    private void setFilter(Filter f) {
        if (pickingMod) {
            // Cambiar de pestaña también cierra el selector de mods.
            pickingMod = false;
            query = savedQuery;
        }
        filter = f;
        scroll = 0;
        rebuildWidgets();
    }

    private void toggleModPicker() {
        if (pickingMod) {
            pickingMod = false;
            query = savedQuery;
        } else {
            pickingMod = true;
            savedQuery = query;
            query = "";
        }
        scroll = 0;
        rebuildWidgets();
    }

    private void pickMod(String ns) {
        mod = ns;
        pickingMod = false;
        query = savedQuery;
        scroll = 0;
        rebuildWidgets();
    }

    // ---------- Scroll ----------

    private static final int BAR = 6;

    private int listSize() {
        return pickingMod ? modMatches.size() : matches.size();
    }

    private int maxScroll() {
        return Math.max(0, listSize() - perPage);
    }

    private void scrollTo(int row) {
        int s = Math.max(0, Math.min(row, maxScroll()));
        if (s != scroll) {
            scroll = s;
            refresh();
        }
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        if (editing != null || amount == 0) return false;
        scrollTo(scroll - (int) Math.signum(amount) * 3);
        return true;
    }

    private int barX() { return left + W - BAR; }
    private int listBottom() { return TOP + perPage * ROW - 2; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editing == null && button == 0 && maxScroll() > 0 && mouseX >= barX() && mouseX < barX() + BAR
                && mouseY >= TOP && mouseY < listBottom()) {
            dragging = true;
            dragTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            dragTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Lleva la lista a la altura del mouse sobre la barra (arriba = principio, abajo = final). */
    private void dragTo(double mouseY) {
        double f = (mouseY - TOP) / Math.max(1, listBottom() - TOP);
        scrollTo((int) Math.round(f * maxScroll()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Enter: guardar y siguiente (Enter solo es un salto de línea en el cuadro).
        if (editing != null && (keyCode == 257 || keyCode == 335) && (modifiers & 2) != 0) {
            save(nextEntry() != null);
            return true;
        }
        if (editing == null) {
            // Re Pág / Av Pág
            if (keyCode == 266) { scrollTo(scroll - perPage); return true; }
            if (keyCode == 267) { scrollTo(scroll + perPage); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------- Acciones ----------

    private void openEditor(Entry e) {
        warningInList = null;
        editing = e;
        draft = null;
        warning = null;
        confirmWarning = false;
        status = null;
        rebuildWidgets();
    }

    private void closeEditor(String message) {
        // Para ubicar la lista después: lo editado puede salir del filtro (por ejemplo, de "Sin corregir").
        List<Entry> before = new ArrayList<>(matches);
        int i = before.indexOf(editing);
        if (pendingApply) {
            pendingApply = false;
            String applied = applyAll(pendingQuests);
            pendingQuests = false;
            if (message == null) message = applied;
        }
        editing = null;
        draft = null;
        warning = null;
        editor = null;
        status = message;
        rebuildWidgets();
        if (i >= 0) showFrom(before, i);
    }

    /** Lleva la lista al primer texto de la lista anterior, desde el índice i, que sigue estando (si no se ve). */
    private void showFrom(List<Entry> before, int i) {
        int j = -1;
        for (int k = i; k < before.size() && j < 0; k++) j = matches.indexOf(before.get(k));
        if (j < 0) j = matches.size() - 1;
        if (j >= 0 && (j < scroll || j >= scroll + perPage)) {
            scroll = Math.max(0, j - perPage / 2);
            refresh();
        }
    }

    /**
     * La pantalla se cierra por cualquier motivo (otra pantalla, desconexión): lo guardado con "Guardar y siguiente"
     * se aplica igual.
     */
    @Override
    public void removed() {
        if (pendingApply) {
            pendingApply = false;
            applyAll(pendingQuests);
            pendingQuests = false;
        }
        super.removed();
    }

    private void showWarning(String text) {
        boolean relayout = warning == null;
        warning = text;
        if (relayout) {
            draft = editor.getValue();
            rebuildWidgets();
        }
    }

    private String visibleCodes(String s) {
        return SECTION.matcher(s).replaceAll(java.util.regex.Matcher.quoteReplacement(codeMark) + "$1");
    }

    private String realCodes(String s) {
        return s.replaceAll(java.util.regex.Pattern.quote(codeMark) + "([0-9a-fk-orA-FK-OR])", "§$1");
    }

    private void save(boolean andNext) {
        Entry e = editing;
        // Se calcula antes de guardar: al corregirlo, el texto puede salir del filtro (por ejemplo, de "Sin corregir").
        Entry next = andNext ? nextEntry() : null;
        String text = realCodes(editor.getValue());
        if (text.isBlank()) {
            showWarning(e.origin == Origin.MISSING ? "La traducción está vacía: escribí el texto en español."
                    : "La traducción está vacía. Si querés volver atrás, usá «" + e.origin.restore + "».");
            return;
        }
        String problem = e.original.isEmpty() ? null : Validator.check(e.original, text);
        if (problem != null && !confirmWarning) {
            confirmWarning = true;
            showWarning("Ojo: " + visibleCodes(problem) + " respecto del original (colores, %s, {0}…). Puede verse mal. Guardá otra vez para guardarla igual.");
            return;
        }
        try {
            // Igual a la de base = volver a ella. Salvo en lo que falta: "Create" o "RF" quedan igual en español,
            // y guardarlo es la forma de marcarlo como traducido (y de poder compartirlo).
            boolean backToBase = text.equals(e.ai) && e.origin != Origin.MISSING;
            if (backToBase) Corrections.remove(e.ns, e.key);
            else Corrections.put(e.ns, e.key, text);
            e.setCorrected(backToBase ? null : text);
            TraductorES.LOG.info("[Traductor ES] Corrección guardada: {}:{}", e.ns, e.key);
            if (next != null) {
                pendingApply = true;
                pendingQuests |= e.ns.equals(FtbQuests.NS);
                openEditor(next);
                status = "Guardado. Se aplica todo al volver a la lista.";
            } else {
                pendingQuests |= e.ns.equals(FtbQuests.NS);
                pendingApply = false;
                closeEditor(applyAll(pendingQuests));
                pendingQuests = false;
            }
        } catch (Exception ex) {
            showWarning("No se pudo guardar: " + ex.getMessage());
        }
    }

    private void restore() {
        Entry e = editing;
        try {
            Corrections.remove(e.ns, e.key);
            e.setCorrected(null);
            pendingQuests |= e.ns.equals(FtbQuests.NS);
            pendingApply = false;
            closeEditor(applyAll(pendingQuests));
            pendingQuests = false;
        } catch (Exception ex) {
            showWarning("No se pudo guardar: " + ex.getMessage());
        }
    }

    /** Aplica todas las correcciones guardadas y devuelve el mensaje para el jugador. */
    private static String applyAll(boolean quests) {
        if (!Traductor.applyCorrections()) return "Guardado. Se aplica cuando termine la traducción en curso.";
        if (quests) return "Guardado. Las misiones se ven al volver a entrar al mundo.";
        return "Guardado y aplicado.";
    }

    @Override
    public void onClose() {
        if (editing != null) closeEditor(null);
        else if (pickingMod) toggleModPicker();
        else minecraft.setScreen(parent);
    }

    // ---------- Dibujo ----------

    @Override
    protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {
        if (editing != null) {
            renderEditor(g);
            return;
        }
        g.drawString(font, "Corregir traducciones", left, 10, UiKit.GOLD, true);
        List<Entry> all = entries;
        String info;
        if (loadError != null) info = loadError;
        else if (all == null) info = "Cargando…";
        else if (all.isEmpty()) info = "Todavía no hay nada traducido.";
        else if (pickingMod) {
            info = modMatches.isEmpty() ? "Ningún mod coincide." : "Elegí qué textos ver (Esc vuelve sin cambiar).";
            renderScrollbar(g);
        } else if (matches.isEmpty()) info = !query.isBlank() ? "Nada coincide con la búsqueda."
                : filter == Filter.DOUBTFUL ? "No hay dudosas acá: todo parece traducido."
                : filter == Filter.MISSING ? "¡No falta nada! Compartí lo tuyo en Herramientas → Compartir…" : "Nada coincide con la búsqueda.";
        else {
            int last = Math.min(matches.size(), scroll + perPage);
            info = Traductor.fmt(matches.size()) + " textos" + (maxScroll() > 0 ? " · viendo " + Traductor.fmt(scroll + 1) + "–" + Traductor.fmt(last) : "");
            renderScrollbar(g);
        }
        g.drawString(font, font.plainSubstrByWidth(info, W - (pickingMod ? 110 : 214)), left, height - 22, UiKit.MUTED, false);
        if (warningInList != null) g.drawString(font, font.plainSubstrByWidth(warningInList, W - 150), left + 150, 10, UiKit.RED, false);
        else if (status != null) g.drawString(font, font.plainSubstrByWidth(status, W - 150), left + 150, 10, UiKit.GREEN, false);
        if (all != null && all.isEmpty() && loadError == null) {
            UiKit.wrappedCentered(g, font, Component.literal("Cuando la IA traduzca algo (o se baje de la comunidad), aparece acá para corregirlo."), width / 2, TOP + 10, W, UiKit.MUTED);
        }
    }

    /** Barra al costado de la lista: el tamaño del tirador muestra cuánto de la lista se ve. */
    private void renderScrollbar(Gfx g) {
        int max = maxScroll();
        if (max == 0) return;
        int x = barX(), top = TOP, bottom = listBottom(), h = bottom - top;
        g.fill(x, top, x + BAR, bottom, 0xFF000000);
        int thumb = Math.max(12, h * perPage / listSize());
        int ty = top + (int) Math.round((h - thumb) * (scroll / (double) max));
        g.fill(x, ty, x + BAR, ty + thumb, dragging ? 0xFFC0C0C0 : 0xFF8A8A8A);
        g.fill(x, ty, x + BAR - 1, ty + thumb - 1, dragging ? 0xFFE0E0E0 : 0xFFB0B0B0);
    }

    private void renderEditor(Gfx g) {
        Entry e = editing;
        String header = "Corregir · " + sourceLabel(e.ns);
        g.drawString(font, header, left, 10, UiKit.GOLD, true);
        int pos = matches.indexOf(e);
        String where = pos >= 0 ? Traductor.fmt(pos + 1) + " de " + Traductor.fmt(matches.size()) : "";
        g.drawString(font, where, left + W - font.width(where), 10, UiKit.DIM, false);
        if (status != null) {
            int sx = left + font.width(header) + 12;
            g.drawString(font, font.plainSubstrByWidth(status, left + W - font.width(where) - 8 - sx), sx, 10, UiKit.GREEN, false);
        }
        g.drawString(font, font.plainSubstrByWidth(e.ns + ":" + e.key, W), left, 22, UiKit.DIM, false);
        g.drawString(font, "Original", left, 36, UiKit.MUTED, false);
        int y = 46, shown = 0;
        for (var line : font.split(Component.literal(e.original.isEmpty() ? "(texto oficial, sin original guardado)" : visibleCodes(e.original)), W)) {
            if (++shown > 6) break;
            g.drawString(font, line, left, y, UiKit.WHITE, false);
            y += font.lineHeight + 1;
        }
        String label = e.corrected != null ? "Tu corrección" : e.origin.label;
        if (SECTION.matcher(e.original + e.current()).find()) label += "  ·  " + codeMark + "9, " + codeMark + "r… son colores: dejalos en su lugar";
        g.drawString(font, font.plainSubstrByWidth(label, W), left, originalBottom() + 4, UiKit.MUTED, false);
        if (warning != null && editor != null) {
            UiKit.wrapped(g, font, Component.literal(warning), left, editorBottom + 13, W, UiKit.YELLOW);
        }
    }

    /** "Misiones", "Libro: nombre" o el mod. */
    static String sourceLabel(String ns) {
        if (ns.equals(FtbQuests.NS)) return "Misiones";
        if (ns.equals(InfoTexts.NS)) return "Fichas de REI";
        if (ns.startsWith(Books.PREFIX)) {
            // "@guideme/ae2/ae2guide" → "Libro: ae2 · ae2guide"; "@patchouli/botania/lexicon" → "Libro: botania · lexicon"
            String[] parts = ns.split("/");
            String mod = parts.length > 1 ? parts[1] : ns, book = parts[parts.length - 1];
            return "Libro: " + mod + (book.equals(mod) ? "" : " · " + book);
        }
        return ns;
    }

    /** Fila de la lista: original arriba, traducción abajo y de dónde es a la derecha. */
    private class Row extends CustomButton {
        private final Entry e;

        Row(int x, int y, int w, int h, Entry e, OnPress onPress) {
            super(x, y, w, h, Component.literal(e.original), onPress);
            this.e = e;
        }

        @Override
        protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, hover ? 0xFF8A8A8A : 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF33363C : 0xFF24262B);
            Font font = Minecraft.getInstance().font;
            String origin = e.corrected != null ? "★ Tuya" : e.origin.tag;
            int originColor = e.corrected != null ? UiKit.GOLD : e.origin.color;
            String source = sourceLabel(e.ns);
            int tagW = Math.min(110, Math.max(font.width(origin), font.width(source)));
            int textW = w - tagW - 18;
            g.drawString(font, font.plainSubstrByWidth(oneLine(e.original), textW), x + 5, y + 3, UiKit.DIM, false);
            g.drawString(font, font.plainSubstrByWidth(oneLine(e.current()), textW), x + 5, y + 13, e.corrected != null ? UiKit.GOLD : UiKit.WHITE, false);
            g.drawString(font, origin, x + w - font.width(origin) - 5, y + 3, originColor, false);
            String src = font.plainSubstrByWidth(source, tagW);
            g.drawString(font, src, x + w - font.width(src) - 5, y + 13, UiKit.DIM, false);
        }

        private String oneLine(String s) {
            return s.replace('\n', ' ').replace("\\n", " ");
        }
    }

    /** Fila del selector de mods: nombre a la izquierda y cantidad de textos a la derecha. */
    private class ModRow extends CustomButton {
        private final ModItem m;

        ModRow(int x, int y, int w, int h, ModItem m, OnPress onPress) {
            super(x, y, w, h, Component.literal(m.label()), onPress);
            this.m = m;
        }

        @Override
        protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            boolean chosen = java.util.Objects.equals(m.ns(), mod);
            g.fill(x, y, x + w, y + h, hover ? 0xFF8A8A8A : 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF33363C : 0xFF24262B);
            Font font = Minecraft.getInstance().font;
            String count = Traductor.fmt(m.count()) + " textos";
            int cw = font.width(count);
            g.drawString(font, font.plainSubstrByWidth(m.label(), w - cw - 20), x + 5, y + (h - 8) / 2, chosen ? UiKit.YELLOW : UiKit.WHITE, false);
            g.drawString(font, count, x + w - cw - 5, y + (h - 8) / 2, UiKit.MUTED, false);
        }
    }
}
