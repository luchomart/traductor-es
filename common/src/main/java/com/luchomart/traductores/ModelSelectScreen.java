package com.luchomart.traductores;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lista de modelos del servicio, con buscador (OpenRouter tiene cientos) y el recomendado primero. */
public class ModelSelectScreen extends BaseScreen {
    private final ConfigScreen parent;
    private final List<Button> rows = new ArrayList<>();
    /** Títulos de sección visibles: texto y altura donde se dibujan. */
    private final List<Map.Entry<String, Integer>> headers = new ArrayList<>();
    private int shown;
    private volatile List<String> models;
    private volatile String error;
    private String recommended;
    private EditBox search;
    private int W, left, listTop;

    public ModelSelectScreen(ConfigScreen parent) {
        super(Component.literal("Elegir modelo"));
        this.parent = parent;
        load();
    }

    private void load() {
        Config cfg = Config.get();
        Thread t = new Thread(() -> {
            try {
                List<String> all = new LlmClient().listModels(cfg);
                Preset p = Preset.fromUrl(cfg.apiUrl);
                recommended = ModelPicker.recommend(p, ModelStatus.usable(cfg.apiUrl, all));
                models = ModelPicker.ranked(p, all);
                if (models.isEmpty()) error = "El servicio no informa sus modelos. Escribí el nombre a mano en el campo Modelo.";
            } catch (Exception e) {
                error = LlmClient.describe(e);
                models = List.of();
            }
            minecraft.execute(this::refreshRows);
        }, "Traductor ES - modelos");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void init() {
        W = Math.min(width - 16, 360);
        left = (width - W) / 2;
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, left, 30, W, 20, Component.literal("Buscar"));
        search.setValue(previous);
        if (Preset.fromUrl(Config.get().apiUrl) == Preset.OPENROUTER) {
            search.setHint(Component.literal("Escribí «free» para ver solo los gratis").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        search.setResponder(s -> refreshRows());
        addRenderableWidget(search);
        setInitialFocus(search);
        listTop = 56;
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
        rows.clear();
        refreshRows();
    }

    private void refreshRows() {
        for (Button b : rows) removeWidget(b);
        rows.clear();
        headers.clear();
        shown = 0;
        List<String> all = models;
        if (all == null || search == null) return;
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        Config cfg = Config.get();
        String current = cfg.model;
        boolean openRouter = Preset.fromUrl(cfg.apiUrl) == Preset.OPENROUTER;
        // Secciones: el recomendado, después por tipo (rápidos, equilibrados, calidad, de prueba, no sirven) y al
        // final los que el servicio lista pero ya no da.
        List<String> retired = new ArrayList<>();
        java.util.LinkedHashMap<String, List<String>> sections = new java.util.LinkedHashMap<>();
        for (String m : all) {
            if (!q.isEmpty() && !m.toLowerCase(Locale.ROOT).contains(q)) continue;
            ModelStatus.Info st = ModelStatus.get(cfg.apiUrl, m);
            if (st != null && st.state() == ModelStatus.State.RETIRED) { retired.add(m); continue; }
            String section = m.equals(recommended) ? "★ Recomendado" : ModelPicker.kind(m).label;
            sections.computeIfAbsent(section, k -> new ArrayList<>()).add(m);
        }
        if (!retired.isEmpty()) sections.put("Retirados (el servicio los lista, pero ya no los da)", retired);
        if (sections.containsKey("★ Recomendado")) {
            // El recomendado siempre arriba.
            List<String> rec = sections.remove("★ Recomendado");
            java.util.LinkedHashMap<String, List<String>> ordered = new java.util.LinkedHashMap<>();
            ordered.put("★ Recomendado", rec);
            ordered.putAll(sections);
            sections = ordered;
        }
        int y = listTop, bottom = height - 50;
        outer:
        for (Map.Entry<String, List<String>> sec : sections.entrySet()) {
            if (y + 12 + 20 > bottom) break;
            headers.add(Map.entry(sec.getKey(), y + 2));
            y += 12;
            for (String m : sec.getValue()) {
                if (y + 20 > bottom) break outer;
                rows.add(addRenderableWidget(row(m, cfg, current, openRouter, y)));
                shown++;
                y += 22;
            }
        }
    }

    private Button row(String m, Config cfg, String current, boolean openRouter, int y) {
        net.minecraft.network.chat.MutableComponent text = Component.literal(m + (m.equals(current) ? "  (actual)" : ""));
        // En OpenRouter conviven modelos gratis y de pago: se marca cada uno.
        if (openRouter) text.append(m.endsWith(":free") ? Component.literal("  GRATIS").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.literal("  DE PAGO").withStyle(net.minecraft.ChatFormatting.GOLD));
        ModelStatus.Info st = ModelStatus.get(cfg.apiUrl, m);
        String weak = ModelPicker.weakReason(m);
        if (st != null) text.append(Component.literal("  " + st.tag()).withStyle(net.minecraft.ChatFormatting.RED));
        else if (weak != null) text.append(Component.literal("  NO RECOMENDADO").withStyle(net.minecraft.ChatFormatting.RED));
        String help = st != null ? st.explain(m) : weak != null ? "No conviene para traducir al español: " + weak + "." : ModelPicker.kind(m).help;
        return Button.builder(text, btn -> pick(m)).bounds(left, y, W, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(help))).build();
    }

    private void pick(String m) {
        parent.setModel(Config.get(), m);
        TraductorES.LOG.info("[Traductor ES] Panel: modelo elegido a mano: {}", m);
        ModelStatus.Info st = ModelStatus.get(Config.get().apiUrl, m);
        String weak = ModelPicker.weakReason(m);
        if (st != null) {
            parent.report(Component.literal("Ojo: " + st.explain(m)).withStyle(net.minecraft.ChatFormatting.GOLD));
        } else if (weak != null) {
            parent.report(Component.literal("Ojo: «" + m + "» no conviene para traducir al español (" + weak
                    + "). Si traduce mal, en Herramientas → «Rehacer» se vuelve a traducir lo que hizo.").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, "Elegir modelo · " + Preset.fromUrl(Config.get().apiUrl).label, width / 2, 12, UiKit.GOLD);
        List<String> all = models;
        if (all == null) {
            g.drawCenteredString(font, "Cargando modelos…", width / 2, listTop + 6, UiKit.MUTED);
        } else if (error != null) {
            UiKit.wrappedCentered(g, font, Component.literal(error), width / 2, listTop + 6, W, UiKit.RED);
        } else {
            String q = search.getValue().trim().toLowerCase(Locale.ROOT);
            long matches = all.stream().filter(m -> q.isEmpty() || m.toLowerCase(Locale.ROOT).contains(q)).count();
            for (Map.Entry<String, Integer> h : headers) g.drawString(font, h.getKey(), left + 2, h.getValue(), UiKit.GOLD, false);
            if (matches > shown) {
                g.drawCenteredString(font, "y " + (matches - shown) + " más: escribí arriba para filtrar", width / 2, height - 42, UiKit.DIM);
            } else if (matches == 0) {
                g.drawCenteredString(font, "Ningún modelo coincide", width / 2, listTop + 6, UiKit.MUTED);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
