package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Tarjeta grande que se elige con un clic (asistente: "En mi PC" / "En la nube"). La elegida tiene borde verde. */
public class OptionCard extends Button {
    private final BooleanSupplier selected;
    private final Supplier<List<Component>> lines;

    public OptionCard(int x, int y, int w, int h, Component title, Supplier<List<Component>> lines, OnPress onPress, BooleanSupplier selected) {
        super(x, y, w, h, title, onPress, DEFAULT_NARRATION);
        this.selected = selected;
        this.lines = lines;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean on = selected.getAsBoolean();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, on ? UiKit.GREEN : isHoveredOrFocused() ? 0xFF8A8A8A : 0xFF000000);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, on ? 0xFF1F2A1F : 0xFF24262B);
        Font font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), x + 8, y + 8, UiKit.WHITE, true);
        int ly = y + 22;
        for (Component line : lines.get()) {
            ly = UiKit.wrapped(g, font, line, x + 8, ly, w - 16, UiKit.MUTED) + 2;
        }
    }
}
