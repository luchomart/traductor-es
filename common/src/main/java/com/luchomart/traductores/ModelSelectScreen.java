package com.luchomart.traductores;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lista de modelos del servicio, con buscador (OpenRouter tiene cientos) y el recomendado primero. */
public class ModelSelectScreen extends BaseScreen {
    private final ConfigScreen parent;
    private final List<Button> rows = new ArrayList<>();
    private volatile List<String> models;
    private volatile String error;
    private String recommended;
    private EditBox search;
    private int W, left, listTop, perPage;

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
                recommended = ModelPicker.recommend(p, all);
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
        perPage = Math.max(3, (height - listTop - 34) / 22);
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
        rows.clear();
        refreshRows();
    }

    private void refreshRows() {
        for (Button b : rows) removeWidget(b);
        rows.clear();
        List<String> all = models;
        if (all == null || search == null) return;
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        String current = Config.get().model;
        boolean openRouter = Preset.fromUrl(Config.get().apiUrl) == Preset.OPENROUTER;
        int y = listTop;
        for (String m : all) {
            if (!q.isEmpty() && !m.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (rows.size() >= perPage) break;
            String label = m + (m.equals(recommended) ? "  ★ recomendado" : "") + (m.equals(current) ? "  (actual)" : "");
            net.minecraft.network.chat.MutableComponent text = Component.literal(label);
            // En OpenRouter conviven modelos gratis y de pago: se marca cada uno.
            if (openRouter) text.append(m.endsWith(":free") ? Component.literal("  GRATIS").withStyle(net.minecraft.ChatFormatting.GREEN)
                    : Component.literal("  DE PAGO").withStyle(net.minecraft.ChatFormatting.GOLD));
            String weak = ModelPicker.weakReason(m);
            if (weak != null) text.append(Component.literal("  NO RECOMENDADO").withStyle(net.minecraft.ChatFormatting.RED));
            Button.Builder bb = Button.builder(text, btn -> pick(m)).bounds(left, y, W, 20);
            if (weak != null) bb.tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("No conviene para traducir al español: " + weak + ".")));
            Button b = bb.build();
            rows.add(addRenderableWidget(b));
            y += 22;
        }
    }

    private void pick(String m) {
        parent.setModel(Config.get(), m);
        TraductorES.LOG.info("[Traductor ES] Panel: modelo elegido a mano: {}", m);
        String weak = ModelPicker.weakReason(m);
        if (weak != null) {
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
            if (matches > rows.size()) {
                g.drawCenteredString(font, "y " + (matches - rows.size()) + " más: escribí arriba para filtrar", width / 2, height - 42, UiKit.DIM);
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
