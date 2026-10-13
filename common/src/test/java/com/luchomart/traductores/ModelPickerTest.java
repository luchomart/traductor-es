package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelPickerTest {
    // Lista real que devolvió Groq en una prueba (octubre 2026), en el orden recibido.
    private static final List<String> GROQ = List.of("allam-2-7b", "canopylabs/orpheus-arabic-saudi", "canopylabs/orpheus-v1-english",
            "meta-llama/llama-prompt-guard-2-22m", "meta-llama/llama-prompt-guard-2-86m", "openai/gpt-oss-120b", "openai/gpt-oss-20b",
            "openai/gpt-oss-safeguard-20b", "whisper-large-v3");

    @Test
    void descartaModelosQueNoSonDeChat() {
        List<String> chat = ModelPicker.chatModels(GROQ);
        assertFalse(chat.contains("canopylabs/orpheus-v1-english"));
        assertFalse(chat.contains("meta-llama/llama-prompt-guard-2-22m"));
        assertFalse(chat.contains("openai/gpt-oss-safeguard-20b"));
        assertFalse(chat.contains("whisper-large-v3"));
        assertTrue(chat.contains("openai/gpt-oss-120b"));
    }

    @Test
    void groqEligeElModeloGrande() {
        assertEquals("openai/gpt-oss-120b", ModelPicker.recommend(Preset.GROQ, GROQ));
        assertEquals("openai/gpt-oss-120b", ModelPicker.ranked(Preset.GROQ, GROQ).get(0));
    }

    @Test
    void geminiEligeElFlashMasNuevoSinVariantes() {
        List<String> gemini = List.of("gemini-2.5-flash", "gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.8-pro",
                "gemini-3.9-flash-preview-09-2026", "gemini-embedding-001", "imagen-4.0-generate");
        // En el plan gratis el flash-lite tiene mucho más cupo (el flash llegó a dar 20 por día).
        assertEquals("gemini-3.8-flash-lite", ModelPicker.recommend(Preset.GEMINI, gemini));
        assertEquals("gemini-3.8-flash", ModelPicker.recommend(Preset.GEMINI, List.of("gemini-3.8-flash", "gemini-2.5-flash")));
    }

    @Test
    void openRouterPrefiereGeminiFlashYSinoUnoGratis() {
        assertEquals("google/gemini-3.8-flash", ModelPicker.recommend(Preset.OPENROUTER,
                List.of("anthropic/claude-x", "google/gemini-3.8-flash", "google/gemini-2.5-flash", "qwen/qwen3:free")));
        assertEquals("qwen/qwen3:free", ModelPicker.recommend(Preset.OPENROUTER, List.of("anthropic/claude-x", "qwen/qwen3:free")));
    }

    @Test
    void leeLaSugerenciaDelError() {
        String gemini = "no existe ese modelo o la URL está mal (HTTP 404: This model models/gemini-2.5-flash is no longer available to new users. "
                + "Please update your code to use models/gemini-3.8-flash for the latest features and improvements.)";
        assertEquals("gemini-3.8-flash", ModelPicker.suggestedReplacement(gemini));
        assertNull(ModelPicker.suggestedReplacement("model not found"));
    }

    @Test
    void sinModelosDeChatNoRecomiendaNada() {
        assertNull(ModelPicker.recommend(Preset.GROQ, List.of("whisper-large-v3")));
    }

    @Test
    void lasVersionesDePruebaVanAlFinalYLasRarasNoAparecen() {
        List<String> gemini = List.of("antigravity-preview-05-2026", "deep-research-preview-04-2026", "gemini-3.9-flash-preview-09-2026",
                "gemini-2.5-flash", "gemini-3.8-flash", "gemini-3.8-pro");
        List<String> ranked = ModelPicker.ranked(Preset.GEMINI, gemini);
        assertEquals("gemini-3.8-flash", ranked.get(0));
        assertFalse(ranked.contains("antigravity-preview-05-2026"));
        assertFalse(ranked.contains("deep-research-preview-04-2026"));
        assertEquals("gemini-3.9-flash-preview-09-2026", ranked.get(ranked.size() - 1));
    }

    @Test
    void modelosQueNoSirvenParaTraducir() {
        assertNotNull(ModelPicker.weakReason("allam-2-7b"));
        assertNotNull(ModelPicker.weakReason("qwen/qwen-2.5-coder-32b-instruct"));
        assertNotNull(ModelPicker.weakReason("gemma3:1b"));
        assertNotNull(ModelPicker.weakReason("llama-3.2-3b-preview"));
        assertNull(ModelPicker.weakReason("gemma3:4b"));
        assertNull(ModelPicker.weakReason("openai/gpt-oss-120b"));
        assertNull(ModelPicker.weakReason("llama-3.1-8b-instant"));
        // Nunca se elige solo, y en la lista va al final.
        assertNotEquals("allam-2-7b", ModelPicker.recommend(Preset.GROQ, List.of("allam-2-7b", "llama-3.1-8b-instant")));
        List<String> ranked = ModelPicker.ranked(Preset.GROQ, GROQ);
        assertEquals("allam-2-7b", ranked.get(ranked.size() - 1));
    }

    @Test
    void tiposDeModelo() {
        assertEquals(ModelPicker.Kind.FAST, ModelPicker.kind("gemini-3.1-flash-lite"));
        assertEquals(ModelPicker.Kind.FAST, ModelPicker.kind("openai/gpt-oss-20b"));
        assertEquals(ModelPicker.Kind.FAST, ModelPicker.kind("gemma3:12b"));
        assertEquals(ModelPicker.Kind.BALANCED, ModelPicker.kind("gemini-3.8-flash"));
        assertEquals(ModelPicker.Kind.QUALITY, ModelPicker.kind("gemini-3.8-pro"));
        assertEquals(ModelPicker.Kind.QUALITY, ModelPicker.kind("openai/gpt-oss-120b"));
        assertEquals(ModelPicker.Kind.PREVIEW, ModelPicker.kind("gemini-3.1-pro-preview"));
        assertEquals(ModelPicker.Kind.WEAK, ModelPicker.kind("allam-2-7b"));
    }

    @Test
    void enLaPcPrefiereTranslateGemma() {
        assertEquals("translategemma:12b", ModelPicker.recommend(Preset.OLLAMA, List.of("gemma3:12b", "qwen3:14b", "translategemma:12b")));
        assertEquals("gemma3:12b", ModelPicker.recommend(Preset.OLLAMA, List.of("qwen3:14b", "gemma3:12b", "gemma3:4b")));
        assertEquals("qwen3:14b", ModelPicker.recommend(Preset.OLLAMA, List.of("llama3.2:3b", "qwen3:14b")));
    }
}
