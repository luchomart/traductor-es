package com.luchomart.traductores;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exporta el pack generado como un .zip normal en resourcepacks/, para pasárselo a alguien
 * que juegue el mismo modpack: lo activa como cualquier paquete de recursos, sin IA ni este mod.
 */
public final class PackExporter {
    public static final String FILE_NAME = "TraductorES-traducciones.zip";

    private PackExporter() {}

    /** Devuelve la ruta del .zip creado. */
    public static Path export() throws IOException {
        Path source = PackWriter.DIR;
        if (!Files.exists(source.resolve("pack.mcmeta"))) throw new IOException("todavía no hay traducciones para exportar");
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");
        Files.createDirectories(dir);
        Path zip = dir.resolve(FILE_NAME), tmp = dir.resolve(FILE_NAME + ".tmp");

        List<Path> files;
        try (Stream<Path> walk = Files.walk(source)) {
            files = walk.filter(Files::isRegularFile).filter(p -> !p.toString().endsWith(".tmp")).sorted().toList();
        }
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream z = new ZipOutputStream(out)) {
            for (Path f : files) {
                z.putNextEntry(new ZipEntry(source.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, z);
                z.closeEntry();
            }
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        return zip;
    }
}
