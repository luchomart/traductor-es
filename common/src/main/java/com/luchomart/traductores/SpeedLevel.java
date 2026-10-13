package com.luchomart.traductores;

/** Las 5 velocidades de traducción, en textos por minuto (medidas en traducciones reales). */
public enum SpeedLevel {
    VERY_FAST("Muy rápida", 150, 0xFF55FFFF), // celeste
    FAST("Rápida", 80, 0xFF55FF55),          // verde
    NORMAL("Normal", 40, 0xFFFFFF55),        // amarillo
    SLOW("Lenta", 15, 0xFFFFAA00),           // naranja
    VERY_SLOW("Muy lenta", 0, 0xFFFF5555);   // rojo

    public final String label;
    /** Desde cuántos textos por minuto. */
    public final int from;
    public final int color;

    SpeedLevel(String label, int from, int color) {
        this.label = label;
        this.from = from;
        this.color = color;
    }

    /** Para el borde inferior de la barra: el mismo color, más oscuro. */
    public int shade() {
        int r = (color >> 16 & 0xFF) * 2 / 3, gr = (color >> 8 & 0xFF) * 2 / 3, b = (color & 0xFF) * 2 / 3;
        return 0xFF000000 | r << 16 | gr << 8 | b;
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
