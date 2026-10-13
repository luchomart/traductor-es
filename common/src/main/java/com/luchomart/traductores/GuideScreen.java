package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Guía dentro del juego: índice a la izquierda y pasos numerados en tarjetas a la derecha. */
public class GuideScreen extends BaseScreen {
    /** n: número o símbolo de la tarjeta; t: título; d: detalle. */
    private record Step(String n, String t, String d) {}

    private record Section(String title, String intro, List<Step> steps, String tip) {}

    /** Una página: una sección entera o una parte de sus pasos (si no entran en la pantalla). */
    private record Page(int section, String intro, List<Step> steps, String tip, boolean continued) {}

    private static final int HELP = 9; // índice de la sección "Ayuda con una IA"

    private static final List<Section> SECTIONS = List.of(
            new Section("¿Qué hace?",
                    "Traduce los textos de tus mods al español que tengas elegido en el juego: objetos, descripciones, menús y manuales.",
                    List.of(new Step("✓", "Respeta el español oficial", "Si un mod ya trae tu español, no se toca."),
                            new Step("✓", "Usa otras variantes", "Si trae español de otro país, se usa ese en vez de traducir."),
                            new Step("✓", "Traduce lo que falta", "Con una IA en tu PC o en la nube, y lo guarda para la próxima.")),
                    ""),
            new Section("Antes de empezar",
                    "Necesitás tres cosas. El asistente (Herramientas → Asistente) las comprueba por vos.",
                    List.of(new Step("1", "Ollama instalado y abierto", "Descargalo con el botón «Descargar» del panel (ollama.com/download)."),
                            new Step("2", "El modelo de traducción", "En una terminal: ollama pull gemma3:12b (o gemma3:4b con menos de 12 GB de VRAM)."),
                            new Step("3", "El juego en español", "Opciones → Idioma: cualquier país.")),
                    "¿Sin placa de video potente? Mirá «IA en la nube»."),
            new Section("Cómo se usa",
                    "Al abrir el juego traduce solo lo que falta mientras estás en el menú principal.",
                    List.of(new Step("1", "Mirá el progreso", "Arriba a la derecha: barra, mod actual y tiempo restante."),
                            new Step("2", "Podés jugar", "Con IA en la nube sigue traduciendo; con IA en tu PC se pausa para no bajar los FPS (Traducción → Mientras juego)."),
                            new Step("3", "Al terminar", "Los textos se actualizan solos. La primera vez tarda; después, solo lo nuevo.")),
                    "Comandos en el chat: /traductor estado, panel, iniciar, pausar, reanudar, cancelar."),
            new Section("El panel",
                    "Mods → Traductor ES → Configurar (en Fabric, con Mod Menu), o /traductor panel en el chat. Tiene tres pestañas.",
                    List.of(new Step("1", "Conexión", "Servicio de IA, clave, modelo y «Probar conexión»."),
                            new Step("2", "Traducción", "Español de destino, traducir al abrir, mientras juego, libros, lote y peticiones a la vez."),
                            new Step("3", "Herramientas", "Exportar el pack para amigos, ver el informe, abrir la carpeta y el asistente.")),
                    "Pasá el mouse sobre cualquier opción para ver qué hace."),
            new Section("IA en la nube",
                    "Si tu PC no tiene una placa de video potente, podés usar un servicio por internet. Varios tienen planes gratis con límites.",
                    List.of(new Step("1", "Elegí el servicio", "Gemini, Groq u OpenRouter, con las flechas de «Servicio»."),
                            new Step("2", "Conseguí una clave", "El botón «Conseguir» abre la página para crearla."),
                            new Step("3", "Probá la conexión", "El mod elige solo el mejor modelo disponible.")),
                    "Ojo: los textos de tus mods se envían a ese servicio, y la clave queda guardada en config.json."),
            new Section("¿Cuál conviene?",
                    "Tiempos aproximados para 1.000 y 10.000 textos (un modpack grande tiene unos 10.000 sin español).",
                    ServiceGuide.OPTIONS.stream().map(o -> new Step(o.free() ? "✓" : "$", o.name() + " · " + o.cost(),
                            "1.000: " + o.duration(1000) + " · 10.000: " + o.duration(10000) + ". " + o.note())).toList(),
                    "El asistente y el panel («¿Cuál conviene?») calculan el tiempo para tu modpack. Tocá una opción para usarla."),
            new Section("Conseguir una clave",
                    "Una clave (API key) es como una contraseña que le dice al servicio que sos vos.",
                    List.of(new Step("G", "Google Gemini", "aistudio.google.com/apikey · entrá con tu cuenta de Google y tocá «Create API key»."),
                            new Step("Q", "Groq", "console.groq.com/keys · creá una cuenta y tocá «Create API Key»."),
                            new Step("O", "OpenRouter", "openrouter.ai/settings/keys · «Create Key». Los modelos gratis terminan en «:free».")),
                    "La clave se muestra una sola vez: copiala enseguida y no se la pases a nadie."),
            new Section("Corregir textos",
                    "La IA a veces se equivoca. Las correcciones tienen prioridad sobre todo.",
                    List.of(new Step("1", "Abrí la carpeta", "Herramientas → Abrir carpeta."),
                            new Step("2", "Buscá la clave", "En cache, carpeta de tu idioma, archivo del mod: buscá la traducción con Ctrl + F."),
                            new Step("3", "Corregí", "En correcciones.json: \"mod:clave\": \"texto corregido\".")),
                    "Para que una palabra se traduzca siempre igual, agregala en glosario.json."),
            new Section("Si algo falla",
                    "Los problemas más comunes y cómo resolverlos.",
                    List.of(new Step("!", "No se pudo conectar con la IA", "Ollama no está abierto: abrilo y tocá «Probar conexión»."),
                            new Step("!", "La clave falta o no es válida", "Creala de nuevo con «Conseguir» y pegala en Clave."),
                            new Step("!", "Va muy lento", "Usá gemma3:4b o un servicio en la nube, y cerrá videos del navegador."),
                            new Step("!", "Textos en inglés", "Mirá el informe (Herramientas). Se reintentan cada vez que abrís el juego.")),
                    "El asistente (Herramientas → Asistente) revisa todo y te dice qué falta."),
            new Section("Ayuda con una IA",
                    "¿Te trabaste? Pedile ayuda a la IA que uses (ChatGPT, Claude, Gemini…).",
                    List.of(new Step("1", "Copiá el prompt", "Tocá «Copiar prompt», acá abajo."),
                            new Step("2", "Pegalo en la IA", "Abrila en el navegador y pegá el texto (Ctrl + V)."),
                            new Step("3", "Respondé sus preguntas", "Te va a guiar paso a paso, con los datos correctos del mod.")),
                    ""));

    private final Screen parent;
    private final List<Page> pages = new ArrayList<>();
    private int page;
    private Button prev, next, copy;
    private int indexW, cx, cw;

    public GuideScreen(Screen parent) {
        super(Component.literal("Guía de Traductor ES"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        indexW = Math.min(130, width / 3);
        cx = indexW + 14;
        cw = Math.min(width - cx - 12, 380);
        paginate();
        page = Math.min(page, pages.size() - 1);

        int y = 30;
        for (int i = 0; i < SECTIONS.size(); i++) {
            int section = i;
            addRenderableWidget(new IndexButton(0, y, indexW, 16, Component.literal((i + 1) + ". " + SECTIONS.get(i).title()),
                    b -> goToSection(section), () -> pages.get(page).section() == section));
            y += 17;
        }

        int by = height - 26, bw = 90;
        prev = addRenderableWidget(Button.builder(Component.literal("< Anterior"), b -> turn(-1)).bounds(cx, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose()).bounds(cx + cw / 2 - 40, by, 80, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Siguiente >"), b -> turn(1)).bounds(cx + cw - bw, by, bw, 20).build());
        copy = addRenderableWidget(Button.builder(Component.literal("Copiar prompt"), b -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(HelpPrompt.TEXT);
            b.setMessage(Component.literal("¡Copiado!"));
        }).bounds(cx, by - 24, 120, 20).build());
        updateButtons();
    }

    /** Reparte los pasos de cada sección en páginas según el alto de la ventana. */
    private void paginate() {
        pages.clear();
        int areaTop = 44, areaBottom = height - 56;
        for (int s = 0; s < SECTIONS.size(); s++) {
            Section sec = SECTIONS.get(s);
            int introH = lines(sec.intro(), cw) * 10 + 8;
            int tipH = sec.tip().isEmpty() ? 0 : lines(sec.tip(), cw - 12) * 10 + 14;
            List<Step> current = new ArrayList<>();
            int used = introH;
            boolean continued = false;
            for (Step st : sec.steps()) {
                int h = stepHeight(st);
                if (!current.isEmpty() && areaTop + used + h + tipH > areaBottom) {
                    pages.add(new Page(s, continued ? "" : sec.intro(), current, "", continued));
                    current = new ArrayList<>();
                    used = 0;
                    continued = true;
                }
                current.add(st);
                used += h + 4;
            }
            pages.add(new Page(s, continued ? "" : sec.intro(), current, sec.tip(), continued));
        }
    }

    private int lines(String text, int width) {
        return text.isEmpty() ? 0 : font.split(Component.literal(text), width).size();
    }

    private int stepHeight(Step st) {
        return 8 + lines(st.t(), cw - 30) * 10 + lines(st.d(), cw - 30) * 10;
    }

    private void goToSection(int section) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).section() == section) {
                page = i;
                break;
            }
        }
        updateButtons();
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages.size() - 1, page + delta));
        updateButtons();
    }

    private void updateButtons() {
        prev.active = page > 0;
        next.active = page < pages.size() - 1;
        copy.visible = pages.get(page).section() == HELP;
    }

    @Override
    protected void renderPanels(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, indexW, height, 0xE016181C);
        g.fill(indexW, 0, indexW + 1, height, 0xFF000000);
        Page p = pages.get(page);
        int y = 44 + (p.intro().isEmpty() ? 0 : lines(p.intro(), cw) * 10 + 8);
        for (Step st : p.steps()) {
            int h = stepHeight(st);
            UiKit.card(g, cx, y, cw, h);
            y += h + 4;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "Guía", 8, 12, UiKit.GOLD, true);
        Page p = pages.get(page);
        Section sec = SECTIONS.get(p.section());
        g.drawString(font, sec.title() + (p.continued() ? " (cont.)" : ""), cx, 12, UiKit.WHITE, true);
        g.drawString(font, (page + 1) + " / " + pages.size(), cx + cw - font.width((page + 1) + " / " + pages.size()), 12, UiKit.DIM, false);

        int y = 30 + 14;
        if (!p.intro().isEmpty()) y = UiKit.wrapped(g, font, Component.literal(p.intro()), cx, y, cw, UiKit.MUTED) + 7;
        for (Step st : p.steps()) {
            int h = stepHeight(st);
            g.fill(cx + 6, y + 5, cx + 20, y + 19, UiKit.GOLD);
            g.drawCenteredString(font, st.n(), cx + 13, y + 8, 0xFF2A2000);
            int ty = UiKit.wrapped(g, font, Component.literal(st.t()), cx + 26, y + 4, cw - 30, UiKit.WHITE);
            UiKit.wrapped(g, font, Component.literal(st.d()), cx + 26, ty, cw - 30, UiKit.DIM);
            y += h + 4;
        }
        if (!p.tip().isEmpty()) {
            int th = lines(p.tip(), cw - 12) * 10 + 8;
            g.fill(cx, y + 2, cx + cw, y + 2 + th, 0xFF2D5A7A);
            g.fill(cx + 1, y + 3, cx + cw - 1, y + 1 + th, 0xFF16212B);
            UiKit.wrapped(g, font, Component.literal(p.tip()), cx + 6, y + 6, cw - 12, 0xFF9FD3FF);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Entrada del índice: sin relieve; la elegida, en amarillo con una barra dorada a la izquierda. */
    private static final class IndexButton extends Button {
        private final java.util.function.BooleanSupplier selected;

        IndexButton(int x, int y, int w, int h, Component msg, OnPress onPress, java.util.function.BooleanSupplier selected) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
            this.selected = selected;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean on = selected.getAsBoolean();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (on) {
                g.fill(x, y, x + w, y + h, 0xFF2F3238);
                g.fill(x, y, x + 2, y + h, UiKit.GOLD);
            } else if (isHoveredOrFocused()) {
                g.fill(x, y, x + w, y + h, 0xFF24262B);
            }
            Font font = Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(getMessage(), w - 12);
            if (!lines.isEmpty()) g.drawString(font, lines.get(0), x + 8, y + (h - 8) / 2, on ? UiKit.YELLOW : 0xFFC8C8C8, false);
        }
    }
}
