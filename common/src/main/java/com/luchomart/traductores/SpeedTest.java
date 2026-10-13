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

    /** Textos por minuto estimados con esta configuración (ya contadas las peticiones simultáneas y el límite). */
    public static int measure(LlmClient client, Config cfg, String target) throws IOException, InterruptedException {
        String prompt = cfg.effectivePrompt(target);
        long t0 = System.nanoTime();
        client.translate(cfg, prompt, SAMPLE);
        double seconds = Math.max(0.2, (System.nanoTime() - t0) / 1e9);
        int perMinute = estimate(seconds, SAMPLE.size(), cfg.parallelRequests, cfg.isLocalAi(), ServiceGuide.current(cfg));
        ServiceGuide.measured(cfg.apiUrl, cfg.model, perMinute);
        TraductorES.LOG.info("[Traductor ES] Minitest: {} textos en {} s → ~{} textos/min", SAMPLE.size(),
                Math.round(seconds * 10) / 10.0, perMinute);
        return perMinute;
    }

    /** Como {@link #measure}, pero si falla devuelve null (el minitest es un extra: no debe cortar la prueba). */
    public static Integer tryMeasure(LlmClient client, Config cfg, String target) {
        try {
            return measure(client, cfg, target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            TraductorES.LOG.info("[Traductor ES] No se pudo hacer el minitest de velocidad: {}", LlmClient.describe(e));
            return null;
        }
    }

    /**
     * Una IA en la PC atiende de a una petición (las simultáneas hacen fila); en la nube se multiplican, pero
     * los servicios con plan gratis tienen un techo de textos por minuto que el test corto no llega a ver.
     */
    static int estimate(double seconds, int texts, int parallel, boolean local, ServiceGuide.Option service) {
        double single = texts / seconds * 60;
        double total = local ? single : single * Math.max(1, parallel);
        if (!local && service.free()) total = Math.min(total, service.perMinute());
        return (int) Math.max(1, Math.round(total));
    }
}
