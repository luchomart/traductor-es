package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Lo que cambia entre plataformas. Versión Fabric 1.20.1. */
public final class Platform {
    public static final int PACK_FORMAT = 15;

    private Platform() {}

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static void toast(Component title, Component message) {
        // Varias líneas con ancho fijo: con addOrUpdate un texto largo estira el aviso a lo ancho de la pantalla.
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(SystemToast.multiline(mc, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, message));
    }

    /** En 1.20.1 cada pantalla tiene que dibujar su fondo antes que los widgets. */
    public static void renderBackground(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(g);
    }

    /** La pantalla de idioma del juego (cambia de paquete entre versiones). */
    public static Screen languageScreen(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        return new LanguageSelectScreen(parent, mc.options, mc.getLanguageManager());
    }
}
