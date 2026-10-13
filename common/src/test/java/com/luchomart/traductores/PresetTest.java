package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresetTest {
    @Test
    void lasFlechasRecorrenLosServiciosSinPasarPorPersonalizado() {
        Preset p = Preset.OLLAMA;
        for (int i = 0; i < 20; i++) {
            p = p.next();
            assertNotEquals(Preset.CUSTOM, p);
            assertNotNull(p.url);
        }
        assertEquals(Preset.GROQ, Preset.OLLAMA.previous());
        assertEquals(Preset.OLLAMA, Preset.GROQ.next());
    }

    @Test
    void desdeUnaUrlPropiaLasFlechasVanAUnServicio() {
        assertEquals(Preset.CUSTOM, Preset.fromUrl("http://mi-servidor:8080/v1/chat/completions"));
        assertEquals(Preset.OLLAMA, Preset.CUSTOM.next());
        assertEquals(Preset.GROQ, Preset.CUSTOM.previous());
    }
}
