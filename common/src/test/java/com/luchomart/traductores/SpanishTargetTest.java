package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpanishTargetTest {
    @Test
    void autoUsaElIdiomaDelJuegoSoloSiEsEspanol() {
        assertEquals("es_ar", SpanishTarget.resolve("auto", "es_ar"));
        assertEquals("es_es", SpanishTarget.resolve("", "es_es"));
        assertNull(SpanishTarget.resolve("auto", "en_us"));
    }

    @Test
    void unDestinoFijoGanaAlIdiomaDelJuego() {
        assertEquals("es_mx", SpanishTarget.resolve("es_mx", "en_us"));
        assertNull(SpanishTarget.resolve("fr_fr", "es_es"));
    }

    @Test
    void respaldosOrdenadosPorParecido() {
        assertEquals(List.of("es_mx", "es_ar", "es_cl", "es_uy", "es_ve", "es_ec"), SpanishTarget.fallbacks("es_es"));
        assertEquals("es_uy", SpanishTarget.fallbacks("es_ar").get(0));
        assertEquals("es_es", SpanishTarget.fallbacks("es_mx").get(SpanishTarget.fallbacks("es_mx").size() - 1));
        assertFalse(SpanishTarget.fallbacks("es_cl").contains("es_cl"));
    }
}
