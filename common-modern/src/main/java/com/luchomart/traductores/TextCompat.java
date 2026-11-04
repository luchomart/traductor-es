package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Textos que cambian entre versiones. */
final class TextCompat {
    /** Clave que nunca existe: así Minecraft usa el texto en inglés como "fallback" y le aplica los argumentos. */
    private static final String MISSING = "traductor_es.original.";

    private TextCompat() {}

    /** El texto indicado como plantilla (con sus %s), sin pasar por la traducción de su clave. */
    static MutableComponent fixedTemplate(String key, String text, Object[] args) {
        return Component.translatableWithFallback(MISSING + key, text, args);
    }
}
