package com.luchomart.traductores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommunityUsedTest {
    @TempDir
    Path dir;

    @Test
    void escribeYLeeConLaClave() throws IOException {
        CommunityUsed used = new CommunityUsed();
        used.add("create", "block.create.cogwheel", "Cogwheel", "Engranaje", true);
        used.add("create", "item.create.wrench", "Wrench", "Llave inglesa", false);
        Path f = CommunityUsed.file(dir, "es_ar");
        used.write(f);

        Map<String, Map<String, CommunityUsed.Entry>> read = CommunityUsed.read(f);
        CommunityUsed.Entry cog = read.get("create").get("block.create.cogwheel");
        assertEquals("Cogwheel", cog.o);
        assertEquals("Engranaje", cog.t);
        assertTrue(cog.revised());
        assertFalse(read.get("create").get("item.create.wrench").revised());
        assertEquals("usadas-es_ar.json", f.getFileName().toString());
    }

    @Test
    void cadaPasadaReemplazaLaAnterior() throws IOException {
        Path f = CommunityUsed.file(dir, "es_ar");
        CommunityUsed first = new CommunityUsed();
        first.add("a", "k1", "One", "Uno", false);
        first.write(f);
        CommunityUsed second = new CommunityUsed();
        second.add("b", "k2", "Two", "Dos", false);
        second.write(f);
        Map<String, Map<String, CommunityUsed.Entry>> read = CommunityUsed.read(f);
        assertNull(read.get("a"));
        assertEquals("Dos", read.get("b").get("k2").t);
        // Sin nada usado, el archivo desaparece.
        new CommunityUsed().write(f);
        assertFalse(Files.exists(f));
    }

    @Test
    void archivoFaltanteODaniadoNoRompe() throws IOException {
        Path f = CommunityUsed.file(dir, "es_mx");
        assertTrue(CommunityUsed.read(f).isEmpty());
        Files.writeString(f, "{esto no es json", StandardCharsets.UTF_8);
        assertTrue(CommunityUsed.read(f).isEmpty());
        // Entradas incompletas se descartan.
        Files.writeString(f, "{\"a\": {\"k\": {\"o\": \"Hi\"}, \"k2\": {\"o\": \"Bye\", \"t\": \"Chau\"}}}", StandardCharsets.UTF_8);
        Map<String, Map<String, CommunityUsed.Entry>> read = CommunityUsed.read(f);
        assertEquals(1, read.get("a").size());
        assertEquals("Chau", read.get("a").get("k2").t);
    }
}
