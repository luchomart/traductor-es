<!--
Borrador de la página de CurseForge.
Nombre del proyecto (propuesta): Traductor ES - AI Spanish Translator
Resumen corto (propuesta, máx. ~250 caracteres):
  Play any modpack in Spanish. Translates mod texts, in-game books and FTB Quests with an AI on your PC (Ollama) or a free cloud service. Keeps official translations. / Jugá cualquier modpack en español.
Categorías sugeridas: Client-side, Utility & QoL, Map and Information
Licencia: MIT
-->

# Traductor ES — play any modpack in Spanish

**Traductor ES** translates the texts of your mods into Spanish using an AI: item names, tooltips, menus, **in-game books and guides** (Patchouli, Tinkers' Construct, AE2 and other GuideME guides, Immersive Engineering…) and **FTB Quests**. It runs on your PC with [Ollama](https://ollama.com) (free, offline) or with a free cloud service (Google Gemini, Groq or OpenRouter).

It translates into the Spanish variant your game uses: Spain, Mexico, Argentina, Chile, Uruguay, Venezuela or Ecuador.

> *¿Hablás español? La descripción en español está más abajo.*

## Features

- **Respects official translations.** If a mod already has your Spanish, it isn't touched. If it has another Spanish variant, that one is used instead of translating.
- **Detects fake translations.** Many mods ship a Spanish file that is just English copied. Those texts get translated.
- **Books and quests.** Patchouli books (pages and covers), Tinkers' Construct manuals, GuideME guides (AE2 and addons, Powah), the Immersive Engineering manual, Alex's Mobs' dictionary and FTB Quests missions (single player).
- **Keeps formatting safe.** Colors, placeholders (`%s`, `{0}`), Patchouli macros and JSON text with links are protected; broken translations are rejected and retried.
- **Setup assistant.** Checks everything the first time and tells you what is missing, with buttons to fix it. Its time estimate already counts what the community translated.
- **"Which one should I use?"** Compares services with estimated times for *your* modpack, and which ones are free.
- **Correction editor.** Search any translated text in game and fix it; applied instantly. Filters (uncorrected, doubtful, community, by mod), "save and next" to fix them in a row, and find & replace across many texts with a preview.
- **Hold Alt** on an item to see its original English text.
- **Translates in the background** and only translates new texts after the first time. With a cloud AI it keeps going while you play; with a local AI it pauses in game to keep your FPS.
- **Community translations.** Before using the AI, it downloads what other players already translated for *your* mods, from a public [GitHub repo](https://github.com/luchomart/traductor-es-traducciones). Saves quota and hours. Can be turned off («Bajar de la comunidad»). **No AI at all:** if the community already covers your modpack, the «Solo comunidad» mode needs no AI and no key; whatever is missing stays in English, and you can translate it by hand in the editor («Faltan» tab) and share it. **Give back in one click:** *Herramientas → Compartir…* sends your new translations as a pull request with your GitHub account, or exports a folder to upload by hand.
- **Share it:** export the translations as a resource pack so friends with the same modpack don't need an AI.

## Requirements

- Minecraft **Forge 1.20.1**, **NeoForge 1.21.1**, **Fabric 1.19.2**, **Fabric 1.20.1** or **Fabric 1.21.1** (Fabric needs Fabric API; Mod Menu is optional). On 1.19.2, old-format FTB Quests and REI information pages with fixed text (like StaTech Industry's ore veins, made with KubeJS) are translated in memory, without changing the modpack's files.
- **Need another version or loader?** Ask in the CurseForge comments and I'll gladly add it!
- **Client only.** Servers don't need it.
- An AI (or none, in «Solo comunidad» mode), one of:
  - **On your PC:** Ollama with `translategemma:12b` (about 8 GB, ideal with a 12 GB GPU; a Gemma 3 trained for translation) or `translategemma:4b` for smaller GPUs.
  - **In the cloud:** a free API key from Google Gemini, Groq or OpenRouter.

| Option | Cost | 1,000 texts | 10,000 texts |
|---|---|---|---|
| OpenRouter (paid models) | Paid | ~5 min | ~50 min |
| Groq | Free | ~10 min | ~1 h 40 min |
| Google Gemini | Free | ~17 min | ~2 h 50 min |
| OpenRouter (`:free` models) | Free | ~20 min | ~10 days (≈1,000/day limit) |
| Your PC (Ollama) | Free | ~17 min | ~2 h 50 min (depends on GPU) |

## Privacy

With a cloud service, your mods' texts are sent to that service, and your API key is stored in plain text in `.traductor_es/claves.json` in your user folder, so all your modpacks can use it. It is **not** saved inside the modpack, so exporting your `config` folder doesn't leak it. If you turn off «Claves compartidas» (shared keys), each modpack keeps its own key in `config/traductor_es/config.json` instead. Don't share those files. With Ollama your texts never leave your PC.

**Community translations** (on by default): the mod downloads files from GitHub (`raw.githubusercontent.com`), only for the mods you have installed. Like any download, GitHub sees your IP address and the names of those mods. Nothing is uploaded unless you press *Compartir*. Turn downloads off with «Bajar de la comunidad» in the Traducción tab.

**Sharing** (only when you choose to): your translations are published on GitHub under your username. The GitHub session is stored in `.traductor_es/github.json` in your user folder (never in the modpack); it lets the mod write to your public repos, which is what opening a pull request needs. «Desconectar GitHub» deletes it.

## Made with AI assistance

This mod was developed with the help of an AI assistant (Claude, by Anthropic). The design, decisions and testing are mine. The source code is on [GitHub](https://github.com/luchomart/traductor-es) under the MIT license.

---

# Traductor ES — jugá cualquier modpack en español

**Traductor ES** traduce los textos de tus mods al español con una IA: nombres de objetos, descripciones, menús, **libros y guías del juego** (Patchouli, Tinkers' Construct, AE2 y otras guías de GuideME, Immersive Engineering…) y **misiones de FTB Quests**. Funciona en tu PC con [Ollama](https://ollama.com) (gratis, sin internet) o con un servicio gratis en la nube (Google Gemini, Groq u OpenRouter).

Traduce a la variante del español que tengas en el juego: España, México, Argentina, Chile, Uruguay, Venezuela o Ecuador.

## Qué hace

- **Respeta las traducciones oficiales.** Si un mod ya trae tu español, no se toca; si trae otra variante, usa esa en vez de traducir.
- **Detecta traducciones falsas.** Muchos mods traen un archivo "en español" que es el inglés copiado: esos textos se traducen.
- **Libros y misiones.** Libros de Patchouli (páginas y portadas), manuales de Tinkers' Construct, guías de GuideME (AE2 y sus agregados, Powah), el manual de Immersive Engineering, el diccionario de Alex's Mobs y misiones de FTB Quests (en un jugador).
- **Cuida el formato.** Colores, marcadores (`%s`, `{0}`), macros de Patchouli y texto con links quedan protegidos; si la IA rompe algo, se descarta y se reintenta.
- **Asistente.** La primera vez revisa todo y te dice qué falta, con botones para arreglarlo. Su estimación de tiempo ya descuenta lo que tradujo la comunidad.
- **«¿Cuál conviene?»** Compara los servicios con el tiempo estimado para *tu* modpack y cuáles son gratis.
- **Editor de correcciones.** Buscá cualquier texto traducido dentro del juego y corregilo; se aplica al instante. Filtros (sin corregir, dudosas, comunidad, por mod), «guardar y siguiente» para corregir de corrido y reemplazo en muchos textos con vista previa.
- **Mantené Alt** sobre un objeto para ver su texto original en inglés.
- **Traduce en segundo plano** y después de la primera vez solo lo nuevo. Con IA en la nube sigue mientras jugás; con IA en tu PC se pausa en el mundo para no bajar los FPS.
- **Traducciones de la comunidad.** Antes de usar la IA, baja lo que otros jugadores ya tradujeron de *tus* mods, desde un [repo público de GitHub](https://github.com/luchomart/traductor-es-traducciones). Ahorra cupo y horas. Se puede apagar («Bajar de la comunidad»). **Sin ninguna IA:** si la comunidad ya cubre tu modpack, el modo «Solo comunidad» no necesita IA ni clave; lo que falte queda en inglés, y lo podés traducir a mano en el editor (pestaña «Faltan») y compartirlo. **Devolvé el favor con un clic:** *Herramientas → Compartir…* manda tus traducciones nuevas como pull request con tu cuenta de GitHub, o exporta una carpeta para subirla a mano.
- **Compartilo:** exportá las traducciones como resource pack para amigos con el mismo modpack, sin IA.

## Qué necesitás

- Minecraft **Forge 1.20.1**, **NeoForge 1.21.1**, **Fabric 1.19.2**, **Fabric 1.20.1** o **Fabric 1.21.1** (Fabric necesita Fabric API; Mod Menu es opcional). En 1.19.2, las misiones de FTB Quests en formato viejo y las fichas de información de REI con texto fijo (como las vetas de minerales de StaTech Industry, armadas con KubeJS) se traducen en memoria, sin cambiar los archivos del modpack.
- **¿Lo necesitás en otra versión o loader?** Pedilo en los comentarios de CurseForge, ¡lo agrego con gusto!
- **Solo cliente.** Los servidores no lo necesitan.
- Una IA: **en tu PC** (Ollama con `translategemma:12b`, o `translategemma:4b` con menos VRAM) o **en la nube** (clave gratis de Gemini, Groq u OpenRouter). Los tiempos están en la tabla de arriba. En modo «Solo comunidad» no hace falta ninguna.

## Privacidad

Con un servicio en la nube, los textos de tus mods se envían a ese servicio y la clave queda guardada en `.traductor_es/claves.json` dentro de tu carpeta de usuario, para que la usen todos tus modpacks. **No** se guarda dentro del modpack: si exportás la carpeta `config`, la clave no va adentro. Si desactivás «Claves compartidas», cada modpack guarda la suya en `config/traductor_es/config.json`. No compartas esos archivos. Con Ollama tus textos no salen de tu PC.

**Traducciones de la comunidad** (prendido de entrada): el mod baja archivos de GitHub (`raw.githubusercontent.com`), solo de los mods que tenés instalados. Como en cualquier descarga, GitHub ve tu IP y los nombres de esos mods. No se sube nada salvo que toques *Compartir*. Las descargas se apagan con «Bajar de la comunidad» en la pestaña Traducción.

**Compartir** (solo cuando vos querés): tus traducciones se publican en GitHub con tu usuario. La sesión de GitHub se guarda en `.traductor_es/github.json` en tu carpeta de usuario (nunca en el modpack); le permite al mod escribir en tus repos públicos, que es lo que hace falta para abrir un pull request. «Desconectar GitHub» la borra.

## Hecho con ayuda de IA

Este mod se desarrolló con ayuda de un asistente de IA (Claude, de Anthropic). El diseño, las decisiones y las pruebas son míos. El código está en [GitHub](https://github.com/luchomart/traductor-es) con licencia MIT.
