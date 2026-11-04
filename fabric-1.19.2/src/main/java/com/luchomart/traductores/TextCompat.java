package com.luchomart.traductores;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Textos que cambian entre versiones. */
final class TextCompat {
    private TextCompat() {}

    /**
     * El texto indicado como plantilla (con sus %s), sin pasar por la traducción de su clave. En 1.19 no hay
     * "fallback", pero una clave que no existe se muestra tal cual: el propio texto hace de clave.
     */
    static MutableComponent fixedTemplate(String key, String text, Object[] args) {
        return Component.translatable(text, args);
    }
}
