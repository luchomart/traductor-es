package com.luchomart.traductores;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controla la tarea de traducción y su relación con el juego (menú, comandos, recarga).
 * Cada plataforma (NeoForge, Forge) conecta sus eventos con los métodos on*.
 */
public final class Traductor {
    private static final long SHOW_RESULT_MS = 30_000;

    private static volatile TranslationJob job;

    /** Estado de la conexión con la IA, para el indicador del panel. */
    public enum Connection { UNKNOWN, OK, FAIL }
    public static volatile Connection connection = Connection.UNKNOWN;
    /** Modelo con el que se conectó, o el motivo del fallo. */
    public static volatile String connectionDetail = "";

    public static void setConnection(Connection state, String detail) {
        connection = state;
        connectionDetail = detail == null ? "" : detail;
    }
    /** Idioma con el que ya se evaluó el inicio automático (para volver a evaluar si el jugador lo cambia). */
    private static String autoStartCheckedLang;
    /** Terminó mientras se jugaba: los textos se recargan al volver al menú. */
    private static boolean reloadOnMenu;
    private static long finishedAtMs;
    /** El jugador cerró el asistente con "Ahora no": no se vuelve a abrir solo en esta sesión. */
    public static boolean wizardDismissed;
    /** La pausa la puso el mod al entrar a un mundo (y no el jugador). */
    private static boolean autoPaused;

    private Traductor() {}

    public static TranslationJob job() { return job; }

    public static boolean isRunning() {
        TranslationJob j = job;
        return j != null && j.isRunning();
    }

    /** Lanza una pasada de traducción si no hay una en curso. */
    public static synchronized boolean start() {
        if (isRunning()) return false;
        Minecraft mc = Minecraft.getInstance();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        if (target == null) {
            toast("Traductor ES", "Elegí un idioma español en Opciones → Idioma (o un destino en config.json)");
            return false;
        }
        TranslationJob j = new TranslationJob(Config.get().snapshot(), target, Traductor::onFinish);
        launch(j);
        return true;
    }

    /**
     * Rearma el pack con las correcciones guardadas, sin usar la IA, y recarga los textos al terminar (también
     * dentro de un mundo). Devuelve false si hay una traducción en curso: la corrección se aplica cuando termine.
     */
    public static synchronized boolean applyCorrections() {
        if (isRunning()) return false;
        Minecraft mc = Minecraft.getInstance();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        if (target == null) return false;
        launch(new TranslationJob(Config.get().snapshot(), target, Traductor::onFinish, true));
        return true;
    }

    private static void launch(TranslationJob j) {
        job = j;
        Thread t = new Thread(j, "Traductor ES");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void onFinish(TranslationJob j) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            finishedAtMs = System.currentTimeMillis();
            boolean inMenu = mc.level == null;
            if (j.applyOnly) {
                // El jugador acaba de corregir algo: se recarga ya, aunque esté en un mundo.
                if (j.packChanged) mc.reloadResourcePacks();
                if (j.questsChanged && !inMenu) toast("Traductor ES", "Corrección guardada. Las misiones se ven al volver a entrar al mundo.");
                return;
            }
            if (j.packChanged && inMenu && Config.get().autoReload) {
                mc.reloadResourcePacks();
            } else if (j.packChanged) {
                reloadOnMenu = Config.get().autoReload;
                toast("Traductor ES", shortResult(j) + " Lo traducido se aplica al volver al menú (o con F3+T).");
            } else if (j.state != TranslationJob.State.DONE) {
                toast("Traductor ES", shortResult(j));
            }
            // Las misiones las carga FTB Quests al abrir el mundo: si ya estaba adentro, las ve la próxima vez.
            if (j.questsChanged && !inMenu) toast("Traductor ES", "Misiones traducidas: se ven al volver a entrar al mundo.");
            // Falló por algo de configuración y el jugador está en el menú: el asistente lo ayuda a arreglarlo.
            Config cfg = Config.get();
            if (j.setupProblem && cfg.showWizard && !wizardDismissed && mc.screen instanceof TitleScreen) {
                mc.setScreen(new WizardScreen(mc.screen));
            }
        });
    }

    /** Resultado en pocas palabras para el aviso: el detalle del error (a veces largo y en inglés) queda en el panel y el log. */
    private static String shortResult(TranslationJob j) {
        return switch (j.state) {
            case ERROR -> j.setupProblem ? "Se detuvo: hay que revisar la configuración. Abrí el panel para ver el motivo."
                    : "Se detuvo por un error. Abrí el panel para ver el motivo.";
            case CANCELLED -> "Traducción cancelada.";
            default -> "¡Traducción terminada!";
        };
    }

    private static void toast(String title, String msg) {
        Platform.toast(Component.literal(title), Component.literal(msg));
    }

    // ---------- Eventos ----------

    /** Después de inicializar cualquier pantalla. */
    public static void onScreenInit(Screen screen) {
        if (!(screen instanceof TitleScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (reloadOnMenu) {
            reloadOnMenu = false;
            mc.execute(mc::reloadResourcePacks);
            return; // al terminar la recarga se vuelve a abrir el menú y se evalúa lo demás
        }
        // Primera vez (o nunca se completó): el asistente en vez de traducir directamente.
        Config setup = Config.get();
        if (!setup.setupDone && setup.showWizard && !wizardDismissed && !isRunning()) {
            mc.execute(() -> mc.setScreen(new WizardScreen(screen)));
            return;
        }
        // Se evalúa una vez por idioma: si el jugador lo cambia a español durante la sesión, arranca.
        String lang = mc.options.languageCode;
        if (lang.equals(autoStartCheckedLang)) return;
        autoStartCheckedLang = lang;
        Config cfg = Config.get();
        if (!cfg.autoStart || isRunning()) return;
        // Cerró el asistente sin terminarlo: no se traduce por su cuenta hasta que lo complete (o lo desactive).
        if (!cfg.setupDone && cfg.showWizard) return;
        if (SpanishTarget.resolve(cfg.targetLanguage, lang) == null) {
            TraductorES.LOG.info("[Traductor ES] El idioma del juego es {}, no es una variante del español: no se traduce automáticamente", lang);
            return;
        }
        start();
    }

    /** Al entrar a un mundo o servidor. */
    public static void onWorldJoin() {
        TranslationJob j = job;
        if (j == null || !j.isRunning() || j.isPauseRequested() || j.applyOnly) return;
        // La opción es la del panel; si la IA es local o no, la de la traducción en curso (puede haber cambiado de servicio).
        Config cfg = Config.get();
        boolean keep = switch (cfg.whilePlaying) { case "si" -> true; case "no" -> false; default -> !j.usesLocalAi(); };
        if (keep) {
            toast("Traductor ES", j.usesLocalAi()
                    ? "La traducción sigue mientras jugás (la IA de tu PC comparte la placa de video: puede bajar los FPS)"
                    : "La traducción sigue mientras jugás");
            return;
        }
        j.pause();
        autoPaused = true;
        toast("Traductor ES", "En pausa mientras jugás. Sigue al volver al menú");
    }

    /** Al salir de un mundo o servidor. */
    public static void onWorldLeave() {
        TranslationJob j = job;
        if (!autoPaused) return;
        autoPaused = false;
        if (j != null && j.isRunning()) j.resume();
    }

    /** Al cerrar el juego. */
    public static void onShutdown() {
        TranslationJob j = job;
        if (j != null && j.isRunning()) j.flushCache();
    }

    /** Después de dibujar cualquier pantalla: en el menú principal muestra el aviso de progreso en la esquina. */
    public static void onScreenRender(Screen screen, GuiGraphics g) {
        if (!(screen instanceof TitleScreen)) return;
        TranslationJob j = job;
        if (j == null || j.applyOnly) return;
        if (!j.isRunning() && System.currentTimeMillis() - finishedAtMs > SHOW_RESULT_MS) return;
        Font font = Minecraft.getInstance().font;
        int w = 176, h = 38, x = screen.width - w - 4, y = 4;
        UiKit.overlay(g, x, y, w, h);
        int tx = x + 26, tw = w - 32;
        switch (j.state) {
            case SCANNING -> {
                UiKit.icon(g, x + 6, y + 11, 1);
                g.drawString(font, "Traductor ES", tx, y + 8, UiKit.YELLOW, false);
                g.drawString(font, font.plainSubstrByWidth(j.message, tw), tx, y + 21, UiKit.MUTED, false);
            }
            case TRANSLATING, PAUSED -> {
                UiKit.icon(g, x + 6, y + 11, 1);
                boolean paused = j.state == TranslationJob.State.PAUSED;
                int pct = j.total == 0 ? 0 : j.done * 100 / j.total;
                g.drawString(font, paused ? "En pausa" : "Traduciendo mods…", tx, y + 6, paused ? UiKit.YELLOW : UiKit.YELLOW, false);
                String p = pct + " %";
                g.drawString(font, p, x + w - 6 - font.width(p), y + 6, UiKit.WHITE, false);
                UiKit.progressBar(g, tx, y + 17, tw, 5, j.total == 0 ? 0 : (double) j.done / j.total);
                String waiting = j.waitingMessage;
                String sub;
                int color = UiKit.MUTED;
                if (waiting != null) { sub = waiting; color = UiKit.YELLOW; }
                else {
                    double rate = j.ratePerMinute();
                    sub = j.currentNamespace + (rate > 0 ? " · quedan " + duration((long) ((j.total - j.done) / rate * 60_000)) : "");
                }
                g.drawString(font, font.plainSubstrByWidth(sub, tw), tx, y + 26, color, false);
            }
            case DONE -> {
                UiKit.check(g, x + 6, y + 11, 1);
                g.drawString(font, "¡Traducción terminada!", tx, y + 8, UiKit.GREEN, false);
                String sub = j.done > 0 ? Traductor.fmt(j.done - j.failed) + " textos nuevos en español" : "No había textos nuevos";
                g.drawString(font, font.plainSubstrByWidth(sub, tw), tx, y + 21, UiKit.MUTED, false);
            }
            case CANCELLED -> {
                UiKit.icon(g, x + 6, y + 11, 1);
                g.drawString(font, "Traducción cancelada", tx, y + 8, UiKit.YELLOW, false);
                g.drawString(font, "Lo hecho quedó guardado", tx, y + 21, UiKit.MUTED, false);
            }
            case ERROR -> {
                UiKit.icon(g, x + 6, y + 11, 1);
                g.drawString(font, "Traductor ES: hay un problema", tx, y + 8, UiKit.RED, false);
                g.drawString(font, font.plainSubstrByWidth(j.message, tw), tx, y + 21, UiKit.MUTED, false);
            }
        }
    }

    /** El comando /traductor. Genérico en la fuente: cada loader tiene la suya para comandos de cliente. */
    public static <S> LiteralArgumentBuilder<S> command() {
        return LiteralArgumentBuilder.<S>literal("traductor")
                .executes(Traductor::cmdStatus)
                .then(LiteralArgumentBuilder.<S>literal("estado").executes(Traductor::cmdStatus))
                .then(LiteralArgumentBuilder.<S>literal("corregir")
                        .executes(ctx -> openCorrections(""))
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("texto",
                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> openCorrections(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto")))))
                .then(LiteralArgumentBuilder.<S>literal("panel").executes(ctx -> {
                    // Al tick siguiente: el chat se cierra después de ejecutar el comando y taparía el panel.
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new ConfigScreen(mc.screen)));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(LiteralArgumentBuilder.<S>literal("iniciar").executes(ctx -> reply(ctx, start() ? "Traducción iniciada" : "Ya hay una traducción en curso")))
                .then(LiteralArgumentBuilder.<S>literal("pausar").executes(ctx -> {
                    TranslationJob j = job;
                    if (j == null || !j.isRunning()) return reply(ctx, "No hay ninguna traducción en curso");
                    j.pause();
                    autoPaused = false;
                    return reply(ctx, "Traducción en pausa");
                }))
                .then(LiteralArgumentBuilder.<S>literal("reanudar").executes(ctx -> {
                    TranslationJob j = job;
                    if (j == null || !j.isRunning()) return reply(ctx, "No hay ninguna traducción en curso");
                    j.resume();
                    return reply(ctx, "Traducción reanudada");
                }))
                .then(LiteralArgumentBuilder.<S>literal("cancelar").executes(ctx -> {
                    TranslationJob j = job;
                    if (j == null || !j.isRunning()) return reply(ctx, "No hay ninguna traducción en curso");
                    j.cancel();
                    return reply(ctx, "Cancelando… lo ya traducido se guarda");
                }));
    }

    /** El editor de correcciones, con la búsqueda ya escrita. Al tick siguiente, como el panel. */
    private static int openCorrections(String query) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new CorrectionScreen(mc.screen, query)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int cmdStatus(CommandContext<S> ctx) {
        TranslationJob j = job;
        if (j == null) return reply(ctx, "Traductor ES: todavía no se ejecutó en esta sesión. Usá /traductor iniciar o abrí el panel con /traductor panel");
        for (Component line : statusLines(j)) chat(line);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int reply(CommandContext<S> ctx, String msg) {
        chat(Component.literal("[Traductor ES] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(msg).withStyle(ChatFormatting.WHITE)));
        return Command.SINGLE_SUCCESS;
    }

    /** Los comandos son del cliente: la respuesta va directo al chat propio. */
    private static void chat(Component line) {
        Minecraft.getInstance().gui.getChat().addMessage(line);
    }

    // ---------- Texto de estado ----------

    public static List<Component> statusLines(TranslationJob j) {
        List<Component> out = new ArrayList<>();
        Component title = Component.literal("Traductor ES: ").withStyle(ChatFormatting.GOLD);
        switch (j.state) {
            case SCANNING -> out.add(title.copy().append(Component.literal(j.message).withStyle(ChatFormatting.WHITE)));
            case TRANSLATING, PAUSED -> {
                int pct = j.total == 0 ? 0 : j.done * 100 / j.total;
                String head = (j.state == TranslationJob.State.PAUSED ? "en pausa " : "traduciendo ")
                        + fmt(j.done) + " / " + fmt(j.total) + " (" + pct + " %)";
                out.add(title.copy().append(Component.literal(head).withStyle(
                        j.state == TranslationJob.State.PAUSED ? ChatFormatting.YELLOW : ChatFormatting.WHITE)));
                double rate = j.ratePerMinute();
                String detail = j.currentNamespace + " · " + Math.round(rate) + " textos/min";
                if (rate > 0) detail += " · quedan " + duration((long) ((j.total - j.done) / rate * 60_000));
                out.add(Component.literal(detail).withStyle(ChatFormatting.GRAY));
                String waiting = j.waitingMessage;
                if (waiting != null) out.add(Component.literal(waiting).withStyle(ChatFormatting.YELLOW));
                if (j.failed > 0) out.add(Component.literal(j.failed + " fallidos (quedan en el idioma original)").withStyle(ChatFormatting.RED));
            }
            case DONE -> out.add(title.copy().append(Component.literal(j.message).withStyle(ChatFormatting.GREEN)));
            case CANCELLED -> out.add(title.copy().append(Component.literal(j.message).withStyle(ChatFormatting.YELLOW)));
            case ERROR -> out.add(title.copy().append(Component.literal(j.message).withStyle(ChatFormatting.RED)));
        }
        return out;
    }

    static String fmt(int n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }

    static String duration(long ms) {
        long min = Math.max(1, ms / 60_000);
        return min < 60 ? "~" + min + " min" : "~" + (min / 60) + " h " + (min % 60) + " min";
    }
}
