# Changelog

## 1.0.0

First release. / Primera versión.

**English**
- Translates mod texts from any language into the Spanish variant your game uses (Spain, Mexico, Argentina, Chile, Uruguay, Venezuela, Ecuador) with an AI on your PC (Ollama, LM Studio) or in the cloud (Gemini, Groq, OpenRouter).
- Keeps official translations, reuses other Spanish variants before translating, and detects "Spanish" files that are just English copies.
- Translates in-game books and guides: Patchouli (including covers), Tinkers' Construct (Mantle), GuideME (AE2 and addons, Powah), the Immersive Engineering manual and Alex's Mobs' dictionary (Citadel); and FTB Quests (lang-file format, single player).
- Protects formatting: colors (§a, &a), placeholders (%s, {0}), Patchouli macros, JSON text with colors and links.
- First-run setup assistant, "Which one should I use?" comparison with time estimates for your modpack, tabbed settings panel, in-game guide.
- In-game correction editor and "hold Alt to see the original" on item tooltips.
- Connection test measures your real speed and shows the remaining quota (Groq, OpenRouter); switch service mid-translation with "Use this service"; warns when a translation is slow or a model is not suited for Spanish; "Redo" re-translates everything a given model did.
- Five speed levels (very fast to very slow). Automatic switch: if the quota runs out or it is very slow, it moves to another free model or to another free service you have a key for (never to a paid one, never from your PC to the cloud). Keys are shared between your modpacks and kept outside the modpack folder (exporting `config` does not leak them).
- Local AI: recommends `translategemma:12b` (Gemma 3 trained for translation; 98 % valid vs 96 % for `gemma3:12b` in a 300-text test, same speed). Local models are asked not to reason (qwen3:14b: 60 texts/min instead of 22).
- Capitalization like official Minecraft ("Nether brick slab" style), also fixed on existing translations without re-translating.
- Translates in the background; works while you play when the AI is in the cloud.
- Forge 1.20.1, NeoForge 1.21.1, Fabric 1.20.1 and Fabric 1.21.1. Client only.

**Español**
- Traduce los textos de los mods desde cualquier idioma a la variante del español del juego (España, México, Argentina, Chile, Uruguay, Venezuela, Ecuador) con una IA en tu PC (Ollama, LM Studio) o en la nube (Gemini, Groq, OpenRouter).
- Respeta las traducciones oficiales, aprovecha otras variantes del español antes de traducir y detecta los archivos "en español" que son el inglés copiado.
- Traduce los libros y guías del juego: Patchouli (también la portada), Tinkers' Construct (Mantle), GuideME (AE2 y sus agregados, Powah), el manual de Immersive Engineering y el diccionario de Alex's Mobs (Citadel); y las misiones de FTB Quests (formato con archivos de idioma, un jugador).
- Protege el formato: colores (§a, &a), marcadores (%s, {0}), macros de Patchouli y texto JSON con colores y links.
- Asistente para la primera vez, comparación «¿Cuál conviene?» con tiempos para tu modpack, panel con pestañas y guía dentro del juego.
- Editor de correcciones dentro del juego y «original con Alt» en la descripción de los objetos.
- La prueba de conexión mide tu velocidad real y muestra el límite que le queda a la clave (Groq, OpenRouter); «Usar este servicio» cambia de servicio sin perder lo traducido; avisa si va lento o si el modelo no sirve para español; «Rehacer» vuelve a traducir lo que hizo un modelo.
- Cinco velocidades (de muy rápida a muy lenta). Cambio automático: si se acaba el cupo o va muy lenta, pasa a otro modelo gratis o a otro servicio gratis del que tengas clave (nunca a uno de pago ni de tu PC a la nube). Las claves se comparten entre tus modpacks y se guardan fuera de la carpeta del modpack (exportar `config` no las incluye).
- IA en la PC: recomienda `translategemma:12b` (Gemma 3 entrenado para traducir; 98 % bien contra 96 % de `gemma3:12b` en una prueba con 300 textos, misma velocidad). A los modelos de la PC se les pide no razonar (qwen3:14b: 60 textos/min en vez de 22).
- Mayúsculas como el Minecraft oficial («Losa de ladrillos del Nether»), corregidas también en lo ya traducido sin volver a traducir.
- Traduce en segundo plano; con una IA en la nube sigue mientras jugás.
- Forge 1.20.1, NeoForge 1.21.1, Fabric 1.20.1 y Fabric 1.21.1. Solo cliente.
