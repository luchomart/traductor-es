package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {
    @Test
    void aceptaTraduccionConLosMismosMarcadores() {
        assertNull(Validator.check("Stores up to §a%s FE§r", "Almacena hasta §a%s FE§r"));
        assertNull(Validator.check("%1$s killed %2$s", "%2$s fue asesinado por %1$s"));
        assertNull(Validator.check("Hello {0}", "Hola {0}"));
        assertNull(Validator.check("See $(item)Mana$()", "Mira $(item)Maná$()"));
        assertNull(Validator.check("The <item>Manual<r>", "El <item>Manual<r>"));
    }

    @Test
    void elPorcentajeSueltoNoEsUnMarcador() {
        assertNull(Validator.check("50% damage", "50 % de daño"));
    }

    @Test
    void rechazaMarcadoresPerdidosOCambiados() {
        assertNotNull(Validator.check("Stores up to §a%s FE§r", "Almacena hasta %s FE"));
        assertNotNull(Validator.check("+%s%% Speed", "+%s% Velocidad"));
        assertNotNull(Validator.check("Hello {player}", "Hola {jugador}"));
        assertNotNull(Validator.check("A $(l:x)link$(/l)", "Un enlace"));
        assertNotNull(Validator.check("The <item>Manual<r>", "El Manual"));
    }

    @Test
    void rechazaSaltosDeLineaDistintos() {
        assertEquals(Validator.NEWLINES, Validator.check("Uno\nDos", "Uno Dos"));
        assertEquals(Validator.NEWLINES, Validator.check("Uno\nDos", "Uno\\nDos"));
        assertEquals(Validator.NEWLINES, Validator.check("Uno\\nDos", "Uno Dos"));
    }

    @Test
    void rechazaVacioYNulo() {
        assertNotNull(Validator.check("Text", ""));
        assertNotNull(Validator.check("Text", null));
    }

    @Test
    void detectaTextosTriviales() {
        assertTrue(Validator.isTrivial("§a%s/%s"));
        assertTrue(Validator.isTrivial("123 - 45"));
        assertFalse(Validator.isTrivial("FE"));
    }

    @Test
    void descartaCodigosQueNoSonPalabras() {
        // Casos reales que la IA devolvía iguales.
        for (String s : List.of("00ccff", "002d04", "#FF55FF", "CXXIX", "CCIV", "IV", "Ⱎ", "x%s", "1 x 1", "setIsAndFunction()",
                "minecraft:stone", "item.mod.thing_name", "https://discord.gg/abc", "&a&l", "v1.2.3")) {
            assertTrue(Validator.isTrivial(s), s);
        }
        // Textos de verdad, aunque tengan números o códigos.
        for (String s : List.of("Solar Cell", "1st Slot: Outside Texture", "Tier IV", "Level 2", "0 = South, 90 = West",
                "MP3 Player", "Windswept", "&aFree Runners&r", "Mix", "Proxy ID: %s", "OK", "B", "R.I.P.")) {
            assertFalse(Validator.isTrivial(s), s);
        }
    }

    @Test
    void detectaTextosQueQuedaronSinTraducir() {
        assertTrue(Validator.looksUntranslated("Solar Cell", "Solar Cell"));
        assertFalse(Validator.looksUntranslated("Solar Cell", "Célula solar"));
        assertFalse(Validator.looksUntranslated("Mekanism", "Mekanism")); // una sola palabra: nombre propio
        assertFalse(Validator.looksUntranslated("§a%s FE", "§a%s FE"));
    }

    @Test
    void respetaElResaltadoDeCreate() {
        assertNull(Validator.check("Spawns _Mobs_ with spawn liquid.", "Genera _criaturas_ con líquido de aparición."));
        assertNotNull(Validator.check("Spawns _Mobs_ with spawn liquid.", "Genera criaturas con líquido de aparición."));
        // El guion bajo dentro de un identificador no es resaltado.
        assertNull(Validator.check("Place stone_bricks here", "Coloca stone_bricks aquí"));
        assertNull(Validator.check("_Right-Click_ to use.", "_Clic derecho_ para usar."));
    }
}
