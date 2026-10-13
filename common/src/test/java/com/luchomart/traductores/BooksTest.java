package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BooksTest {
    private static Map<String, String> extract(Books.Format f, String file, JsonElement el) {
        Map<String, String> out = new LinkedHashMap<>();
        Books.extract(f, file, el, out::put);
        return out;
    }

    @Test
    void mantleTomaTitulosTextosYListas() {
        // Página de herramienta de Tinkers' Construct (con un comentario, como algunos libros).
        JsonElement page = JsonParser.parseString("""
                {
                  // comentario
                  "title": "Pattern Crafting",
                  "recipe": "tconstruct:tables/pattern",
                  "grid_size": "small",
                  "tool": "tconstruct:sky_staff",
                  "image": { "file": "tconstruct:book/images/selena.png", "width": 95 },
                  "description": [ { "text": "You will need many patterns.", "paragraph": true } ],
                  "properties": ["Low Durability", "tconstruct:some_id"],
                  "effects": ["Increases accuracy of fired projectiles"]
                }
                """);
        Map<String, String> got = extract(Books.Format.MANTLE, "intro/pattern_craft.json", page);
        assertEquals(Map.of(
                "/title", "Pattern Crafting",
                "/description/0/text", "You will need many patterns.",
                "/properties/0", "Low Durability",
                "/effects/0", "Increases accuracy of fired projectiles"), got);

        JsonElement done = Books.apply(page.getAsJsonObject(), Map.of("/title", "Fabricación de patrones", "/properties/0", "Poca durabilidad"));
        assertEquals("Fabricación de patrones", done.getAsJsonObject().get("title").getAsString());
        assertEquals("Poca durabilidad", done.getAsJsonObject().getAsJsonArray("properties").get(0).getAsString());
        assertEquals("tconstruct:some_id", done.getAsJsonObject().getAsJsonArray("properties").get(1).getAsString());
        assertTrue(done.getAsJsonObject().getAsJsonObject("image").has("width"));
    }

    @Test
    void patchouliNoTomaListasDeMantle() {
        JsonElement page = JsonParser.parseString("{\"title\": \"Hello\", \"effects\": [\"Not a Patchouli field\"]}");
        assertEquals(Map.of("/title", "Hello"), extract(Books.Format.PATCHOULI, "entries/a.json", page));
    }

    @Test
    void languageLangDeMantle() throws Exception {
        JsonObject lang = Books.parseLang(new StringReader("""
                intro=Introduction
                // comentario

                tools.group_general=General
                armor.subtext=Basic armor = basic protection.
                """));
        assertEquals(3, lang.size());
        assertEquals("Basic armor = basic protection.", lang.get("armor.subtext").getAsString());

        Map<String, String> got = extract(Books.Format.MANTLE, "language.lang", lang);
        assertEquals("Introduction", got.get("/intro"));
        assertEquals("General", got.get("/tools.group_general"));

        // Lo que no se tradujo sigue en inglés en el mismo archivo: Mantle no mezcla idiomas.
        JsonElement done = Books.apply(lang, Map.of("/intro", "Introducción"));
        assertEquals("intro=Introducción\ntools.group_general=General\narmor.subtext=Basic armor = basic protection.\n", Books.toLang(done));
    }

    @Test
    void manualDeImmersiveEngineering() throws Exception {
        String original = """
                Alloy Kiln
                Mixin' Metals
                <&recipe>The Alloy Kiln is a furnace, creating an <link;basic_metal_products;alloy;alloys>.<np>
                Power is <config;i;machines.dieselGen_output> RF/t, see <link;wiring;cables> and \\
                press <keybind;key.immersiveengineering.magnetEquip>, §2insulating glass§r.
                """;
        JsonObject lines = TextPages.parse(new StringReader(original), TextPages.Kind.IE);
        Map<String, String> got = extract(Books.Format.IE_MANUAL, "alloy_kiln.txt", lines);
        assertEquals("Alloy Kiln", got.get("/0"));
        assertEquals("<t1>The Alloy Kiln is a furnace, creating an <l2>alloy</l2>.<np>", got.get("/2"));
        assertEquals("Power is <t3> RF/t, see <l5>cables</l5> and <t4>", got.get("/3"));
        // Las marcas nuevas son de las que el traductor ya protege.
        assertEquals(List.of("<t1>", "<l2>", "</l2>", "<np>"), Masker.mask(got.get("/2")).tokens());

        // Sin traducir, vuelve exactamente igual.
        assertEquals(original, TextPages.write(lines));

        String out = TextPages.write(Books.apply(lines, Map.of(
                "/2", "<t1>El horno de aleación es un horno que crea una <l2>aleación</l2>.<np>",
                "/3", "La potencia es <t3> RF/t, mirá los <l5>cables</l5> y <t4>")));
        assertTrue(out.contains("<&recipe>El horno de aleación es un horno que crea una <link;basic_metal_products;aleación;alloys>.<np>\n"), out);
        assertTrue(out.contains("La potencia es <config;i;machines.dieselGen_output> RF/t, mirá los <link;wiring;cables> y \\\n"), out);
    }

    @Test
    void linkSeparadoPorLaIaNoRompeElArchivo() throws Exception {
        JsonObject lines = TextPages.parse(new StringReader("See <link;wiring;cables>.\n"), TextPages.Kind.IE);
        assertEquals("Mirá los cables.\n", TextPages.write(Books.apply(lines, Map.of("/0", "Mirá </l1>los cables<l1>."))));
    }

    @Test
    void libroDeCitadelSaltaLasLineasSinTexto() throws Exception {
        JsonObject lines = TextPages.parse(new StringReader("<NEWLINE>\n\nAnacondas are gigantic snakes.\n<NEWLINE>\n"), TextPages.Kind.LINES);
        assertEquals(Map.of("/2", "Anacondas are gigantic snakes."), extract(Books.Format.CITADEL, "anaconda.txt", lines));
        assertEquals("<NEWLINE>\n\nLas anacondas son serpientes gigantes.\n<NEWLINE>\n",
                TextPages.write(Books.apply(lines, Map.of("/2", "Las anacondas son serpientes gigantes."))));
    }

    @Test
    void guiaDeGuideMe() throws Exception {
        String page = """
                ---
                navigation:
                  title: Getting Started (1.20+)
                  position: 10
                ---

                # Getting Started

                <GameScene zoom="4" background="transparent">
                  <ImportStructure src="assets/assemblies/meteor_interior.snbt" />
                  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
                    Flawless Budding Certus
                  </BoxAnnotation>
                </GameScene>

                To get started, find a [meteorite](ae2-mechanics/meteorites.md). If you
                haven't, craft a <ItemLink id="meteorite_compass" />, using `/give` \\*here\\*.

                - Mine the **clusters**
                - Keep the <ItemLink id="mysterious_cube" />

                <RecipeFor
                  id="inscriber" />

                | Tier   | Power |
                | ------ | ----- |
                | Basic  | <powah:EnergyCapacity id="x" /> |

                ```
                code stays
                ```
                """;
        JsonObject md = TextPages.parse(new StringReader(page), TextPages.Kind.MARKDOWN);
        Map<String, String> got = extract(Books.Format.GUIDEME, "getting-started.md", md);
        assertEquals(List.of(
                "Getting Started (1.20+)",
                "Getting Started",
                "Flawless Budding Certus",
                "To get started, find a <l5>meteorite</l5>. If you haven't, craft a <t1>, using <t2> <t3>here<t4>.",
                "Mine the **clusters**",
                "Keep the <t6>",
                "Tier", "Power", "Basic"), List.copyOf(got.values()));

        // Sin traducir, solo cambia que el párrafo cortado queda en una línea (en Markdown se ve igual).
        String same = TextPages.write(md);
        assertEquals(page.replace("If you\nhaven't", "If you haven't"), same);

        Map<String, String> tr = new LinkedHashMap<>();
        got.forEach((k, v) -> tr.put(k, v));
        tr.put(key(got, "Getting Started"), "Primeros pasos");
        tr.put(key(got, "Flawless Budding Certus"), "Certus en germinación impecable");
        tr.put(key(got, "To get started"), "Para empezar, buscá un <l5>meteorito</l5>. Si no, fabricá una <t1> con <t2> <t3>acá<t4>.");
        tr.put(key(got, "Tier"), "Nivel");
        String out = TextPages.write(Books.apply(md, tr));
        assertTrue(out.contains("\n# Primeros pasos\n"), out);
        assertTrue(out.contains("\n    Certus en germinación impecable\n"), out);
        assertTrue(out.contains("Para empezar, buscá un [meteorito](ae2-mechanics/meteorites.md). Si no, fabricá una <ItemLink id=\"meteorite_compass\" /> con `/give` \\*acá\\*.\n"), out);
        assertTrue(out.contains("| Nivel   | Power |\n"), out);
        assertTrue(out.contains("<RecipeFor\n  id=\"inscriber\" />\n"), out);
        assertTrue(out.contains("```\ncode stays\n```\n"), out);
    }

    private static String key(Map<String, String> m, String startsWith) {
        return m.entrySet().stream().filter(e -> e.getValue().equals(startsWith)).findFirst()
                .or(() -> m.entrySet().stream().filter(e -> e.getValue().startsWith(startsWith)).findFirst()).orElseThrow().getKey();
    }
}
