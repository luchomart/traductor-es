package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {
    @Test
    void leeJsonPuro() throws Exception {
        assertEquals(Map.of("a", "uno", "b", "dos"), LlmClient.parseObject("{\"a\":\"uno\",\"b\":\"dos\"}"));
    }

    @Test
    void toleraBloquesDeMarkdownYTextoAlrededor() throws Exception {
        assertEquals(Map.of("a", "uno"), LlmClient.parseObject("Aquí tienes:\n```json\n{\"a\": \"uno\"}\n```"));
    }

    @Test
    void ignoraValoresQueNoSonTexto() throws Exception {
        assertEquals(Map.of("a", "uno"), LlmClient.parseObject("{\"a\":\"uno\",\"b\":{\"x\":1},\"c\":[1]}"));
    }

    @Test
    void fallaSinJson() {
        assertThrows(LlmClient.LlmException.class, () -> LlmClient.parseObject("lo siento, no puedo"));
        assertThrows(LlmClient.LlmException.class, () -> LlmClient.parseObject("{roto"));
    }

    @Test
    void normalizaLaUrl() {
        assertEquals("http://127.0.0.1:11434/v1/chat/completions", LlmClient.normalizeUrl("http://localhost:11434/v1/chat/completions"));
        assertEquals("http://127.0.0.1:1234/v1/chat/completions", LlmClient.normalizeUrl("localhost:1234/v1/chat/completions"));
        assertEquals("http://localhostfoo:1/x", LlmClient.normalizeUrl("http://localhostfoo:1/x"));
        assertEquals("https://api.example.com/v1", LlmClient.normalizeUrl(" https://api.example.com/v1 "));
    }

    @Test
    void clasificaErroresPasajeros() {
        assertTrue(new LlmClient.LlmException("x", 429, 10).isTransient());
        assertTrue(new LlmClient.LlmException("x", 503, 0).isTransient());
        assertFalse(new LlmClient.LlmException("x", 400, 0).isTransient());
        assertTrue(new LlmClient.LlmException("x", 404, 0).isModelNotFound());
        assertEquals(30, LlmClient.parseRetryAfter(" 30 "));
        assertEquals(0, LlmClient.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"));
    }

    @Test
    void leeLaListaDeModelos() {
        assertEquals(java.util.List.of("gemma3:12b", "gemma3:4b"),
                LlmClient.parseModelList("{\"object\":\"list\",\"data\":[{\"id\":\"gemma3:4b\"},{\"id\":\"gemma3:12b\"}]}"));
        // Gemini antepone "models/".
        assertEquals(java.util.List.of("gemini-2.5-flash"), LlmClient.parseModelList("{\"data\":[{\"id\":\"models/gemini-2.5-flash\"}]}"));
        assertTrue(LlmClient.parseModelList("<html>no</html>").isEmpty());
    }

    @Test
    void erroresHttpEntendibles() {
        String gemini = "[{\n  \"error\": {\n    \"code\": 400,\n    \"message\": \"Please pass a valid API key\",\n    \"status\": \"INVALID_ARGUMENT\"\n  }\n}\n]";
        String msg = LlmClient.httpError(400, gemini);
        assertTrue(msg.startsWith("la clave (API key) falta o no es válida"), msg);
        assertTrue(msg.contains("Please pass a valid API key"), msg);
        assertTrue(new LlmClient.LlmException(msg, 400, 0).isAuthError());

        assertTrue(LlmClient.httpError(404, "{\"error\":{\"message\":\"model 'x' not found\"}}").startsWith("no existe ese modelo"));
        assertTrue(LlmClient.httpError(429, "").startsWith("el servicio pide bajar el ritmo"));
        assertFalse(new LlmClient.LlmException(LlmClient.httpError(400, "{\"error\":{\"message\":\"bad json\"}}"), 400, 0).isAuthError());
    }

    @Test
    void describeErroresDeConexion() {
        assertTrue(LlmClient.describe(new java.net.ConnectException()).startsWith("no hay ninguna IA respondiendo"));
        assertTrue(LlmClient.describe(new java.net.UnknownHostException("x")).startsWith("no se encontró el servidor"));
    }
}
