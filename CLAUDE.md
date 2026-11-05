# Traductor ES

Mod de Minecraft que traduce al español (variante del juego: es_es, es_mx, es_ar...) los textos de los mods,
libros (Patchouli, Mantle, Citadel, IE, GuideME) y FTB Quests, con IA local (Ollama, LM Studio) o en la nube
(Gemini, Groq, OpenRouter). Repo **público**: https://github.com/luchomart/traductor-es (MIT, por luchomart).

**Antes de trabajar, leé `ROADMAP.md` ("Dónde quedamos" arriba).** Decisiones y lecciones: `PROYECTO.md`.
Para jugadores: `README.md`. Textos de CurseForge: `publicacion/curseforge.md`.

## Estructura
- `common/`: toda la lógica (motor, validador, Masker, glosario, caché, libros, FTB Quests, interfaz). Tests en `common/src/test`.
- `common-modern/`: capa de interfaz para 1.20+ (`Gfx`, `Widgets`, `CustomButton`, `TextCompat`). En `common/` usar esa capa, nunca `GuiGraphics` ni `Button.builder`.
- `neoforge-1.21.1/`, `forge-1.20.1/` (ModDevGradle legacyforge), `fabric-1.21.1/`, `fabric-1.20.1/`, `fabric-1.19.2/` (Loom; trae su propia capa de interfaz):
  solo `Platform`, registro del pack y eventos. En Fabric el pack entra con un mixin (`PackRepositoryMixin`).

## Comandos
- Compilar todo: `./gradlew build` → jars en `*/build/libs/`. Versión en `mod_version` de `gradle.properties`.
- Cliente de desarrollo: `./gradlew :fabric-1.20.1:runClient` (config de prueba en `fabric-1.20.1/run/`, ignorada por git).
- Archivos para CurseForge: `publicacion/subir-X.Y.Z/` (ignorada por git).

## Instalar en un modpack para probar
Instancia de Prism en `~/.local/share/PrismLauncher/instances/`: StaTech Industry = Fabric 1.19.2, carpeta `minecraft/` sin punto. Copiar el jar desde `build/libs`.
Para las otras versiones no hay instancia: se prueban en `run/`.
- **Nunca reemplazar el jar de una instancia con el juego abierto**: crashea con NoClassDefFoundError y parece un bug del mod.
  Ver cuáles están abiertas con `ps -eo comm,args | awk '$1=="java"' | grep -o "instances/[^/]*/" | sort -u`
  y copiar solo en las que no aparecen; si no, pedir que reinicie.
- No abrir una instancia para probar: con "Al abrir el juego" traduce sola y gasta cupo real. Probar en `run/` con `shareKeys: false` y Ollama.
- **Nunca `pkill -f <patrón>`**: puede matar mi propia shell, que tiene el patrón en su línea de comandos.

## Reglas
- Las claves de API van solo en `~/.traductor_es` ("Claves compartidas"), nunca en `config.json` (viaja al exportar modpacks).
- No fijar nombres de modelos en la nube: caducan. Se eligen solos (ModelPicker).
- Probar con IA falsa cuando haga falta simular cupos o errores; no gastar ni tocar las claves reales.
- Los tests no tienen Minecraft en el classpath: la lógica testeable va separada de lo que usa `net.minecraft`.
- No leer `record` con Gson por reflexión: 1.19.2 trae Gson 2.8.9 y falla al ejecutar, no al compilar (los tests usan 2.11).
- Al portar: el pack tiene que informar `PackWriter.PACK_ID` como nombre (`sourcePackId`). Si no, sus traducciones parecen oficiales y se pierden en la pasada siguiente. Probar siempre **dos pasadas** seguidas.
- No compilar con un modpack abierto: la RAM queda justa (1,4 GB libres con StaTech) y el sistema podría cerrar el juego.
- `unzip` no está instalado: inspeccionar jars con `bsdtar` o `zipfile` de Python, y sin esconder errores con `2>/dev/null`.
- Repo público: commits con el email noreply (ya configurado). Nada personal en el repo.
