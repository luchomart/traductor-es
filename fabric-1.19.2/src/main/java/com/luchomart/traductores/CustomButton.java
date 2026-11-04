package com.luchomart.traductores;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botón dibujado a mano, igual en todas las versiones. */
public abstract class CustomButton extends Button {
    protected CustomButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress);
    }

    protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

    /** En 1.20 existe getX(); acá es un campo. */
    protected int getX() {
        return x;
    }

    protected int getY() {
        return y;
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(pose), mouseX, mouseY, partialTick);
    }
}
