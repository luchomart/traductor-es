package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.Optional;

/** El pack generado como pack del juego: siempre activo y por encima del resto. Lo suma PackRepositoryMixin. */
public final class PackRegistration {
    private PackRegistration() {}

    public static final RepositorySource SOURCE = consumer -> {
        PackWriter.ensureExists();
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(PackWriter.PACK_ID, Component.literal("Traductor ES"), PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(PackWriter.DIR),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) consumer.accept(pack);
        else TraductorES.LOG.error("[Traductor ES] No se pudo crear el pack de traducciones");
    };
}
