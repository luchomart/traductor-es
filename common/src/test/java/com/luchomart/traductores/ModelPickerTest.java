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
        assertEquals("gemini-3.8-flash", ModelPicker.recommend(Preset.GEMINI, gemini));
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
}
