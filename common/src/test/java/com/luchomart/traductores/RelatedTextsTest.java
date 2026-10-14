package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelatedTextsTest {
    @Test
    void textosParecidosDelMismoMod() {
        assertTrue(Validator.related("ME DISK Housing", "ME Chemical DISK Housing"));
        assertTrue(Validator.related("MEGA Item DISK Housing", "ME Fluid Disk Housing"));
        // "ME" es corta y "with" es demasiado común: no alcanzan.
        assertFalse(Validator.related("ME Controller", "ME Drive"));
        assertFalse(Validator.related("Works with Create", "Compatible with Botania"));
    }
}
