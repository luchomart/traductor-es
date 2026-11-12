# Hoja de ruta hacia la publicación

## 📍 Dónde quedamos (actualizado 2026-11-12)
- **1.2.0 publicada (2026-11-12):** tag `v1.2.0` en GitHub y 5 jars en CurseForge (https://www.curseforge.com/minecraft/mc-mods/traductor-es-ai-spanish-translator). Forge y NeoForge aprobados; los 3 de Fabric estaban en revisión. Probada con Ollama en 1.20.1 y 1.21.1, y en StaTech (fichas de REI y tarjeta "Última traducción").
- **A medias:** CurseForge aprobó 1 de los 3 jars de Fabric (2026-11-12); falta que aprueben los otros 2. La descripción nueva (fichas de REI en 1.19.2) ya está en la página.
- **1.3.0 lista para publicar (2026-11-12), etapas 1 a 3 hechas:** repo de datos en `../traductor-es-traducciones/` (público en GitHub: luchomart/traductor-es-traducciones), con StaTech + Valhelsia en es_ar (350 archivos, 30.199 textos), `herramientas/validar.py` y CI. Se carga con `tools/compartir.py`.
- Etapa 2: el mod baja y usa las traducciones (opción "Bajar de la comunidad" en el panel). Probada en `run/` de 1.19.2 sin caché: 469 textos de la comunidad, solo las misiones a la IA; segunda pasada: 0 bajados, 12 al día, nada por traducir.
- Etapa 3: README (sección "Traducciones de la comunidad"), CurseForge (función + privacidad: GitHub ve IP y nombres de mods), Guía (sección "La comunidad"), prompt de ayuda, CHANGELOG y `mod_version=1.3.0`.
- **Próximo paso:** publicar 1.3.0 (`./gradlew build`, jars a `publicacion/subir-1.3.0/`, tag `v1.3.0`, CurseForge con la descripción nueva). Antes, probarla en otra versión (NeoForge o Fabric 1.21.1) con Ollama.
- Pendientes chicos de 1.3.0: ajustar "texto corto" para otras variantes (hoy ≤40 caracteres sin punto final) cuando haya datos de otra variante; el estimador del asistente todavía no descuenta lo de la comunidad.
- Opcionales: tag `v1.1.0` en `784f1ce` (nunca se creó); en las fichas la IA alterna "Mena de" y "Mineral de" (se corrige con el editor).
- Misiones y libros en 1.20.1: a la espera de un modpack real que lo necesite. Atender reportes.

Publicación: **CurseForge + GitHub**, declarando que el mod se hizo con ayuda de IA.
(Modrinth no permite proyectos con código mayormente generado por IA desde agosto de 2026.)

## Idea para 1.3.0: traducciones compartidas (después de publicar 1.2.0)
Objetivo: que la gente **descargue** lo que otro ya tradujo para ahorrar cupo y tiempo (colaborar corrigiendo es secundario).
- Plan elegido: repo público en GitHub con las traducciones **por mod** (no por modpack), con el texto en inglés como clave para que sirvan aunque el mod se actualice. Misiones y fichas de REI, por modpack.
- El mod las baja antes de usar la IA, con una opción visible y apagable (avisarlo en CurseForge). Se aportan por pull request; el repo arranca con lo ya traducido (StaTech, packs de prueba, correcciones).
- Marcar las revisadas por una persona y darles prioridad.
- Subir desde el juego (servidor propio, moderación) solo si la comunidad lo pide.
- Precedente: CFPA "Minecraft Mod Language Package" (comunidad china, repo de GitHub + resource pack).
- Decidido (2026-11-12):
  - Formato: `es_ar/<archivo del caché>.json` = `{"revisadas": {inglés: español}, "ia": {...}}` + `index.json` con hash por archivo. Libros sí; misiones y REI no.
  - La variante sale sola de la carpeta del caché (nadie la declara); el CI rechaza variantes desconocidas.
  - Prioridad en el mod: correcciones → compartidas revisadas → caché propio → compartidas de la IA → IA.
  - Sin archivo de tu variante: usar otra variante solo para textos cortos (nombres); los largos van a la IA (voseo/tú).
  - Bajar solo los archivos de los mods instalados y solo si cambió el hash; guardarlas en `compartidas/`, aparte del caché. Si GitHub falla, seguir con la IA.
  - Licencia del repo de datos: CC BY-NC-SA 4.0 (como CFPA); herramientas MIT.
- [x] Etapa 1: `tools/compartir.py` + repo de datos local con validador y CI
- [x] Subir el repo a GitHub: https://github.com/luchomart/traductor-es-traducciones (CI en verde)
- [x] Etapa 2: descarga y uso en el mod (`SharedTranslations.java`, `TranslationJob`, `Config`, `ConfigScreen`, tests)
- [x] Etapa 3: README, CurseForge y Guía

## Fase 1: robustez y tests
- [x] Reintentos con espera ante errores pasajeros (Ollama reiniciado, 429, 5xx, timeouts) en vez de cancelar
- [x] Arrancar la traducción si se cambia el idioma a español durante la sesión
- [x] Recargar textos automáticamente al volver al menú si terminó mientras se jugaba
- [x] Forzar HTTP/1.1
- [x] Blindar el escaneo frente a recargas de recursos simultáneas
- [x] Mensajes del panel en varias líneas
- [x] Tests automáticos: Validator, Masker, LlmClient (lectura de JSON, URL)

## Fase 2: más público
- [x] Proteger el _resaltado_ de Create en tooltips (encontrado al probar)
- [x] Destino según el español elegido en el juego (es_es, es_mx, es_ar, es_cl, es_uy, es_ve, es_ec)
- [x] Presets de IA: Ollama, LM Studio, Gemini, OpenRouter, Groq; manejo de límites de peticiones
- [x] Peticiones simultáneas (configurable)
- [x] "Probar conexión" lista los modelos instalados y avisa si el configurado no existe
- [x] Exportar el pack como .zip
- [x] Mensajes de error entendibles y botón Conseguir clave / Descargar (encontrado al probar)
- [x] Elección automática del modelo en la nube, tope de respuesta (max_tokens) y cambio solo si el servicio retira el modelo (encontrado al probar con claves reales)

## Pendientes chicos (para la etapa visual)
- [x] Filtrar modelos raros de la lista de Gemini (antigravity, deep-research, música, voz…; los preview van al final y nunca se eligen solos)
- [x] Mostrar en el log los reintentos de Probar conexión (panel y asistente)
- [ ] Probar una traducción completa con un servicio en la nube

## Etapa visual
- [x] Maquetas para elegir: https://claude.ai/artifact/5EXWcX5416X7ArPw8Ua8S3
- Elegido: panel A (pestañas) con botón Guía visible arriba · progreso B (aviso en la esquina) ·
  asistente de 3 pasos que aparece la primera vez y cada vez que algo no está listo · guía A (índice lateral)
- [x] Base visual común (paneles, barra, ícono, pestañas) para las dos versiones
- [x] Panel con pestañas
- [x] Progreso como aviso en la esquina
- [x] Asistente
- [x] Guía con índice
- [ ] Probar todo en el juego (las dos versiones)

## Fase 3: Fabric
- [x] Fabric 1.20.1 (Loom 1.15, nombres de Mojang, mixin para el pack, Mod Menu opcional)
- [x] Fabric 1.21.1
- [x] Probar en el juego con un modpack de Fabric (1.21.1: Farmer's Delight, JEI, Mod Menu)
- [x] Fabric 1.19.2: compila y arranca en un cliente de desarrollo (asistente y pack)
- [x] Fabric 1.19.2: panel, tooltips, hints y editor probados en cliente de desarrollo; traducción real en StaTech Industry
- [x] Regresión tras el refactor: panel abierto en Fabric 1.20.1 y 1.21.1, Forge 1.20.1 y NeoForge 1.21.1
- [x] Fabric 1.19.2: Alt sobre un objeto traducido, probado en StaTech con el jar corregido (se declara solo 1.19.2; 1.19.1 no se probó)
- [x] "Última traducción" (Herramientas): un informe largo se sale de la tarjeta y pisa el botón (arreglo escrito: corta con «…»; falta compilar y verlo en el juego)

## Fase 4: FTB Quests
- [x] Traducir misiones (formato con archivos de idioma, FTB Quests 2101+): conserva el español oficial y agrega lo que falta
- [x] Probar en el juego (OceanBlock: 17 textos que faltaban en el español oficial, 0 fallidos)
- [x] Formato viejo (textos dentro de los capítulos), solo Fabric 1.19.2: en memoria, sin tocar el modpack. Probado en cliente de desarrollo con el capítulo `welcome` de StaTech
- [x] Formato viejo: StaTech completa (2.936 textos aplicados)
- [x] Formato viejo: editar una misión guarda el inglés (`FtbQuestsEditMixin`), probado en cliente de desarrollo
- [ ] Formato viejo: llevarlo a otras versiones si aparece un caso (revisar 1.20.1)
- [ ] Fabric Loader 0.14: probar el jar final en un modpack real (en desarrollo anda con 0.14.25)

## Extras
- [x] Libros de Patchouli en `data/`, solo Fabric 1.19.2 (`PatchouliContentMixin`): probado en cliente de desarrollo con Extended Drawers (16 de 16 archivos entregados)
- [x] Libros en `data/`: abrirlos en StaTech (Runic Grimoire, Spectrum)
- [ ] Libros en `data/` en las otras versiones, si aparece un caso
- [x] Editor de correcciones dentro del juego (buscador + editor; /traductor corregir)
- [x] Ver el texto original manteniendo Alt
- [ ] Forge 1.19.2

## Publicación
- [x] Nombre en inglés para CurseForge, descripción en inglés primero
- [x] Ícono 400×400 hecho por el autor
- [x] Changelog, esquema de versiones, repositorio en GitHub
- [x] Subido a CurseForge (v1.0.0)
