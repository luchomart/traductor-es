package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpeedLevelTest {
    @Test
    void cincoVelocidades() {
        assertEquals(SpeedLevel.VERY_FAST, SpeedLevel.of(210));
        assertEquals(SpeedLevel.VERY_FAST, SpeedLevel.of(150));
        assertEquals(SpeedLevel.FAST, SpeedLevel.of(95));
        assertEquals(SpeedLevel.NORMAL, SpeedLevel.of(60));
        assertEquals(SpeedLevel.SLOW, SpeedLevel.of(20));
        assertEquals(SpeedLevel.VERY_SLOW, SpeedLevel.of(9));
        assertEquals(SpeedLevel.VERY_SLOW, SpeedLevel.of(0));
        assertEquals("Rápida (~95 textos/min)", SpeedLevel.describe(95.4));
    }
}
