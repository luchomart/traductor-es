package com.luchomart.traductores;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botón dibujado a mano, igual en todas las versiones. */
public abstract class CustomButton extends Button {
    protected CustomButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(g), mouseX, mouseY, partialTick);
    }
}
