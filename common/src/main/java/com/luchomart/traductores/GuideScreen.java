package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Guía de uso dentro del juego, por páginas (se reparten según el tamaño de la ventana). */
public class GuideScreen extends Screen {
    private record Section(String title, String body) {}

    private record Page(String title, List<FormattedCharSequence> lines) {}

    private static final List<Section> SECTIONS = List.of(
            new Section("¿Qué hace este mod?", """
                    Traduce al §eespañol de España§r los textos de tus mods: nombres de objetos, descripciones, menús y manuales.

                    La traducción la hace una IA que corre en tu propia PC (Ollama), gratis y sin internet.

                    • Si un mod ya trae español de España, se respeta.
                    • Si trae otro español (México, Argentina…), se usa ese.
                    • Lo que falta lo traduce la IA y queda guardado."""),
            new Section("Antes de empezar", """
                    Necesitás tres cosas:

                    §e1.§r Tener §eOllama§r instalado y abierto (ollama.com/download).

                    §e2.§r Haber bajado el modelo. En una terminal:
                    §7ollama pull gemma3:12b§r
                    Si tu placa de video tiene menos de 12 GB, usá §7gemma3:4b§r y escribilo en el campo Modelo.

                    §e3.§r Tener el juego en §eEspañol (España)§r: Opciones → Idioma."""),
            new Section("Cómo se usa", """
                    No hace falta hacer nada: al abrir el juego, el mod traduce solo lo que falta mientras estás en el §emenú principal§r. Arriba a la izquierda ves el progreso y cuánto falta.

                    La primera vez tarda bastante (de 30 minutos a varias horas). Las siguientes, solo lo nuevo.

                    • §eSi entrás a un mundo§r, se pausa y sigue al volver al menú.
                    • §eSi cerrás el juego§r, lo traducido queda guardado.
                    • §eSi termina mientras jugás§r, apretá F3 + T para ver los textos nuevos."""),
            new Section("Este panel", """
                    §eProbar conexión:§r comprueba que la IA responde.
                    §eTraducir ahora / Pausar:§r arranca, pausa o reanuda.
                    §eTraducir al iniciar:§r en "No", solo traduce con el botón.
                    §eTextos por lote:§r si ves errores de "respuesta cortada", bajalo a 10.
                    §eAbrir carpeta:§r configuración, informe y correcciones.

                    También hay comandos en el chat:
                    §7/traductor estado, iniciar, pausar, reanudar, cancelar"""),
            new Section("Corregir traducciones", """
                    La IA a veces se equivoca. Para corregir un texto, abrí la carpeta (botón Abrir carpeta) y editá §ecorrecciones.json§r:

                    §7"mod:clave": "texto corregido"§r

                    La clave la encontrás en la carpeta §ecache§r: abrí el archivo del mod y buscá la traducción con Ctrl + F.

                    Para que una palabra se traduzca siempre igual, agregala en §eglosario.json§r:
                    §7"Pressure Tube": "Tubo de presión"§r

                    Los cambios se aplican la próxima vez que abras el juego."""),
            new Section("Si algo falla", """
                    §e"No se pudo conectar con la IA":§r Ollama no está abierto. Abrilo y tocá Probar conexión.

                    §e"No encuentra el modelo":§r corré §7ollama list§r en una terminal y copiá el nombre exacto en el campo Modelo.

                    §eMuy lento:§r usá gemma3:4b y cerrá videos del navegador.

                    §eEl juego se cierra solo:§r falta memoria. Cerrá programas o usá un modelo más chico.

                    §eTextos en inglés:§r mirá §einforme.txt§r en la carpeta del mod. Se reintentan cada vez que abrís el juego."""));

    private final Screen parent;
    private final List<Page> pages = new ArrayList<>();
    private int page;
    private Button prev, next;

    public GuideScreen(Screen parent) {
        super(Component.literal("Guía de Traductor ES"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        paginate();
        page = Math.min(page, pages.size() - 1);
        int y = height - 28, w = 90;
        prev = addRenderableWidget(Button.builder(Component.literal("< Anterior"), b -> turn(-1))
                .bounds(width / 2 - w - w / 2 - 6, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> onClose())
                .bounds(width / 2 - w / 2, y, w, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Siguiente >"), b -> turn(1))
                .bounds(width / 2 + w / 2 + 6, y, w, 20).build());
        updateButtons();
    }

    private int textWidth() { return Math.min(340, width - 40); }

    /** Reparte cada sección en tantas páginas como hagan falta para el alto de la ventana. */
    private void paginate() {
        pages.clear();
        int lineH = font.lineHeight + 2;
        int perPage = Math.max(4, (height - 40 - 40) / lineH);
        for (Section s : SECTIONS) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String paragraph : s.body().split("\n", -1)) {
                if (paragraph.isBlank()) lines.add(FormattedCharSequence.EMPTY);
                else lines.addAll(font.split(Component.literal(paragraph), textWidth()));
            }
            for (int i = 0; i < lines.size(); i += perPage) {
                String title = i == 0 ? s.title() : s.title() + " (cont.)";
                pages.add(new Page(title, lines.subList(i, Math.min(lines.size(), i + perPage))));
            }
        }
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages.size() - 1, page + delta));
        updateButtons();
    }

    private void updateButtons() {
        prev.active = page > 0;
        next.active = page < pages.size() - 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Platform.renderBackground(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        Page p = pages.get(page);
        g.drawCenteredString(font, Component.literal(p.title()), width / 2, 14, 0xFFD700);
        int x = (width - textWidth()) / 2, y = 36;
        for (FormattedCharSequence line : p.lines()) {
            g.drawString(font, line, x, y, 0xE0E0E0);
            y += font.lineHeight + 2;
        }
        g.drawCenteredString(font, Component.literal((page + 1) + " / " + pages.size()), width / 2, height - 42, 0x808080);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
