# Traductor ES

Mod de Minecraft que traduce al español (variante del juego: es_es, es_mx, es_ar...) los textos de los mods,
libros (Patchouli, Mantle, Citadel, IE, GuideME) y FTB Quests, con IA local (Ollama, LM Studio) o en la nube
(Gemini, Groq, OpenRouter). Repo **público**: https://github.com/luchomart/traductor-es (MIT, por luchomart).

**Antes de trabajar, leé `ROADMAP.md` ("Dónde quedamos" arriba).** Decisiones y lecciones: `PROYECTO.md`.
Para jugadores: `README.md`. Textos de CurseForge: `publicacion/curseforge.md`.

## Estructura
- `common/`: toda la lógica (motor, validador, Masker, glosario, caché, libros, FTB Quests, interfaz). Tests en `common/src/test`.
- `neoforge-1.21.1/`, `forge-1.20.1/` (ModDevGradle legacyforge), `fabric-1.21.1/`, `fabric-1.20.1/` (Loom):
  solo `Platform`, registro del pack y eventos. En Fabric el pack entra con un mixin (`PackRepositoryMixin`).

## Comandos
- Compilar todo: `./gradlew build` → jars en `*/build/libs/`. Versión en `mod_version` de `gradle.properties`.
- Cliente de desarrollo: `./gradlew :fabric-1.20.1:runClient` (config de prueba en `fabric-1.20.1/run/`, ignorada por git).
- Archivos para CurseForge: `publicacion/subir-X.Y.Z/` (ignorada por git).

## Instalar en un modpack para probar
Instancias de Prism en `~/.local/share/PrismLauncher/instances/` (FTB OceanBlock 2 = NeoForge 1.21.1;
Create- Too Many Addons = Forge 1.20.1, carpeta `minecraft/` sin punto). Copiar el jar desde `build/libs`.
- **Nunca reemplazar el jar de una instancia con el juego abierto**: crashea con NoClassDefFoundError y parece un bug del mod.
  Ver cuáles están abiertas con `ps -eo comm,args | awk '$1=="java"' | grep -o "instances/[^/]*/" | sort -u`
  y copiar solo en las que no aparecen; si no, pedir que reinicie.
- **Nunca `pkill -f <patrón>`**: puede matar mi propia shell, que tiene el patrón en su línea de comandos.

## Reglas
- Las claves de API van solo en `~/.traductor_es` ("Claves compartidas"), nunca en `config.json` (viaja al exportar modpacks).
- No fijar nombres de modelos en la nube: caducan. Se eligen solos (ModelPicker).
- Probar con IA falsa cuando haga falta simular cupos o errores; no gastar ni tocar las claves reales.
- Repo público: commits con el email noreply (ya configurado). Nada personal en el repo.
