package com.luchomart.traductores;

import java.util.List;

/**
 * Comparación de servicios para "¿Cuál conviene?": velocidad aproximada y costo de cada opción.
 * Las velocidades salen de traducciones reales (textos por minuto, con los límites de cada plan gratis).
 */
public final class ServiceGuide {
    private ServiceGuide() {}

    /**
     * @param perMinute textos por minuto aproximados
     * @param perDay    tope diario de textos (0 = sin tope), por el límite de peticiones del plan gratis
     * @param free      si se puede usar sin pagar
     * @param freeModel si hay que elegir un modelo gratis del servicio (OpenRouter ":free")
     */
    public record Option(String name, Preset preset, boolean free, boolean freeModel, int perMinute, int perDay,
                         String cost, String note) {
        /** Tiempo aproximado para traducir esa cantidad de textos. */
        public String duration(int texts) {
            if (texts <= 0) return "nada";
            if (perDay > 0 && texts > perDay) {
                long days = (texts + perDay - 1) / perDay;
                return "~" + days + " días";
            }
            long min = Math.max(1, Math.round((double) texts / perMinute));
            if (min < 2) return "~1 min";
            if (min < 60) return "~" + min + " min";
            long h = min / 60, m = Math.round((min % 60) / 10.0) * 10;
            if (m == 60) { h++; m = 0; }
            return "~" + h + " h" + (m > 0 ? " " + m + " min" : "");
        }
    }

    /** De la más rápida a la más lenta en la nube, y al final la PC del jugador. */
    public static final List<Option> OPTIONS = List.of(
            new Option("OpenRouter (de pago)", Preset.OPENROUTER, false, false, 200, 0,
                    "De pago", "El más rápido. Cobra por uso: necesitás cargar saldo."),
            new Option("Groq", Preset.GROQ, true, false, 100, 0,
                    "Gratis", "Rápido. A ratos frena unos segundos por el límite del plan gratis."),
            new Option("Google Gemini", Preset.GEMINI, true, false, 60, 0,
                    "Gratis", "Velocidad irregular: Google limita el plan gratis según la demanda."),
            new Option("OpenRouter (gratis)", Preset.OPENROUTER, true, true, 50, 1000,
                    "Gratis", "Lento: el plan gratis permite unos 1.000 textos por día."),
            new Option("En tu PC (Ollama)", Preset.OLLAMA, true, false, 60, 0,
                    "Gratis", "Sin internet ni cuentas. Depende de tu placa de video (gemma3:12b)."));

    /** La opción que corresponde a la configuración actual. */
    public static Option current(Config cfg) {
        return current(cfg.apiUrl, cfg.model, cfg.isLocalAi());
    }

    static Option current(String apiUrl, String model, boolean localAi) {
        Preset p = Preset.fromUrl(apiUrl);
        boolean freeModel = model != null && model.endsWith(":free");
        for (Option o : OPTIONS) if (o.preset() == p && o.freeModel() == freeModel) return o;
        // LM Studio o una URL propia: se estima como una IA en la PC o como la nube más común.
        return p.isCloud() || !localAi ? OPTIONS.get(1) : OPTIONS.get(OPTIONS.size() - 1);
    }
}
