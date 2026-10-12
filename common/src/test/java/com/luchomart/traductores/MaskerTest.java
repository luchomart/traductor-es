package com.luchomart.traductores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaskerTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "Stores up to §a%s FE§r",
            "The <item>Actually Additions Manual<r>, the book <n>Once",
            "Connect a $(l:tools/drone)Drone$(/l) to the $(item)Controller$().$(br2)Then press $(k:use).",
            "Use {item:<modid>:<itemid>} or {item:minecraft:diamond}",
            "50% faster, efficiency %%: %1$s\\nNext line",
            "Texto sin marcadores",
    })
    void enmascararYRestituirDevuelveElOriginal(String text) {
        Masker.Masked m = Masker.mask(text);
        assertFalse(m.text().contains("§") || m.text().contains("$(") || m.text().contains("<item>"));
        assertEquals(text, Masker.unmask(m.text(), m.tokens()));
    }

    @Test
    void agrupaMarcadoresSeguidos() {
        Masker.Masked m = Masker.mask("Stores up to §a%s FE§r");
        assertEquals("Stores up to ⟦1⟧ FE⟦2⟧", m.text());
        assertEquals(List.of("§a%s", "§r"), m.tokens());
    }

    @Test
    void lasLlavesConEtiquetasSeTomanEnteras() {
        assertEquals(List.of("{item:<modid>:<itemid>}"), Masker.mask("Use {item:<modid>:<itemid>}").tokens());
    }

    @Test
    void permiteReordenarFichas() {
        Masker.Masked m = Masker.mask("A $(item)B$() C");
        assertEquals("C $(item)B$() A", Masker.unmask("C ⟦1⟧B⟦2⟧ A", m.tokens()));
    }

    @Test
    void rechazaFichasPerdidasRepetidasOInventadas() {
        List<String> tokens = Masker.mask("A $(item)B$() C").tokens();
        assertNull(Masker.unmask("C B⟦2⟧ A", tokens));
        assertNull(Masker.unmask("⟦1⟧⟦1⟧B⟦2⟧", tokens));
        assertNull(Masker.unmask("⟦1⟧B⟦2⟧⟦3⟧", tokens));
        assertNull(Masker.unmask("⟦0⟧B", tokens));
    }

    @Test
    void sinMarcadoresNoAceptaFichasSueltas() {
        assertEquals("Hola", Masker.unmask("Hola", List.of()));
        assertNull(Masker.unmask("Hola ⟦1⟧", List.of()));
    }

    @Test
    void protegeElResaltadoDeCreatePeroNoLosIdentificadores() {
        assertEquals("Spawns ⟦1⟧Mobs⟦2⟧ with spawn liquid.", Masker.mask("Spawns _Mobs_ with spawn liquid.").text());
        assertEquals("Use stone_bricks", Masker.mask("Use stone_bricks").text());
    }
}
