<!--
Borrador de la página de CurseForge.
Nombre del proyecto (propuesta): Traductor ES - AI Spanish Translator
Resumen corto (propuesta, máx. ~250 caracteres):
  Play any modpack in Spanish. Translates mod texts, Patchouli books and FTB Quests with an AI on your PC (Ollama) or a free cloud service. Keeps official translations. / Jugá cualquier modpack en español.
Categorías sugeridas: Client-side, Utility & QoL, Map and Information
Licencia: MIT
-->

# Traductor ES — play any modpack in Spanish

**Traductor ES** translates the texts of your mods into Spanish using an AI: item names, tooltips, menus, **Patchouli books** and **FTB Quests**. It runs on your PC with [Ollama](https://ollama.com) (free, offline) or with a free cloud service (Google Gemini, Groq or OpenRouter).

It translates into the Spanish variant your game uses: Spain, Mexico, Argentina, Chile, Uruguay, Venezuela or Ecuador.

> *¿Hablás español? La descripción en español está más abajo.*

## Features

- **Respects official translations.** If a mod already has your Spanish, it isn't touched. If it has another Spanish variant, that one is used instead of translating.
- **Detects fake translations.** Many mods ship a Spanish file that is just English copied. Those texts get translated.
- **Books and quests.** Patchouli books (pages and covers) and FTB Quests missions (single player).
- **Keeps formatting safe.** Colors, placeholders (`%s`, `{0}`), Patchouli macros and JSON text with links are protected; broken translations are rejected and retried.
- **Setup assistant.** Checks everything the first time and tells you what is missing, with buttons to fix it.
- **"Which one should I use?"** Compares services with estimated times for *your* modpack, and which ones are free.
- **Correction editor.** Search any translated text in game and fix it; applied instantly.
- **Hold Alt** on an item to see its original English text.
- **Translates in the background** and only translates new texts after the first time. With a cloud AI it keeps going while you play; with a local AI it pauses in game to keep your FPS.
- **Share it:** export the translations as a resource pack so friends with the same modpack don't need an AI.

## Requirements

- Minecraft **Forge 1.20.1**, **NeoForge 1.21.1**, **Fabric 1.20.1** or **Fabric 1.21.1** (Fabric needs Fabric API; Mod Menu is optional).
- **Client only.** Servers don't need it.
- An AI, one of:
  - **On your PC:** Ollama with `gemma3:12b` (about 8 GB, ideal with a 12 GB GPU) or `gemma3:4b` for smaller GPUs.
  - **In the cloud:** a free API key from Google Gemini, Groq or OpenRouter.

| Option | Cost | 1,000 texts | 10,000 texts |
|---|---|---|---|
| OpenRouter (paid models) | Paid | ~5 min | ~50 min |
| Groq | Free | ~10 min | ~1 h 40 min |
| Google Gemini | Free | ~17 min | ~2 h 50 min |
| OpenRouter (`:free` models) | Free | ~20 min | ~10 days (≈1,000/day limit) |
| Your PC (Ollama) | Free | ~17 min | ~2 h 50 min (depends on GPU) |

## Privacy

With a cloud service, your mods' texts are sent to that service, and your API key is stored in plain text in `config/traductor_es/config.json`. Don't share that file. With Ollama nothing leaves your PC.

## Made with AI assistance

This mod was developed with the help of an AI assistant (Claude, by Anthropic). The design, decisions and testing are mine. The source code is on GitHub under the MIT license.

---

# Traductor ES — jugá cualquier modpack en español

**Traductor ES** traduce los textos de tus mods al español con una IA: nombres de objetos, descripciones, menús, **libros de Patchouli** y **misiones de FTB Quests**. Funciona en tu PC con [Ollama](https://ollama.com) (gratis, sin internet) o con un servicio gratis en la nube (Google Gemini, Groq u OpenRouter).

Traduce a la variante del español que tengas en el juego: España, México, Argentina, Chile, Uruguay, Venezuela o Ecuador.

## Qué hace

- **Respeta las traducciones oficiales.** Si un mod ya trae tu español, no se toca; si trae otra variante, usa esa en vez de traducir.
- **Detecta traducciones falsas.** Muchos mods traen un archivo "en español" que es el inglés copiado: esos textos se traducen.
- **Libros y misiones.** Libros de Patchouli (páginas y portadas) y misiones de FTB Quests (en un jugador).
- **Cuida el formato.** Colores, marcadores (`%s`, `{0}`), macros de Patchouli y texto con links quedan protegidos; si la IA rompe algo, se descarta y se reintenta.
- **Asistente.** La primera vez revisa todo y te dice qué falta, con botones para arreglarlo.
- **«¿Cuál conviene?»** Compara los servicios con el tiempo estimado para *tu* modpack y cuáles son gratis.
- **Editor de correcciones.** Buscá cualquier texto traducido dentro del juego y corregilo; se aplica al instante.
- **Mantené Alt** sobre un objeto para ver su texto original en inglés.
- **Traduce en segundo plano** y después de la primera vez solo lo nuevo. Con IA en la nube sigue mientras jugás; con IA en tu PC se pausa en el mundo para no bajar los FPS.
- **Compartilo:** exportá las traducciones como resource pack para amigos con el mismo modpack, sin IA.

## Qué necesitás

- Minecraft **Forge 1.20.1**, **NeoForge 1.21.1**, **Fabric 1.20.1** o **Fabric 1.21.1** (Fabric necesita Fabric API; Mod Menu es opcional).
- **Solo cliente.** Los servidores no lo necesitan.
- Una IA: **en tu PC** (Ollama con `gemma3:12b`, o `gemma3:4b` con menos VRAM) o **en la nube** (clave gratis de Gemini, Groq u OpenRouter). Los tiempos están en la tabla de arriba.

## Privacidad

Con un servicio en la nube, los textos de tus mods se envían a ese servicio y la clave queda guardada en `config/traductor_es/config.json` (no compartas ese archivo). Con Ollama nada sale de tu PC.

## Hecho con ayuda de IA

Este mod se desarrolló con ayuda de un asistente de IA (Claude, de Anthropic). El diseño, las decisiones y las pruebas son míos. El código está en GitHub con licencia MIT.
