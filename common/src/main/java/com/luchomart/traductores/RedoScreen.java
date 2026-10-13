package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rehacer lo que tradujo un modelo: lista los modelos con cuántos textos hizo cada uno. Al elegir uno, esas
 * traducciones van a un respaldo y se vuelven a traducir con el servicio y modelo del panel.
 */
public class RedoScreen extends BaseScreen {
    private final ConfigScreen parent;
    private final String target;
    private volatile Map<String, Integer> counts;
    private String selected;
    private String message;
    private int W, left, listTop;

    public RedoScreen(ConfigScreen parent, String target) {
        super(Component.literal("Rehacer traducciones"));
        this.parent = parent;
        this.target = target;
        Thread t = new Thread(() -> {
            counts = new TranslationCache(target).countByModel();
            minecraft.execute(this::rebuildWidgets);
        }, "Traductor ES - rehacer");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void init() {
        W = Math.min(width - 16, 360);
        left = (width - W) / 2;
        listTop = 70;
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
        Map<String, Integer> c = counts;
        if (c == null) return;
        if (selected != null) {
            int n = c.getOrDefault(selected, 0);
            addRenderableWidget(Button.builder(Component.literal("Rehacer " + Traductor.fmt(n) + " textos").withStyle(ChatFormatting.GOLD),
                    b -> redo()).bounds(width / 2 - 124, listTop + 40, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> {
                selected = null;
                rebuildWidgets();
            }).bounds(width / 2 + 4, listTop + 40, 120, 20).build());
            return;
        }
        int y = listTop;
        for (String model : sortedModels(c)) {
            if (y > height - 60) break;
            Component label = Component.literal(model + "  ·  " + Traductor.fmt(c.get(model)) + " textos");
            String weak = ModelPicker.weakReason(model);
            if (weak != null) label = label.copy().append(Component.literal("  NO RECOMENDADO").withStyle(ChatFormatting.RED));
            addRenderableWidget(Button.builder(label, b -> {
                selected = model;
                message = null;
                rebuildWidgets();
            }).bounds(left, y, W, 20).build());
            y += 22;
        }
    }

    /** Los que no sirven para traducir primero (son los que más se van a querer rehacer), después por cantidad. */
    private static List<String> sortedModels(Map<String, Integer> c) {
        List<String> out = new ArrayList<>(c.keySet());
        out.remove("");
        out.sort((a, b) -> {
            boolean wa = ModelPicker.weakReason(a) != null, wb = ModelPicker.weakReason(b) != null;
            if (wa != wb) return wa ? -1 : 1;
            return Integer.compare(c.get(b), c.get(a));
        });
        return out;
    }

    private void redo() {
        if (Traductor.isRunning()) {
            message = "Hay una traducción en curso: cancelala (o esperá que termine) y probá de nuevo.";
            return;
        }
        String model = selected;
        if (model.equals(Config.get().model)) {
            message = "«" + model + "» es el modelo elegido ahora: primero elegí otro en la pestaña Conexión.";
            return;
        }
        try {
            int n = new TranslationCache(target).removeByModel(model);
            Traductor.start();
            parent.report(Component.literal("Se van a traducir de nuevo " + Traductor.fmt(n) + " textos que había hecho «" + model
                    + "», con " + modelName() + ". Lo anterior quedó en respaldos/.").withStyle(ChatFormatting.GREEN));
            minecraft.setScreen(parent);
        } catch (Exception e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo rehacer {}", model, e);
            message = "No se pudo: " + LlmClient.describe(e);
        }
    }

    private static String modelName() {
        String m = Config.get().model;
        return m.isBlank() ? "el modelo recomendado del servicio" : "«" + m + "»";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, "Rehacer lo que tradujo un modelo", width / 2, 12, UiKit.GOLD);
        UiKit.wrappedCentered(g, font, Component.literal("Si un modelo tradujo mal, elegilo: sus traducciones se guardan en un respaldo y se "
                + "vuelven a traducir con el modelo elegido en el panel. Tus correcciones no se tocan."), width / 2, 30, W, UiKit.MUTED);
        Map<String, Integer> c = counts;
        if (c == null) {
            g.drawCenteredString(font, "Leyendo traducciones…", width / 2, listTop + 6, UiKit.MUTED);
        } else if (selected != null) {
            UiKit.wrappedCentered(g, font, Component.literal("¿Rehacer las " + Traductor.fmt(c.getOrDefault(selected, 0)) + " traducciones de «"
                    + selected + "»? Se vuelven a traducir con " + modelName() + "."), width / 2, listTop + 6, W, UiKit.WHITE);
        } else if (sortedModels(c).isEmpty()) {
            UiKit.wrappedCentered(g, font, Component.literal("Todavía no hay traducciones con el modelo guardado (se guarda desde esta versión)."),
                    width / 2, listTop + 6, W, UiKit.MUTED);
        }
        if (c != null && c.containsKey("") && selected == null) {
            g.drawCenteredString(font, Traductor.fmt(c.get("")) + " textos de antes no tienen el modelo guardado (no se pueden elegir acá).",
                    width / 2, height - 44, UiKit.DIM);
        }
        if (message != null) UiKit.wrappedCentered(g, font, Component.literal(message), width / 2, listTop + 66, W, UiKit.RED);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
