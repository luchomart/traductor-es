package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuotaTest {
    private static HttpHeaders headers(Map<String, List<String>> m) {
        return HttpHeaders.of(m, (a, b) -> true);
    }

    @Test
    void groqHeaders() {
        assertEquals("Groq: quedan 13.200 de 14.400 peticiones hoy", Quota.fromGroqHeaders(headers(Map.of(
                "x-ratelimit-remaining-requests", List.of("13200"), "x-ratelimit-limit-requests", List.of("14400")))));
        assertEquals("Groq: quedan 5 peticiones hoy", Quota.fromGroqHeaders(headers(Map.of(
                "x-ratelimit-remaining-requests", List.of("5")))));
        assertNull(Quota.fromGroqHeaders(headers(Map.of())));
        assertNull(Quota.fromGroqHeaders(headers(Map.of("x-ratelimit-remaining-requests", List.of("mucho")))));
    }

    @Test
    void openRouterFreeModel() {
        String free = "{\"data\":{\"label\":\"x\",\"usage\":0,\"limit\":null,\"limit_remaining\":null,\"is_free_tier\":true}}";
        assertTrue(Quota.fromOpenRouter(free, null, true).contains("50 peticiones"));
        String paid = "{\"data\":{\"usage\":3.2,\"limit\":null,\"limit_remaining\":null,\"is_free_tier\":false}}";
        assertTrue(Quota.fromOpenRouter(paid, null, true).contains("1.000"));
    }

    @Test
    void openRouterBalance() {
        String key = "{\"data\":{\"usage\":3.2,\"limit\":5,\"limit_remaining\":1.8,\"is_free_tier\":false}}";
        String credits = "{\"data\":{\"total_credits\":10,\"total_usage\":5.68}}";
        assertEquals("OpenRouter: saldo 4,32 US$ · a esta clave le quedan 1,80 US$", Quota.fromOpenRouter(key, credits, false));
        String noLimit = "{\"data\":{\"usage\":3.2,\"limit\":null,\"limit_remaining\":null,\"is_free_tier\":false}}";
        assertEquals("OpenRouter: saldo 4,32 US$", Quota.fromOpenRouter(noLimit, credits, false));
        assertNull(Quota.fromOpenRouter(noLimit, null, false));
        assertNull(Quota.fromOpenRouter("no es json", credits, false));
    }

    @Test
    void speedEstimate() {
        ServiceGuide.Option groq = ServiceGuide.current("https://api.groq.com/openai/v1/chat/completions", "llama", false);
        ServiceGuide.Option paid = ServiceGuide.current("https://openrouter.ai/api/v1/chat/completions", "gpt", false);
        ServiceGuide.Option local = ServiceGuide.current("http://localhost:11434/v1/chat/completions", "gemma3:12b", true);
        // 20 textos en 10 s = 120/min por petición.
        assertEquals(48, SpeedTest.estimate(10, 20, 4, true, local));         // en la PC no se multiplica y rinde ~40 % del test
        assertEquals(groq.perMinute(), SpeedTest.estimate(10, 20, 2, false, groq)); // tope del plan gratis
        assertEquals(480, SpeedTest.estimate(10, 20, 4, false, paid));       // de pago: sin tope
        assertEquals(1, SpeedTest.estimate(10_000, 1, 1, true, local));
    }
}
