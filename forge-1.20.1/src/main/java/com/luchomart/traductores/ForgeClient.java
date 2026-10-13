package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/** Conexión con Forge 1.20.1: solo se carga en el cliente. */
final class ForgeClient {
    private ForgeClient() {}

    static void init(IEventBus modBus) {
        Config.load();
        PackWriter.ensureExists();

        modBus.addListener(EventPriority.NORMAL, false, AddPackFindersEvent.class, ForgeClient::onAddPackFinders);
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.NORMAL, false, ScreenEvent.Init.Post.class, e -> Traductor.onScreenInit(e.getScreen()));
        bus.addListener(EventPriority.NORMAL, false, ScreenEvent.Render.Post.class, e -> Traductor.onScreenRender(e.getScreen(), e.getGuiGraphics()));
        bus.addListener(EventPriority.NORMAL, false, RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(Traductor.command()));
        bus.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, e -> Traductor.onWorldJoin());
        bus.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class, e -> Traductor.onWorldLeave());
        bus.addListener(EventPriority.NORMAL, false, GameShuttingDownEvent.class, e -> Traductor.onShutdown());
        // Al final, para que la pista "Alt: ver original" quede debajo de lo que agregan otros mods.
        bus.addListener(EventPriority.LOWEST, false, ItemTooltipEvent.class, e -> OriginalText.onTooltip(e.getToolTip()));
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));

        TraductorES.LOG.info("[Traductor ES] Cargado. Configuración en {}", Config.DIR);
    }

    /** Registra el pack generado: siempre activo y por encima del resto. */
    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        PackWriter.ensureExists();
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(PackWriter.PACK_ID, Component.literal("Traductor ES"), true,
                    id -> new PathPackResources(id, PackWriter.DIR, true),
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
            else TraductorES.LOG.error("[Traductor ES] No se pudo crear el pack de traducciones");
        });
    }
}
