# Hoja de ruta hacia la publicación

## 📍 Dónde quedamos (actualizado 2026-11-16)
- **Versión actual: 1.6.0** (sin tag; jars en `*/build/libs/`). En CurseForge sigue la 1.2.0. `publicacion/subir-1.5.0/` quedó armada, pero conviene subir directo la 1.6.0 (changelog 1.6.0 + 1.5.0 + 1.4.1 + 1.4.0 + 1.3.0).
- **1.6.0 hecha: modo "solo comunidad"** (sin IA ni claves). Asistente con tercera tarjeta y % de cobertura (marcada como recomendada si ≥ 90 %; no cambia la elección sola), paso 2 solo con idioma y textos; la pasada nunca llama a la IA y anota lo que quedó en inglés en `compartidas/faltan-<variante>.json` (`MissingTexts`); pestaña **Faltan** en el editor (solo aparece si hay faltantes); Conexión → «Modo» y los botones «Traducir lo que falta con IA» (ancho, principal) y «Traducir a mano» (angosto); invitación a completar y compartir en el aviso, el informe, el asistente y Conexión. El clic en el aviso abre el panel en Conexión (no el editor: decisión de Luciano). Probado en Fabric 1.20.1 con tres sesiones (`ollama ps` vacío en todas: no se llamó a la IA).
- **Revisión de la 1.6.0 hecha** (/revisar, 10 arreglos): sin internet en modo comunidad termina con error en vez de dar todo por faltante; en "Faltan", guardar un texto igual al inglés lo marca como traducido; salir del asistente sin «Empezar» devuelve el modo; la tarjeta solo se marca como recomendada; `JsonFiles.writeAtomic` para todo lo de `compartidas/`; un solo aviso dentro de un mundo; `Origin` con sus textos y colores. Visto en el juego: la tercera pasada da 134 faltantes (lo traducido a mano sale de la lista).
- **Visto en el juego (2026-11-16):** arreglos 2, 3 y 6 OK (6 necesita la ventana maximizada: con 854×480 la escala llega a 3). Siguen sin ver el 1 (sin internet), el 4 y el 9.
- **Botones de Conexión en modo comunidad:** «Traducir lo que falta con IA» ancho y primero; «Traducir a mano» angosto (100 px) al lado (decisión de Luciano: protagonismo a la IA). Visto en el juego.
- `run/` de Fabric 1.20.1 devuelto a la normalidad (con IA y su caché). `correcciones.json` tiene dos correcciones de prueba ("Bioma" y `modmenu.searchTerms.hasUpdate`).
- **Agregado a la 1.6.0 (2026-11-16):** el % ya en español gracias a la comunidad (`TranslationJob.coveredPct`: caché + comunidad + correcciones sobre eso + faltantes) en el aviso, el recuadro del menú (elige la frase que entra) y Conexión; ruedita de espera y segundos que corren (`UiKit.spinner`/`loading`/`elapsed`) en el asistente (con qué está haciendo: conectando, cargando el modelo, midiendo la velocidad), Probar conexión, Elegir modelo, el editor, Compartir y «Buscando textos». Visto en 1.19.2.
- **1.19.2 probada en `run/`** (modo comunidad: 73 faltantes, 86 %; `ollama ps` vacío). Su `run/` ya se devolvió a la normalidad.
- **Fabric 1.21.1 probada en `run/`** (modo comunidad con el juego en es_ar: 680 faltantes; pantallas, «Faltan» y asistente OK). Arreglado ahí: cerrar el juego con el asistente abierto dejaba «con IA» en el disco (`Config.holdMode`/`releaseMode`: el asistente retiene el modo de antes hasta «Empezar»; el botón «Modo» de Conexión siempre gana). Su `run/` ya se devolvió (es_es, con su caché).
- **Próximo paso:** armar `publicacion/subir-1.6.0/` y subirla a CurseForge; después tag `v1.6.0`.
- Traducciones de la comunidad (1.3.0–1.4.1): repo de datos `luchomart/traductor-es-traducciones` (local en `../traductor-es-traducciones/`, CC BY-NC-SA; 350 archivos es_ar de StaTech + Valhelsia). El mod baja solo lo de los mods instalados (de a 6, con barra) y lo usa en este orden: correcciones → comunidad revisada → caché propio → comunidad IA → IA. Herramientas → Compartir… manda lo nuevo como PR (device flow; OAuth App "Traductor ES" en la cuenta luchomart, Client ID en `GitHubShare`) o exporta una carpeta. Probado en las 5 versiones y con envíos reales (PR #1 y #2 de prueba, cerrados).
- Revisar aportes: en cada PR, "Approve and run" (solo corre el control: formato y marcadores, no ortografía), mirar el diff y recién ahí "Merge". `index.json` se rehace solo.
- Opcionales: probar el envío con otra cuenta de GitHub (camino con fork; hoy solo con el GitHub falso de los tests); ajustar "texto corto" para otras variantes (≤40 caracteres sin punto final) cuando haya datos; tag `v1.1.0` en `784f1ce`; "Mena de"/"Mineral de" en las fichas.
- Misiones y libros en 1.20.1: a la espera de un modpack real que lo necesite. Atender reportes.

Publicación: **CurseForge + GitHub**, declarando que el mod se hizo con ayuda de IA.
(Modrinth no permite proyectos con código mayormente generado por IA desde agosto de 2026.)

## 1.6.0: solo comunidad (hecha 2026-11-12)
Objetivo: que quien tiene un modpack ya cubierto por la comunidad no tenga que conectar Ollama ni una clave, y que pueda completar lo que falta.
- [x] Asistente, paso 1: opción "Usar solo las traducciones de la comunidad (sin IA, sin claves)", recomendada si cubre ≥ 90 % del modpack; con menos, se ofrece igual avisando que lo que falte queda en inglés. Elegirla saltea la comprobación de la IA (paso 2).
- [x] Traducción en ese modo: nunca llama a una IA ni elige modelo; usa comunidad, caché y correcciones. El informe y el aviso dicen cuánto quedó en inglés.
- [x] Configuración: "Modo: solo comunidad" y un botón "Completar con IA" para configurar una más adelante.
- [x] Editor: pestaña "Faltan" con lo que quedó sin traducir (la pasada lo anota, como `usadas`), para traducirlo a mano con «Guardar y siguiente».
- [x] Invitar a completar el 100 %: el aviso de fin de pasada lleva al editor; lo corregido sale como revisado en Herramientas → Compartir.
- Archivos: `Config`, `WizardScreen`, `TranslationJob`, `ConfigScreen`, el panel y `CorrectionScreen`; después README, Guía y CurseForge.

## 1.5.0: editor de correcciones (2026-11-12)
- [x] Lista con scroll (ruedita, barra, Re Pág/Av Pág; `onScroll` en los 5 `BaseScreen`) y filas con origen (IA, Comunidad, ★ Tuya) y mod
- [x] Filtros: Todo · Sin corregir · Corregidas · Dudosas · Comunidad, y selector de mod
- [x] «Guardar y siguiente» (Ctrl+Enter), aplicando todo junto al volver a la lista
- [x] Lo de la comunidad en el editor (`CommunityUsed`, anotado en cada pasada)
- [x] «Reemplazar…» (`BulkReplace` + `ReplaceScreen`), con vista previa
- [x] Asistente: baja la comunidad antes de estimar y la descuenta (`Estimator`)

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
