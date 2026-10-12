package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.nio.file.Path;
import java.util.List;

/** Pantalla de configuración (Mods → Traductor ES → Configurar). */
public class ConfigScreen extends Screen {
    private static final int LABEL_W = 110, FIELD_W = 220, ROW = 22, TOP = 26;
    private static final int MAX_MODELS_SHOWN = 8;

    private final Screen parent;
    private EditBox url, key, model, batch, parallel;
    private Button runBtn, cancelBtn, presetBtn;
    private boolean autoStart;
    private volatile Component testResult;

    /** Muestra un resultado en el panel y lo deja también en el log (para poder diagnosticar después). */
    private void report(Component msg) {
        testResult = msg;
        TraductorES.LOG.info("[Traductor ES] Panel: {}", msg.getString());
    }

    public ConfigScreen(Screen parent) {
        super(Component.literal("Traductor ES"));
        this.parent = parent;
    }

    private int left() { return (width - LABEL_W - FIELD_W) / 2; }

    @Override
    protected void init() {
        Config cfg = Config.get();
        int left = left(), fx = left + LABEL_W, full = LABEL_W + FIELD_W, y = TOP;

        presetBtn = addRenderableWidget(Button.builder(presetLabel(Preset.fromUrl(cfg.apiUrl)), b -> cyclePreset())
                .bounds(left, y, full, 20).build());
        y += ROW;
        url = field(fx, y, FIELD_W, cfg.apiUrl, 512); y += ROW;
        key = field(fx, y, FIELD_W, cfg.apiKey, 256); y += ROW;
        model = field(fx, y, FIELD_W, cfg.model, 128); y += ROW;
        batch = field(fx, y, 50, String.valueOf(cfg.batchSize), 3);
        batch.setFilter(s -> s.chars().allMatch(Character::isDigit));
        parallel = field(fx + FIELD_W - 40, y, 40, String.valueOf(cfg.parallelRequests), 2);
        parallel.setFilter(s -> s.chars().allMatch(Character::isDigit));
        y += ROW + 4;

        int half = (full - 4) / 2, third = (full - 8) / 3;
        autoStart = cfg.autoStart;
        addRenderableWidget(Button.builder(autoStartLabel(), b -> {
            autoStart = !autoStart;
            b.setMessage(autoStartLabel());
        }).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Probar conexión"), b -> testConnection())
                .bounds(left + half + 4, y, half, 20).build());
        y += ROW;

        runBtn = addRenderableWidget(Button.builder(Component.empty(), b -> toggleRun()).bounds(left, y, third, 20).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> {
            TranslationJob j = Traductor.job();
            if (j != null) j.cancel();
        }).bounds(left + third + 4, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Exportar pack"), b -> exportPack())
                .bounds(left + 2 * (third + 4), y, third, 20).build());
        y += ROW;

        addRenderableWidget(Button.builder(Component.literal("Abrir carpeta"), b -> Util.getPlatform().openFile(Config.DIR.toFile()))
                .bounds(left, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guía"), b -> {
            applyFields();
            Minecraft.getInstance().setScreen(new GuideScreen(this));
        }).bounds(left + third + 4, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar y volver"), b -> onClose())
                .bounds(left + 2 * (third + 4), y, third, 20).build());
        updateButtons();
    }

    private EditBox field(int x, int y, int w, String value, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(max);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private static Component presetLabel(Preset p) {
        return Component.literal("Servicio: " + p.label + "  ▸");
    }

    private Component autoStartLabel() {
        return Component.literal("Traducir al iniciar: " + (autoStart ? "Sí" : "No"));
    }

    /** Pasa al siguiente servicio y completa URL, modelo y peticiones simultáneas. La clave no se toca. */
    private void cyclePreset() {
        Preset p = Preset.fromUrl(url.getValue()).next();
        if (p == Preset.CUSTOM) p = p.next(); // "Personalizado" aparece solo al escribir una URL propia
        url.setValue(p.url);
        model.setValue(p.model);
        // La clave de relleno de Ollama no sirve en la nube y confunde los errores: se vacía.
        if (p.isCloud() && key.getValue().trim().equalsIgnoreCase("ollama")) key.setValue("");
        if (p.parallel > 0) parallel.setValue(String.valueOf(p.parallel));
        presetBtn.setMessage(presetLabel(p));
        TraductorES.LOG.info("[Traductor ES] Panel: servicio cambiado a {}", p.label);
        if (p.isCloud()) {
            testResult = Component.literal((key.getValue().isBlank() ? "Este servicio necesita una clave (API key). " : "")
                    + "Ojo: los textos de los mods se envían a ese servicio por internet. Tocá Probar conexión para ver los modelos.")
                    .withStyle(ChatFormatting.YELLOW);
        } else {
            testResult = Component.literal("IA en tu PC: tiene que estar abierta. Tocá Probar conexión para ver los modelos instalados.")
                    .withStyle(ChatFormatting.GRAY);
        }
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
        presetBtn.setMessage(presetLabel(Preset.fromUrl(url.getValue())));
    }

    /** Lista los modelos del servidor, avisa si el configurado no existe y hace una petición de prueba. */
    private void testConnection() {
        applyFields();
        testResult = Component.literal("Probando…").withStyle(ChatFormatting.GRAY);
        Config cfg = Config.get();
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            LlmClient client = new LlmClient();
            try {
                List<String> models = client.listModels(cfg);
                if (cfg.model.isBlank() && !models.isEmpty()) {
                    // Sin modelo elegido: se usa el primero disponible.
                    cfg.model = models.get(0);
                    cfg.save();
                    mc.execute(() -> model.setValue(cfg.model));
                }
                if (!models.isEmpty() && !models.contains(cfg.model)) {
                    report(Component.literal("El modelo «" + cfg.model + "» no está en el servidor. Disponibles: "
                            + summarize(models)).withStyle(ChatFormatting.RED));
                    return;
                }
                long t0 = System.currentTimeMillis();
                client.ping(cfg);
                MutableComponent ok = Component.literal("Conexión correcta con «" + cfg.model + "» ("
                        + (System.currentTimeMillis() - t0) / 100 / 10.0 + " s).").withStyle(ChatFormatting.GREEN);
                if (models.size() > 1) ok.append(Component.literal(" Otros modelos: " + summarize(models)).withStyle(ChatFormatting.GRAY));
                report(ok);
            } catch (Exception e) {
                report(Component.literal("Error: " + LlmClient.describe(e)).withStyle(ChatFormatting.RED));
            }
        }, "Traductor ES - prueba");
        t.setDaemon(true);
        t.start();
    }

    private static String summarize(List<String> models) {
        if (models.size() <= MAX_MODELS_SHOWN) return String.join(", ", models);
        return String.join(", ", models.subList(0, MAX_MODELS_SHOWN)) + " y " + (models.size() - MAX_MODELS_SHOWN) + " más";
    }

    private void exportPack() {
        try {
            Path zip = PackExporter.export();
            report(Component.literal("Pack exportado a resourcepacks/" + zip.getFileName()
                    + ". Quien juegue el mismo modpack lo activa como cualquier paquete de recursos, sin IA.")
                    .withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            TraductorES.LOG.error("[Traductor ES] Error al exportar el pack", e);
            report(Component.literal("No se pudo exportar: " + LlmClient.describe(e)).withStyle(ChatFormatting.RED));
        }
    }

    /** Copia los campos a la configuración y la guarda. */
    private void applyFields() {
        Config cfg = Config.get();
        cfg.apiUrl = url.getValue().trim();
        cfg.apiKey = key.getValue().trim();
        cfg.model = model.getValue().trim();
        cfg.batchSize = parsePositive(batch.getValue(), cfg.batchSize);
        cfg.parallelRequests = Math.min(16, parsePositive(parallel.getValue(), cfg.parallelRequests));
        cfg.autoStart = autoStart;
        cfg.save();
    }

    private static int parsePositive(String s, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Platform.renderBackground(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFD700);
        int left = left(), y = TOP + ROW + 6;
        for (String label : List.of("URL de la API", "Clave (API key)", "Modelo", "Textos por lote")) {
            g.drawString(font, label, left, y, 0xE0E0E0);
            y += ROW;
        }
        String atOnce = "A la vez";
        g.drawString(font, atOnce, left + LABEL_W + FIELD_W - 44 - font.width(atOnce), TOP + ROW * 4 + 6, 0xE0E0E0);

        int sy = TOP + ROW * 8 + 8;
        TranslationJob j = Traductor.job();
        if (j != null) {
            for (Component line : Traductor.statusLines(j)) sy = drawWrapped(g, line, sy);
        }
        Component test = testResult;
        if (test != null) drawWrapped(g, test, sy + 2);
    }

    /** Dibuja un texto centrado partido en renglones que entren en la pantalla. Devuelve la y siguiente. */
    private int drawWrapped(GuiGraphics g, Component text, int y) {
        for (FormattedCharSequence line : font.split(text, Math.max(100, width - 30))) {
            g.drawString(font, line, (width - font.width(line)) / 2, y, 0xFFFFFF);
            y += font.lineHeight + 2;
        }
        return y;
    }

    @Override
    public void onClose() {
        applyFields();
        Minecraft.getInstance().setScreen(parent);
    }
}
