package com.luchomart.traductores;

/** Prompt para pegar en cualquier IA (ChatGPT, Claude, Gemini…) y que guíe la instalación. Mismo texto que el README. */
public final class HelpPrompt {
    private HelpPrompt() {}

    public static final String TEXT = """
            Quiero instalar el mod de Minecraft "Traductor ES" y necesito que me guíes paso a paso, en español y sin tecnicismos. Antes de empezar, preguntame: mi sistema operativo, mi placa de video y cuánta VRAM tiene, cuánta RAM tengo y qué loader y versión usa mi modpack (Forge, NeoForge o Fabric; 1.20.1 o 1.21.1). Con eso, recomendame IA en mi PC o en la nube. Dame un paso por vez y esperá a que te confirme antes de seguir.

            Datos del mod (no inventes opciones que no estén acá):
            - Traduce los textos de los mods al español que el jugador tenga elegido en el juego (España, México, Argentina, Chile, Uruguay, Venezuela o Ecuador) usando una IA. Respeta las traducciones oficiales. Es un mod solo de cliente.
            - Hay un archivo por loader y versión: traductor_es-forge-1.20.1, traductor_es-neoforge-1.21.1, traductor_es-fabric-1.20.1 y traductor_es-fabric-1.21.1 (….jar). Va en la carpeta "mods" del modpack. En Fabric también hace falta Fabric API; Mod Menu es opcional y agrega el botón para abrir el panel.
            - El juego tiene que estar en algún español: Opciones → Idioma.

            Dos formas de usarlo:
            - IA en la PC (gratis, sin internet): Ollama instalado y abierto (ollama.com/download; en Linux desde el gestor de paquetes) y un modelo bajado con "ollama pull translategemma:12b" (un Gemma 3 entrenado para traducir) (unos 8 GB, ideal con 12 GB de VRAM o más) o "ollama pull translategemma:4b" con menos VRAM. "ollama list" muestra los modelos instalados.
            - IA en la nube (sin placa de video potente): Gemini, Groq u OpenRouter, con una clave (API key) gratis. Se crean en aistudio.google.com/apikey (Gemini), console.groq.com/keys (Groq) y openrouter.ai/settings/keys (OpenRouter). La clave se muestra una sola vez. Los textos de los mods se envían a ese servicio.

            - Tiempos aproximados para 1.000 textos (un modpack grande tiene unos 10.000): OpenRouter de pago ~5 min (cobra por uso, hay que cargar saldo), Groq ~10 min (gratis), Gemini ~17 min (gratis, irregular), OpenRouter con modelos ":free" ~20 min pero como máximo unos 1.000 textos por día (gratis), en la PC ~17 min (gratis, depende de la placa de video). El botón «¿Cuál conviene?» (en el asistente y en el panel) calcula el tiempo para su modpack y elige el servicio con un clic.

            Primer uso:
            - Al abrir el juego aparece un asistente de 3 pasos: elegir "En mi PC" o "En la nube", una comprobación automática (que la IA responda, el modelo, el idioma y cuántos textos faltan, con botones para arreglar lo que falte: Descargar, Conseguir, Copiar comando, Cambiar idioma) y "Empezar a traducir". También se abre desde el panel (Herramientas → Asistente) y vuelve a aparecer si una traducción falla por la configuración.

            Panel (Mods → Traductor ES → Configurar; en Fabric hace falta Mod Menu para ese botón; en cualquier versión también con /traductor panel en el chat), con un botón Guía arriba a la derecha y tres pestañas:
            - Conexión: Servicio (flechas para elegir Ollama, LM Studio, Gemini, Groq u OpenRouter), URL (se completa sola), Clave (se ve oculta; cada servicio guarda la suya; no hace falta con Ollama), botón "Conseguir"/"Descargar", Modelo (vacío = el mod elige el recomendado del servicio; botón "Elegir…" con la lista agrupada por tipo —rápidos, equilibrados, mejor calidad, de prueba— y marcados los agotados, retirados o que no sirven para español) y "Probar conexión" (también mide la velocidad y muestra cuánto cupo le queda a la clave en Groq y OpenRouter). Si se elige otro servicio mientras traduce, el botón "Usar este servicio" sigue con ese sin perder lo hecho.
            - Traducción: español de destino (Automático = el idioma del juego), "Al abrir el juego", "Mientras juego" (Auto: sigue si la IA está en la nube y se pausa si está en la PC para no bajar los FPS; Sí; No), libros y misiones (manuales de Patchouli y misiones de FTB Quests; las misiones se ven al volver a entrar al mundo y solo en mundos de un jugador), otras variantes del español, "Claves compartidas" (las claves y lo aprendido de cada modelo valen para todos los modpacks; se guardan en la carpeta .traductor_es del usuario), "Cambio automático" (si se acaba el cupo o va muy lenta, pasa sola a otro modelo gratis o a otro servicio gratis con clave; nunca a uno de pago ni de la PC a la nube), textos por lote (20) y peticiones a la vez (1 en la PC, 2 a 4 en la nube).
            - Herramientas: editor de correcciones, "Rehacer…" (vuelve a traducir todo lo que hizo un modelo que tradujo mal; lo anterior queda en respaldos/), exportar el pack (un .zip para amigos con el mismo modpack, que lo usan sin IA), ver el informe, abrir la carpeta y el asistente.
            - Mientras traduce aparece un aviso arriba a la derecha del menú principal con el progreso y el tiempo restante. El color de la barra indica la velocidad: celeste muy rápida, verde rápida, amarillo normal, naranja lenta, rojo muy lenta. La primera vez puede tardar horas; después solo traduce lo nuevo. F3 + T recarga los textos.
            - Original con Alt: dejando apretada la tecla Alt sobre un objeto traducido por el mod, su nombre y descripción se ven en inglés (se desactiva en Traducción → Original con Alt).
            - Corregir una traducción: Herramientas → Abrir editor (o /traductor corregir en el chat): buscar el texto en inglés o español, corregirlo y Guardar. Se aplica enseguida (las misiones, al volver a entrar al mundo).
            - Comandos en el chat: /traductor estado, panel, corregir, iniciar, pausar, reanudar, cancelar.
            - Archivos en config/traductor_es/: correcciones.json ("mod:clave": "texto"), glosario.json ("Original": "Traducción") e informe.txt (resumen y textos que fallaron).

            Problemas conocidos:
            - "No hay ninguna IA respondiendo en esa dirección": Ollama no está abierto (o LM Studio, si se eligió ese).
            - "La clave falta o no es válida": crear una clave nueva con "Conseguir" y pegarla en Clave.
            - "No tiene saldo" (OpenRouter): elegir "OpenRouter (gratis)" en «¿Cuál conviene?», un modelo terminado en ":free" con "Elegir…", o cambiar de servicio.
            - "No existe ese modelo": dejar Modelo vacío o usar "Elegir…"; el mod también cambia solo si el servicio retiró el modelo.
            - "El servicio está saturado" o pide bajar el ritmo: el mod espera y reintenta solo.
            - "Respuesta cortada": bajar Textos por lote a 10, o subir el contexto de Ollama con la variable de entorno OLLAMA_CONTEXT_LENGTH=8192.
            - Va muy lento: en la nube el mod cambia solo de modelo o servicio (Cambio automático); en la PC, usar translategemma:4b y cerrar videos del navegador.
- "Se acabó el cupo" o un modelo marcado AGOTADO: es el límite gratis del servicio; el mod sigue con otro modelo o servicio, o se puede esperar a que se renueve.
            - El juego se cierra por falta de memoria mientras traduce: Ollama puede usar varios GB de RAM. Se soluciona con las variables de entorno LLAMA_ARG_CACHE_RAM=0 y LLAMA_ARG_CTX_CHECKPOINTS=2 en el servicio de Ollama (después hay que reiniciarlo), cerrando otros programas o usando translategemma:4b.
            """;
}
