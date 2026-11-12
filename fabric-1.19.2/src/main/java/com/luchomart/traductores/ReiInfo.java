package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Las líneas de las fichas de información de REI, del lado de Minecraft (la lógica está en {@link InfoTexts}). Solo se
 * tocan las de texto fijo y sin partes: las que usan una clave de idioma ya se traducen con el pack.
 */
public final class ReiInfo {
    private static volatile java.lang.reflect.Field texts;
    private static volatile boolean warned;

    private ReiInfo() {}

    /** El texto de una línea que se puede traducir, o null. */
    private static String plain(Component line) {
        return line.getContents() instanceof LiteralContents && line.getSiblings().isEmpty() ? line.getString() : null;
    }

    /**
     * Anota las líneas de una ficha. Se leen del campo porque getTexts ya devuelve el español, y por reflexión porque
     * REI es opcional: si otra versión lo llama distinto, las fichas quedan en inglés en vez de romper el juego.
     */
    @SuppressWarnings("unchecked")
    public static void capture(Object display) {
        try {
            if (texts == null) {
                Class<?> c = display.getClass();
                while (c != null && !hasTexts(c)) c = c.getSuperclass();
                if (c == null) throw new NoSuchFieldException("texts");
                java.lang.reflect.Field f = c.getDeclaredField("texts");
                f.setAccessible(true);
                texts = f;
            }
            for (Component line : (List<Component>) texts.get(display)) {
                String text = plain(line);
                if (text != null) InfoTexts.capture(text);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) TraductorES.LOG.warn("[Traductor ES] No se pudieron leer las fichas de REI: {}", e.toString());
            warned = true;
        }
    }

    private static boolean hasTexts(Class<?> c) {
        for (java.lang.reflect.Field f : c.getDeclaredFields()) if (f.getName().equals("texts")) return true;
        return false;
    }

    /** Las líneas en español; las que no tienen traducción quedan como están. */
    public static List<Component> translated(List<Component> lines) {
        String lang = Minecraft.getInstance().options.languageCode;
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            String text = plain(line);
            String t = text == null ? null : InfoTexts.translate(text, lang);
            out.add(t == null ? line : Component.literal(t).setStyle(line.getStyle()));
        }
        return out;
    }
}
