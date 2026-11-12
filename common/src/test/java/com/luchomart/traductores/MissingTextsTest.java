package com.luchomart.traductores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MissingTextsTest {
    @TempDir
    Path dir;

    @Test
    void escribeYLeeConLaClave() throws IOException {
        MissingTexts missing = new MissingTexts();
        missing.add("create", "block.create.cogwheel", "Cogwheel");
        missing.add("create", "item.create.wrench", "Wrench");
        missing.add("mekanism", "item.mekanism.alloy", "Alloy");
        assertEquals(3, missing.size());
        Path f = MissingTexts.file(dir, "es_ar");
        missing.write(f);

        Map<String, Map<String, String>> read = MissingTexts.read(f);
        assertEquals("Cogwheel", read.get("create").get("block.create.cogwheel"));
        assertEquals("Alloy", read.get("mekanism").get("item.mekanism.alloy"));
        assertEquals("faltan-es_ar.json", f.getFileName().toString());
    }

    @Test
    void cadaPasadaReemplazaLaAnterior() throws IOException {
        Path f = MissingTexts.file(dir, "es_ar");
        MissingTexts first = new MissingTexts();
        first.add("a", "k1", "One");
        first.write(f);
        MissingTexts second = new MissingTexts();
        second.add("b", "k2", "Two");
        second.write(f);
        Map<String, Map<String, String>> read = MissingTexts.read(f);
        assertNull(read.get("a"));
        assertEquals("Two", read.get("b").get("k2"));
        // Si no falta nada, el archivo desaparece.
        new MissingTexts().write(f);
        assertFalse(Files.exists(f));
    }

    @Test
    void archivoFaltanteODaniadoNoRompe() throws IOException {
        Path f = MissingTexts.file(dir, "es_mx");
        assertTrue(MissingTexts.read(f).isEmpty());
        Files.writeString(f, "{esto no es json", StandardCharsets.UTF_8);
        assertTrue(MissingTexts.read(f).isEmpty());
        // Entradas vacías se descartan.
        Files.writeString(f, "{\"a\": {\"k\": \"\", \"k2\": \"Bye\"}, \"b\": null}", StandardCharsets.UTF_8);
        Map<String, Map<String, String>> read = MissingTexts.read(f);
        assertEquals(1, read.get("a").size());
        assertEquals("Bye", read.get("a").get("k2"));
        assertNull(read.get("b"));
    }
}
