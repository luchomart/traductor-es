package com.luchomart.traductores;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Dibujo de las pantallas, igual en todas las versiones. Desde 1.20 es GuiGraphics. */
public final class Gfx {
    private final GuiGraphics g;

    public Gfx(GuiGraphics g) {
        this.g = g;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    public void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, color);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        g.renderComponentTooltip(font, lines, x, y);
    }
}
