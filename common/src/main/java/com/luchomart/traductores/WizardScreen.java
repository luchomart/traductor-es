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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Asistente de 3 pasos: dónde trabaja la IA, comprobación automática y resumen.
 * Aparece la primera vez y cada vez que una traducción falla por algo de configuración.
 */
public class WizardScreen extends BaseScreen {
    private enum Mode { LOCAL, CLOUD }

    private enum Status { PENDING, OK, FAIL, WARN }

    /**
     * Cada ronda de comprobaciones tiene un número. Si el jugador cambia de servicio mientras una corre, empieza otra y
     * la vieja ya no puede escribir en pantalla ni en la configuración (si no, se mezclaban servicio y modelo).
     */
    private static volatile int currentRun;
    private static final ThreadLocal<Integer> RUN = new ThreadLocal<>();

    /** Lo escribe la ronda vigente (o el hilo del juego, que no tiene ronda). */
    private static boolean current() {
        Integer r = RUN.get();
        return r == null || r == currentRun;
    }

    /** Una fila de la comprobación: estado, texto y un botón opcional para arreglarlo. */
    private static final class Check {
        volatile Status status = Status.PENDING;
        volatile String title = "", detail = "";
        volatile String actionLabel;
        volatile Runnable action;

        void set(Status s, String t, String d) {
            if (!current()) return;
            status = s; title = t; detail = d; actionLabel = null; action = null;
        }

        void action(String label, Runnable r) {
            if (!current()) return;
            actionLabel = label; action = r;
        }
    }

    /** El modelo elegido en la comprobación pasa a la configuración, si la ronda sigue vigente y el servicio es el mismo. */
    private static void applyModel(Config work) {
        if (!current()) return;
        Config live = Config.get();
        if (!live.apiUrl.equals(work.apiUrl)) return;
        live.model = work.model;
        live.save();
    }

    private static final int ROW = 28;

    private final Screen parent;
    private int step = 1;
    private Mode mode;
    /** null mientras se busca; después, si Ollama respondió en esta PC. */
    private volatile Boolean ollamaFound;
    private final Check cAi = new Check(), cModel = new Check(), cLang = new Check(), cTexts = new Check();
    private volatile Estimator.Result estimate;
    private volatile boolean checking;
    private int W, left, checksTop;
    /** Idioma con el que se hizo la última comprobación (si cambia, se vuelve a comprobar). */
    private String checkedLang;

    public WizardScreen(Screen parent) {
        super(Component.literal("Asistente de Traductor ES"));
        this.parent = parent;
        mode = Preset.fromUrl(Config.get().apiUrl).isCloud() ? Mode.CLOUD : Mode.LOCAL;
        detectOllama();
        estimateEarly();
    }

    /** Cuenta los textos desde el paso 1, para comparar servicios antes de elegir (sin IA, solo leyendo los mods). */
    private void estimateEarly() {
        Minecraft mc = Minecraft.getInstance();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        String assumed = target != null ? target : "es_es";
        Thread t = new Thread(() -> {
            try {
                Estimator.Result r = Estimator.estimate(scanResources(mc), assumed, Config.get());
                if (estimate == null) estimate = r;
            } catch (Throwable e) {
                TraductorES.LOG.warn("[Traductor ES] No se pudo contar los textos: {}", e.toString());
            }
        }, "Traductor ES - contar textos");
        t.setDaemon(true);
        t.start();
    }

    /** Leer los recursos va en el hilo del juego; el resto, en el hilo que llama. */
    static Map<String, Map<String, Map<String, String>>> scanResources(Minecraft mc) {
        CompletableFuture<Map<String, Map<String, Map<String, String>>>> scan = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                scan.complete(LangScanner.scan(mc.getResourceManager()));
            } catch (Throwable e) {
                scan.completeExceptionally(e);
            }
        });
        return scan.join();
    }

    /** Abre "¿Cuál conviene?"; al elegir una opción, la aplica y vuelve al paso 2 a comprobarla. */
    private void openCompare() {
        Estimator.Result r = estimate;
        minecraft.setScreen(new CompareScreen(this, r == null ? -1 : r.texts(), o -> {
            mode = o.preset().isCloud() ? Mode.CLOUD : Mode.LOCAL;
            CompareScreen.apply(o, () -> {
                minecraft.setScreen(this);
                goTo(2);
            });
        }));
    }

    private void detectOllama() {
        Thread t = new Thread(() -> {
            Config probe = new Config();
            probe.apiUrl = Preset.OLLAMA.url;
            probe.apiKey = "";
            try {
                new LlmClient().listModels(probe);
                ollamaFound = true;
            } catch (Exception e) {
                ollamaFound = false;
            }
        }, "Traductor ES - buscar Ollama");
        t.setDaemon(true);
        t.start();
    }

    // ---------- Construcción ----------

    @Override
    protected void init() {
        W = Math.min(width - 24, 440);
        left = (width - W) / 2;
        int by = height - 28;
        switch (step) {
            case 1 -> initStep1(by);
            case 2 -> {
                if (!checking && checkedLang != null && !checkedLang.equals(minecraft.options.languageCode)) runChecks();
                initStep2(by);
            }
            default -> initStep3(by);
        }
    }

    private void initStep1(int by) {
        int cw = (W - 8) / 2, cy = 70, ch = Math.min(100, by - cy - 10);
        addRenderableWidget(new OptionCard(left, cy, cw, ch, Component.literal("En mi PC"), () -> List.of(
                Component.literal("✓ Gratis · sin internet · sin cuentas").withStyle(ChatFormatting.GREEN),
                Component.literal("Usa Ollama. Ideal con placa de video de 8 GB o más."),
                ollamaLine()), b -> mode = Mode.LOCAL, () -> mode == Mode.LOCAL));
        addRenderableWidget(new OptionCard(left + cw + 8, cy, cw, ch, Component.literal("En la nube"), () -> List.of(
                Component.literal("✓ Sin placa de video · rápido").withStyle(ChatFormatting.GREEN),
                Component.literal("Gemini, Groq u OpenRouter. Necesitás una clave gratis; los textos se envían por internet.")),
                b -> mode = Mode.CLOUD, () -> mode == Mode.CLOUD));
        addRenderableWidget(Button.builder(Component.literal("Ahora no"), b -> notNow()).bounds(left, by, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No mostrar más"), b -> neverAgain()).bounds(left + 84, by, 100, 20).build());
        // En pantallas angostas no entra entre los otros botones: va arriba de "Siguiente".
        boolean fits = left + W - 214 >= left + 188;
        addRenderableWidget(Button.builder(Component.literal("¿Cuál conviene?"), b -> openCompare())
                .bounds(fits ? left + W - 214 : left + W - 110, fits ? by : by - 24, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Siguiente"), b -> goTo(2)).bounds(left + W - 100, by, 100, 20).build());
    }

    private Component ollamaLine() {
        Boolean found = ollamaFound;
        if (found == null) return Component.literal("Buscando Ollama en tu PC…").withStyle(ChatFormatting.GRAY);
        return found ? Component.literal("Detectado: Ollama está abierto en tu PC").withStyle(ChatFormatting.GREEN)
                : Component.literal("No encontramos Ollama abierto (te ayudamos en el paso 2)").withStyle(ChatFormatting.GRAY);
    }

    private void initStep2(int by) {
        Config cfg = Config.get();
        int y = 46;
        if (mode == Mode.CLOUD) {
            int fx = left + 90, fw = W - 90;
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> switchCloud(Preset.fromUrl(cfg.apiUrl).previous()))
                    .bounds(fx, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> switchCloud(Preset.fromUrl(cfg.apiUrl).next()))
                    .bounds(fx + fw - 20, y, 20, 20).build());
            y += 24;
            EditBox key = new EditBox(font, fx, y, fw - 84, 20, Component.literal("Clave"));
            key.setMaxLength(256);
            key.setValue(cfg.apiKey);
            key.setResponder(s -> cfg.apiKey = s.trim());
            ConfigScreen.hideSecret(key);
            key.setHint(Component.literal("Pegá acá tu clave").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(key);
            addRenderableWidget(Button.builder(Component.literal("Conseguir"), b -> {
                Preset p = Preset.fromUrl(cfg.apiUrl);
                if (p.helpUrl != null) Util.getPlatform().openUri(p.helpUrl);
            }).bounds(fx + fw - 80, y, 80, 20).build());
            y += 30;
        }
        checksTop = y;
        Check[] checks = {cAi, cModel, cLang, cTexts};
        for (int i = 0; i < checks.length; i++) {
            Check c = checks[i];
            if (c.actionLabel != null && c.action != null && !checking) {
                Runnable r = c.action;
                addRenderableWidget(Button.builder(Component.literal(c.actionLabel), b -> r.run())
                        .bounds(left + W - 96, checksTop + i * ROW + 3, 96, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> goTo(1)).bounds(left, by, 70, 20).build());
        Button again = addRenderableWidget(Button.builder(Component.literal(checking ? "Comprobando…" : "Comprobar de nuevo"),
                b -> runChecks()).bounds(left + W / 2 - 60, by, 120, 20).build());
        again.active = !checking;
        Button next = addRenderableWidget(Button.builder(Component.literal("Siguiente"), b -> goTo(3)).bounds(left + W - 100, by, 100, 20).build());
        next.active = !checking && cAi.status == Status.OK && cModel.status == Status.OK && cLang.status == Status.OK;
    }

    private void initStep3(int by) {
        Config cfg = Config.get();
        int y = height - 62;
        int half = (W - 4) / 2;
        addRenderableWidget(Button.builder(ConfigScreen.whilePlayingLabel(cfg), b -> {
            cfg.whilePlaying = switch (cfg.whilePlaying) { case "auto" -> "si"; case "si" -> "no"; default -> "auto"; };
            b.setMessage(ConfigScreen.whilePlayingLabel(cfg));
        }).bounds(left + half + 4, y, half, 20).build());
        addRenderableWidget(Button.builder(autoStartLabel(cfg), b -> {
            cfg.autoStart = !cfg.autoStart;
            b.setMessage(autoStartLabel(cfg));
        }).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> goTo(2)).bounds(left, by, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Empezar a traducir").withStyle(ChatFormatting.GREEN), b -> finish())
                .bounds(left + W - 140, by, 140, 20).build());
    }

    private static Component autoStartLabel(Config cfg) {
        return Component.literal("Al abrir el juego: ").append(cfg.autoStart
                ? Component.literal("Sí").withStyle(ChatFormatting.GREEN) : Component.literal("No").withStyle(ChatFormatting.RED));
    }

    // ---------- Acciones ----------

    private void goTo(int s) {
        step = s;
        if (s == 2) {
            prepareService();
            runChecks();
        }
        rebuildWidgets();
    }

    /** Ajusta el servicio al modo elegido: Ollama en la PC; en la nube, Gemini si no había uno elegido. */
    private void prepareService() {
        Config cfg = Config.get();
        Preset current = Preset.fromUrl(cfg.apiUrl);
        if (mode == Mode.LOCAL && current.isCloud()) applyPreset(Preset.OLLAMA);
        if (mode == Mode.CLOUD && !current.isCloud()) applyPreset(Preset.GEMINI);
    }

    private void switchCloud(Preset p) {
        while (!p.isCloud()) p = p.next();
        applyPreset(p);
        runChecks();
        rebuildWidgets();
    }

    private static void applyPreset(Preset p) {
        Config.get().applyPreset(p);
    }

    /** Comprueba IA, modelo, idioma y cuánto falta traducir. Lo lento va en otro hilo. */
    private void runChecks() {
        Config.get().save();
        // La ronda trabaja con su propia copia: cambiar de servicio a mitad de camino no la mezcla.
        Config cfg = Config.get().snapshot();
        int run = ++currentRun;
        Minecraft mc = Minecraft.getInstance();
        checking = true;
        checkedLang = mc.options.languageCode;
        for (Check c : new Check[]{cAi, cModel, cLang, cTexts}) c.set(Status.PENDING, "Comprobando…", "");

        String target = SpanishTarget.resolve(cfg.targetLanguage, mc.options.languageCode);
        if (target != null) {
            cLang.set(Status.OK, "El juego está en " + SpanishTarget.displayName(target), "se traduce a esa variante");
        } else {
            cLang.set(Status.FAIL, "El juego no está en español", "elegí cualquier español en el idioma del juego");
        }
        cLang.action("Cambiar", () -> mc.setScreen(Platform.languageScreen(this)));

        Preset preset = Preset.fromUrl(cfg.apiUrl);
        Thread t = new Thread(() -> {
            RUN.set(run);
            try {
                checkAi(cfg, preset);
                if (target != null) {
                    Estimator.Result r = Estimator.estimate(scanResources(mc), target, cfg);
                    estimate = r;
                    if (r.texts() == 0) cTexts.set(Status.OK, "No falta traducir nada", "todo está en tu español");
                    else {
                        cTexts.set(Status.WARN, Traductor.fmt(r.texts()) + " textos sin traducir en " + r.mods() + " mods",
                                "la primera vez tarda: " + r.duration(cfg) + " aprox.");
                        cTexts.action("Comparar", this::openCompare);
                    }
                } else {
                    cTexts.set(Status.WARN, "Textos por traducir", "se calculan cuando el juego esté en español");
                }
            } catch (Throwable e) {
                TraductorES.LOG.error("[Traductor ES] Error en la comprobación del asistente", e);
                if (cTexts.status == Status.PENDING) cTexts.set(Status.WARN, "No se pudo calcular cuánto falta", LlmClient.describe(e));
            } finally {
                if (run == currentRun) {
                    checking = false;
                    mc.execute(this::rebuildWidgets);
                }
            }
        }, "Traductor ES - asistente");
        t.setDaemon(true);
        t.start();
    }

    private void checkAi(Config cfg, Preset preset) {
        boolean cloud = preset.isCloud();
        if (cloud && cfg.apiKey.isBlank()) {
            cAi.set(Status.FAIL, "Falta la clave de " + preset.label.replace(" (nube)", ""), "creala con «Conseguir» y pegala arriba");
            cAi.action("Conseguir", () -> Util.getPlatform().openUri(preset.helpUrl));
            cModel.set(Status.PENDING, "Modelo", "primero tiene que responder la IA");
            return;
        }
        LlmClient client = new LlmClient();
        List<String> models;
        try {
            models = client.listModels(cfg);
        } catch (Exception e) {
            failAi(preset, e);
            return;
        }
        String where = cloud ? preset.label.replace(" (nube)", "") : cfg.apiUrl.replaceFirst("^https?://", "").replaceFirst("/v1/.*$", "");
        if (models.isEmpty() && !cloud) {
            cAi.set(Status.OK, "La IA responde", "en " + where);
            if (preset == Preset.LM_STUDIO) {
                cModel.set(Status.FAIL, "No hay ningún modelo cargado", "en LM Studio: bajá un modelo (por ejemplo Gemma 3 12B) y cargalo");
            } else {
                cModel.set(Status.FAIL, "No hay ningún modelo instalado", "en una terminal: ollama pull translategemma:12b");
                cModel.action("Copiar comando", () -> Minecraft.getInstance().keyboardHandler.setClipboard("ollama pull translategemma:12b"));
            }
            return;
        }
        if (!models.isEmpty() && (cfg.model.isBlank() || !models.contains(cfg.model))) {
            String pick = ModelPicker.recommend(preset, ModelStatus.usable(cfg.apiUrl, models));
            if (pick != null) {
                cfg.model = pick;
                applyModel(cfg);
            }
        }
        String switched = null;
        for (int attempt = 0; ; attempt++) {
            try {
                ConfigScreen.pingPatiently(client, cfg, msg -> cAi.set(Status.PENDING, "Comprobando…", msg));
                ModelStatus.ok(cfg.apiUrl, cfg.model);
                break;
            } catch (LlmClient.LlmException e) {
                // Retirado, sin plan gratis o agotado: se prueba con otro modelo que pueda andar.
                ModelStatus.Info info = ModelStatus.get(cfg.apiUrl, cfg.model); // ya lo aprendió pingPatiently
                List<String> usable = ModelStatus.usable(cfg.apiUrl, models);
                usable.remove(cfg.model);
                String alt = info == null || attempt >= 3 ? null : ModelPicker.recommend(preset, usable);
                if (alt == null) {
                    failAi(preset, e);
                    if (info != null) cModel.set(Status.FAIL, "Modelo: " + cfg.model, info.tag().toLowerCase(java.util.Locale.ROOT) + " · elegí otro en el panel");
                    return;
                }
                switched = cfg.model + ": " + info.tag().toLowerCase(java.util.Locale.ROOT);
                TraductorES.LOG.info("[Traductor ES] Asistente: {} ({}), se prueba con {}", cfg.model, info.tag(), alt);
                cfg.model = alt;
                applyModel(cfg);
            } catch (Exception e) {
                failAi(preset, e);
                return;
            }
        }
        Traductor.setConnection(Traductor.Connection.OK, cfg.model);
        cAi.set(Status.OK, "La IA responde", "en " + where + " · midiendo la velocidad…");
        // Minitest: con la velocidad medida, el tiempo de "textos sin traducir" sale más preciso.
        String target = SpanishTarget.resolve(cfg.targetLanguage, Minecraft.getInstance().options.languageCode);
        SpeedTest.Result speed = ConfigScreen.speedTestAllowed() ? SpeedTest.tryMeasure(client, cfg, target != null ? target : "es_es") : null;
        String quota = client.quota(cfg);
        cAi.set(Status.OK, "La IA responde" + (speed != null ? " · " + SpeedLevel.describe(speed.effective()) : ""),
                quota != null ? quota : "en " + where);
        cModel.set(Status.OK, "Modelo: " + cfg.model, switched != null ? "en vez de " + switched
                : cloud ? "elegido automáticamente" : "instalado en tu PC");
        // Con Ollama y sin TranslateGemma: anda, pero hay uno que traduce mejor con el mismo tamaño.
        if (preset == Preset.OLLAMA && !cfg.model.startsWith("translategemma") && models.stream().noneMatch(m -> m.startsWith("translategemma"))) {
            cModel.set(Status.OK, "Modelo: " + cfg.model, "consejo: translategemma:12b traduce mejor (mismo tamaño)");
            cModel.action("Copiar comando", () -> Minecraft.getInstance().keyboardHandler.setClipboard("ollama pull translategemma:12b"));
        }
    }

    private void failAi(Preset preset, Exception e) {
        Traductor.setConnection(Traductor.Connection.FAIL, LlmClient.describe(e));
        cAi.set(Status.FAIL, preset.isCloud() ? "La IA no responde" : "Ollama no responde", LlmClient.describe(e));
        if (preset.helpUrl != null) cAi.action(preset.isCloud() ? "Conseguir" : "Descargar", () -> Util.getPlatform().openUri(preset.helpUrl));
        cModel.set(Status.PENDING, "Modelo", "primero tiene que responder la IA");
    }

    private void notNow() {
        Traductor.wizardDismissed = true;
        minecraft.setScreen(parent);
    }

    private void neverAgain() {
        Config cfg = Config.get();
        cfg.showWizard = false;
        cfg.save();
        minecraft.setScreen(parent);
    }

    private void finish() {
        Config cfg = Config.get();
        cfg.setupDone = true;
        cfg.save();
        TraductorES.LOG.info("[Traductor ES] Asistente completado: {} · {}", Preset.fromUrl(cfg.apiUrl).label, cfg.model);
        minecraft.setScreen(parent);
        Traductor.start();
    }

    @Override
    public void tick() {
        // La detección de Ollama y las comprobaciones terminan en otro hilo: el paso 1 se redibuja solo.
    }

    // ---------- Dibujo ----------

    @Override
    protected void renderPanels(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (step == 2) UiKit.panel(g, left - 6, checksTop - 4, W + 12, ROW * 4 + 6);
        if (step == 3) {
            int cw = (W - 12) / 3;
            for (int i = 0; i < 3; i++) UiKit.card(g, left + i * (cw + 6), 46, cw, 34);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Encabezado: "Paso N de 3" y los tres segmentos.
        g.drawString(font, "Paso " + step + " de 3", left, 8, UiKit.DIM, false);
        for (int i = 0; i < 3; i++) {
            int sx = left + W - 3 * 34 + i * 34;
            g.fill(sx, 10, sx + 30, 14, i < step ? UiKit.GOLD : 0xFF3A3A3A);
        }
        switch (step) {
            case 1 -> {
                g.drawString(font, "¡Hola! Vamos a poner tus mods en español", left, 24, UiKit.GOLD, true);
                UiKit.wrapped(g, font, Component.literal("Una IA traduce los textos de los mods que no tienen español. Primero, ¿dónde querés que trabaje?"),
                        left, 40, W, UiKit.MUTED);
                Estimator.Result r = estimate;
                int sy = 70 + Math.min(100, height - 28 - 70 - 10) + 6;
                if (r != null && r.texts() > 0 && sy + 10 < height - 32) {
                    List<ServiceGuide.Option> o = ServiceGuide.OPTIONS;
                    String line = "Este modpack: " + Traductor.fmt(r.texts()) + " textos · En tu PC " + o.get(o.size() - 1).duration(r.texts())
                            + " · En la nube " + o.get(0).duration(r.texts()) + " a " + o.get(2).duration(r.texts());
                    g.drawString(font, font.plainSubstrByWidth(line, W), left, sy, UiKit.DIM, false);
                }
            }
            case 2 -> {
                g.drawString(font, "Comprobando que todo esté listo", left, 24, UiKit.GOLD, true);
                if (mode == Mode.CLOUD) {
                    Preset p = Preset.fromUrl(Config.get().apiUrl);
                    g.drawString(font, "Servicio", left, 52, UiKit.MUTED, false);
                    g.drawCenteredString(font, p.label, left + 90 + (W - 90) / 2, 52, UiKit.WHITE);
                    g.drawString(font, "Clave (API key)", left, 76, UiKit.MUTED, false);
                }
                Check[] checks = {cAi, cModel, cLang, cTexts};
                for (int i = 0; i < checks.length; i++) drawCheck(g, checks[i], checksTop + i * ROW);
            }
            default -> {
                g.drawString(font, "¡Todo listo!", left, 24, UiKit.GOLD, true);
                Config cfg = Config.get();
                String target = SpanishTarget.resolve(cfg.targetLanguage, minecraft.options.languageCode);
                Estimator.Result r = estimate;
                Preset p = Preset.fromUrl(cfg.apiUrl);
                String[][] cards = {
                        {"IA", p.label.replaceFirst(" \\(.*\\)", "") + " · " + cfg.model},
                        {"Idioma", target == null ? "—" : SpanishTarget.displayName(target)},
                        {"Por traducir", r == null ? "—" : r.texts() == 0 ? "Nada pendiente" : Traductor.fmt(r.texts()) + " textos · " + r.duration(cfg)}};
                int cw = (W - 12) / 3;
                for (int i = 0; i < 3; i++) {
                    int cx = left + i * (cw + 6);
                    g.drawString(font, cards[i][0], cx + 6, 51, UiKit.DIM, false);
                    g.drawString(font, font.plainSubstrByWidth(cards[i][1], cw - 12), cx + 6, 64, UiKit.WHITE, false);
                }
                int y = 92;
                String playing = cfg.translateWhilePlaying()
                        ? (cfg.isLocalAi() ? "• Sigue traduciendo mientras jugás. Ojo: con la IA en tu PC bajan los FPS y va más lento."
                                           : "• Sigue traduciendo mientras jugás: la IA está en la nube y no usa tu placa de video.")
                        : "• Traduce mientras estás en el menú. Al entrar a un mundo se pausa, y sigue al volver.";
                for (String line : new String[]{
                        playing,
                        "• Si cerrás el juego, lo traducido queda guardado.",
                        "• Las próximas veces solo traduce lo nuevo, en segundos."}) {
                    y = UiKit.wrapped(g, font, Component.literal(line), left, y, W, UiKit.MUTED) + 4;
                }
            }
        }
    }

    private void drawCheck(GuiGraphics g, Check c, int y) {
        int color = switch (c.status) {
            case OK -> 0xFF2F7A35;
            case FAIL -> 0xFF8A2020;
            case WARN -> 0xFF8A6D1A;
            default -> 0xFF3A3A3A;
        };
        String mark = switch (c.status) {
            case OK -> "✓";
            case FAIL -> "✗";
            case WARN -> "!";
            default -> "…";
        };
        g.fill(left, y + 4, left + 14, y + 18, color);
        g.drawCenteredString(font, mark, left + 7, y + 7, UiKit.WHITE);
        int tw = W - 22 - (c.actionLabel != null ? 100 : 0);
        g.drawString(font, font.plainSubstrByWidth(c.title, tw), left + 22, y + 3, UiKit.TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(c.detail, tw), left + 22, y + 14, UiKit.DIM, false);
    }

    @Override
    public void onClose() {
        notNow();
    }
}
