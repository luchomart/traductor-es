package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FolderPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

/** El pack generado como pack del juego: siempre activo y por encima del resto. Lo suma PackRepositoryMixin. */
public final class PackRegistration {
    private PackRegistration() {}

    public static final RepositorySource SOURCE = (consumer, constructor) -> {
        PackWriter.ensureExists();
        // El nombre tiene que ser PACK_ID: así se reconoce el pack propio al escanear. Si no, lo traducido parece
        // "español oficial" y la pasada siguiente lo saca del pack.
        Pack pack = Pack.create(PackWriter.PACK_ID, true, () -> new FolderPackResources(PackWriter.DIR.toFile()) {
                    @Override
                    public String getName() {
                        return PackWriter.PACK_ID;
                    }
                },
                (id, title, required, resources, metadata, position, source) ->
                        constructor.create(id, Component.literal("Traductor ES"), required, resources, metadata, position, source),
                Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) consumer.accept(pack);
        else TraductorES.LOG.error("[Traductor ES] No se pudo crear el pack de traducciones");
    };
}
