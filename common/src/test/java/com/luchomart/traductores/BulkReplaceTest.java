package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkReplaceTest {
    private static BulkReplace.Target t(String original, String current) {
        return new BulkReplace.Target("mod", "k." + current.hashCode(), original, current, current);
    }

    @Test
    void reemplazaRespetandoLaInicial() {
        List<BulkReplace.Change> c = BulkReplace.preview(List.of(
                t("Iron Ore", "Mena de hierro"),
                t("Mine the iron ore", "Picá la mena de hierro")), "mena de", "mineral de");
        assertEquals(2, c.size());
        assertEquals("Mineral de hierro", c.get(0).after());
        assertEquals("Picá la mineral de hierro", c.get(1).after());
        assertEquals("Picá la ".length(), c.get(1).firstMatch());
        assertTrue(c.get(0).ok());
    }

    @Test
    void loEscritoEnMayusculaSeRespeta() {
        // Siglas y arreglos de mayúscula: no se "adaptan" a la minúscula del texto.
        assertEquals("Genera RF", BulkReplace.preview(List.of(t("x", "Genera rf")), "rf", "RF").get(0).after());
        assertEquals("Lingote de Hierro", BulkReplace.preview(List.of(t("x", "Lingote de hierro")), "hierro", "Hierro").get(0).after());
    }

    @Test
    void soloPalabrasEnteras() {
        List<BulkReplace.Change> c = BulkReplace.preview(List.of(
                t("Beacon", "Almenara"),
                t("Ore", "Mena"),
                t("Ores", "Menas")), "mena", "mineral");
        assertEquals(1, c.size());
        assertEquals("Mineral", c.get(0).after());
    }

    @Test
    void todasLasVecesDelTexto() {
        List<BulkReplace.Change> c = BulkReplace.preview(List.of(t("x", "Mena y mena")), "mena", "mineral");
        assertEquals("Mineral y mineral", c.get(0).after());
    }

    @Test
    void salteaLoQueRompeMarcadores() {
        List<BulkReplace.Change> c = BulkReplace.preview(List.of(
                t("Stores %s FE", "Guarda %s FE")), "%s FE", "FE");
        assertEquals(1, c.size());
        assertFalse(c.get(0).ok());
    }

    @Test
    void sinBusquedaOSinCambiosNoHayNada() {
        List<BulkReplace.Target> ts = List.of(t("Ore", "Mena"));
        assertTrue(BulkReplace.preview(ts, "  ", "x").isEmpty());
        assertTrue(BulkReplace.preview(ts, "mena", "Mena").isEmpty()); // queda igual
        assertTrue(BulkReplace.preview(ts, "hierro", "x").isEmpty());
    }

    @Test
    void textoRaroSeBuscaLiteral() {
        List<BulkReplace.Change> c = BulkReplace.preview(List.of(t("a", "Precio (x2)")), "(x2)", "×2");
        assertEquals("Precio ×2", c.get(0).after());
    }
}
