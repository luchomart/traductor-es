package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

/** El pack generado como pack del juego: siempre activo y por encima del resto. Lo suma PackRepositoryMixin. */
public final class PackRegistration {
    private PackRegistration() {}

    public static final RepositorySource SOURCE = consumer -> {
        PackWriter.ensureExists();
        Pack pack = Pack.readMetaAndCreate(PackWriter.PACK_ID, Component.literal("Traductor ES"), true,
                id -> new PathPackResources(id, PackWriter.DIR, true),
                PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) consumer.accept(pack);
        else TraductorES.LOG.error("[Traductor ES] No se pudo crear el pack de traducciones");
    };
}
