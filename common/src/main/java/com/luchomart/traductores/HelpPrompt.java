package com.luchomart.traductores;

/** Prompt para pegar en cualquier IA (ChatGPT, Claude, Gemini…) y que guíe la instalación. Mismo texto que el README. */
public final class HelpPrompt {
    private HelpPrompt() {}

    public static final String TEXT = """
            Quiero instalar el mod de Minecraft "Traductor ES" y necesito que me guíes paso a paso, en español y sin tecnicismos. Antes de empezar, preguntame: mi sistema operativo, mi placa de video y cuánta VRAM tiene, cuánta RAM tengo y qué versión usa mi modpack (Forge 1.20.1 o NeoForge 1.21.1). Dame un paso por vez y esperá a que te confirme antes de seguir.

            Datos del mod (no inventes opciones que no estén acá):
            - Traduce los textos de los mods al español que el jugador tenga elegido en el juego (es_es, es_mx, es_ar, es_cl, es_uy, es_ve, es_ec) usando una IA. Es un mod solo de cliente.
            - Hay dos archivos: traductor_es-forge-1.20.1-….jar (Forge 1.20.1) y traductor_es-neoforge-1.21.1-….jar (NeoForge 1.21.1). Va en la carpeta "mods" del modpack.
            - Lo recomendado es una IA local con Ollama instalado y abierto (ollama.com/download; en Linux desde el gestor de paquetes). El mod se conecta a http://localhost:11434/v1/chat/completions.
            - Sin placa de video potente puede usar un servicio en la nube compatible con OpenAI. El botón "Servicio" del panel trae Gemini, OpenRouter y Groq. El botón "Conseguir" (al lado del campo Clave) abre la página para crear la clave: Gemini en aistudio.google.com/apikey, Groq en console.groq.com/keys, OpenRouter en openrouter.ai/settings/keys. La clave se muestra una sola vez y va en el campo Clave. Los textos de los mods se envían a ese servicio.
            - Modelo recomendado: "ollama pull gemma3:12b" (unos 8 GB, ideal con 12 GB de VRAM o más). Con menos VRAM: "ollama pull gemma3:4b". "ollama list" muestra los modelos instalados.
            - El juego tiene que estar en algún español: Opciones → Idioma.
            - Configuración en el juego: Mods → Traductor ES → Configurar. Tiene tres pestañas. Conexión: Servicio (Ollama, LM Studio, Gemini, OpenRouter, Groq), URL, Clave (oculta; no hace falta con Ollama), Modelo (botón Elegir… con la lista del servicio) y Probar conexión. Traducción: español de destino, al abrir el juego, mientras juego, libros, otras variantes, textos por lote (20) y a la vez (1 local, 2 a 4 en la nube). Herramientas: exportar pack, informe, abrir carpeta y asistente. Botón Guía arriba a la derecha.
            - Traduce solo en el menú principal al abrir el juego y muestra el progreso arriba a la izquierda. La primera vez puede tardar horas. Mientras se juega: con IA en la nube sigue traduciendo; con IA en la PC se pausa para no bajar los FPS (opción "Mientras juego": Auto/Sí/No en la pestaña Traducción). F3 + T recarga los textos.
            - Comandos: /traductor estado, iniciar, pausar, reanudar, cancelar.
            - Archivos en config/traductor_es/: correcciones.json ("mod:clave": "texto"), glosario.json ("Original": "Traducción") e informe.txt (resumen y textos que fallaron).

            Problemas conocidos:
            - "No se pudo conectar con la IA": Ollama no está abierto.
            - "No encuentra el modelo": el campo Modelo no coincide con "ollama list".
            - "Respuesta cortada": bajar Textos por lote a 10, o subir el contexto de Ollama con la variable de entorno OLLAMA_CONTEXT_LENGTH=8192.
            - El juego se cierra por falta de memoria mientras traduce: Ollama puede usar varios GB de RAM. Se soluciona con las variables de entorno LLAMA_ARG_CACHE_RAM=0 y LLAMA_ARG_CTX_CHECKPOINTS=2 en el servicio de Ollama (después hay que reiniciarlo), cerrando otros programas o usando gemma3:4b.
            """;
}
