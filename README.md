# Traductor ES

Mod de Minecraft que **traduce al español de España todos los textos de tus mods**: nombres de objetos, descripciones, menús y manuales del juego. Usa una inteligencia artificial que corre **en tu propia PC**: gratis, sin cuentas y sin mandar nada a internet.

- Si un mod ya trae español de España, lo respeta y no lo toca.
- Si trae otro español (México, Argentina…), lo usa en vez de traducir.
- Lo que falta lo traduce la IA, y lo guarda para no volver a traducirlo.

## Qué necesitás

| | |
|---|---|
| **Minecraft** | Forge **1.20.1** o NeoForge **1.21.1** |
| **Placa de video** | Recomendado: 12 GB de VRAM o más. Con menos también funciona, usando un modelo más chico (ver abajo). |
| **Espacio** | Unos 8 GB libres para el modelo de IA |
| **Ollama** | El programa que hace correr la IA. Se instala una sola vez. |

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

Abrí el juego → **Opciones → Idioma → Español (España)**.

### 5. ¡Listo!

Cada vez que abras el juego, el mod revisa si hay textos sin traducir y los traduce solo mientras estás en el **menú principal**. Arriba a la izquierda vas a ver el progreso y el tiempo que falta. Al terminar, los textos se actualizan solos.

> La primera vez tarda bastante (de 30 minutos a varias horas, según la cantidad de mods y tu placa de video). Las siguientes veces solo traduce lo nuevo y tarda segundos.

## Mientras traduce

- **Podés jugar.** Al entrar a un mundo la traducción se pausa para no ponerle el juego lento, y sigue sola cuando volvés al menú.
- **Podés cerrar el juego.** Lo que ya tradujo queda guardado y la próxima vez sigue desde ahí.
- **Si estás jugando cuando termina**, apretá **F3 + T** para ver los textos nuevos.

## Panel de configuración

En el menú principal: **Mods → Traductor ES → Configurar**.

| Opción | Para qué sirve |
|---|---|
| URL de la API | Dónde está la IA. Con Ollama no hace falta tocarla. |
| Modelo | El modelo de IA. Cambialo si bajaste otro (por ejemplo `gemma3:4b`). |
| Textos por lote | Cuántos textos manda juntos. Si ves errores de "respuesta cortada", bajalo a 10. |
| Traducir al iniciar | Si está en "No", solo traduce cuando apretás "Traducir ahora". |
| Probar conexión | Comprueba que la IA responde. |
| Traducir ahora / Pausar | Arranca, pausa o reanuda la traducción. |
| Abrir carpeta | Abre la carpeta del mod (configuración, informe y correcciones). |
| Guía | Esta guía, dentro del juego. |

También podés usar comandos en el chat: `/traductor estado`, `/traductor iniciar`, `/traductor pausar`, `/traductor reanudar` y `/traductor cancelar`.

## Corregir una traducción

La IA a veces se equivoca. Para arreglar un texto a mano, abrí la carpeta del mod (botón **Abrir carpeta**) y editá `correcciones.json`:

```json
{
  "pneumaticcraft:block.pneumaticcraft.solar_compressor": "Compresor solar"
}
```

El formato es `"mod:clave": "texto"`. Para encontrar la clave, abrí la carpeta `cache`, abrí el archivo del mod (por ejemplo `pneumaticcraft.json`) y buscá (Ctrl + F) la traducción que querés cambiar. Cada texto aparece así, y la clave es lo que encabeza el bloque:

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

## Archivos del mod

Todo está en `config/traductor_es/` dentro de la carpeta del modpack:

| Archivo | Qué es |
|---|---|
| `config.json` | La configuración del panel |
| `correcciones.json` | Tus correcciones a mano |
| `glosario.json` | Términos que siempre se traducen igual |
| `informe.txt` | Resumen de la última traducción y textos que fallaron |
| `cache/` | Todas las traducciones hechas (si la borrás, se traduce todo de nuevo) |
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
