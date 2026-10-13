package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Pestaña: Minecraft no trae una, así que se dibuja a mano (la elegida, más clara y en amarillo). */
public class TabButton extends Button {
    private final BooleanSupplier selected;

    public TabButton(int x, int y, int w, int h, Component message, OnPress onPress, BooleanSupplier selected) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean on = selected.getAsBoolean();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int fill = on ? 0xFF4E5157 : isHoveredOrFocused() ? 0xFF3E4147 : 0xFF2C2E33;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        // La pestaña elegida no tiene borde abajo: se "une" con el panel.
        g.fill(x + 1, y + 1, x + w - 1, y + h - (on ? 0 : 1), fill);
        if (on) g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF7A7E86);
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, getMessage(), x + w / 2, y + (h - 8) / 2, on ? UiKit.YELLOW : UiKit.TEXT);
    }
}
