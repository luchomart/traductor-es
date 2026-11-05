# Hoja de ruta hacia la publicación

## 📍 Dónde quedamos (actualizado 2026-11-05)
- **v1.0.0 lista** y con tag en GitHub. Repo público. Fases 1 a 4 y extras hechos (salvo Forge 1.19.2).
- **Publicado en CurseForge:** https://www.curseforge.com/minecraft/mc-mods/traductor-es-ai-spanish-translator
- **1.2.0 lista, sin publicar:** Fabric 1.19.2 nueva, con libros de Patchouli en `data/` y misiones en formato viejo (solo esa versión). Probada en StaTech Industry: traducción completa y 2.936 textos de misiones aplicados. Commits solo locales; jars en `publicacion/subir-1.2.0/`.
- **Antes de publicar:** traducción real con Ollama en una versión de 1.20.1 y una de 1.21.1 (después del refactor solo se abrieron pantallas). Alt en StaTech ya probado: funciona.
- **Commiteado pero sin compilar:** arreglo de la tarjeta "Última traducción" y link de CurseForge en el README. Hay que compilar, correr los tests, mirar la pestaña Herramientas y regenerar los jars de `publicacion/subir-1.2.0/`, que quedaron viejos.
- **Próximo paso (con el juego cerrado):** compilar y hacer esas pruebas, después push y tag `v1.2.0`, y subir a CurseForge.
- **Misiones y libros en 1.20.1:** "Create- Too Many Addons" no da motivo para portarlos: no tiene FTB Quests ni Patchouli, y sus libros son de formatos ya soportados (AE2 con GuideME, Alex's Mobs y Citadel). Queda a la espera de un modpack real que lo necesite.
- También pendiente: atender los primeros reportes.

Publicación: **CurseForge + GitHub**, declarando que el mod se hizo con ayuda de IA.
(Modrinth no permite proyectos con código mayormente generado por IA desde agosto de 2026.)

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
- [ ] "Última traducción" (Herramientas): un informe largo se sale de la tarjeta y pisa el botón (arreglo escrito: corta con «…»; falta compilar y verlo en el juego)

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
