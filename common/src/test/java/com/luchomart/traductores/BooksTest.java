package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
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
}
