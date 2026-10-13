package com.luchomart.traductores;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minitest de velocidad: traduce un lote chico de textos típicos de mods y calcula cuántos textos por minuto
 * daría la traducción real. En la nube se respeta el ritmo que permite el plan gratis (un test corto no llega a
 * chocar con el límite y daría un tiempo demasiado optimista).
 */
public final class SpeedTest {
    private SpeedTest() {}

    static final Map<String, String> SAMPLE = new LinkedHashMap<>();
    static {
        String[] texts = {
                "Iron Gear", "Copper Wire", "Steel Ingot", "Crushing Wheel", "Mechanical Press",
                "Fluid Tank", "Energy Cell", "Upgrade Module", "Ender Chest Upgrade", "Item Filter",
                "Right-click to open the menu.", "Stores up to %s FE of energy.",
                "Can be placed on any side of a machine.", "Shift-click to change the mode.",
                "Requires a redstone signal to operate.", "Increases the processing speed of the machine.",
                "Hold Shift for more information.", "Automatically pulls items from adjacent inventories.",
                "Not enough energy!", "Configure which sides accept items and fluids."};
        for (int i = 0; i < texts.length; i++) SAMPLE.put("t" + i, texts[i]);
    }

    /**
     * raw: lo que dio el test (con las peticiones simultáneas); effective: lo que rinde de verdad con el límite
     * del plan gratis. Si son muy distintos, el servicio es rápido pero el plan gratis lo frena.
     */
    public record Result(int raw, int effective) {
        public boolean limited() {
            return raw > effective * 3 / 2;
        }

        /** "Rápida (~95 textos/min)" o "Normal (~60 textos/min): responde a ~920/min, pero el plan gratis la frena". */
        public String describe() {
            return SpeedLevel.describe(effective) + (limited() ? ": responde a ~" + raw + "/min, pero los límites del servicio la frenan" : "");
        }
    }

    private static volatile Result last;

    /** El último minitest hecho en esta sesión (para pintar el resultado con el color de su velocidad). */
    public static Result lastResult() {
        return last;
    }

    /** Textos por minuto estimados con esta configuración (ya contadas las peticiones simultáneas y el límite). */
    public static Result measure(LlmClient client, Config cfg, String target) throws IOException, InterruptedException {
        String prompt = cfg.effectivePrompt(target);
        long t0 = System.nanoTime();
        client.translate(cfg, prompt, SAMPLE);
        double seconds = Math.max(0.2, (System.nanoTime() - t0) / 1e9);
        int perMinute = estimate(seconds, SAMPLE.size(), cfg.parallelRequests, cfg.isLocalAi(), ServiceGuide.current(cfg));
        // En la PC no hay plan gratis que la frene: lo que se muestra es directamente la estimación real.
        int raw = cfg.isLocalAi() ? perMinute : (int) Math.max(1, Math.round(SAMPLE.size() / seconds * 60 * Math.max(1, cfg.parallelRequests)));
        ServiceGuide.measured(cfg.apiUrl, cfg.model, perMinute);
        TraductorES.LOG.info("[Traductor ES] Minitest: {} textos en {} s → ~{} textos/min (sin límite: ~{})", SAMPLE.size(),
                Math.round(seconds * 10) / 10.0, perMinute, raw);
        last = new Result(Math.max(raw, perMinute), perMinute);
        return last;
    }

    private static volatile String lastError;

    /** Por qué falló el último minitest (para mostrarlo: suele decir algo del modelo), o null. */
    public static String lastError() {
        return lastError;
    }

    /** Como {@link #measure}, pero si falla devuelve null (el minitest es un extra: no debe cortar la prueba). */
    public static Result tryMeasure(LlmClient client, Config cfg, String target) {
        lastError = null;
        try {
            return measure(client, cfg, target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            lastError = LlmClient.describe(e);
            TraductorES.LOG.info("[Traductor ES] No se pudo hacer el minitest de velocidad: {}", lastError);
            return null;
        }
    }

    /**
     * Con la IA en la PC, la traducción real rinde menos de la mitad que el test: los textos reales son más largos
     * (libros: 43 letras de promedio contra 24 del test) y cada pedido lleva el glosario. Medido: 161/min en el test,
     * ~60/min traduciendo OceanBlock.
     */
    static final double LOCAL_REAL_FACTOR = 0.4;

    /**
     * Una IA en la PC atiende de a una petición (las simultáneas hacen fila); en la nube se multiplican, pero
     * los servicios con plan gratis tienen un techo de textos por minuto que el test corto no llega a ver.
     */
    static int estimate(double seconds, int texts, int parallel, boolean local, ServiceGuide.Option service) {
        double single = texts / seconds * 60;
        double total = local ? single * LOCAL_REAL_FACTOR : single * Math.max(1, parallel);
        // En la nube, un test corto no choca con los límites por minuto (ni los planes pagos: OpenRouter de pago rinde
        // ~200/min aunque responda a miles). Se usa el ritmo medido en traducciones reales como techo.
        if (!local) total = Math.min(total, service.perMinute());
        return (int) Math.max(1, Math.round(total));
    }
}
