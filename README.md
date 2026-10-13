# Traductor ES

Mod de Minecraft que **traduce al español todos los textos de tus mods**: nombres de objetos, descripciones, menús y manuales del juego. Traduce al español que tengas elegido en el juego: **España, México, Argentina, Chile, Uruguay, Venezuela o Ecuador**, cada uno con sus palabras ("cubo" o "balde", "tienes" o "tenés").

Usa una inteligencia artificial que corre **en tu propia PC** (gratis, sin cuentas y sin mandar nada a internet) o, si tu PC no da, un **servicio en la nube**.

- Si un mod ya trae tu español, lo respeta y no lo toca.
- Si trae otro español, lo usa en vez de traducir.
- Lo que falta lo traduce la IA, y lo guarda para no volver a traducirlo.

## Qué necesitás

| | |
|---|---|
| **Minecraft** | Forge **1.20.1** o NeoForge **1.21.1** |
| **Placa de video** | Recomendado: 12 GB de VRAM o más. Con menos también funciona, usando un modelo más chico (ver abajo). |
| **Espacio** | Unos 8 GB libres para el modelo de IA |
| **Ollama** | El programa que hace correr la IA. Se instala una sola vez. (Si no tenés placa de video, mirá [IA en la nube](#ia-en-la-nube).) |

## Instalación paso a paso

### 1. Instalar Ollama

- **Windows y Mac:** bajalo de [ollama.com/download](https://ollama.com/download) e instalalo como cualquier programa. Queda funcionando solo en segundo plano.
- **Linux:** instalalo desde tu gestor de paquetes (en Arch: `sudo pacman -S ollama-vulkan` y después `sudo systemctl enable --now ollama`).

### 2. Bajar el modelo de traducción

Abrí una terminal (en Windows: tecla Windows → escribí `cmd` → Enter) y escribí:

```
ollama pull gemma3:12b
```

Son unos 8 GB, así que tarda un rato.

> **¿Tu placa tiene menos de 12 GB de VRAM?** Usá `ollama pull gemma3:4b`. Traduce un poco peor, pero es mucho más liviano y rápido. Después, en la configuración del mod, cambiá el modelo a `gemma3:4b`.

### 3. Instalar el mod

Copiá el archivo del mod en la carpeta `mods` de tu modpack. Usá el que corresponda a tu versión:

- `traductor_es-forge-1.20.1-….jar` para modpacks de **Forge 1.20.1**
- `traductor_es-neoforge-1.21.1-….jar` para modpacks de **NeoForge 1.21.1**

### 4. Poner el juego en español

Abrí el juego → **Opciones → Idioma** y elegí tu español (España, México, Argentina…). El mod traduce a ese.

### 5. ¡Listo!

Cada vez que abras el juego, el mod revisa si hay textos sin traducir y los traduce solo mientras estás en el **menú principal**. Arriba a la izquierda vas a ver el progreso y el tiempo que falta. Al terminar, los textos se actualizan solos.

> La primera vez tarda bastante (de 30 minutos a varias horas, según la cantidad de mods y tu placa de video). Las siguientes veces solo traduce lo nuevo y tarda segundos.

## Mientras traduce

- **Podés jugar.** Con la opción **Mientras juego** en *Automático*: si la IA está en la nube, sigue traduciendo; si está en tu PC, se pausa al entrar a un mundo (para no bajar los FPS) y sigue sola al volver al menú. Podés forzar *Sí* o *No* en la pestaña Traducción.
- **Podés cerrar el juego.** Lo que ya tradujo queda guardado y la próxima vez sigue desde ahí.
- **Si estás jugando cuando termina**, apretá **F3 + T** para ver los textos nuevos.

## Panel de configuración

En el menú principal: **Mods → Traductor ES → Configurar**.

| Opción | Para qué sirve |
|---|---|
| Servicio | Elegí Ollama, LM Studio o un servicio en la nube (Gemini, OpenRouter, Groq). Completa la URL y el modelo. |
| URL de la API | Dónde está la IA. Con Ollama no hace falta tocarla. |
| Clave (API key) | Solo para servicios en la nube. |
| Modelo | El modelo de IA. Cambialo si bajaste otro (por ejemplo `gemma3:4b`). |
| Textos por lote | Cuántos textos manda juntos. Si ves errores de "respuesta cortada", bajalo a 10. |
| A la vez | Cuántas peticiones manda al mismo tiempo. 1 para una IA en tu PC; 2 a 4 en la nube. |
| Traducir al iniciar | Si está en "No", solo traduce cuando apretás "Traducir ahora". |
| Probar conexión | Comprueba que la IA responde y muestra los modelos disponibles. |
| Traducir ahora / Pausar / Cancelar | Arranca, pausa, reanuda o corta la traducción. |
| Exportar pack | Crea `resourcepacks/TraductorES-traducciones.zip` para pasárselo a amigos con el mismo modpack: lo activan como cualquier paquete de recursos, sin IA ni este mod. |
| Abrir carpeta | Abre la carpeta del mod (configuración, informe y correcciones). |
| Guía | Esta guía, dentro del juego. |

También podés usar comandos en el chat: `/traductor estado`, `/traductor iniciar`, `/traductor pausar`, `/traductor reanudar` y `/traductor cancelar`.

## IA en la nube

Si tu PC no tiene una placa de video potente, el mod puede usar un servicio en internet compatible con OpenAI. Varios tienen planes gratis con límites.

1. En el panel, tocá **Servicio** hasta elegir uno: **Gemini**, **OpenRouter** o **Groq**.
2. Tocá **Conseguir**, al lado de Clave: se abre la página para crear tu clave (ver abajo). Pegala en **Clave**.
3. Tocá **Probar conexión**: te muestra los modelos disponibles. Si el sugerido no existe, copiá uno de la lista en **Modelo**.
4. Subí **A la vez** a 2–4 para que vaya más rápido.

Si el servicio pide bajar el ritmo, el mod espera lo que haga falta y sigue solo.

### Conseguir una clave (API key)

Una clave es como una contraseña que le dice al servicio que sos vos. Los tres tienen un plan gratis con límites (que cambian seguido; los actuales están en la página de cada uno).

| Servicio | Dónde | Cómo |
|---|---|---|
| **Google Gemini** | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | Entrá con tu cuenta de Google y tocá **Create API key**. No pide tarjeta. |
| **Groq** | [console.groq.com/keys](https://console.groq.com/keys) | Creá una cuenta y tocá **Create API Key**. No pide tarjeta. |
| **OpenRouter** | [openrouter.ai/settings/keys](https://openrouter.ai/settings/keys) | Creá una cuenta y tocá **Create Key**. Los modelos gratis llevan `:free` al final del nombre. |

> **Importante:** la clave se muestra **una sola vez**. Copiala enseguida y no se la pases a nadie. Algunos planes gratis pueden usar lo que les mandás (acá, los textos de los mods) para mejorar sus modelos.

> **Ojo:** con un servicio en la nube, los textos de tus mods se envían a ese servicio, y la clave queda guardada en `config/traductor_es/config.json`. No compartas ese archivo.

## Corregir una traducción

La IA a veces se equivoca. Para arreglar un texto a mano, abrí la carpeta del mod (botón **Abrir carpeta**) y editá `correcciones.json`:

```json
{
  "pneumaticcraft:block.pneumaticcraft.solar_compressor": "Compresor solar"
}
```

El formato es `"mod:clave": "texto"`. Para encontrar la clave, abrí la carpeta `cache`, entrá a la carpeta de tu idioma (por ejemplo `es_es`), abrí el archivo del mod (por ejemplo `pneumaticcraft.json`) y buscá (Ctrl + F) la traducción que querés cambiar. Cada texto aparece así, y la clave es lo que encabeza el bloque:

```json
"block.pneumaticcraft.solar_compressor": {
  "o": "Solar Compressor",
  "t": "Compresor Solar"
}
```

`o` es el original y `t` la traducción. Las correcciones tienen prioridad sobre todo y se aplican la próxima vez que abras el juego.

Si querés que una palabra se traduzca **siempre igual** en todos los mods, agregala en `glosario.json`:

```json
{
  "Pressure Tube": "Tubo de presión"
}
```

## Problemas comunes

**"No se pudo conectar con la IA".** Ollama no está abierto. En Windows buscá "Ollama" en el menú inicio y abrilo; en Linux, `sudo systemctl start ollama`. Después usá **Probar conexión**.

**"El servidor no encuentra el modelo".** No bajaste el modelo o el nombre no coincide. Corré `ollama list` en una terminal y copiá el nombre exacto en el campo **Modelo**.

**Va muy lento.** Es normal la primera vez. Si es demasiado, usá `gemma3:4b`. Revisá también que no tengas el navegador con videos abiertos: compiten por la placa de video.

**El juego se cierra solo mientras traduce.** Probablemente falta memoria RAM. Cerrá otros programas, usá un modelo más chico o bajá **Textos por lote**.

**Algunos textos siguen en inglés.** Abrí `informe.txt` en la carpeta del mod: ahí están los textos que la IA no pudo traducir bien y por qué. Esos se vuelven a intentar cada vez que abrís el juego, y también podés traducirlos a mano con `correcciones.json`.

## ¿Necesitás ayuda para instalarlo?

Copiá este texto y pegalo en la IA que uses (ChatGPT, Claude, Gemini…). Te va a guiar paso a paso, con los datos correctos del mod. También podés copiarlo desde el juego: **Configurar → Guía → ¿Necesitás ayuda? → Copiar prompt**.

```text
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
```

## Archivos del mod

Todo está en `config/traductor_es/` dentro de la carpeta del modpack:

| Archivo | Qué es |
|---|---|
| `config.json` | La configuración del panel |
| `correcciones.json` | Tus correcciones a mano |
| `glosario.json` | Términos que siempre se traducen igual |
| `informe.txt` | Resumen de la última traducción y textos que fallaron |
| `cache/` | Todas las traducciones hechas, una carpeta por idioma (si la borrás, se traduce todo de nuevo) |
| `pack/` | El paquete de recursos que usa el juego (se genera solo) |

---

## Para desarrolladores

El proyecto compila las dos versiones a la vez:

```
./gradlew build
```

- `common/`: lógica compartida (motor de traducción, validación, glosario, caché, libros de Patchouli, interfaz).
- `neoforge-1.21.1/` y `forge-1.20.1/`: solo lo que cambia entre plataformas (`Platform`, registro del pack y eventos).

Los jars quedan en `*/build/libs/`.

Licencia MIT · por luchomart
