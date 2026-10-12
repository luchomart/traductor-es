package com.luchomart.traductores;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cliente mínimo para APIs compatibles con OpenAI (/v1/chat/completions). */
public class LlmClient {
    // HTTP/1.1: con http:// Java intenta pasar a HTTP/2 y algunos servidores locales no lo entienden.
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private volatile boolean jsonModeUnsupported = false;

    public static class LlmException extends IOException {
        /** Código HTTP, o 0 si el error no vino de una respuesta HTTP. */
        public final int status;
        /** Segundos que el servidor pidió esperar (cabecera Retry-After), o 0. */
        public final long retryAfterSeconds;

        public LlmException(String msg) { this(msg, 0, 0); }

        public LlmException(String msg, int status, long retryAfterSeconds) {
            super(msg);
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /** Errores que suelen pasar solos: límite de peticiones o servidor ocupado o reiniciándose. */
        public boolean isTransient() {
            return status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }

        public boolean isModelNotFound() {
            return status == 404;
        }
    }

    /** Traduce un lote {clave: texto} y devuelve {clave: traducción} con lo que se pudo leer. */
    public Map<String, String> translate(Config cfg, String systemPrompt, Map<String, String> batch)
            throws IOException, InterruptedException {
        JsonObject input = new JsonObject();
        batch.forEach(input::addProperty);
        String content = chat(cfg, systemPrompt, input.toString(), cfg.jsonMode && !jsonModeUnsupported);
        return parseObject(content);
    }

    /** Petición corta para comprobar que el servidor y el modelo responden. */
    public String ping(Config cfg) throws IOException, InterruptedException {
        return chat(cfg, "Responde solo con la palabra: OK", "Di OK", false).trim();
    }

    private String chat(Config cfg, String system, String user, boolean jsonMode) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("temperature", cfg.temperature);
        body.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        body.add("messages", messages);
        if (jsonMode) {
            JsonObject fmt = new JsonObject();
            fmt.addProperty("type", "json_object");
            body.add("response_format", fmt);
        }

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(normalizeUrl(cfg.apiUrl)))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (cfg.apiKey != null && !cfg.apiKey.isBlank()) req.header("Authorization", "Bearer " + cfg.apiKey);

        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 400 && jsonMode) {
            // Algunos servidores no aceptan response_format: se reintenta sin él y no se vuelve a pedir.
            jsonModeUnsupported = true;
            TraductorES.LOG.warn("[Traductor ES] El servidor no acepta modo JSON, se continúa sin él");
            return chat(cfg, system, user, false);
        }
        if (res.statusCode() / 100 != 2) {
            long retryAfter = res.headers().firstValue("Retry-After").map(LlmClient::parseRetryAfter).orElse(0L);
            throw new LlmException("HTTP " + res.statusCode() + ": " + abbreviate(res.body(), 300), res.statusCode(), retryAfter);
        }
        String finish, content;
        try {
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    ? choice.get("finish_reason").getAsString() : "";
            content = choice.getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException e) {
            // Respuesta 200 que no tiene el formato de OpenAI (proxy, página de error, servidor incompatible).
            throw new LlmException("respuesta del servidor con formato inesperado: " + abbreviate(res.body(), 200));
        }
        if ("length".equals(finish)) {
            throw new LlmException("respuesta cortada por falta de contexto (bajá el tamaño de lote o subí el contexto del servidor)");
        }
        return content;
    }

    static long parseRetryAfter(String value) {
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 0; // también puede venir como fecha HTTP: se usa la espera por defecto
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    /** Extrae el objeto JSON de la respuesta, tolerando bloques ```json y texto alrededor. */
    static Map<String, String> parseObject(String content) throws LlmException {
        int start = content.indexOf('{'), end = content.lastIndexOf('}');
        if (start < 0 || end <= start) throw new LlmException("la respuesta no contiene JSON: " + abbreviate(content, 200));
        JsonElement el;
        try {
            el = JsonParser.parseString(content.substring(start, end + 1));
        } catch (Exception e) {
            throw new LlmException("JSON inválido: " + e.getMessage());
        }
        if (!el.isJsonObject()) throw new LlmException("la respuesta no es un objeto JSON");
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
        }
        return out;
    }

    static String normalizeUrl(String url) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        // Ollama escucha solo en IPv4 (127.0.0.1); algunas versiones de Java resuelven "localhost" como ::1 y fallan.
        return u.replaceFirst("^(https?://)localhost(?=[:/]|$)", "$1127.0.0.1");
    }

    /** Mensaje legible para un error, aunque la excepción no traiga texto. */
    public static String describe(Throwable e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
