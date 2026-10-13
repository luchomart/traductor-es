package com.luchomart.traductores;

/** Las 5 velocidades de traducción, en textos por minuto (medidas en traducciones reales). */
public enum SpeedLevel {
    VERY_FAST("Muy rápida", 150, 0xFF55FF55),
    FAST("Rápida", 80, 0xFF55FF55),
    NORMAL("Normal", 40, 0xFFFFFF55),
    SLOW("Lenta", 15, 0xFFFFAA00),
    VERY_SLOW("Muy lenta", 0, 0xFFFF5555);

    public final String label;
    /** Desde cuántos textos por minuto. */
    public final int from;
    public final int color;

    SpeedLevel(String label, int from, int color) {
        this.label = label;
        this.from = from;
        this.color = color;
    }

    public static SpeedLevel of(double textsPerMinute) {
        for (SpeedLevel l : values()) if (textsPerMinute >= l.from) return l;
        return VERY_SLOW;
    }

    /** "Rápida (~95 textos/min)". */
    public static String describe(double textsPerMinute) {
        return of(textsPerMinute).label + " (~" + Math.round(textsPerMinute) + " textos/min)";
    }
}
