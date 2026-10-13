package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelStatusTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void geminiCupoDiarioAgotado() {
        String msg = "el servicio pide bajar el ritmo (límite de peticiones) (HTTP 429: You exceeded your current quota... "
                + "\\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, "
                + "model: gemini-3.8-flash\\nPlease retry in 19h24m23.124767958s.)";
        ModelStatus.Info i = ModelStatus.classify(429, msg, 0, NOW);
        assertEquals(ModelStatus.State.EXHAUSTED, i.state());
        assertEquals(NOW + (19 * 3600 + 24 * 60 + 23) * 1000L, i.until(), 1000);
        assertEquals("20 peticiones por día", i.limit());
    }

    @Test
    void geminiSinPlanGratis() {
        String msg = "(HTTP 429: Quota exceeded for metric: generate_content_free_tier_requests, limit: 0, model: gemini-3.1-pro)";
        assertEquals(ModelStatus.State.NO_FREE, ModelStatus.classify(429, msg, 0, NOW).state());
    }

    @Test
    void groqTokensPorDia() {
        String msg = "(HTTP 429: Rate limit reached for model `openai/gpt-oss-120b` on tokens per day (TPD): Limit 200000, Used 199500, "
                + "Requested 1800. Please try again in 1h12m5.2s.)";
        ModelStatus.Info i = ModelStatus.classify(429, msg, 0, NOW);
        assertEquals(ModelStatus.State.EXHAUSTED, i.state());
        assertEquals("200000 tokens por día", i.limit());
    }

    @Test
    void esperasCortasNoSonDelModelo() {
        assertNull(ModelStatus.classify(429, "Please try again in 7.5s", 0, NOW));
        assertNull(ModelStatus.classify(429, "rate limited", 30, NOW));
        assertNull(ModelStatus.classify(503, "overloaded", 0, NOW));
        assertEquals(ModelStatus.State.EXHAUSTED, ModelStatus.classify(429, "rate limited", 3600, NOW).state());
    }

    @Test
    void openRouterGratisPorDia() {
        ModelStatus.Info i = ModelStatus.classify(429, "Rate limit exceeded: free-models-per-day", 0, NOW);
        assertEquals(ModelStatus.State.EXHAUSTED, i.state());
        assertTrue(i.until() > NOW && i.until() <= NOW + 86_400_000L);
    }

    @Test
    void retirado() {
        assertEquals(ModelStatus.State.RETIRED, ModelStatus.classify(404, "This model is no longer available", 0, NOW).state());
    }

    @Test
    void esperaEnPalabras() {
        assertEquals("5 min", ModelStatus.inWords(5 * 60_000 - 10));
        assertEquals("19 h", ModelStatus.inWords((19 * 60 + 24) * 60_000L));
    }

    @Test
    void groqCupoDiarioCasiGastadoConEsperaCorta() {
        String msg = "(HTTP 429: Rate limit reached for model `openai/gpt-oss-120b` in organization `org_x` service tier `on_demand` "
                + "on tokens per day (TPD): Limit 200000, Used 199647, Requested 1088. Please try again in 5m17.52s.)";
        ModelStatus.Info i = ModelStatus.classify(429, msg, 0, NOW);
        assertEquals(ModelStatus.State.EXHAUSTED, i.state());
        assertEquals(NOW + 3_600_000L, i.until());
    }

    @Test
    void geminiLimitePorMinutoNoEsAgotado() {
        String msg = "(HTTP 429: Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 15, "
                + "model: gemini-3.5-flash-lite\nPlease retry in 22.222804178s.)";
        assertNull(ModelStatus.classify(429, msg, 0, NOW));
        assertEquals(22, ModelStatus.parseWait(msg));
    }
}
