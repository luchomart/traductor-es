package com.luchomart.traductores;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reemplazo masivo: buscar una palabra o frase en los textos de la lista del editor (con su filtro) y cambiarla en
 * todos, viendo antes cómo queda cada uno. Lo aplica el editor (onApply), todo junto.
 */
public class ReplaceScreen extends BaseScreen {
    private static final int ROW = 24, TOP = 80, BAR = 6;

    private final Screen parent;
    private final List<BulkReplace.Target> targets;
    private final String scopeLabel;
    private final Consumer<List<BulkReplace.Change>> onApply;

    private String find = "", replacement = "";
    private List<BulkReplace.Change> changes = List.of();
    private int okCount;
    private int scroll, W, left, perPage;
    private Button apply;

    /** @param scopeLabel de qué textos se trata ("Dudosas · create"), para que quede claro dónde se reemplaza */
    public ReplaceScreen(Screen parent, List<BulkReplace.Target> targets, String scopeLabel, Consumer<List<BulkReplace.Change>> onApply) {
        super(Component.literal("Reemplazar"));
        this.parent = parent;
        this.targets = targets;
        this.scopeLabel = scopeLabel;
        this.onApply = onApply;
    }

    @Override
    protected void init() {
        W = Math.min(width - 24, 460);
        left = (width - W) / 2;
        int half = (W - 6) / 2;
        EditBox findBox = new EditBox(font, left, 36, half, 20, Component.literal("Buscar"));
        findBox.setMaxLength(200);
        findBox.setValue(find);
        Widgets.hint(findBox, Component.literal("Buscar… (ej.: Mena de)").withStyle(ChatFormatting.DARK_GRAY));
        findBox.setResponder(s -> { find = s; recompute(); });
        EditBox replaceBox = new EditBox(font, left + W - half, 36, half, 20, Component.literal("Reemplazar por"));
        replaceBox.setMaxLength(200);
        replaceBox.setValue(replacement);
        Widgets.hint(replaceBox, Component.literal("Reemplazar por… (ej.: Mineral de)").withStyle(ChatFormatting.DARK_GRAY));
        replaceBox.setResponder(s -> { replacement = s; recompute(); });
        addRenderableWidget(findBox);
        addRenderableWidget(replaceBox);
        setInitialFocus(findBox);
        perPage = Math.max(3, (height - TOP - 34) / ROW);
        int by = height - 28;
        apply = addRenderableWidget(Widgets.button(Component.literal("Reemplazar"), b -> apply()).bounds(left + W - 140, by, 140, 20).build());
        Widgets.tooltip(apply, Component.literal("Se guardan como correcciones (podés deshacer cada una en el editor) y se aplican todas juntas."));
        addRenderableWidget(Widgets.button(Component.literal("Cancelar"), b -> onClose()).bounds(left + W - 234, by, 90, 20).build());
        recompute();
    }

    private void recompute() {
        changes = BulkReplace.preview(targets, find, replacement);
        okCount = (int) changes.stream().filter(BulkReplace.Change::ok).count();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (apply != null) {
            apply.active = okCount > 0;
            apply.setMessage(Component.literal(okCount > 0 ? "Reemplazar en " + Traductor.fmt(okCount) : "Reemplazar"));
        }
    }

    private void apply() {
        List<BulkReplace.Change> ok = new ArrayList<>();
        for (BulkReplace.Change c : changes) if (c.ok()) ok.add(c);
        if (!ok.isEmpty()) onApply.accept(ok);
    }

    private int maxScroll() {
        return Math.max(0, changes.size() - perPage);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        if (amount == 0) return false;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount) * 3, maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 266) { scroll = Math.max(0, scroll - perPage); return true; }
        if (keyCode == 267) { scroll = Math.min(maxScroll(), scroll + perPage); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {
        g.drawString(font, "Reemplazar en varios textos", left, 10, UiKit.GOLD, true);
        g.drawString(font, font.plainSubstrByWidth("En: " + scopeLabel + " (" + Traductor.fmt(targets.size()) + " textos)", W), left, 22, UiKit.DIM, false);

        String summary;
        int color = UiKit.MUTED;
        if (find.isBlank()) summary = "Busca la palabra o frase entera, sin importar mayúsculas. La inicial de cada lugar se respeta.";
        else if (changes.isEmpty()) summary = "No aparece en ningún texto de la lista.";
        else {
            int skipped = changes.size() - okCount;
            summary = "Cambian " + Traductor.fmt(okCount) + " textos"
                    + (skipped > 0 ? " · " + Traductor.fmt(skipped) + " se saltean (romperían colores o %s)" : "")
                    + ". Revisá que quede bien (por ejemplo, «la mena» → «el mineral»).";
            color = UiKit.TEXT;
        }
        UiKit.wrapped(g, font, Component.literal(summary), left, 60, W, color);

        int rowW = W - BAR - 3;
        int y = TOP;
        for (int i = scroll; i < Math.min(changes.size(), scroll + perPage); i++) {
            drawRow(g, changes.get(i), left, y, rowW, ROW - 2);
            y += ROW;
        }
        int max = maxScroll();
        if (max > 0) {
            int x = left + W - BAR, bottom = TOP + perPage * ROW - 2, h = bottom - TOP;
            g.fill(x, TOP, x + BAR, bottom, 0xFF000000);
            int thumb = Math.max(12, h * perPage / changes.size());
            int ty = TOP + (int) Math.round((h - thumb) * (scroll / (double) max));
            g.fill(x, ty, x + BAR, ty + thumb, 0xFF8A8A8A);
            g.fill(x, ty, x + BAR - 1, ty + thumb - 1, 0xFFB0B0B0);
        }
        if (changes.size() > perPage) {
            String where = Traductor.fmt(scroll + 1) + "–" + Traductor.fmt(Math.min(changes.size(), scroll + perPage)) + " de " + Traductor.fmt(changes.size());
            g.drawString(font, where, left, height - 22, UiKit.MUTED, false);
        }
    }

    /** Antes (gris) y después (blanco, o rojo si se saltea), desde un poco antes del primer cambio. */
    private void drawRow(Gfx g, BulkReplace.Change c, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF24262B);
        String tag = CorrectionScreen.sourceLabel(c.target().ns());
        int tagW = Math.min(100, font.width(tag));
        int textW = w - tagW - 18;
        // El texto antes del primer cambio es igual en los dos: se recorta igual, así el cambio queda a la vista.
        int from = Math.max(0, c.firstMatch() - 20);
        String prefix = from > 0 ? "…" : "";
        g.drawString(font, font.plainSubstrByWidth(prefix + oneLine(c.target().current().substring(Math.min(from, c.target().current().length()))), textW), x + 5, y + 3, UiKit.DIM, false);
        g.drawString(font, font.plainSubstrByWidth(prefix + oneLine(c.after().substring(from)), textW), x + 5, y + 13, c.ok() ? UiKit.WHITE : UiKit.RED, false);
        g.drawString(font, font.plainSubstrByWidth(c.ok() ? tag : "se saltea", tagW + 40), x + w - (c.ok() ? tagW : font.width("se saltea")) - 5, y + 8, c.ok() ? UiKit.MUTED : UiKit.RED, false);
    }

    private static String oneLine(String s) {
        return s.replace('\n', ' ').replace("\\n", " ");
    }
}
