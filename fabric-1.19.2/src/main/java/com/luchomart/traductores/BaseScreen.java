package com.luchomart.traductores;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base de las pantallas del mod (versión 1.19.2): fondo, paneles, botones y contenido. Al final, los textos de
 * fondo de los campos y las ayudas, que en esta versión no dibuja el juego (ver Widgets).
 */
public abstract class BaseScreen extends Screen {
    private static final int TOOLTIP_WIDTH = 170;

    protected BaseScreen(Component title) {
        super(title);
    }

    /** Fondos de paneles y decoraciones: quedan detrás de los botones. */
    protected void renderPanels(Gfx g, int mouseX, int mouseY, float partialTick) {}

    /** Textos y demás: quedan delante de los botones. */
    protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        Gfx g = new Gfx(pose);
        renderBackground(pose);
        renderPanels(g, mouseX, mouseY, partialTick);
        super.render(pose, mouseX, mouseY, partialTick);
        renderContent(g, mouseX, mouseY, partialTick);
        renderHints(pose);
        renderWidgetTooltip(pose, mouseX, mouseY);
    }

    private void renderHints(PoseStack pose) {
        for (GuiEventListener child : children()) {
            if (!(child instanceof EditBox box) || !box.visible || box.isFocused() || !box.getValue().isEmpty()) continue;
            Component hint = Widgets.hintOf(box);
            if (hint != null) font.drawShadow(pose, hint, box.x + 4, box.y + (box.getHeight() - 8) / 2, 0xFFE0E0E0);
        }
    }

    private void renderWidgetTooltip(PoseStack pose, int mouseX, int mouseY) {
        for (GuiEventListener child : children()) {
            if (!(child instanceof AbstractWidget w) || !w.visible) continue;
            if (mouseX < w.x || mouseX >= w.x + w.getWidth() || mouseY < w.y || mouseY >= w.y + w.getHeight()) continue;
            Component tooltip = Widgets.tooltipOf(w);
            if (tooltip != null) renderTooltip(pose, font.split(tooltip, TOOLTIP_WIDTH), mouseX, mouseY);
            return;
        }
    }
}
