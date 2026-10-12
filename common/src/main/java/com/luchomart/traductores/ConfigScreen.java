package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pantalla de configuración (Mods → Traductor ES → Configurar). */
public class ConfigScreen extends Screen {
    private static final int LABEL_W = 110, FIELD_W = 220, ROW = 24;

    private final Screen parent;
    private EditBox url, key, model, batch;
    private Button autoStartBtn, runBtn, cancelBtn;
    private boolean autoStart;
    private volatile Component testResult;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Traductor ES"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config cfg = Config.get();
        int left = (width - LABEL_W - FIELD_W) / 2, fx = left + LABEL_W, y = 30;

        url = field(fx, y, cfg.apiUrl, 512); y += ROW;
        key = field(fx, y, cfg.apiKey, 256); y += ROW;
        model = field(fx, y, cfg.model, 128); y += ROW;
        batch = field(fx, y, String.valueOf(cfg.batchSize), 4);
        batch.setFilter(s -> s.chars().allMatch(Character::isDigit));
        y += ROW + 6;

        int half = (LABEL_W + FIELD_W - 4) / 2, rx = left + half + 4;
        autoStart = cfg.autoStart;
        autoStartBtn = addRenderableWidget(Button.builder(autoStartLabel(), b -> {
            autoStart = !autoStart;
            b.setMessage(autoStartLabel());
        }).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Probar conexión"), b -> testConnection())
                .bounds(rx, y, half, 20).build());
        y += ROW;

        runBtn = addRenderableWidget(Button.builder(Component.empty(), b -> toggleRun()).bounds(left, y, half, 20).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("Cancelar traducción"), b -> {
            TranslationJob j = Traductor.job();
            if (j != null) j.cancel();
        }).bounds(rx, y, half, 20).build());
        y += ROW;

        addRenderableWidget(Button.builder(Component.literal("Abrir carpeta"), b -> Util.getPlatform().openFile(Config.DIR.toFile()))
                .bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar y volver"), b -> onClose())
                .bounds(rx, y, half, 20).build());
        updateButtons();
    }

    private EditBox field(int x, int y, String value, int max) {
        EditBox box = new EditBox(font, x, y, FIELD_W, 20, Component.empty());
        box.setMaxLength(max);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private Component autoStartLabel() {
        return Component.literal("Traducir al iniciar: " + (autoStart ? "Sí" : "No"));
    }

    private void toggleRun() {
        TranslationJob j = Traductor.job();
        if (j != null && j.isRunning()) {
            if (j.state == TranslationJob.State.PAUSED) j.resume();
            else j.pause();
        } else {
            applyFields();
            Traductor.start();
        }
        updateButtons();
    }

    private void updateButtons() {
        TranslationJob j = Traductor.job();
        boolean running = j != null && j.isRunning();
        runBtn.setMessage(Component.literal(!running ? "Traducir ahora"
                : j.state == TranslationJob.State.PAUSED ? "Reanudar" : "Pausar"));
        cancelBtn.active = running;
    }

    private void testConnection() {
        applyFields();
        testResult = Component.literal("Probando…").withStyle(ChatFormatting.GRAY);
        Config cfg = Config.get();
        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                String answer = new LlmClient().ping(cfg);
                testResult = Component.literal("Conexión correcta (" + (System.currentTimeMillis() - t0) / 1000.0
                        + " s). Respuesta: " + answer).withStyle(ChatFormatting.GREEN);
            } catch (Exception e) {
                testResult = Component.literal("Error: " + e.getMessage()).withStyle(ChatFormatting.RED);
            }
        }, "Traductor ES - prueba");
        t.setDaemon(true);
        t.start();
    }

    /** Copia los campos a la configuración y la guarda. */
    private void applyFields() {
        Config cfg = Config.get();
        cfg.apiUrl = url.getValue().trim();
        cfg.apiKey = key.getValue().trim();
        cfg.model = model.getValue().trim();
        try {
            cfg.batchSize = Math.max(1, Integer.parseInt(batch.getValue()));
        } catch (NumberFormatException ignored) {}
        cfg.autoStart = autoStart;
        cfg.save();
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Platform.renderBackground(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFD700);
        int left = (width - LABEL_W - FIELD_W) / 2, y = 30 + 6;
        for (String label : List.of("URL de la API", "Clave (API key)", "Modelo", "Textos por lote")) {
            g.drawString(font, label, left, y, 0xE0E0E0);
            y += ROW;
        }
        int sy = 30 + ROW * 7 + 10;
        TranslationJob j = Traductor.job();
        if (j != null) {
            for (Component line : Traductor.statusLines(j)) {
                g.drawCenteredString(font, line, width / 2, sy, 0xFFFFFF);
                sy += font.lineHeight + 2;
            }
        }
        Component test = testResult;
        if (test != null) g.drawCenteredString(font, test, width / 2, sy + 2, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        applyFields();
        Minecraft.getInstance().setScreen(parent);
    }
}
