# Traductor ES: visión, decisiones y lecciones

## Qué es y por qué existe
Jugar cualquier modpack en español. Muchos mods no traen español, o traen otra variante. El mod traduce
lo que falta con una IA y lo guarda como un paquete de recursos interno que siempre está activo.

Nació para reemplazar a un mod chino (AutoTranslator) que solo traducía al chino y se salteaba los mods
que ya traían chino (142 en FTB OceanBlock 2).

## Cómo decide qué traducir
1. Español oficial de la variante del juego → no se toca.
2. Otra variante de español (es_mx, es_ar...) → se usa esa.
3. Caché de traducciones anteriores.
4. IA, con glosario (vanilla + por mod), correcciones del usuario y validador de marcadores.
   El Masker cambia los marcadores (§a, %s, {0}, macros) por fichas ⟦n⟧ antes de mandar el texto.

## Decisiones

| Tema | Decisión |
|---|---|
| Publicación | CurseForge + GitHub, declarando la ayuda de IA. **Modrinth descartado**: desde agosto de 2026 prohíbe proyectos hechos mayormente con IA. CurseForge pide nombre y descripción en inglés primero, ícono 400×400 y changelog por versión. |
| Plataformas | NeoForge 1.21.1, Forge 1.20.1, Fabric 1.20.1 y 1.21.1, con un solo `common/`. Forge 1.19.2 quedó como extra sin hacer. |
| Modelo local recomendado | `translategemma:12b` (ver pruebas abajo). A los modelos locales se les manda `reasoning_effort=none`. |
| Nube | Presets de Gemini, Groq y OpenRouter, con modelo elegido automáticamente. Si se agota el cupo, cambia de modelo solo; si no quedan, frena y guarda lo hecho. |
| Claves | Fuera de `config.json`, en `~/.traductor_es`, para que no viajen al exportar un modpack. |
| Conexión | `127.0.0.1` y no `localhost` (Java 17 resolvía `::1`). HTTP/1.1 forzado. |
| Ícono | El de 128 px escalado sin suavizar (`publicacion/icono-512.png`), elegido por el autor. |
| Descartado | Arreglar los cortes de renglón del manual de Actually Additions (es un error de su propio dibujado). |

## Pruebas de modelos locales (2026-10-13)
300 textos reales de OceanBlock, el mismo pedido que hace el mod, en una RX 6700 XT 12 GB:

| Modelo | Correctos | Tiempo |
|---|---|---|
| translategemma:12b | 98 % | 60 min |
| gemma3:12b | 96 % | 61 min |
| qwen3:14b sin razonar | 99,7 % | 60 min (pero deja anglicismos como "items") |
| qwen3:14b razonando | — | 22 textos/min, demasiado lento |

La calibración del minitest local (×0,4) coincide con unos 60 textos/min reales.

## Lecciones
- Los nombres de los modelos en la nube caducan: nunca fijarlos.
- Los modelos que razonan necesitan `reasoning_effort=low` y margen de tokens, o cortan la respuesta.
- Groq (octubre 2026) solo tiene gpt-oss-120b/20b y allam como modelos de chat: no recomendar llama ahí.
- Ollama con poca RAM: llama-server puede comerse varios GB y Minecraft muere por falta de memoria.
  Se arregla con `LLAMA_ARG_CACHE_RAM=0`, `LLAMA_ARG_CTX_CHECKPOINTS=2` y `OLLAMA_CONTEXT_LENGTH=8192`
  (con 4096 los lotes largos se cortan).
- Para probar cupos agotados sin claves reales: una IA falsa en otra máquina de la red local, configurada como
  servicio "personalizado" (si está en 127.0.0.1 cuenta como IA local y no se prueba lo mismo).
- Sin probar todavía: cambio por lentitud y salto a otro servicio (solo pasan con claves reales en la nube).
