package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceGuideTest {
    private static ServiceGuide.Option named(String name) {
        return ServiceGuide.OPTIONS.stream().filter(o -> o.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void tiemposEnMinutosYHoras() {
        ServiceGuide.Option groq = named("Groq"); // 100 por minuto
        assertEquals("~1 min", groq.duration(50));
        assertEquals("~10 min", groq.duration(1000));
        assertEquals("~1 h 40 min", groq.duration(10000));
        assertEquals("~2 h", groq.duration(11900)); // 119 min redondea a 2 h, no "1 h 60 min"
        assertEquals("nada", groq.duration(0));
    }

    @Test
    void elPlanGratisDeOpenRouterSeCuentaEnDias() {
        ServiceGuide.Option free = named("OpenRouter (gratis)");
        assertEquals("~20 min", free.duration(1000)); // entra en un día
        assertEquals("~10 días", free.duration(10000));
        assertEquals("~2 días", free.duration(1001));
    }

    @Test
    void reconoceLaOpcionActual() {
        assertEquals("OpenRouter (gratis)", ServiceGuide.current(Preset.OPENROUTER.url, "google/gemma-3-27b-it:free", false).name());
        assertEquals("OpenRouter (de pago)", ServiceGuide.current(Preset.OPENROUTER.url, "google/gemini-3.8-flash", false).name());
        assertEquals("OpenRouter (de pago)", ServiceGuide.current(Preset.OPENROUTER.url, "", false).name());
        assertEquals("En tu PC (Ollama)", ServiceGuide.current(Preset.OLLAMA.url, "gemma3:12b", true).name());
        assertEquals("En tu PC (Ollama)", ServiceGuide.current(Preset.LM_STUDIO.url, "", true).name());
    }

    @Test
    void eligeUnModeloGratisDeOpenRouter() {
        List<String> all = List.of("google/gemini-3.8-flash", "meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-12b-it:free",
                "google/gemma-3-27b-it:free", "qwen/qwen3-coder:free");
        assertEquals("google/gemma-3-27b-it:free", ModelPicker.recommendFree(all));
        assertNull(ModelPicker.recommendFree(List.of("google/gemini-3.8-flash")));
    }
}
