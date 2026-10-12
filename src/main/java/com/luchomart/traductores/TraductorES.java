package com.luchomart.traductores;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = TraductorES.MODID, dist = Dist.CLIENT)
public class TraductorES {
    public static final String MODID = "traductor_es";
    public static final Logger LOG = LogUtils.getLogger();

    public TraductorES(IEventBus modBus, ModContainer container) {
        Config.load();
        PackWriter.ensureExists();

        modBus.addListener(PackRegistration::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(Traductor::onScreenInit);
        NeoForge.EVENT_BUS.addListener(Traductor::onScreenRender);
        NeoForge.EVENT_BUS.addListener(Traductor::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Traductor::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(Traductor::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(Traductor::onShutdown);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigScreen(parent));

        LOG.info("[Traductor ES] Cargado. Configuración en {}", Config.DIR);
    }
}
