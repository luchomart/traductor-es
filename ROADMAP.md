# Hoja de ruta hacia la publicación

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
- [ ] Filtrar modelos raros de la lista de Gemini (antigravity, deep-research, preview)
- [ ] Mostrar en el log los reintentos de Probar conexión
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

## Fase 4: FTB Quests
- [ ] Traducir misiones cuando el modpack no trae el idioma

## Extras
- [ ] Editor de correcciones dentro del juego (buscador)
- [ ] Ver el texto original manteniendo Alt
- [ ] Forge 1.19.2

## Publicación
- [ ] Nombre en inglés para CurseForge, descripción en inglés primero
- [ ] Ícono 400×400 hecho por el autor
- [ ] Changelog, esquema de versiones, repositorio en GitHub
