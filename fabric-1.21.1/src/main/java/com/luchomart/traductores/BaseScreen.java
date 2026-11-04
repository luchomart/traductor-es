package com.luchomart.traductores;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base de las pantallas del mod (versión 1.21.1). En 1.21 Screen.render dibuja el fondo por su cuenta,
 * así que los paneles se dibujan justo después del fondo y antes de los botones.
 */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Component title) {
        super(title);
    }

    /** Fondos de paneles y decoraciones: quedan detrás de los botones. */
    protected void renderPanels(Gfx g, int mouseX, int mouseY, float partialTick) {}

    /** Textos y demás: quedan delante de los botones. */
    protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderPanels(new Gfx(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderContent(new Gfx(graphics), mouseX, mouseY, partialTick);
    }
}
