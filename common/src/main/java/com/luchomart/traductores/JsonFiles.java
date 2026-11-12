package com.luchomart.traductores;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Escritura de archivos que nunca deja uno a medias, aunque dos hilos escriban el mismo a la vez. */
final class JsonFiles {
    private JsonFiles() {}

    static void writeAtomic(Path file, String text) throws IOException {
        writeAtomic(file, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Escribe a un temporal propio (con un .tmp fijo, uno podría mover el del otro a medio escribir) y lo pone en su
     * lugar con un movimiento atómico: con REPLACE_EXISTING, Java borra el destino antes de renombrar, y si dos
     * escrituras terminan juntas la segunda falla con NoSuchFileException.
     */
    static void writeAtomic(Path file, byte[] body) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, body);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
