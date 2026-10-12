package com.luchomart.traductores;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(TraductorES.MODID)
public class TraductorES {
    public static final String MODID = "traductor_es";
    public static final Logger LOG = LogUtils.getLogger();

    public TraductorES() {
        // Mod solo de cliente: en un servidor no hace nada.
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
