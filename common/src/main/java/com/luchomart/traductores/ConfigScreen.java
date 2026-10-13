package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Panel de configuración con pestañas (Mods → Traductor ES → Configurar). */
public class ConfigScreen extends BaseScreen {
    private enum Tab {
        CONEXION("Conexión"), TRADUCCION("Traducción"), HERRAMIENTAS("Herramientas");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int ROW = 22, LABEL_W = 100, HELP_W = 74;
    private static final int MAX_MODELS_SHOWN = 8;
    private static final List<String> TARGETS = new ArrayList<>();
    static {
        TARGETS.add("auto");
        TARGETS.addAll(SpanishTarget.CODES);
    }

    /** Se recuerda la pestaña durante la sesión. */
    private static Tab tab = Tab.CONEXION;

    private final Screen parent;
    private EditBox model;
    private Button runBtn, cancelBtn, helpBtn, testBtn;
    private volatile Component testResult;
    /** Hay una prueba de conexión en curso (no se puede lanzar otra). */
    private volatile boolean testing;
    private String lastReport = "";

    // Geometría, calculada en init().
    private int W, left, contentTop, contentBottom, fieldX, fieldW, innerX, innerW;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Traductor ES"));
        this.parent = parent;
    }

    // ---------- Construcción ----------

    @Override
    protected void init() {
        Config cfg = Config.get();
        W = Math.min(width - 16, 460);
        left = (width - W) / 2;
        contentTop = 46;
        contentBottom = height - 36;
        innerX = left + 8;
        innerW = W - 16;
        fieldX = innerX + LABEL_W;
        fieldW = innerW - LABEL_W;
        lastReport = readLastReport();
        model = null;
        helpBtn = null;
        testBtn = null;

        // Encabezado: la guía siempre a la vista.
        addRenderableWidget(Button.builder(Component.literal("Guía"), b -> openGuide())
                .bounds(left + W - 58, 6, 58, 18).build());

        // Pestañas.
        int tx = left;
        for (Tab t : Tab.values()) {
            int tw = Math.min(110, (W - 8) / 3);
            addRenderableWidget(new TabButton(tx, 28, tw, 18, Component.literal(t.label), b -> switchTab(t), () -> tab == t));
            tx += tw + 2;
        }

        int y = contentTop + 8;
        switch (tab) {
            case CONEXION -> initConexion(cfg, y);
            case TRADUCCION -> initTraduccion(cfg, y);
            case HERRAMIENTAS -> initHerramientas();
        }

        // Barra inferior: traducir/pausar, cancelar y salir.
        int by = height - 30;
        runBtn = addRenderableWidget(Button.builder(Component.empty(), b -> toggleRun()).bounds(left + W - 266, by, 92, 20).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> {
            TranslationJob j = Traductor.job();
            if (j != null) j.cancel();
        }).bounds(left + W - 170, by, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar y volver"), b -> onClose())
                .bounds(left + W - 100, by, 100, 20).build());
        updateButtons();
    }

    private void initConexion(Config cfg, int y) {
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> applyPreset(Preset.fromUrl(cfg.apiUrl).previous()))
                .bounds(fieldX, y, 20, 20).tooltip(Tooltip.create(Component.literal("Servicio anterior"))).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> applyPreset(Preset.fromUrl(cfg.apiUrl).next()))
                .bounds(fieldX + fieldW - 20, y, 20, 20).tooltip(Tooltip.create(Component.literal("Servicio siguiente"))).build());
        y += ROW;
        EditBox url = field(fieldX, y, fieldW, cfg.apiUrl, 512, s -> cfg.apiUrl = s.trim());
        url.setTooltip(Tooltip.create(Component.literal("Dónde está la IA. Con un servicio elegido no hace falta tocarla.")));
        y += ROW;
        EditBox key = field(fieldX, y, fieldW - HELP_W - 4, cfg.apiKey, 256, s -> cfg.apiKey = s.trim());
        hideSecret(key);
        Preset current = Preset.fromUrl(cfg.apiUrl);
        if (current.isLocal()) {
            key.setValue("");
            key.setEditable(false);
            key.setHint(Component.literal("No hace falta con " + current.label.replace(" (en tu PC)", "")).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            key.setHint(Component.literal("Pegá acá tu clave").withStyle(ChatFormatting.DARK_GRAY));
        }
        key.setTooltip(Tooltip.create(Component.literal("Solo para servicios en la nube. Se ve oculta y queda guardada en config.json (no compartas ese archivo).")));
        helpBtn = addRenderableWidget(Button.builder(Component.empty(), b -> openHelpPage())
                .bounds(fieldX + fieldW - HELP_W, y, HELP_W, 20).build());
        y += ROW;
        model = field(fieldX, y, fieldW - HELP_W - 4, cfg.model, 128, s -> cfg.model = s.trim());
        model.setTooltip(Tooltip.create(Component.literal("Vacío = se elige solo el recomendado del servicio.")));
        addRenderableWidget(Button.builder(Component.literal("Elegir…"), b -> {
            cfg.save();
            minecraft.setScreen(new ModelSelectScreen(this));
        }).bounds(fieldX + fieldW - HELP_W, y, HELP_W, 20)
                .tooltip(Tooltip.create(Component.literal("Ver los modelos que tiene el servicio y elegir uno."))).build());
        y += ROW + 2;
        testBtn = addRenderableWidget(Button.builder(Component.literal("Probar conexión"), b -> testConnection())
                .bounds(innerX, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("¿Cuál conviene?"), b -> openCompare())
                .bounds(innerX + 124, y, 110, 20)
                .tooltip(Tooltip.create(Component.literal("Cuánto tarda cada servicio y cuáles son gratis."))).build());
    }

    /** "¿Cuál conviene?": con los textos de la última traducción, si hubo una en esta sesión. */
    private void openCompare() {
        Config cfg = Config.get();
        cfg.save();
        TranslationJob j = Traductor.job();
        int texts = j != null && j.total > 0 ? j.total - j.done : -1;
        minecraft.setScreen(new CompareScreen(this, texts, o -> CompareScreen.apply(o, () -> {
            TraductorES.LOG.info("[Traductor ES] Panel: elegido en «¿Cuál conviene?»: {} · {}", o.name(), cfg.model);
            testResult = Component.literal(o.name() + ": " + o.note()).withStyle(ChatFormatting.YELLOW);
            minecraft.setScreen(this);
        })));
    }

    private void initTraduccion(Config cfg, int y) {
        addRenderableWidget(Button.builder(targetLabel(cfg), b -> {
            int i = TARGETS.indexOf(cfg.targetLanguage);
            cfg.targetLanguage = TARGETS.get((i + 1) % TARGETS.size());
            b.setMessage(targetLabel(cfg));
        }).bounds(fieldX, y, fieldW, 20).tooltip(Tooltip.create(Component.literal(
                "Automático usa el español que tengas elegido en Opciones → Idioma."))).build());
        y += ROW + 4;
        int half = (innerW - 4) / 2;
        toggle(innerX, y, half, "Al abrir el juego", () -> cfg.autoStart, v -> cfg.autoStart = v,
                "Traducir solo lo nuevo cada vez que llegás al menú principal.");
        addRenderableWidget(Button.builder(whilePlayingLabel(cfg), b -> {
            cfg.whilePlaying = switch (cfg.whilePlaying) { case "auto" -> "si"; case "si" -> "no"; default -> "auto"; };
            b.setMessage(whilePlayingLabel(cfg));
            if ("si".equals(cfg.whilePlaying) && cfg.isLocalAi()) {
                report(Component.literal("Ojo: la IA corre en tu PC. Mientras jugás, el juego y la IA comparten la placa de video: "
                        + "bajan los FPS y la traducción va más lenta.").withStyle(ChatFormatting.YELLOW));
            } else {
                testResult = null;
            }
        }).bounds(innerX + half + 4, y, half, 20).tooltip(Tooltip.create(Component.literal(
                "Automático: sigue traduciendo si la IA está en la nube; pausa si está en tu PC (para no bajar los FPS)."))).build());
        y += ROW;
        toggle(innerX, y, half, "Traducir libros", () -> cfg.translateBooks, v -> cfg.translateBooks = v,
                "También los manuales de Patchouli que hay dentro del juego.");
        toggle(innerX + half + 4, y, half, "Otras variantes", () -> cfg.useSpanishVariants, v -> cfg.useSpanishVariants = v,
                "Si un mod no trae tu español pero sí otro (México, Argentina…), usarlo en vez de traducir.");
        y += ROW + 4;
        EditBox batch = field(fieldX, y, 40, String.valueOf(cfg.batchSize), 3, s -> cfg.batchSize = parsePositive(s, cfg.batchSize));
        batch.setFilter(s -> s.chars().allMatch(Character::isDigit));
        batch.setTooltip(Tooltip.create(Component.literal("Cuántos textos se mandan juntos a la IA. Si ves «respuesta cortada», bajalo a 10.")));
        EditBox parallel = field(innerX + innerW - 40, y, 40, String.valueOf(cfg.parallelRequests), 2,
                s -> cfg.parallelRequests = Math.min(16, parsePositive(s, cfg.parallelRequests)));
        parallel.setFilter(s -> s.chars().allMatch(Character::isDigit));
        parallel.setTooltip(Tooltip.create(Component.literal("Peticiones al mismo tiempo. 1 para una IA en tu PC; 2 a 4 en la nube.")));
    }

    private void initHerramientas() {
        int cw = (innerW - 6) / 2, ch = (contentBottom - contentTop - 16 - 6) / 2;
        int x1 = innerX, x2 = innerX + cw + 6, y1 = contentTop + 8, y2 = y1 + ch + 6;
        int by1 = y1 + ch - 26, by2 = y2 + ch - 26;
        addRenderableWidget(Button.builder(Component.literal("Exportar"), b -> exportPack()).bounds(x1 + 6, by1, 80, 20).build());
        Button editor = addRenderableWidget(Button.builder(Component.literal("Próximamente"), b -> {}).bounds(x2 + 6, by1, 96, 20).build());
        editor.active = false;
        addRenderableWidget(Button.builder(Component.literal("Ver informe"), b -> Util.getPlatform().openFile(Config.DIR.resolve("informe.txt").toFile()))
                .bounds(x1 + 6, by2, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abrir carpeta"), b -> Util.getPlatform().openFile(Config.DIR.toFile()))
                .bounds(x2 + 6, by2, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Asistente"), b -> {
            Config.get().save();
            minecraft.setScreen(new WizardScreen(this));
        }).bounds(x2 + 96, by2, 64, 20).build());
    }

    private EditBox field(int x, int y, int w, String value, int max, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(max);
        box.setValue(value);
        box.setResponder(onChange);
        return addRenderableWidget(box);
    }

    private void toggle(int x, int y, int w, String label, java.util.function.BooleanSupplier get,
                        java.util.function.Consumer<Boolean> set, String help) {
        addRenderableWidget(Button.builder(toggleLabel(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleLabel(label, get.getAsBoolean()));
        }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(help))).build());
    }

    private static Component toggleLabel(String label, boolean on) {
        return Component.literal(label + ": ").append(on
                ? Component.literal("Sí").withStyle(ChatFormatting.GREEN)
                : Component.literal("No").withStyle(ChatFormatting.RED));
    }

    static Component whilePlayingLabel(Config cfg) {
        Component value = switch (cfg.whilePlaying) {
            case "si" -> Component.literal("Sí").withStyle(ChatFormatting.GREEN);
            case "no" -> Component.literal("No").withStyle(ChatFormatting.RED);
            default -> Component.literal("Auto").withStyle(ChatFormatting.AQUA);
        };
        return Component.literal("Mientras juego: ").append(value);
    }

    /** Muestra la clave como •••• (que no quede en capturas ni en streams). */
    static void hideSecret(EditBox box) {
        box.setFormatter((text, offset) -> net.minecraft.util.FormattedCharSequence.forward(
                "•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
    }

    private Component targetLabel(Config cfg) {
        if ("auto".equals(cfg.targetLanguage)) {
            String game = minecraft.options.languageCode;
            String now = SpanishTarget.isSupported(game) ? SpanishTarget.displayName(game) : "el juego no está en español";
            return Component.literal("Automático: " + now);
        }
        return Component.literal(SpanishTarget.displayName(cfg.targetLanguage));
    }

    // ---------- Acciones ----------

    private void switchTab(Tab t) {
        if (tab == t) return;
        Config.get().save();
        tab = t;
        rebuildWidgets();
    }

    private void openGuide() {
        Config.get().save();
        minecraft.setScreen(new GuideScreen(this));
    }

    /** Pasa a otro servicio y completa URL, modelo y peticiones simultáneas. La clave de relleno se vacía en la nube. */
    private void applyPreset(Preset p) {
        Config cfg = Config.get();
        cfg.applyPreset(p);
        TraductorES.LOG.info("[Traductor ES] Panel: servicio cambiado a {}", p.label);
        testResult = p.isCloud()
                ? Component.literal((cfg.apiKey.isBlank() ? "Este servicio necesita una clave (API key). " : "")
                        + "Tocá «Conseguir» para crearla. Ojo: los textos de los mods se envían a ese servicio por internet.")
                        .withStyle(ChatFormatting.YELLOW)
                : Component.literal("IA en tu PC: tiene que estar abierta. Tocá Probar conexión para comprobarlo.").withStyle(ChatFormatting.GRAY);
        rebuildWidgets();
    }

    private void toggleRun() {
        TranslationJob j = Traductor.job();
        if (j != null && j.isRunning()) {
            if (j.state == TranslationJob.State.PAUSED) j.resume();
            else j.pause();
        } else {
            Config.get().save();
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
        if (testBtn != null) {
            testBtn.active = !testing;
            testBtn.setMessage(Component.literal(testing ? "Probando…" : "Probar conexión"));
        }
        if (helpBtn != null) {
            Preset p = Preset.fromUrl(Config.get().apiUrl);
            helpBtn.setMessage(Component.literal(p.isCloud() ? "Conseguir" : "Descargar"));
            helpBtn.active = p.helpUrl != null;
        }
    }

    private void openHelpPage() {
        Preset p = Preset.fromUrl(Config.get().apiUrl);
        if (p.helpUrl == null) return;
        Util.getPlatform().openUri(p.helpUrl);
        report(Component.literal(p.isCloud()
                ? "Se abrió " + p.helpUrl + " en el navegador. Creá la clave, copiala (se muestra una sola vez) y pegala en Clave."
                : "Se abrió " + p.helpUrl + " en el navegador para descargar " + p.label.replace(" (en tu PC)", "") + ".")
                .withStyle(ChatFormatting.AQUA));
    }

    /** Muestra un resultado en el panel y lo deja también en el log (para poder diagnosticar después). */
    private void report(Component msg) {
        testResult = msg;
        TraductorES.LOG.info("[Traductor ES] Panel: {}", msg.getString());
    }

    /**
     * Lista los modelos del servidor y hace una petición de prueba. Si el modelo configurado no existe
     * o el servicio lo retiró, elige el recomendado (o el que sugiere el propio servicio) y lo deja puesto.
     */
    private void testConnection() {
        Config cfg = Config.get();
        cfg.save();
        Preset preset = Preset.fromUrl(cfg.apiUrl);
        if (preset.isCloud() && cfg.apiKey.isBlank()) {
            report(Component.literal("Este servicio necesita una clave (API key). Tocá «Conseguir», creala y pegala en Clave.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        testResult = Component.literal(cfg.isLocalAi()
                ? "Probando… (la primera vez puede tardar unos segundos mientras la IA carga el modelo)"
                : "Probando… (algunos servicios tardan en responder la primera vez)").withStyle(ChatFormatting.GRAY);
        testing = true;
        Thread t = new Thread(() -> {
            LlmClient client = new LlmClient();
            try {
                List<String> models = client.listModels(cfg);
                String note = "";
                if (!models.isEmpty() && (cfg.model.isBlank() || !models.contains(cfg.model))) {
                    String pick = ModelPicker.recommend(preset, models);
                    if (pick != null) {
                        note = cfg.model.isBlank() ? " Se eligió el modelo recomendado." : " «" + cfg.model + "» no estaba; se eligió el recomendado.";
                        setModel(cfg, pick);
                    }
                }
                long t0 = System.currentTimeMillis();
                try {
                    pingRetrying(client, cfg);
                } catch (LlmClient.LlmException e) {
                    if (!e.isModelNotFound()) throw e;
                    // El servicio lista el modelo pero ya no lo da (pasó con Gemini): se prueba con su sugerencia o el recomendado.
                    String old = cfg.model, alt = ModelPicker.suggestedReplacement(e.getMessage());
                    if (alt == null || alt.equals(old)) {
                        List<String> others = new ArrayList<>(models);
                        others.remove(old);
                        alt = ModelPicker.recommend(preset, others);
                    }
                    if (alt == null) throw e;
                    setModel(cfg, alt);
                    note = " «" + old + "» ya no está disponible; se cambió a «" + alt + "».";
                    t0 = System.currentTimeMillis();
                    pingRetrying(client, cfg);
                }
                Traductor.setConnection(Traductor.Connection.OK, cfg.model);
                MutableComponent ok = Component.literal("Conexión correcta con «" + cfg.model + "» ("
                        + (System.currentTimeMillis() - t0) / 100 / 10.0 + " s)." + note).withStyle(ChatFormatting.GREEN);
                List<String> others = ModelPicker.ranked(preset, models);
                others.remove(cfg.model);
                if (!others.isEmpty()) ok.append(Component.literal(" Otros modelos: " + summarize(others)).withStyle(ChatFormatting.GRAY));
                report(ok);
            } catch (Exception e) {
                Traductor.setConnection(Traductor.Connection.FAIL, LlmClient.describe(e));
                String with = cfg.model.isBlank() ? "" : " con «" + cfg.model + "»";
                report(Component.literal("Error" + with + ": " + LlmClient.describe(e)).withStyle(ChatFormatting.RED));
            } finally {
                testing = false;
            }
        }, "Traductor ES - prueba");
        t.setDaemon(true);
        t.start();
    }

    /** Prueba la conexión reintentando si el servicio está saturado (503) o pide esperar (429). */
    private void pingRetrying(LlmClient client, Config cfg) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                client.ping(cfg);
                return;
            } catch (LlmClient.LlmException e) {
                if (!e.isTransient() || attempt >= 3) throw e;
                report(Component.literal("El servicio está saturado, reintentando (" + attempt + "/2)…").withStyle(ChatFormatting.YELLOW));
                Thread.sleep(e.retryAfterSeconds > 0 ? Math.min(e.retryAfterSeconds, 10) * 1000 : 4000);
            }
        }
    }

    /** Guarda el modelo elegido y lo muestra en el campo (si la pestaña está abierta). */
    void setModel(Config cfg, String m) {
        cfg.model = m;
        cfg.save();
        Minecraft.getInstance().execute(() -> {
            if (model != null) model.setValue(m);
        });
    }

    private static String summarize(List<String> models) {
        if (models.size() <= MAX_MODELS_SHOWN) return String.join(", ", models);
        return String.join(", ", models.subList(0, MAX_MODELS_SHOWN)) + " y " + (models.size() - MAX_MODELS_SHOWN) + " más";
    }

    private void exportPack() {
        try {
            Path zip = PackExporter.export();
            Platform.toast(Component.literal("Traductor ES"), Component.literal("Pack exportado a resourcepacks/" + zip.getFileName()));
            report(Component.literal("Pack exportado a resourcepacks/" + zip.getFileName()
                    + ". Quien juegue el mismo modpack lo activa como cualquier paquete de recursos, sin IA.")
                    .withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            TraductorES.LOG.error("[Traductor ES] Error al exportar el pack", e);
            report(Component.literal("No se pudo exportar: " + LlmClient.describe(e)).withStyle(ChatFormatting.RED));
        }
    }

    /** Resumen de la última traducción, sacado de informe.txt. */
    private static String readLastReport() {
        try {
            String date = "", result = "", sent = "", failed = "";
            for (String line : Files.readAllLines(Config.DIR.resolve("informe.txt"), StandardCharsets.UTF_8)) {
                if (line.startsWith("Fecha: ")) date = line.substring(7);
                else if (line.startsWith("Resultado: ")) result = line.substring(11);
                else if (line.startsWith("Textos únicos enviados a la IA: ")) sent = line.substring(32);
                else if (line.startsWith("Fallidos")) failed = line.substring(line.indexOf(':') + 1).trim();
            }
            if (result.isEmpty()) return "Todavía no hay ninguna.";
            return result + " · " + sent + " textos a la IA · " + failed + " fallidos · " + date;
        } catch (Exception e) {
            return "Todavía no hay ninguna.";
        }
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

    // ---------- Dibujo ----------

    @Override
    protected void renderPanels(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UiKit.panel(g, left, contentTop, W, contentBottom - contentTop);
        if (tab == Tab.HERRAMIENTAS) {
            int cw = (innerW - 6) / 2, ch = (contentBottom - contentTop - 16 - 6) / 2;
            int y1 = contentTop + 8, y2 = y1 + ch + 6, x2 = innerX + cw + 6;
            UiKit.card(g, innerX, y1, cw, ch);
            UiKit.card(g, x2, y1, cw, ch);
            UiKit.card(g, innerX, y2, cw, ch);
            UiKit.card(g, x2, y2, cw, ch);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Config cfg = Config.get();

        // Encabezado: ícono, título e indicador de conexión.
        UiKit.icon(g, left, 5, 1);
        g.drawString(font, "Traductor ES", left + 21, 10, UiKit.GOLD, true);
        drawConnectionChip(g);

        int y = contentTop + 8;
        switch (tab) {
            case CONEXION -> {
                g.drawString(font, "Servicio", innerX, y + 6, UiKit.MUTED, false);
                Preset p = Preset.fromUrl(cfg.apiUrl);
                g.drawCenteredString(font, p.label, fieldX + fieldW / 2, y + 6, UiKit.WHITE);
                String[] labels = {"URL de la API", "Clave (API key)", "Modelo"};
                for (int i = 0; i < labels.length; i++) g.drawString(font, labels[i], innerX, y + ROW * (i + 1) + 6, UiKit.MUTED, false);
                drawMessage(g, y + ROW * 4 + 26);
            }
            case TRADUCCION -> {
                g.drawString(font, "Español de destino", innerX, y + 6, UiKit.MUTED, false);
                int ry = y + ROW * 3 + 8 + 6;
                g.drawString(font, "Textos por lote", innerX, ry, UiKit.MUTED, false);
                String vez = "A la vez";
                g.drawString(font, vez, innerX + innerW - 46 - font.width(vez), ry, UiKit.MUTED, false);
                drawMessage(g, y + ROW * 4 + 18);
            }
            case HERRAMIENTAS -> {
                int cw = (innerW - 6) / 2, ch = (contentBottom - contentTop - 16 - 6) / 2;
                int y1 = contentTop + 8, y2 = y1 + ch + 6, x2 = innerX + cw + 6;
                card(g, innerX, y1, cw, "Exportar pack", "Un .zip para amigos con el mismo modpack. Lo usan sin IA.");
                card(g, x2, y1, cw, "Corregir traducciones", "Buscá un texto y cambiá su traducción.");
                card(g, innerX, y2, cw, "Última traducción", lastReport);
                card(g, x2, y2, cw, "Archivos y asistente", "Configuración, glosario e informe. El asistente comprueba todo de nuevo.");
            }
        }
        drawProgress(g);
    }

    private void card(GuiGraphics g, int x, int y, int w, String title, String text) {
        g.drawString(font, title, x + 6, y + 6, UiKit.YELLOW, false);
        UiKit.wrapped(g, font, Component.literal(text), x + 6, y + 18, w - 12, UiKit.MUTED);
    }

    private void drawMessage(GuiGraphics g, int y) {
        Component msg = testResult;
        if (msg != null) UiKit.wrapped(g, font, msg, innerX, y, innerW, UiKit.WHITE);
    }

    private void drawConnectionChip(GuiGraphics g) {
        String text;
        int color, box;
        switch (Traductor.connection) {
            case OK -> { text = "Conectado · " + Traductor.connectionDetail; color = UiKit.GREEN; box = 0xFF0F2A12; }
            case FAIL -> { text = "Sin conexión"; color = UiKit.RED; box = 0xFF2A0F0F; }
            default -> { text = "Sin probar"; color = UiKit.MUTED; box = 0xFF1F2126; }
        }
        int maxW = W - 58 - 6 - 110;
        String shown = font.width(text) > maxW ? font.plainSubstrByWidth(text, maxW - 6) + "…" : text;
        int w = font.width(shown) + 16, x = left + W - 58 - 6 - w;
        g.fill(x, 6, x + w, 24, 0xFF000000);
        g.fill(x + 1, 7, x + w - 1, 23, box);
        UiKit.dot(g, x + 5, 13, color);
        g.drawString(font, shown, x + 13, 11, color, false);
    }

    /** Estado de la traducción abajo a la izquierda: texto y barra. */
    private void drawProgress(GuiGraphics g) {
        int x = left, y = height - 30, w = W - 274;
        TranslationJob j = Traductor.job();
        if (j == null) {
            g.drawString(font, "Listo para traducir", x, y + 6, UiKit.MUTED, false);
            return;
        }
        switch (j.state) {
            case TRANSLATING, PAUSED -> {
                int pct = j.total == 0 ? 0 : j.done * 100 / j.total;
                String head = (j.state == TranslationJob.State.PAUSED ? "En pausa " : "Traduciendo ") + Traductor.fmt(j.done) + "/" + Traductor.fmt(j.total);
                g.drawString(font, font.plainSubstrByWidth(head, w - 30), x, y, j.state == TranslationJob.State.PAUSED ? UiKit.YELLOW : UiKit.GOLD, false);
                String right = pct + " %";
                g.drawString(font, right, x + w - font.width(right), y, UiKit.MUTED, false);
                UiKit.progressBar(g, x, y + 11, w, 7, j.total == 0 ? 0 : (double) j.done / j.total);
            }
            case SCANNING -> g.drawString(font, font.plainSubstrByWidth(j.message, w), x, y + 6, UiKit.MUTED, false);
            case DONE -> {
                String done = j.total == 0 ? "✓ Todo traducido" : "✓ Terminado: " + Traductor.fmt(j.done - j.failed) + " nuevos";
                g.drawString(font, font.plainSubstrByWidth(done, w), x, y + 6, UiKit.GREEN, false);
            }
            default -> g.drawString(font, font.plainSubstrByWidth(j.message, w), x, y + 6,
                    j.state == TranslationJob.State.ERROR ? UiKit.RED : UiKit.YELLOW, false);
        }
    }

    @Override
    public void onClose() {
        Config.get().save();
        minecraft.setScreen(parent);
    }
}
