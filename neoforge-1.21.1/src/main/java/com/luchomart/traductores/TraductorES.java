package com.luchomart.traductores;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.slf4j.Logger;

@Mod(value = TraductorES.MODID, dist = Dist.CLIENT)
public class TraductorES {
    public static final String MODID = "traductor_es";
    public static final Logger LOG = LogUtils.getLogger();

    public TraductorES(IEventBus modBus, ModContainer container) {
        Config.load();
        PackWriter.ensureExists();

        modBus.addListener(PackRegistration::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> Traductor.onScreenInit(e.getScreen()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, e -> Traductor.onScreenRender(e.getScreen(), e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(Traductor.command()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> Traductor.onWorldJoin());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> Traductor.onWorldLeave());
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, e -> Traductor.onShutdown());
        // Al final, para que la pista "Alt: ver original" quede debajo de lo que agregan otros mods.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ItemTooltipEvent.class, e -> OriginalText.onTooltip(e.getToolTip()));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigScreen(parent));

        LOG.info("[Traductor ES] Cargado. Configuración en {}", Config.DIR);
    }
}
