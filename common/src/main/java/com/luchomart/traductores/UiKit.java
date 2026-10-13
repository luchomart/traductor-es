package com.luchomart.traductores;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Dibujo común de las pantallas: paneles con relieve, barra de progreso, ícono y colores al estilo Minecraft. */
public final class UiKit {
    public static final int GOLD = 0xFFFFD84A, YELLOW = 0xFFFFFF55, GREEN = 0xFF55FF55, RED = 0xFFFF5555,
            AQUA = 0xFF55FFFF, TEXT = 0xFFE0E0E0, MUTED = 0xFFA0A0A0, DIM = 0xFF808080, WHITE = 0xFFFFFFFF;

    private UiKit() {}

    /** Panel principal: borde negro, relleno gris oscuro y luz arriba a la izquierda. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        bevel(g, x, y, w, h, 0xFF2A2C31, 0xFF3D4047);
    }

    /** Tarjeta dentro de un panel, un poco más oscura. */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        bevel(g, x, y, w, h, 0xFF1F2126, 0xFF2E3138);
    }

    /** Recuadro semitransparente para mostrar sobre el menú principal. */
    public static void overlay(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xE8212121);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF555555);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF555555);
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int fill, int light) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
    }

    /** Barra de progreso verde, como la de experiencia. fraction entre 0 y 1. */
    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, double fraction) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF262626);
        int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, 0xFF7FF23A);
            if (h > 4) g.fill(x + 1, y + h - 3, x + 1 + fw, y + h - 1, 0xFF4CAF1A);
        }
    }

    /** Cuadradito de estado (verde, rojo, gris). */
    public static void dot(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 5, color);
    }

    /** Ícono del mod (globo de diálogo con "ES"), 16×16 celdas de scale píxeles. */
    public static void icon(GuiGraphics g, int x, int y, int scale) {
        int gold = 0xFFF2C94C, dark = 0xFF3B2A0A, white = 0xFFFFFFFF;
        rect(g, x, y, scale, 1, 2, 14, 10, gold);
        rect(g, x, y, scale, 2, 3, 12, 8, dark);
        rect(g, x, y, scale, 3, 12, 3, 2, gold);
        // E
        rect(g, x, y, scale, 4, 5, 3, 1, white);
        rect(g, x, y, scale, 4, 5, 1, 5, white);
        rect(g, x, y, scale, 4, 7, 2, 1, white);
        rect(g, x, y, scale, 4, 9, 3, 1, white);
        // S
        rect(g, x, y, scale, 9, 5, 3, 1, white);
        rect(g, x, y, scale, 9, 5, 1, 3, white);
        rect(g, x, y, scale, 9, 7, 3, 1, white);
        rect(g, x, y, scale, 11, 7, 1, 3, white);
        rect(g, x, y, scale, 9, 9, 3, 1, white);
    }

    /** Tilde verde de "listo" en 16×16 celdas. */
    public static void check(GuiGraphics g, int x, int y, int scale) {
        rect(g, x, y, scale, 2, 2, 12, 12, 0xFF2F7A35);
        int w = 0xFFFFFFFF;
        rect(g, x, y, scale, 4, 8, 2, 2, w);
        rect(g, x, y, scale, 6, 10, 2, 2, w);
        rect(g, x, y, scale, 8, 8, 2, 2, w);
        rect(g, x, y, scale, 10, 6, 2, 2, w);
        rect(g, x, y, scale, 12, 4, 1, 2, w);
    }

    private static void rect(GuiGraphics g, int ox, int oy, int s, int cx, int cy, int cw, int ch, int color) {
        g.fill(ox + cx * s, oy + cy * s, ox + (cx + cw) * s, oy + (cy + ch) * s, color);
    }

    /** Texto partido en renglones dentro de un ancho. Devuelve la y siguiente. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    /** Igual que wrapped, pero centrado. */
    public static int wrappedCentered(GuiGraphics g, Font font, Component text, int cx, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, cx - font.width(line) / 2, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }
}
