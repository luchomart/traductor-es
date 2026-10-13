package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FtbQuestsTest {
    private static final String SAMPLE = """
            {
            \tchapter.061E43DCC3E0E302.title: "Welcome"
            \tchapter.29669B3D02C7D3D3.title: "Tips \\\\&\\\\ Tricks"
            \tquest.007CD364F96C2912.quest_desc: ["Want to run faster? &aFree Runners&r won't help."]
            \tquest.0113FC4C63E64DEE.quest_desc: [
            \t\t"The &3Cast Iron Ingots&r seem useful."
            \t\t""
            \t\t"{@pagebreak}"
            \t\t"{image:ftbquests:textures/logo.png width:100 height:50}"
            \t]
            \tquest.016E959A387A1E03.title: "{\\"text\\": \\"Purple Chalk\\", \\"color\\": \\"#9D7AD0\\"}"
            }
            """;

    @Test
    void leeTextosYListas() {
        Map<String, Object> m = FtbQuests.parse(SAMPLE);
        assertEquals("Welcome", m.get("chapter.061E43DCC3E0E302.title"));
        assertEquals("Tips \\&\\ Tricks", m.get("chapter.29669B3D02C7D3D3.title"));
        assertEquals(List.of("The &3Cast Iron Ingots&r seem useful.", "", "{@pagebreak}",
                "{image:ftbquests:textures/logo.png width:100 height:50}"), m.get("quest.0113FC4C63E64DEE.quest_desc"));
        assertEquals("{\"text\": \"Purple Chalk\", \"color\": \"#9D7AD0\"}", m.get("quest.016E959A387A1E03.title"));
    }

    @Test
    void escribirYLeerDaLoMismo() {
        Map<String, Object> m = FtbQuests.parse(SAMPLE);
        assertEquals(m, FtbQuests.parse(FtbQuests.format(m)));
    }

    @Test
    void saltaRenglonesQueNoSonTexto() {
        assertNull(FtbQuests.translatable(""));
        assertNull(FtbQuests.translatable("{@pagebreak}"));
        assertNull(FtbQuests.translatable("{image:ftbquests:textures/logo.png width:100 height:50}"));
        assertEquals("[Nota] Hay que tener cuidado", FtbQuests.translatable("[Nota] Hay que tener cuidado"));
        // JSON con algo que no se sabe rearmar: no se toca.
        assertNull(FtbQuests.translatable("[{\"translate\": \"item.minecraft.stone\"}]"));
    }

    @Test
    void jsonConColoresSeTraduceComoEtiquetas() {
        String line = "[\"Using \", {\"text\": \"Lime\", \"color\": \"#E2FC97\"}, \" chalk, you can attract a \", {\"text\": \"Djinni\", \"color\": \"#FF5555\"}, \".\"]";
        assertEquals("Using <c1>Lime</c1> chalk, you can attract a <c3>Djinni</c3>.", FtbQuests.translatable(line));
        // La IA puede cambiar el orden de las partes.
        String out = FtbQuests.restore(line, "Con tiza <c1>lima</c1> podés atraer a un <c3>Djinni</c3>.");
        assertEquals("[\"Con tiza \",{\"text\":\"lima\",\"color\":\"#E2FC97\"},\" podés atraer a un \",{\"text\":\"Djinni\",\"color\":\"#FF5555\"},\".\"]", out);
    }

    @Test
    void tituloJsonSigueSiendoUnObjeto() {
        String title = "{\"text\": \"Purple Chalk\", \"color\": \"#9D7AD0\"}";
        assertEquals("<c0>Purple Chalk</c0>", FtbQuests.translatable(title));
        assertEquals("{\"text\":\"Tiza violeta\",\"color\":\"#9D7AD0\"}", FtbQuests.restore(title, "<c0>Tiza violeta</c0>"));
    }

    @Test
    void protegeLosCodigosDeColorConAmpersand() {
        Masker.Masked m = Masker.mask("The &3Cast Iron Ingots&r seem useful.");
        assertEquals("The ⟦1⟧Cast Iron Ingots⟦2⟧ seem useful.", m.text());
        assertNotNull(Validator.check("&aFree Runners&r", "&aZapatillas&r") == null ? "ok" : null);
        assertNotNull(Validator.check("&aFree Runners&r", "Zapatillas&r"));
    }

    /** Con el archivo real de un modpack (si está en esta PC): leer y reescribir no cambia nada. */
    @Test
    void archivoRealDeUnModpack() throws Exception {
        Path real = Path.of(System.getProperty("user.home"),
                ".local/share/PrismLauncher/instances/FTB OceanBlock 2/.minecraft/config/ftbquests/quests/lang/en_us.snbt");
        assumeTrue(Files.isRegularFile(real));
        String text = Files.readString(real);
        Map<String, Object> m = FtbQuests.parse(text);
        assertTrue(m.size() > 1000);
        assertEquals(text, FtbQuests.format(m));
    }
}
