# Traductor ES

Mod de Minecraft que **traduce al español todos los textos de tus mods**: nombres de objetos, descripciones, menús, manuales del juego y misiones. Traduce al español que tengas elegido en el juego: **España, México, Argentina, Chile, Uruguay, Venezuela o Ecuador**, cada uno con sus palabras ("cubo" o "balde", "tienes" o "tenés").

Usa una inteligencia artificial que corre **en tu propia PC** (gratis, sin cuentas y sin mandar nada a internet) o, si tu PC no da, un **servicio en la nube**.

- Si un mod ya trae tu español, lo respeta y no lo toca.
- Si trae otro español, lo usa en vez de traducir.
- Lo que falta lo traduce la IA, y lo guarda para no volver a traducirlo.

## Qué necesitás

| | |
|---|---|
| **Minecraft** | **1.20.1** (Forge o Fabric) o **1.21.1** (NeoForge o Fabric) |
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
ollama pull translategemma:12b
```

Son unos 8 GB, así que tarda un rato. **TranslateGemma** es un Gemma 3 que Google entrenó para traducir: en una prueba con 300 textos reales de un modpack tradujo bien el 98 % (contra 96 % de `gemma3:12b`) y dejó la mitad de textos en inglés, a la misma velocidad.

> **¿Tu placa tiene menos de 12 GB de VRAM?** Usá `ollama pull translategemma:4b`. Traduce un poco peor, pero es mucho más liviano y rápido. Después, en la configuración del mod, cambiá el modelo a `translategemma:4b`.
>
> **¿Ya tenías `gemma3:12b`?** Sigue andando. Si bajás `translategemma:12b`, el mod lo elige solo cuando dejás el modelo vacío (o elegilo con **Elegir…**).

### 3. Instalar el mod

Bajá el mod de la [página de versiones en GitHub](https://github.com/luchomart/traductor-es/releases/latest) y copiá el archivo en la carpeta `mods` de tu modpack. Usá el que corresponda a tu versión:

- `traductor_es-forge-1.20.1-….jar` para modpacks de **Forge 1.20.1**
- `traductor_es-neoforge-1.21.1-….jar` para modpacks de **NeoForge 1.21.1**
- `traductor_es-fabric-1.20.1-….jar` para modpacks de **Fabric 1.20.1**
- `traductor_es-fabric-1.21.1-….jar` para modpacks de **Fabric 1.21.1**

En Fabric también necesitás **Fabric API** (casi todos los modpacks ya lo traen). Con **Mod Menu** aparece el botón para abrir el panel desde la lista de mods; sin él, escribí `/traductor panel` en el chat dentro de un mundo.

### 4. Poner el juego en español

Abrí el juego → **Opciones → Idioma** y elegí tu español (España, México, Argentina…). El mod traduce a ese.

### 5. El asistente

La primera vez que abras el juego aparece un **asistente de 3 pasos**:

1. Elegís dónde trabaja la IA: **en tu PC** (Ollama) o **en la nube**.
2. Comprueba solo que todo esté listo: que la IA responda, el modelo, el idioma y cuántos textos faltan. Si algo falla, te da el botón para arreglarlo (Descargar, Conseguir clave, Copiar comando, Cambiar idioma).
3. Te muestra un resumen y tocás **Empezar a traducir**.

Desde ahí, cada vez que abras el juego el mod traduce solo lo que falta. Mientras trabaja vas a ver un **aviso arriba a la derecha** del menú principal con el progreso y el tiempo restante. Al terminar, los textos se actualizan solos.

> La primera vez tarda bastante (de 30 minutos a varias horas, según la cantidad de mods y tu placa de video o el servicio). Las siguientes veces solo traduce lo nuevo y tarda segundos.

## Mientras traduce

- **Podés jugar.** Con la opción **Mientras juego** en *Automático*: si la IA está en la nube, sigue traduciendo; si está en tu PC, se pausa al entrar a un mundo (para no bajar los FPS) y sigue sola al volver al menú. Podés forzar *Sí* o *No* en la pestaña Traducción.
- **Podés cerrar el juego.** Lo que ya tradujo queda guardado y la próxima vez sigue desde ahí.
- **Si estás jugando cuando termina**, apretá **F3 + T** para ver los textos nuevos.

## Panel de configuración

En el menú principal: **Mods → Traductor ES → Configurar** (en Fabric hace falta Mod Menu), o dentro de un mundo con `/traductor panel`. Arriba tiene un indicador de conexión y el botón **Guía**, y abajo el progreso con **Traducir ahora / Pausar / Cancelar**. Pasá el mouse sobre cualquier opción para ver qué hace.

**Pestaña Conexión**

| Opción | Para qué sirve |
|---|---|
| Servicio | Elegí con las flechas: Ollama, LM Studio (en tu PC), Gemini, Groq u OpenRouter (en la nube). Completa la URL solo. |
| Clave (API key) | Solo para la nube. Se ve oculta y cada servicio guarda la suya. Con Ollama no hace falta. |
| Conseguir / Descargar | Abre la página para crear la clave (nube) o para descargar el programa (PC). |
| Modelo | Vacío = el mod elige el recomendado del servicio. **Elegir…** muestra la lista con buscador. |
| Probar conexión | Comprueba que la IA responde y elige el modelo si hace falta. |

**Pestaña Traducción**

| Opción | Para qué sirve |
|---|---|
| Español de destino | *Automático* usa el idioma del juego; también podés fijar uno. |
| Al abrir el juego | Traducir solo lo nuevo cada vez que llegás al menú. |
| Mientras juego | *Auto*: con IA en la nube sigue; con IA en tu PC se pausa para no bajar los FPS. También *Sí* o *No*. |
| Libros y misiones | Los manuales y guías del juego (Patchouli, Tinkers' Construct, GuideME —AE2 y sus agregados, Powah—, el manual de Immersive Engineering y el diccionario de Alex's Mobs) y las misiones de FTB Quests. Las misiones se ven al volver a entrar al mundo, y solo en mundos de un jugador (en un servidor las misiones vienen del servidor). |
| Original con Alt | Con **Alt** apretado sobre un objeto traducido por el mod, su nombre y descripción se ven en inglés. Sirve para buscar guías o recetas en internet. |
| Otras variantes | Si un mod no trae tu español pero sí otro, usarlo en vez de traducir. |
| Textos por lote / A la vez | Cuántos textos van juntos (si ves "respuesta cortada", bajalo a 10) y cuántas peticiones al mismo tiempo (1 en tu PC, 2 a 4 en la nube). |

**Pestaña Herramientas**: exportar el pack (`resourcepacks/TraductorES-traducciones.zip`, para amigos con el mismo modpack: lo activan como cualquier paquete de recursos, sin IA ni este mod), ver el informe, abrir la carpeta y volver a abrir el asistente.

También podés usar comandos en el chat: `/traductor estado`, `/traductor panel`, `/traductor corregir`, `/traductor iniciar`, `/traductor pausar`, `/traductor reanudar` y `/traductor cancelar`.

## IA en la nube

Si tu PC no tiene una placa de video potente, el mod puede usar un servicio en internet compatible con OpenAI. Varios tienen planes gratis con límites.

1. En el panel, tocá **Servicio** hasta elegir uno: **Gemini**, **OpenRouter** o **Groq**.
2. Tocá **Conseguir**, al lado de Clave: se abre la página para crear tu clave (ver abajo). Pegala en **Clave**.
3. Tocá **Probar conexión**: te muestra los modelos disponibles. Si el sugerido no existe, copiá uno de la lista en **Modelo**.
4. Subí **A la vez** a 2–4 para que vaya más rápido.

Si el servicio pide bajar el ritmo, el mod espera lo que haga falta y sigue solo.

### ¿Cuál conviene?

Tiempos aproximados para traducir (un modpack grande tiene unos 10.000 textos sin español). Salen de traducciones reales y varían según la hora y tu PC. El botón **¿Cuál conviene?** del asistente y del panel calcula el tiempo para tu modpack y te deja elegir con un clic.

| Opción | Costo | 1.000 textos | 10.000 textos | Qué tener en cuenta |
|---|---|---|---|---|
| OpenRouter (de pago) | De pago | ~5 min | ~50 min | El más rápido. Cobra por uso: necesitás cargar saldo. |
| Groq | Gratis | ~10 min | ~1 h 40 min | Rápido; a ratos frena unos segundos por el límite del plan gratis. |
| Google Gemini | Gratis | ~17 min | ~2 h 50 min | Velocidad irregular: Google limita el plan gratis según la demanda. |
| OpenRouter (gratis) | Gratis | ~20 min | ~10 días | Modelos que terminan en `:free`; unos 1.000 textos por día. |
| En tu PC (Ollama) | Gratis | ~17 min | ~2 h 50 min | Sin internet ni cuentas. Depende de tu placa de video. |

Las próximas veces solo se traduce lo nuevo, así que tarda segundos.

### Velocidad, cupos y cambio automático

- **«Probar conexión»** también mide la velocidad con un minitest y muestra cuánto cupo le queda a tu clave (Groq y OpenRouter).
- **El color de la barra** dice cómo va: 🔵 muy rápida (150+ textos/min) · 🟢 rápida (80-150) · 🟡 normal (40-80) · 🟠 lenta (15-40) · 🔴 muy lenta (menos de 15).
- **Cambio automático** (Traducción → Cambio automático, encendido por defecto): si se acaba el cupo, va muy lenta 3 minutos o va lenta y faltan más de 2 horas, pasa sola a otro modelo gratis del mismo servicio o a otro servicio gratis del que tengas clave. **Nunca** a uno de pago ni de tu PC a la nube. Te avisa con un mensaje.
- **«Elegir…»** agrupa los modelos por tipo (rápidos, equilibrados, mejor calidad, de prueba) y marca los **agotados**, **retirados** o que **no sirven para español**. El mod lo aprende de los errores del servicio.
- **«Usar este servicio»**: si elegís otro servicio mientras traduce, sigue con ese sin perder lo hecho.
- **Claves compartidas**: las claves y lo aprendido de cada modelo valen para todos tus modpacks (se guardan en `~/.traductor_es/`).
- **Herramientas → Rehacer…**: si un modelo tradujo mal, vuelve a traducir todo lo que hizo (lo anterior queda en `respaldos/`).

### Conseguir una clave (API key)

Una clave es como una contraseña que le dice al servicio que sos vos. Los tres tienen un plan gratis con límites (que cambian seguido; los actuales están en la página de cada uno).

| Servicio | Dónde | Cómo |
|---|---|---|
| **Google Gemini** | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | Entrá con tu cuenta de Google y tocá **Create API key**. No pide tarjeta. |
| **Groq** | [console.groq.com/keys](https://console.groq.com/keys) | Creá una cuenta y tocá **Create API Key**. No pide tarjeta. |
| **OpenRouter** | [openrouter.ai/settings/keys](https://openrouter.ai/settings/keys) | Creá una cuenta y tocá **Create Key**. Los modelos gratis llevan `:free` al final del nombre. |

> **Importante:** la clave se muestra **una sola vez**. Copiala enseguida y no se la pases a nadie. Algunos planes gratis pueden usar lo que les mandás (acá, los textos de los mods) para mejorar sus modelos.

> **Ojo:** con un servicio en la nube, los textos de tus mods se envían a ese servicio, y la clave queda guardada en `~/.traductor_es/claves.json`, compartida por todos tus modpacks y fuera de la carpeta del modpack (exportar `config` no la incluye). Con «Claves compartidas» desactivado, cada modpack la guarda en `config/traductor_es/config.json`. No compartas esos archivos.

## Corregir una traducción

La IA a veces se equivoca. Para arreglar un texto, abrí el editor: **Herramientas → Abrir editor**, o escribí `/traductor corregir` en el chat. También podés buscar directo: `/traductor corregir lingote`.

1. Buscá el texto en inglés o en español. Aparecen los objetos, los libros y las misiones que tradujo la IA.
2. Tocá el texto, corregilo y tocá **Guardar**.
3. Se aplica enseguida. Las misiones se ven al volver a entrar al mundo.

Si tu corrección rompe un color o un marcador (`%s`, `{0}`, `&a`…), el editor te avisa antes de guardar. **Volver a la de la IA** deshace la corrección.

Las correcciones se guardan en `correcciones.json`, con el formato `"mod:clave": "texto"`, tienen prioridad sobre todo (incluso sobre el español oficial) y no se pierden al volver a traducir. También podés editar ese archivo a mano.

Si querés que una palabra se traduzca **siempre igual** en todos los mods, agregala en `glosario.json`:

```json
{
  "Pressure Tube": "Tubo de presión"
}
```

## Problemas comunes

**"No se pudo conectar con la IA".** Ollama no está abierto. En Windows buscá "Ollama" en el menú inicio y abrilo; en Linux, `sudo systemctl start ollama`. Después usá **Probar conexión**.

**"El servidor no encuentra el modelo".** No bajaste el modelo o el nombre no coincide. Corré `ollama list` en una terminal y copiá el nombre exacto en el campo **Modelo**.

**Va muy lento.** Es normal la primera vez. Si es demasiado, usá `translategemma:4b`. Revisá también que no tengas el navegador con videos abiertos: compiten por la placa de video.

**El juego se cierra solo mientras traduce.** Probablemente falta memoria RAM. Cerrá otros programas, usá un modelo más chico o bajá **Textos por lote**.

**Algunos textos siguen en inglés.** Abrí `informe.txt` en la carpeta del mod: ahí están los textos que la IA no pudo traducir bien y por qué. Se reintentan al abrir el juego; si fallan 3 veces con el mismo modelo, se dejan de mandar para no perder tiempo (vuelven a intentarse si cambiás de modelo o de servicio). Suelen ser textos largos con muchos códigos de formato: con un servicio en la nube salen mejor. También podés traducirlos a mano con el editor de correcciones.

## ¿Necesitás ayuda para instalarlo?

Copiá este texto y pegalo en la IA que uses (ChatGPT, Claude, Gemini…). Te va a guiar paso a paso, con los datos correctos del mod. También podés copiarlo desde el juego: **Configurar → Guía → ¿Necesitás ayuda? → Copiar prompt**.

```text
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
- Traducción: español de destino (Automático = el idioma del juego), "Al abrir el juego", "Mientras juego" (Auto: sigue si la IA está en la nube y se pausa si está en la PC para no bajar los FPS; Sí; No), libros y misiones (manuales y guías: Patchouli, Tinkers' Construct, GuideME —AE2 y sus agregados, Powah—, el manual de Immersive Engineering y el diccionario de Alex's Mobs; y misiones de FTB Quests; las misiones se ven al volver a entrar al mundo y solo en mundos de un jugador), otras variantes del español, "Claves compartidas" (las claves y lo aprendido de cada modelo valen para todos los modpacks; se guardan en la carpeta .traductor_es del usuario), "Cambio automático" (si se acaba el cupo o va muy lenta, pasa sola a otro modelo gratis o a otro servicio gratis con clave; nunca a uno de pago ni de la PC a la nube), textos por lote (20) y peticiones a la vez (1 en la PC, 2 a 4 en la nube).
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

- `common/`: lógica compartida (motor de traducción, validación, glosario, caché, libros y guías (Patchouli, Mantle, Citadel, IE, GuideME), misiones de FTB Quests, interfaz).
- `neoforge-1.21.1/`, `forge-1.20.1/`, `fabric-1.21.1/` y `fabric-1.20.1/`: solo lo que cambia entre plataformas (`Platform`, registro del pack y eventos). En Fabric el pack se suma con un mixin (`PackRepositoryMixin`).

Los jars quedan en `*/build/libs/`.

Licencia MIT · por luchomart
