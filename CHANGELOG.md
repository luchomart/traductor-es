# Changelog

## 1.6.0

**English**
- **Community only mode (no AI, no keys).** If the community already translated your modpack, you don't need Ollama or a cloud key: choose «Solo la comunidad» in the setup assistant (it shows how much of your modpack the community covers, and is marked as recommended at 90 % or more) or *Conexión → Modo*. The mod never calls an AI; whatever is missing stays in English. If the community can't be reached (no internet), it says so instead of reporting everything as missing.
- **Help complete it:** the menu notice and the report say how many texts are missing. *Conexión → Traducir a mano* opens the editor on the new **Faltan** tab to translate them yourself (then share them in *Herramientas → Compartir…*), and *Traducir lo que falta con IA* opens the assistant to pick an AI.
- The notice, the menu box and *Conexión* say how much is already in Spanish thanks to the community («86 % en español»).
- Waiting screens no longer look frozen: a spinner and a seconds counter while the AI loads the model, checks the connection or loads lists; the assistant says what it is doing («Cargando el modelo en tu PC…», «Midiendo la velocidad…»).
- Fixed: two downloads of the same community file at the same time (assistant and translation) could fail with "NoSuchFileException".

**Español**
- **Modo solo comunidad (sin IA, sin claves).** Si la comunidad ya tradujo tu modpack, no necesitás Ollama ni una clave: elegí «Solo la comunidad» en el asistente (muestra cuánto de tu modpack cubre la comunidad y aparece como recomendada si es el 90 % o más) o en *Conexión → Modo*. El mod nunca llama a una IA; lo que falte queda en inglés. Si no se puede llegar a la comunidad (sin internet), lo avisa en vez de dar todo por faltante.
- **Ayudá a completarlo:** el aviso del menú y el informe dicen cuántos textos faltan. *Conexión → Traducir a mano* abre el editor en la pestaña nueva **Faltan** para traducirlos vos (después compartilos en *Herramientas → Compartir…*), y *Traducir lo que falta con IA* abre el asistente para elegir una IA.
- El aviso, el recuadro del menú y *Conexión* dicen cuánto ya está en español gracias a la comunidad («86 % en español»).
- Las esperas ya no parecen trabadas: una ruedita y los segundos que corren mientras la IA carga el modelo, se prueba la conexión o se cargan listas; el asistente dice qué está haciendo («Cargando el modelo en tu PC…», «Midiendo la velocidad…»).
- Arreglado: dos descargas del mismo archivo de la comunidad a la vez (el asistente y la traducción) podían fallar con "NoSuchFileException".

## 1.5.0

**English**
- Correction editor, reworked: the list scrolls (mouse wheel, draggable bar, Page Up/Down) instead of pages, and each row shows where the text comes from (AI, Community, ★ yours) and its mod.
- Filters: tabs **Todo · Sin corregir · Corregidas · Dudosas · Comunidad** ("Dudosas" = texts that look left in English) and a mod picker, combined with the search.
- **Guardar y siguiente** (Ctrl+Enter): save and open the next text of the list. Everything is applied together when you go back to the list, so the game doesn't reload after each one.
- Texts downloaded from the community now appear in the editor and can be corrected like the AI ones (after the next translation pass).
- **Reemplazar…**: change a word or phrase in every text of the list at once (for example "Mena de" → "Mineral de"), with a preview. Whole words only, ignoring case but keeping each capital letter; changes that would break colors or %s are skipped.
- The setup assistant counts what the community already translated: it downloads it first and only estimates time for what's left for the AI.

**Español**
- Editor de correcciones renovado: la lista se scrollea (ruedita, barra para arrastrar, Re Pág/Av Pág) en vez de ir por páginas, y cada fila muestra de dónde viene el texto (IA, Comunidad, ★ tuya) y su mod.
- Filtros: pestañas **Todo · Sin corregir · Corregidas · Dudosas · Comunidad** ("Dudosas" = textos que parecen haber quedado en inglés) y un selector de mod, que se suman a la búsqueda.
- **Guardar y siguiente** (Ctrl+Enter): guarda y abre el texto siguiente de la lista. Se aplica todo junto al volver a la lista, así el juego no recarga a cada rato.
- Lo bajado de la comunidad ahora aparece en el editor y se corrige igual que lo de la IA (desde la próxima pasada de traducción).
- **Reemplazar…**: cambiá una palabra o frase en todos los textos de la lista de una vez (por ejemplo "Mena de" → "Mineral de"), con vista previa. Solo palabras enteras, sin importar mayúsculas pero respetando la inicial de cada lugar; lo que rompería colores o %s se saltea.
- El asistente cuenta lo que ya tradujo la comunidad: lo baja primero y solo estima el tiempo de lo que le queda a la IA.

## 1.4.1

**English**
- Community translations download 6 files at a time (the first time on a big modpack goes from about a minute and a half to seconds), with a progress bar in the corner notice and in the panel.
- Click the corner notice on the main menu to open the panel.

**Español**
- Las traducciones de la comunidad se bajan de a 6 archivos a la vez (la primera vez en un modpack grande pasa de un minuto y medio a segundos), con una barra de avance en el aviso de la esquina y en el panel.
- Un clic en el aviso de la esquina del menú principal abre el panel.

## 1.4.0

**English**
- Share your translations with the community from the game: *Herramientas → Compartir…* shows how many new translations you have (the ones not in the repo yet). **Enviar a la comunidad** opens a pull request with your GitHub account (the first time you connect it with a code, like on a TV); **Exportar carpeta** builds a folder and opens the repo's upload page, no account connection needed. Each file keeps what was already in the repo, and what you already sent isn't sent again. Your corrections go in as reviewed.

**Español**
- Compartí tus traducciones con la comunidad desde el juego: *Herramientas → Compartir…* muestra cuántas traducciones nuevas tenés (las que todavía no están en el repo). **Enviar a la comunidad** abre un pull request con tu cuenta de GitHub (la primera vez la conectás con un código, como en un televisor); **Exportar carpeta** arma una carpeta y abre la página de subida del repo, sin conectar la cuenta. Cada archivo conserva lo que ya estaba en el repo, y lo que ya mandaste no se vuelve a mandar. Tus correcciones entran como revisadas.

## 1.3.0

**English**
- Community translations: before using the AI, the mod downloads what other players already translated for your installed mods, from the public repo [luchomart/traductor-es-traducciones](https://github.com/luchomart/traductor-es-traducciones). Only files that changed are downloaded again. Translations reviewed by a person come first; your own corrections always win. Mod texts and books only (not quests or REI pages). Turn it off with «Bajar de la comunidad» in the Traducción tab. Nothing is uploaded.
- If there is no translation for your Spanish variant, another variant is used only for short names; longer texts go to the AI.

**Español**
- Traducciones de la comunidad: antes de usar la IA, el mod baja lo que otros jugadores ya tradujeron de tus mods instalados, desde el repo público [luchomart/traductor-es-traducciones](https://github.com/luchomart/traductor-es-traducciones). Solo se vuelve a bajar lo que cambió. Primero van las revisadas por una persona; tus correcciones siempre ganan. Solo textos de mods y libros (no misiones ni fichas de REI). Se apaga con «Bajar de la comunidad» en la pestaña Traducción. No se sube nada.
- Si no hay traducción de tu variante del español, se usa la de otra solo para nombres cortos; los textos largos van a la IA.

## 1.2.0

**English**
- On Fabric 1.19.2, also translates FTB Quests in the old format (texts inside the chapters), such as StaTech Industry. The modpack's files are not changed: the translation is applied in memory and shows up when you enter a world.
- Time estimates now include books and quests. Exporting the pack warns about what needs the mod. Patchouli templates are no longer sent to the AI, and books inside nested jars are found.
- On Fabric 1.19.2, also translates REI information pages with fixed text, such as the ore vein pages that StaTech Industry builds with KubeJS. They are detected when you enter a world and translated the next time the mod runs. Controlled by the "Libros y misiones" option.

**Español**
- En Fabric 1.19.2 también se traducen las misiones de FTB Quests en el formato viejo (textos dentro de los capítulos), como las de StaTech Industry. Los archivos del modpack no cambian: la traducción se aplica en memoria y se ve al entrar a un mundo.
- Las estimaciones de tiempo ahora cuentan libros y misiones. Al exportar el pack se avisa qué necesita el mod. Las plantillas de Patchouli ya no van a la IA y se encuentran los libros dentro de jars anidados.
- En Fabric 1.19.2 también se traducen las fichas de información de REI con texto fijo, como las de vetas de minerales que StaTech Industry arma con KubeJS. Se detectan al entrar a un mundo y se traducen la siguiente vez que trabaja el mod. Las controla la opción "Libros y misiones".

## 1.1.0

**English**
- New version: Fabric 1.19.2. On 1.19.2, FTB Quests are not translated yet (old format).
- On Fabric 1.19.2, also translates Patchouli books stored in a mod's `data/` folder (for example Spectrum or Simply Swords). They show up when you enter a world.
- Patchouli books: pages written as plain text are now translated, and book macros such as `@item@` are protected.

**Español**
- Nueva versión: Fabric 1.19.2. En 1.19.2 todavía no se traducen las misiones de FTB Quests (formato viejo).
- En Fabric 1.19.2 también se traducen los libros de Patchouli guardados en la carpeta `data/` de un mod (por ejemplo, Spectrum o Simply Swords). Se ven al entrar a un mundo.
- Libros de Patchouli: ahora se traducen las páginas escritas como texto suelto y se protegen macros como `@item@`.

## 1.0.0

First release. / Primera versión.

**English**
- Translates mod texts from any language into the Spanish variant your game uses (Spain, Mexico, Argentina, Chile, Uruguay, Venezuela, Ecuador) with an AI on your PC (Ollama, LM Studio) or in the cloud (Gemini, Groq, OpenRouter).
- Keeps official translations, reuses other Spanish variants before translating, and detects "Spanish" files that are just English copies.
- Translates in-game books and guides: Patchouli (including covers), Tinkers' Construct (Mantle), GuideME (AE2 and addons, Powah), the Immersive Engineering manual and Alex's Mobs' dictionary (Citadel); and FTB Quests (lang-file format, single player).
- If the AI leaves a name untranslated claiming it is a proper noun, it is asked again with similar names from the same mod that were already translated.
- Protects formatting: colors (§a, &a), placeholders (%s, {0}), Patchouli macros, JSON text with colors and links.
- First-run setup assistant, "Which one should I use?" comparison with time estimates for your modpack, tabbed settings panel, in-game guide.
- In-game correction editor and "hold Alt to see the original" on item tooltips.
- Connection test measures your real speed and shows the remaining quota (Groq, OpenRouter); switch service mid-translation with "Use this service"; warns when a translation is slow or a model is not suited for Spanish; "Redo" re-translates everything a given model did. On OpenRouter without credit it picks a free model.
- Five speed levels (very fast to very slow). Automatic switch: if the quota runs out or it is very slow, it moves to another free model or to another free service you have a key for (never to a paid one, never from your PC to the cloud). Keys are shared between your modpacks and kept outside the modpack folder (exporting `config` does not leak them).
- Local AI: recommends `translategemma:12b` (Gemma 3 trained for translation; 98 % valid vs 96 % for `gemma3:12b` in a 300-text test, same speed). Local models are asked not to reason (qwen3:14b: 60 texts/min instead of 22).
- Capitalization like official Minecraft ("Nether brick slab" style), also fixed on existing translations without re-translating.
- Translates in the background; works while you play when the AI is in the cloud.
- Forge 1.20.1, NeoForge 1.21.1, Fabric 1.20.1 and Fabric 1.21.1. Client only.

**Español**
- Traduce los textos de los mods desde cualquier idioma a la variante del español del juego (España, México, Argentina, Chile, Uruguay, Venezuela, Ecuador) con una IA en tu PC (Ollama, LM Studio) o en la nube (Gemini, Groq, OpenRouter).
- Respeta las traducciones oficiales, aprovecha otras variantes del español antes de traducir y detecta los archivos "en español" que son el inglés copiado.
- Traduce los libros y guías del juego: Patchouli (también la portada), Tinkers' Construct (Mantle), GuideME (AE2 y sus agregados, Powah), el manual de Immersive Engineering y el diccionario de Alex's Mobs (Citadel); y las misiones de FTB Quests (formato con archivos de idioma, un jugador).
- Si la IA deja un nombre sin traducir diciendo que es un nombre propio, se le vuelve a pedir mostrándole nombres parecidos del mismo mod ya traducidos.
- Protege el formato: colores (§a, &a), marcadores (%s, {0}), macros de Patchouli y texto JSON con colores y links.
- Asistente para la primera vez, comparación «¿Cuál conviene?» con tiempos para tu modpack, panel con pestañas y guía dentro del juego.
- Editor de correcciones dentro del juego y «original con Alt» en la descripción de los objetos.
- La prueba de conexión mide tu velocidad real y muestra el límite que le queda a la clave (Groq, OpenRouter); «Usar este servicio» cambia de servicio sin perder lo traducido; avisa si va lento o si el modelo no sirve para español; «Rehacer» vuelve a traducir lo que hizo un modelo. En OpenRouter sin saldo elige un modelo gratis.
- Cinco velocidades (de muy rápida a muy lenta). Cambio automático: si se acaba el cupo o va muy lenta, pasa a otro modelo gratis o a otro servicio gratis del que tengas clave (nunca a uno de pago ni de tu PC a la nube). Las claves se comparten entre tus modpacks y se guardan fuera de la carpeta del modpack (exportar `config` no las incluye).
- IA en la PC: recomienda `translategemma:12b` (Gemma 3 entrenado para traducir; 98 % bien contra 96 % de `gemma3:12b` en una prueba con 300 textos, misma velocidad). A los modelos de la PC se les pide no razonar (qwen3:14b: 60 textos/min en vez de 22).
- Mayúsculas como el Minecraft oficial («Losa de ladrillos del Nether»), corregidas también en lo ya traducido sin volver a traducir.
- Traduce en segundo plano; con una IA en la nube sigue mientras jugás.
- Forge 1.20.1, NeoForge 1.21.1, Fabric 1.20.1 y Fabric 1.21.1. Solo cliente.
