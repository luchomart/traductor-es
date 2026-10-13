package com.luchomart.traductores;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Las variantes del español que tiene Minecraft y cómo se usan como destino o como respaldo. */
public final class SpanishTarget {
    /** Códigos de idioma del juego, en el orden en que se prueban como respaldo para España. */
    public static final List<String> CODES = List.of("es_es", "es_mx", "es_ar", "es_cl", "es_uy", "es_ve", "es_ec");

    private static final Map<String, String> NAMES = Map.of(
            "es_es", "español de España",
            "es_mx", "español de México",
            "es_ar", "español de Argentina (rioplatense, con voseo: «tenés», «hacé clic»)",
            "es_cl", "español de Chile",
            "es_uy", "español de Uruguay (rioplatense, con voseo: «tenés», «hacé clic»)",
            "es_ve", "español de Venezuela",
            "es_ec", "español de Ecuador");

    private static final Map<String, String> DISPLAY = Map.of(
            "es_es", "Español (España)", "es_mx", "Español (México)", "es_ar", "Español (Argentina)",
            "es_cl", "Español (Chile)", "es_uy", "Español (Uruguay)", "es_ve", "Español (Venezuela)",
            "es_ec", "Español (Ecuador)");

    private SpanishTarget() {}

    /** Nombre para mostrar en pantalla ("Español (México)"). */
    public static String displayName(String code) {
        return DISPLAY.getOrDefault(code, code);
    }

    public static boolean isSupported(String code) {
        return CODES.contains(code);
    }

    /** Cómo se le nombra la variante a la IA. */
    public static String describe(String code) {
        return NAMES.getOrDefault(code, "español");
    }

    /**
     * Variantes a usar cuando un mod no trae el idioma de destino, de la más parecida a la menos.
     * Rioplatense con rioplatense; las de Latinoamérica antes que España, y España prefiere México.
     */
    public static List<String> fallbacks(String target) {
        List<String> order = new ArrayList<>();
        switch (target) {
            case "es_ar" -> order.add("es_uy");
            case "es_uy" -> order.add("es_ar");
            default -> {}
        }
        for (String c : CODES) if (!c.equals("es_es") && !order.contains(c)) order.add(c);
        order.add("es_es");
        order.remove(target);
        return order;
    }

    /** El destino configurado: "auto" usa el idioma del juego si es una variante del español. Null si no hay. */
    public static String resolve(String configured, String gameLanguage) {
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
            return isSupported(configured) ? configured : null;
        }
        return isSupported(gameLanguage) ? gameLanguage : null;
    }
}
