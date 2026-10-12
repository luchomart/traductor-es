package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Lo que cambia entre plataformas. Versión NeoForge 1.21.1. */
public final class Platform {
    public static final int PACK_FORMAT = 34;
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(8000L);

    private Platform() {}

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static void toast(Component title, Component message) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), TOAST, title, message);
    }

    /** En 1.21 Screen.render ya dibuja el fondo: no hay que hacer nada. */
    public static void renderBackground(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}
