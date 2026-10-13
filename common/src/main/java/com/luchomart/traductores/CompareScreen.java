package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * "¿Cuál conviene?": cuánto tardaría cada servicio con los textos de este modpack, si es gratis o de pago,
 * y qué tener en cuenta. Tocar una fila elige ese servicio.
 */
public class CompareScreen extends BaseScreen {
    /** Cantidad de ejemplo cuando todavía no se sabe cuánto falta (o no falta nada). */
    private static final int SAMPLE = 1000;

    private final Screen parent;
    /** Textos por traducir de este modpack; -1 mientras se cuentan o si no se pudo. */
    private volatile int texts;
    private volatile boolean counting;
    private final Consumer<ServiceGuide.Option> onPick;
    private int W, left, top, rowH;

    /** texts: textos por traducir de este modpack, o -1 si no se sabe. */
    public CompareScreen(Screen parent, int texts, Consumer<ServiceGuide.Option> onPick) {
        super(Component.literal("¿Cuál conviene?"));
        this.parent = parent;
        this.texts = texts;
        this.onPick = onPick;
        if (texts < 0) count();
    }

    /** Sin el dato (por ejemplo, desde el panel): se cuentan los textos de los mods sin usar la IA. */
    private void count() {
        Minecraft mc = Minecraft.getInstance();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        String assumed = target != null ? target : "es_es";
        counting = true;
        Thread t = new Thread(() -> {
            try {
                java.util.concurrent.CompletableFuture<java.util.Map<String, java.util.Map<String, java.util.Map<String, String>>>> scan =
                        new java.util.concurrent.CompletableFuture<>();
                mc.execute(() -> {
                    try {
                        scan.complete(LangScanner.scan(mc.getResourceManager()));
                    } catch (Throwable e) {
                        scan.completeExceptionally(e);
                    }
                });
                texts = Estimator.estimate(scan.join(), assumed, Config.get()).texts();
            } catch (Throwable e) {
                TraductorES.LOG.warn("[Traductor ES] No se pudo contar los textos: {}", e.toString());
            } finally {
                counting = false;
            }
        }, "Traductor ES - contar textos");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void init() {
        W = Math.min(width - 24, 440);
        left = (width - W) / 2;
        top = 44;
        List<ServiceGuide.Option> options = ServiceGuide.OPTIONS;
        rowH = Math.max(22, Math.min(28, (height - top - 54) / options.size()));
        ServiceGuide.Option current = ServiceGuide.current(Config.get());
        int y = top;
        for (ServiceGuide.Option o : options) {
            addRenderableWidget(new Row(left, y, W, rowH - 2, o, o == current, b -> onPick.accept(o)));
            y += rowH;
        }
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "¿Cuál conviene?", left, 12, UiKit.GOLD, true);
        String sub = counting ? "Contando los textos de este modpack…" : texts == 0 ? "Este modpack no tiene nada por traducir. Tiempos para " + Traductor.fmt(SAMPLE) + " textos de ejemplo."
                : texts > 0 ? "Para este modpack: " + Traductor.fmt(texts) + " textos por traducir. Tiempos aproximados."
                : "Tiempos aproximados para " + Traductor.fmt(SAMPLE) + " textos de ejemplo.";
        g.drawString(font, font.plainSubstrByWidth(sub, W), left, 27, UiKit.MUTED, false);
        int fy = top + rowH * ServiceGuide.OPTIONS.size() + 4;
        if (fy + 20 < height - 32) {
            UiKit.wrapped(g, font, Component.literal("Tocá uno para usarlo. Los tiempos varían según la hora, el servicio y tu PC; las próximas veces solo se traduce lo nuevo."),
                    left, fy, W, UiKit.DIM);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /**
     * Aplica la opción a la configuración. Para "OpenRouter (gratis)" busca el modelo gratis en la lista del
     * servicio (en otro hilo) y después llama a done en el hilo del juego.
     */
    public static void apply(ServiceGuide.Option o, Runnable done) {
        Config cfg = Config.get();
        cfg.applyPreset(o.preset());
        Minecraft mc = Minecraft.getInstance();
        if (!o.freeModel()) {
            done.run();
            return;
        }
        // El plan gratis permite pocas peticiones por minuto: de a una.
        cfg.parallelRequests = 1;
        Thread t = new Thread(() -> {
            try {
                String pick = ModelPicker.recommendFree(ModelStatus.usable(cfg.apiUrl, new LlmClient().listModels(cfg)));
                if (pick != null) cfg.model = pick;
                TraductorES.LOG.info("[Traductor ES] Modelo gratis elegido: {}", pick);
            } catch (Exception e) {
                TraductorES.LOG.warn("[Traductor ES] No se pudo buscar un modelo gratis: {}", LlmClient.describe(e));
            }
            cfg.save();
            mc.execute(done);
        }, "Traductor ES - modelo gratis");
        t.setDaemon(true);
        t.start();
    }

    /** Una fila: nombre, tiempo, gratis/de pago y la nota debajo. */
    private class Row extends Button {
        private final ServiceGuide.Option option;
        private final boolean current;

        Row(int x, int y, int w, int h, ServiceGuide.Option option, boolean current, OnPress onPress) {
            super(x, y, w, h, Component.literal(option.name()), onPress, DEFAULT_NARRATION);
            this.option = option;
            this.current = current;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, current ? UiKit.GREEN : isHoveredOrFocused() ? 0xFF8A8A8A : 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, current ? 0xFF1F2A1F : isHoveredOrFocused() ? 0xFF33363C : 0xFF24262B);
            Font font = Minecraft.getInstance().font;
            boolean twoLines = h >= 24;
            int ty = twoLines ? y + 3 : y + (h - 8) / 2;
            String name = option.name() + (current ? "  ✓" : "");
            g.drawString(font, name, x + 6, ty, UiKit.WHITE, false);
            String time = option.duration(texts > 0 ? texts : SAMPLE);
            int tagW = 56, timeX = x + w - tagW - 8 - 70;
            g.drawString(font, time, timeX, ty, UiKit.GOLD, false);
            int tagX = x + w - tagW - 4;
            g.fill(tagX, ty - 2, tagX + tagW, ty + 9, option.free() ? 0xFF1E4D23 : 0xFF5A4511);
            g.drawCenteredString(font, option.cost(), tagX + tagW / 2, ty, option.free() ? UiKit.GREEN : UiKit.GOLD);
            if (twoLines) g.drawString(font, font.plainSubstrByWidth(option.note(), w - 12), x + 6, y + 14, UiKit.DIM, false);
        }
    }
}
