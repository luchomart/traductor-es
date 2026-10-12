package com.luchomart.traductores;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
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
    /** Idioma con el que ya se evaluó el inicio automático (para volver a evaluar si el jugador lo cambia). */
    private static String autoStartCheckedLang;
    /** Terminó mientras se jugaba: los textos se recargan al volver al menú. */
    private static boolean reloadOnMenu;
    private static long finishedAtMs;
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
        TranslationJob j = new TranslationJob(Config.get(), Traductor::onFinish);
        job = j;
        Thread t = new Thread(j, "Traductor ES");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return true;
    }

    private static void onFinish(TranslationJob j) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            finishedAtMs = System.currentTimeMillis();
            boolean inMenu = mc.level == null;
            if (j.packChanged && inMenu && Config.get().autoReload) {
                mc.reloadResourcePacks();
            } else if (j.packChanged) {
                reloadOnMenu = Config.get().autoReload;
                toast("Traductor ES", j.message + " — se aplica al volver al menú (o pulsa F3+T)");
            } else if (j.state != TranslationJob.State.DONE) {
                toast("Traductor ES", j.message);
            }
        });
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
        // Se evalúa una vez por idioma: si el jugador lo cambia a español durante la sesión, arranca.
        String lang = mc.options.languageCode;
        if (lang.equals(autoStartCheckedLang)) return;
        autoStartCheckedLang = lang;
        Config cfg = Config.get();
        if (!cfg.autoStart || isRunning()) return;
        if (!"es_es".equals(lang)) {
            TraductorES.LOG.info("[Traductor ES] El idioma del juego es {}, no es_es: no se traduce automáticamente", lang);
            return;
        }
        start();
    }

    /** Al entrar a un mundo o servidor. */
    public static void onWorldJoin() {
        TranslationJob j = job;
        if (!Config.get().pauseInWorld || j == null || !j.isRunning() || j.isPauseRequested()) return;
        j.pause();
        autoPaused = true;
        toast("Traductor ES", "Traducción en pausa mientras juegas. Se reanuda al volver al menú");
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

    /** Después de dibujar cualquier pantalla: en el menú principal muestra el progreso. */
    public static void onScreenRender(Screen screen, GuiGraphics g) {
        if (!(screen instanceof TitleScreen)) return;
        TranslationJob j = job;
        if (j == null) return;
        if (!j.isRunning() && System.currentTimeMillis() - finishedAtMs > SHOW_RESULT_MS) return;
        List<Component> lines = statusLines(j);
        Font font = Minecraft.getInstance().font;
        int y = 4;
        for (Component line : lines) {
            g.drawString(font, line, 4, y, 0xFFFFFF, true);
            y += font.lineHeight + 2;
        }
    }

    /** El comando de cliente /traductor; cada plataforma lo registra en su evento. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("traductor")
                .executes(Traductor::cmdStatus)
                .then(Commands.literal("estado").executes(Traductor::cmdStatus))
                .then(Commands.literal("iniciar").executes(ctx -> reply(ctx, start() ? "Traducción iniciada" : "Ya hay una traducción en curso")))
                .then(Commands.literal("pausar").executes(ctx -> {
                    TranslationJob j = job;
                    if (j == null || !j.isRunning()) return reply(ctx, "No hay ninguna traducción en curso");
                    j.pause();
                    autoPaused = false;
                    return reply(ctx, "Traducción en pausa");
                }))
                .then(Commands.literal("reanudar").executes(ctx -> {
                    TranslationJob j = job;
                    if (j == null || !j.isRunning()) return reply(ctx, "No hay ninguna traducción en curso");
                    j.resume();
                    return reply(ctx, "Traducción reanudada");
                }))
                .then(Commands.literal("cancelar").executes(ctx -> {
                    TranslationJob j = job;
                    if (j == null || !j.isRunning()) return reply(ctx, "No hay ninguna traducción en curso");
                    j.cancel();
                    return reply(ctx, "Cancelando… lo ya traducido se guarda");
                }));
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        TranslationJob j = job;
        if (j == null) return reply(ctx, "Traductor ES: todavía no se ejecutó en esta sesión. Usa /traductor iniciar");
        for (Component line : statusLines(j)) ctx.getSource().sendSystemMessage(line);
        return Command.SINGLE_SUCCESS;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSystemMessage(Component.literal("[Traductor ES] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(msg).withStyle(ChatFormatting.WHITE)));
        return Command.SINGLE_SUCCESS;
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
