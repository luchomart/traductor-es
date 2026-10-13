package com.luchomart.traductores;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;

/** Conexión con Fabric. El pack se registra con PackRepositoryMixin y el botón de configuración con Mod Menu. */
public class TraductorES implements ClientModInitializer {
    public static final String MODID = "traductor_es";
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        Config.load();
        PackWriter.ensureExists();

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            Traductor.onScreenInit(screen);
            // Los eventos de cada pantalla se reinician en cada init, así que no se registran dos veces.
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, partialTick) -> Traductor.onScreenRender(s, g));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(Traductor.command()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Traductor.onWorldJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Traductor.onWorldLeave());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Traductor.onShutdown());

        LOG.info("[Traductor ES] Cargado. Configuración en {}", Config.DIR);
    }
}
