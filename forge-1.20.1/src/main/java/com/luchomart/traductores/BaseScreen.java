package com.luchomart.traductores;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base de las pantallas del mod (versión 1.20.1). En 1.20 cada pantalla dibuja su fondo:
 * fondo, después los paneles, los botones y por último el contenido.
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Gfx g = new Gfx(graphics);
        renderBackground(graphics);
        renderPanels(g, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderContent(g, mouseX, mouseY, partialTick);
    }
}
