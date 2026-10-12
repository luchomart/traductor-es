package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

/** Registra el pack generado como pack del juego: siempre activo y por encima del resto. */
public final class PackRegistration {
    private PackRegistration() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        PackWriter.ensureExists();
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(PackWriter.PACK_ID, Component.literal("Traductor ES"), PackSource.BUILT_IN, Optional.empty()),
                    BuiltInPackSource.fromName(info -> new PathPackResources(info, PackWriter.DIR)),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
            else TraductorES.LOG.error("[Traductor ES] No se pudo crear el pack de traducciones");
        });
    }
}
