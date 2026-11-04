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
    void conservaElAmpersandEscapadoSinDoblarlo() {
        // Así lo escribió a mano el traductor oficial de OceanBlock: \& sin duplicar la barra.
        Map<String, Object> m = FtbQuests.parse("{\n\tq.title: \"Slice \\&\\ Splice\"\n}");
        assertEquals("Slice \\&\\ Splice", m.get("q.title"));
        assertEquals(m, FtbQuests.parse(FtbQuests.format(m)));
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

    // ---------- Formato viejo ----------

    private static final String CHAPTER = """
            {
            \tfilename: "title_is_not_a_key"
            \timages: [{ hover: ["Wiki", "&oIn case"], image: "ae2:item/blank_pattern", x: -2.5d }]
            \tquests: [{
            \t\tdescription: [
            \t\t\t"Welcome to &eChapter 3&r."
            \t\t\t""
            \t\t\t"{@pagebreak}"
            \t\t\t"{\\"text\\": \\"Purple\\", \\"color\\": \\"#9D7AD0\\"}"
            \t\t]
            \t\tsubtitle: "Say \\"hi\\""
            \t\ttasks: [{ title: "Get it", item: { id: "minecraft:stone" } }]
            \t\ttitle: "Welcome to &eChapter 3&r."
            \t}]
            }
            """;

    @Test
    void formatoViejoJuntaLosTextosSinRepetir(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("chapters"));
        Files.writeString(dir.resolve("chapters/a.snbt"), CHAPTER);
        Files.writeString(dir.resolve("chapter_groups.snbt"), "{ chapter_groups: [{ id: \"1\", title: \"Side\" }] }");
        var lines = List.copyOf(FtbQuests.scanOld(dir).values());
        assertEquals(java.util.Set.of("Side", "Wiki", "&oIn case", "Welcome to &eChapter 3&r.",
                "{\"text\": \"Purple\", \"color\": \"#9D7AD0\"}", "Say \"hi\"", "Get it"), java.util.Set.copyOf(lines));
        assertEquals(7, lines.size());
    }

    @Test
    void formatoViejoAvisaSiElArchivoEstaRoto() {
        assertThrows(IllegalArgumentException.class, () -> FtbQuests.collectOldTexts("{ title: \"sin cerrar", t -> {}));
    }

    @Test
    void formatoViejoGuardaOriginalYTraduccion(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/textos.json");
        String json = "{\"text\": \"Purple\", \"color\": \"#9D7AD0\"}";
        var lines = Map.of("a", "Get it", "b", json, "c", "Sin traducir");
        var translated = Map.of("a", "Conseguilo", "b", "<c0>Violeta</c0>");
        assertTrue(FtbQuests.writeOld(file, lines, translated));
        assertFalse(FtbQuests.writeOld(file, lines, translated));
        var saved = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("Conseguilo", saved.get("Get it").getAsString());
        assertEquals("Violeta", com.google.gson.JsonParser.parseString(saved.get(json).getAsString()).getAsJsonObject().get("text").getAsString());
        assertFalse(saved.has("Sin traducir"));
    }

    public static final class FakeImage {
        public List<String> hover = new java.util.ArrayList<>(List.of("Wiki"));
    }

    public static final class FakeChapter {
        public String title = "Start";
        public final List<String> subtitle = new java.util.ArrayList<>(List.of("Sub", "Otro"));
        public final List<FakeImage> images = List.of(new FakeImage());
    }

    public static final class FakeQuest {
        public String title = "Start";
        public String subtitle = "";
        public final List<String> description = new java.util.ArrayList<>(List.of("Line", "", "{@pagebreak}"));
    }

    public static final class FakeFile {
        final FakeChapter chapter = new FakeChapter();
        final FakeQuest quest = new FakeQuest();
        boolean cleared;
        public java.util.Collection<Object> getAllObjects() { return List.of(chapter, quest); }
        public void clearCachedData() { cleared = true; }
    }

    @Test
    void formatoViejoCambiaLosTextosEnMemoria() throws Exception {
        FakeFile f = new FakeFile();
        int n = FtbQuests.applyTexts(f, Map.of("Start", "Inicio", "Sub", "Subtítulo", "Wiki", "Guía", "Line", "Renglón"));
        assertEquals(5, n);
        assertEquals("Inicio", f.chapter.title);
        assertEquals(List.of("Subtítulo", "Otro"), f.chapter.subtitle);
        assertEquals(List.of("Guía"), f.chapter.images.get(0).hover);
        assertEquals("Inicio", f.quest.title);
        assertEquals(List.of("Renglón", "", "{@pagebreak}"), f.quest.description);
        assertTrue(f.cleared);
    }
}
