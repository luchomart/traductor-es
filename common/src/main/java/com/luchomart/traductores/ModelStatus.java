package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que se aprendió de cada modelo por los errores del servicio: que ya no existe aunque se liste, que no tiene plan
 * gratis, o que se acabó su cupo gratis hasta cierta hora. Se guarda en modelos.json para mostrarlo en la lista y no
 * elegirlos solos.
 */
public final class ModelStatus {
    public enum State { RETIRED, NO_FREE, EXHAUSTED }

    /** Estado de un modelo; until: hasta cuándo vale (solo EXHAUSTED); limit: el cupo que informó el servicio, si lo dijo. */
    public record Info(State state, long until, String limit) {
        /** Etiqueta corta para la lista ("AGOTADO · vuelve en 19 h"). */
        public String tag() {
            return switch (state) {
                case RETIRED -> "RETIRADO";
                case NO_FREE -> "SIN PLAN GRATIS";
                case EXHAUSTED -> "AGOTADO · vuelve en " + inWords(until - System.currentTimeMillis());
            };
        }

        /** Explicación para el jugador. */
        public String explain(String model) {
            return switch (state) {
                case RETIRED -> "El servicio todavía lista «" + model + "», pero ya no lo da: elegí otro.";
                case NO_FREE -> "«" + model + "» no tiene plan gratis: hace falta cargar saldo en el servicio.";
                case EXHAUSTED -> "Se acabó el cupo gratis de «" + model + "»" + (limit != null ? " (" + limit + ")" : "")
                        + ". Vuelve en " + inWords(until - System.currentTimeMillis()) + ". Mientras tanto, usá otro modelo.";
            };
        }
    }

    /** Esperas más largas que esto no son "bajar el ritmo": es el cupo del día (o de horas). */
    static final long LONG_WAIT_MS = 10 * 60_000;

    /** Se resuelve recién al usarlo (los tests no tienen la carpeta del juego). */
    private static Path file() {
        // Depende de la cuenta, no del modpack: con las claves compartidas, también se comparte.
        return (Config.get().shareKeys ? Shared.DIR : Config.DIR).resolve("modelos.json");
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Map<String, Info> data;

    private ModelStatus() {}

    private static synchronized Map<String, Info> data() {
        if (data == null) {
            data = new TreeMap<>();
            try {
                if (Files.exists(file())) {
                    Map<String, Info> m = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<TreeMap<String, Info>>() {}.getType());
                    if (m != null) m.forEach((k, v) -> { if (v != null && v.state() != null) data.put(k, v); });
                }
            } catch (Exception e) {
                TraductorES.LOG.warn("[Traductor ES] modelos.json dañado, se empieza de cero");
            }
        }
        return data;
    }

    private static String id(String apiUrl, String model) {
        return Preset.fromUrl(apiUrl) == Preset.CUSTOM ? apiUrl + " " + model : Preset.fromUrl(apiUrl).name() + " " + model;
    }

    /** El estado vigente del modelo, o null si no se sabe nada malo de él. */
    public static synchronized Info get(String apiUrl, String model) {
        Info i = data().get(id(apiUrl, model));
        if (i != null && i.state() == State.EXHAUSTED && i.until() <= System.currentTimeMillis()) {
            data().remove(id(apiUrl, model));
            save();
            return null;
        }
        return i;
    }

    /** Los modelos de la lista que se pueden usar ahora (sin retirados, sin los que no tienen plan gratis, sin agotados). */
    public static List<String> usable(String apiUrl, List<String> models) {
        List<String> out = new ArrayList<>();
        for (String m : models) if (get(apiUrl, m) == null) out.add(m);
        return out;
    }

    /** El modelo respondió bien: se olvida lo malo que se sabía de él. */
    public static synchronized void ok(String apiUrl, String model) {
        if (data().remove(id(apiUrl, model)) != null) save();
    }

    public static synchronized void set(String apiUrl, String model, Info info) {
        data().put(id(apiUrl, model), info);
        save();
        TraductorES.LOG.info("[Traductor ES] Modelo {}: {}", model, info.tag());
    }

    /**
     * Aprende del error, si dice algo del modelo. Devuelve lo aprendido, o null si es un error común (por ejemplo,
     * un límite de pocos segundos, que se resuelve esperando).
     */
    public static Info learn(String apiUrl, String model, LlmClient.LlmException e) {
        if (model == null || model.isBlank()) return null;
        // Con una URL propia, un 404 puede ser la dirección mal escrita y no el modelo.
        if (e.status == 404 && Preset.fromUrl(apiUrl) == Preset.CUSTOM) return null;
        Info info = classify(e.status, e.getMessage(), e.retryAfterSeconds, System.currentTimeMillis());
        if (info != null) set(apiUrl, model, info);
        return info;
    }

    private static final Pattern LIMIT = Pattern.compile("limit:? (\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_IN = Pattern.compile("(?:retry|try again) in ((?:\\d+h)?(?:\\d+m(?!s))?(?:[\\d.]+s)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART = Pattern.compile("([\\d.]+)(h|m|s)");

    static Info classify(int status, String message, long retryAfterSeconds, long now) {
        String msg = message == null ? "" : message;
        String low = msg.toLowerCase(Locale.ROOT);
        if (status == 404) return new Info(State.RETIRED, 0, null);
        if (status != 429) return null;
        Matcher lm = LIMIT.matcher(msg);
        String limitNum = lm.find() ? lm.group(1) : null;
        // Gemini: "free_tier_requests, limit: 0" = ese modelo no se puede usar gratis.
        if ("0".equals(limitNum) && low.contains("free")) return new Info(State.NO_FREE, 0, null);
        long wait = parseWait(msg) * 1000;
        if (wait == 0) wait = retryAfterSeconds * 1000;
        // OpenRouter con modelos ":free": "free-models-per-day" sin decir cuánto falta; se vuelve a la medianoche UTC.
        if (wait == 0 && low.contains("per-day")) {
            long day = 86_400_000L;
            wait = (now / day + 1) * day - now;
        }
        if (wait < LONG_WAIT_MS) return null;
        String limit = null;
        if (limitNum != null) {
            String per = low.contains("per day") || low.contains("perday") || low.contains("per-day") || low.contains("(rpd)") || low.contains("(tpd)")
                    || low.contains("free_tier_requests") ? " por día" : "";
            limit = (low.contains("token") ? limitNum + " tokens" : limitNum + " peticiones") + per;
        }
        return new Info(State.EXHAUSTED, now + wait, limit);
    }

    /** "Please retry in 19h24m23.1s" o "try again in 7m12.5s" → segundos; 0 si no lo dice. */
    static long parseWait(String msg) {
        Matcher m = RETRY_IN.matcher(msg);
        if (!m.find() || m.group(1).isEmpty()) return 0;
        double secs = 0;
        Matcher p = PART.matcher(m.group(1));
        while (p.find()) {
            double v = Double.parseDouble(p.group(1));
            secs += switch (p.group(2)) { case "h" -> v * 3600; case "m" -> v * 60; default -> v; };
        }
        return (long) secs;
    }

    static String inWords(long ms) {
        long min = Math.max(1, (ms + 59_999) / 60_000);
        if (min < 60) return min + " min";
        long h = Math.round(min / 60.0);
        return h + " h";
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo guardar modelos.json: {}", e.toString());
        }
    }
}
