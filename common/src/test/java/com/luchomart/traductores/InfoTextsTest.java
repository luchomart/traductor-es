package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfoTextsTest {
    @Test
    void separaLosNumeros() {
        assertEquals("Range: {0} < y < {1}", InfoTexts.template("Range: -59 < y < 16"));
        assertEquals(List.of("-59", "16"), InfoTexts.numbers("Range: -59 < y < 16"));
        assertEquals("- Ancient Debris ({0}%)", InfoTexts.template("- Ancient Debris (0.5%)"));
        assertEquals(List.of("0.5"), InfoTexts.numbers("- Ancient Debris (0.5%)"));
    }

    @Test
    void lineasConDistintosNumerosDanLaMismaPlantilla() {
        assertEquals(InfoTexts.template("Size: 60"), InfoTexts.template("Size: 40"));
    }

    @Test
    void noTocaNumerosQueSonParteDeOtraCosa() {
        for (String s : List.of("Mk2 Drill", "§4Danger", "&4Danger", "%1$s items", "Color #123", "1st place")) {
            assertEquals(s, InfoTexts.template(s));
            assertEquals(List.of(), InfoTexts.numbers(s));
        }
    }

    @Test
    void siYaTraeLlavesNoLaConvierteEnPlantilla() {
        assertEquals("Size {0}: 60", InfoTexts.template("Size {0}: 60"));
        assertEquals(List.of(), InfoTexts.numbers("Size {0}: 60"));
    }

    @Test
    void ponerLosNumerosEnLaTraduccionPermiteReordenar() {
        assertEquals("Entre y=16 e y=-59", InfoTexts.fill("Entre y={1} e y={0}", List.of("-59", "16")));
        assertEquals("Bloques:", InfoTexts.fill("Bloques:", List.of()));
    }

    @Test
    void laPlantillaPasaElValidadorSiLaIaConservaLasFichas() {
        String template = InfoTexts.template("Range: -59 < y < 16");
        assertFalse(Validator.isTrivial(template));
        assertNull(Validator.check(template, "Rango: {0} < y < {1}"));
        assertNotNull(Validator.check(template, "Rango: -59 < y < 16"));
    }

    @Test
    void laPlantillaSobreviveAlEnmascarado() {
        Masker.Masked m = Masker.mask(InfoTexts.template("- Redstone Ore (75%)"));
        assertEquals("- Redstone Ore (⟦1⟧%)", m.text());
        assertEquals("- Mena de redstone ({0}%)", Masker.unmask("- Mena de redstone (⟦1⟧%)", m.tokens()));
    }
}
