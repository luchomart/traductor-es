package com.luchomart.traductores;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Creación de botones, ayudas y textos de fondo, igual en todas las versiones. En 1.19 los campos no tienen ayuda
 * ni texto de fondo: se guardan acá y los dibuja BaseScreen.
 */
public final class Widgets {
    private static final Map<AbstractWidget, Component> TOOLTIPS = new WeakHashMap<>();
    private static final Map<EditBox, Component> HINTS = new WeakHashMap<>();

    private Widgets() {}

    public static Builder button(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static void tooltip(AbstractWidget widget, Component text) {
        TOOLTIPS.put(widget, text);
    }

    public static void hint(EditBox box, Component text) {
        HINTS.put(box, text);
    }

    static Component tooltipOf(AbstractWidget widget) {
        return TOOLTIPS.get(widget);
    }

    static Component hintOf(EditBox box) {
        return HINTS.get(box);
    }

    public static final class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private int x, y, w = 150, h = 20;
        private Component tooltip;

        private Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public Builder tooltip(Component text) {
            tooltip = text;
            return this;
        }

        public Button build() {
            Button button = new Button(x, y, w, h, message, onPress);
            if (tooltip != null) Widgets.tooltip(button, tooltip);
            return button;
        }
    }
}
