package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpHeaders;
import java.util.Locale;
import java.util.Optional;

/**
 * Cuánto le queda a la clave en el servicio, en una línea corta. Solo algunos servicios lo informan:
 * Groq en las cabeceras de cada respuesta y OpenRouter con una consulta aparte. Gemini no da forma de saberlo.
 */
public final class Quota {
    private Quota() {}

    /** Groq: x-ratelimit-remaining-requests y x-ratelimit-limit-requests son las peticiones del día. Null si no vienen. */
    static String fromGroqHeaders(HttpHeaders h) {
        Optional<Long> left = number(h.firstValue("x-ratelimit-remaining-requests"));
        if (left.isEmpty()) return null;
        Optional<Long> limit = number(h.firstValue("x-ratelimit-limit-requests"));
        return "Groq: quedan " + fmt(left.get()) + (limit.isPresent() ? " de " + fmt(limit.get()) : "") + " peticiones hoy";
    }

    /**
     * OpenRouter: respuestas de /api/v1/key y /api/v1/credits (esta última puede faltar).
     * Con un modelo ":free" lo que importa es el tope diario; con uno de pago, el saldo.
     */
    /** Lo último que se supo de la cuenta de OpenRouter (para «¿Cuál conviene?»): si nunca cargó saldo y cuánto le queda. */
    public static volatile Boolean openRouterFreeTier;
    public static volatile Double openRouterBalance;

    static String fromOpenRouter(String keyBody, String creditsBody, boolean freeModel) {
        JsonObject key = data(keyBody);
        if (key == null) return null;
        // Sin haber comprado créditos el tope de los modelos ":free" es de 50 peticiones por día; con créditos, 1.000.
        boolean freeTier = bool(key, "is_free_tier");
        Double balance = null;
        JsonObject credits = data(creditsBody);
        if (credits != null && credits.has("total_credits") && credits.has("total_usage")) {
            balance = credits.get("total_credits").getAsDouble() - credits.get("total_usage").getAsDouble();
        }
        openRouterFreeTier = freeTier;
        if (balance != null) openRouterBalance = balance;
        if (freeModel) {
            return freeTier ? "OpenRouter gratis: 50 peticiones por día (1.000 si cargás 10 US$)"
                    : "OpenRouter gratis: hasta 1.000 peticiones por día";
        }
        Double keyLeft = key.has("limit_remaining") && !key.get("limit_remaining").isJsonNull()
                ? key.get("limit_remaining").getAsDouble() : null;
        if (balance == null && keyLeft == null) return null;
        StringBuilder sb = new StringBuilder("OpenRouter: ");
        if (balance != null) sb.append("saldo ").append(money(balance));
        if (keyLeft != null) sb.append(balance != null ? " · " : "").append("a esta clave le quedan ").append(money(keyLeft));
        return sb.toString();
    }

    private static JsonObject data(String body) {
        if (body == null) return null;
        try {
            JsonElement d = JsonParser.parseString(body).getAsJsonObject().get("data");
            return d != null && d.isJsonObject() ? d.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean bool(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static Optional<Long> number(Optional<String> s) {
        try {
            return s.map(v -> Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }

    static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0, v)).replace('.', ',') + " US$";
    }
}
