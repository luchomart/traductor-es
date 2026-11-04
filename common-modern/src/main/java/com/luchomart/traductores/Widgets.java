package com.luchomart.traductores;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Creación de botones, ayudas y textos de fondo, igual en todas las versiones. */
public final class Widgets {
    private Widgets() {}

    public static Builder button(Component message, Button.OnPress onPress) {
        return new Builder(Button.builder(message, onPress));
    }

    public static void tooltip(AbstractWidget widget, Component text) {
        widget.setTooltip(Tooltip.create(text));
    }

    public static void hint(EditBox box, Component text) {
        box.setHint(text);
    }

    public static final class Builder {
        private final Button.Builder builder;

        private Builder(Button.Builder builder) {
            this.builder = builder;
        }

        public Builder bounds(int x, int y, int w, int h) {
            builder.bounds(x, y, w, h);
            return this;
        }

        public Builder tooltip(Component text) {
            builder.tooltip(Tooltip.create(text));
            return this;
        }

        public Button build() {
            return builder.build();
        }
    }
}
