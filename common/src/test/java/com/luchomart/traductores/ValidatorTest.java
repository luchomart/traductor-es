package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

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
    void detectaTextosQueQuedaronSinTraducir() {
        assertTrue(Validator.looksUntranslated("Solar Cell", "Solar Cell"));
        assertFalse(Validator.looksUntranslated("Solar Cell", "Célula solar"));
        assertFalse(Validator.looksUntranslated("Mekanism", "Mekanism")); // una sola palabra: nombre propio
        assertFalse(Validator.looksUntranslated("§a%s FE", "§a%s FE"));
    }
}
