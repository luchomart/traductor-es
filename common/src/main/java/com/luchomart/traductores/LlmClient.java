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
    private volatile boolean reasoningUnsupported = false;
    /** Lo que le queda a la clave según la última respuesta (solo Groq lo informa en cada respuesta), o null. */
    private volatile String lastQuota;

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

        /** Clave ausente o inválida. Gemini lo informa con 400 en vez de 401. */
        public boolean isAuthError() {
            if (status == 401 || status == 403) return true;
            return status == 400 && mentionsAuth(getMessage());
        }
    }

    /** Traduce un lote {clave: texto} y devuelve {clave: traducción} con lo que se pudo leer. */
    public Map<String, String> translate(Config cfg, String systemPrompt, Map<String, String> batch)
            throws IOException, InterruptedException {
        JsonObject input = new JsonObject();
        batch.forEach(input::addProperty);
        String content = chat(cfg, systemPrompt, input.toString(), cfg.jsonMode && !jsonModeUnsupported, MAX_OUTPUT_TOKENS);
        return parseObject(content);
    }

    /**
     * Modelos disponibles en el servidor (GET .../models, parte del protocolo de OpenAI).
     * Devuelve una lista vacía si el servidor no lo soporta.
     */
    public java.util.List<String> listModels(Config cfg) throws IOException, InterruptedException {
        String url = normalizeUrl(cfg.apiUrl).replaceFirst("/chat/completions/?$", "/models");
        if (!url.endsWith("/models")) return java.util.List.of();
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        if (cfg.apiKey != null && !cfg.apiKey.isBlank()) req.header("Authorization", "Bearer " + cfg.apiKey);
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            LlmException e = new LlmException(httpError(res.statusCode(), res.body()), res.statusCode(), 0);
            if (e.isAuthError()) throw e;
            return java.util.List.of(); // el servidor no lista modelos: no es un error
        }
        return parseModelList(res.body());
    }

    /** Lee {"data":[{"id":"..."}]}. Gemini antepone "models/" a los ids: se quita. */
    static java.util.List<String> parseModelList(String body) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            for (JsonElement el : JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("data")) {
                String id = el.getAsJsonObject().get("id").getAsString();
                out.add(id.startsWith("models/") ? id.substring("models/".length()) : id);
            }
        } catch (RuntimeException ignored) {
            // formato distinto: se trata como "no soportado"
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Cuánto le queda a la clave, en una línea, o null si el servicio no lo informa.
     * Groq: lo de la última respuesta (hace falta haber hecho una petición). OpenRouter: se consulta.
     */
    public String quota(Config cfg) {
        Preset p = Preset.fromUrl(cfg.apiUrl);
        if (p == Preset.OPENROUTER) {
            String base = normalizeUrl(cfg.apiUrl).replaceFirst("/chat/completions/?$", "");
            String key = get(base + "/key", cfg), credits = get(base + "/credits", cfg);
            String q = Quota.fromOpenRouter(key, credits, cfg.model != null && cfg.model.endsWith(":free"));
            if (q != null) lastQuota = q;
            return q;
        }
        return p == Preset.GROQ ? lastQuota : null;
    }

    /**
     * Si la cuenta puede usar modelos de pago. Solo lo sabe OpenRouter (consulta el saldo); en el resto, true.
     * Sin saldo conocido, se mira si alguna vez cargó crédito; si tampoco se sabe, false: es mejor proponer uno
     * gratis que uno que va a fallar.
     */
    public boolean canPay(Config cfg) {
        if (Preset.fromUrl(cfg.apiUrl) != Preset.OPENROUTER) return true;
        if (ModelStatus.noCredit(cfg.apiUrl)) return false;
        // Lo de una consulta anterior puede ser de otra clave.
        Quota.openRouterBalance = null;
        Quota.openRouterFreeTier = null;
        quota(cfg);
        Double balance = Quota.openRouterBalance;
        if (balance != null) return balance > 0;
        return Boolean.FALSE.equals(Quota.openRouterFreeTier);
    }

    /** GET con la clave; null si falla (es información opcional). */
    private String get(String url, Config cfg) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
            if (cfg.apiKey != null && !cfg.apiKey.isBlank()) req.header("Authorization", "Bearer " + cfg.apiKey);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() / 100 == 2 ? res.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Lo último que se supo del límite, sin hacer peticiones. */
    public String lastQuota() {
        return lastQuota;
    }

    /** Petición corta para comprobar que el servidor y el modelo responden. */
    public String ping(Config cfg) throws IOException, InterruptedException {
        // Margen amplio: los modelos que razonan gastan tokens pensando antes de contestar.
        // Para la prueba alcanza con que el servidor responda bien, aunque el texto venga vacío o cortado.
        return chat(cfg, "Responde solo con la palabra: OK", "Di OK", false, 1024, true).trim();
    }

    /**
     * Tope de la respuesta. Sin él, servicios como OpenRouter reservan saldo para el máximo del modelo
     * (decenas de miles de tokens) y rechazan la petición con poco crédito. Un lote traducido usa menos de 2.000.
     */
    static final int MAX_OUTPUT_TOKENS = 4096;

    private String chat(Config cfg, String system, String user, boolean jsonMode, int maxTokens) throws IOException, InterruptedException {
        return chat(cfg, system, user, jsonMode, maxTokens, false);
    }

    /**
     * @param tolerant si es true, una respuesta vacía o cortada no es un error (para la prueba de conexión).
     */
    private String chat(Config cfg, String system, String user, boolean jsonMode, int maxTokens, boolean tolerant)
            throws IOException, InterruptedException {
        // Traducir no necesita razonar: en la nube se pide el mínimo (ahorra tiempo y saldo) y en la PC, nada (qwen3:14b
        // razonando traducía a 22 textos/min contra 60 sin razonar, con el mismo resultado). Ollama lo acepta también
        // en modelos que no razonan; si un servidor no lo acepta, se sigue sin pedirlo.
        boolean lowReasoning = !reasoningUnsupported;
        String effort = Preset.fromUrl(cfg.apiUrl).isCloud() ? "low" : "none";
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", cfg.temperature);
        body.addProperty("stream", false);
        if (lowReasoning) body.addProperty("reasoning_effort", effort);
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
        if (Preset.fromUrl(cfg.apiUrl) == Preset.GROQ) {
            String q = Quota.fromGroqHeaders(res.headers());
            if (q != null) lastQuota = q;
        }
        if (res.statusCode() == 400) {
            // Parámetros opcionales que algunos servidores no aceptan: se sigue sin ellos y no se vuelven a pedir.
            String err = res.body() == null ? "" : res.body().toLowerCase(java.util.Locale.ROOT);
            if (jsonMode && (err.contains("response_format") || err.contains("json_object"))) {
                jsonModeUnsupported = true;
                TraductorES.LOG.warn("[Traductor ES] El servidor no acepta modo JSON, se continúa sin él");
                return chat(cfg, system, user, false, maxTokens, tolerant);
            }
            // Cada servicio lo dice distinto: "reasoning_effort" (OpenAI) o "Thinking level is not supported" (Google).
            if (lowReasoning && (err.contains("reasoning") || err.contains("thinking"))) {
                reasoningUnsupported = true;
                TraductorES.LOG.warn("[Traductor ES] El servidor no acepta reasoning_effort, se continúa sin él");
                return chat(cfg, system, user, jsonMode, maxTokens, tolerant);
            }
        }
        if (res.statusCode() / 100 != 2) {
            long retryAfter = res.headers().firstValue("Retry-After").map(LlmClient::parseRetryAfter).orElse(0L);
            throw new LlmException(httpError(res.statusCode(), res.body()), res.statusCode(), retryAfter);
        }
        String finish, content;
        try {
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    ? choice.get("finish_reason").getAsString() : "";
            // Los modelos que razonan pueden devolver content null si se les acaban los tokens pensando.
            JsonElement c = choice.getAsJsonObject("message").get("content");
            content = c == null || c.isJsonNull() ? "" : c.getAsString();
        } catch (RuntimeException e) {
            // Respuesta 200 que no tiene el formato de OpenAI (proxy, página de error, servidor incompatible).
            throw new LlmException("respuesta del servidor con formato inesperado: " + abbreviate(res.body().strip(), 200));
        }
        if (tolerant) return content;
        if ("length".equals(finish)) {
            throw new LlmException("respuesta cortada por falta de espacio (bajá Textos por lote; si es un modelo que razona, probá otro)");
        }
        if (content.isBlank()) {
            throw new LlmException("la IA respondió sin texto (si es un modelo que razona, probá otro modelo)");
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

    private static final java.util.regex.Pattern ERROR_MESSAGE = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Mensaje de error HTTP entendible: el motivo que da el servidor y qué hacer. */
    static String httpError(int status, String body) {
        java.util.regex.Matcher m = ERROR_MESSAGE.matcher(body == null ? "" : body);
        String reason = m.find() ? m.group(1).replace("\\\"", "\"") : abbreviate(body == null ? "" : body.strip(), 150);
        String hint;
        if (status == 401 || status == 403 || mentionsAuth(reason)) {
            hint = "la clave (API key) falta o no es válida";
        } else if (status == 402) {
            hint = "la cuenta no tiene saldo suficiente o la clave tiene un límite de gasto";
        } else if (status == 404) {
            hint = "no existe ese modelo o la URL está mal";
        } else if (status == 429) {
            hint = "el servicio pide bajar el ritmo (límite de peticiones)";
        } else if (status >= 500) {
            hint = "el servidor de la IA tuvo un problema";
        } else {
            hint = "el servidor rechazó la petición";
        }
        return hint + " (HTTP " + status + (reason.isEmpty() ? "" : ": " + reason) + ")";
    }

    /** El servidor habla de la clave o de la autorización (cada servicio lo dice distinto). */
    static boolean mentionsAuth(String text) {
        String m = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        for (String w : new String[]{"api key", "api_key", "apikey", "authorization", "authentication", "credential", "unauthorized", "clave"}) {
            if (m.contains(w)) return true;
        }
        return false;
    }

    /** Mensaje legible para un error, aunque la excepción no traiga texto. */
    public static String describe(Throwable e) {
        if (e instanceof java.net.ConnectException || e instanceof java.net.http.HttpConnectTimeoutException) {
            return "no hay ninguna IA respondiendo en esa dirección (¿está abierto Ollama o LM Studio? ¿la URL es correcta?)";
        }
        if (e instanceof java.net.UnknownHostException) {
            return "no se encontró el servidor (revisá la URL o tu conexión a internet)";
        }
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "la IA tardó demasiado en responder";
        }
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
