# Hoja de ruta hacia la publicación

## 📍 Dónde quedamos (actualizado 2026-10-15)
- **v1.0.0 lista** y con tag en GitHub. Repo público. Fases 1 a 4 y extras hechos (salvo Forge 1.19.2).
- **Publicado en CurseForge:** https://www.curseforge.com/minecraft/mc-mods/traductor-es-ai-spanish-translator
- Próximo posible: link de CurseForge en el README, atender los primeros reportes, pendientes sin tildar de abajo.

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

## Fase 4: FTB Quests
- [x] Traducir misiones (formato con archivos de idioma, FTB Quests 2101+): conserva el español oficial y agrega lo que falta
- [x] Probar en el juego (OceanBlock: 17 textos que faltaban en el español oficial, 0 fallidos)
- [ ] Formato viejo (textos dentro de los capítulos), si aparece un modpack que lo use

## Extras
- [x] Editor de correcciones dentro del juego (buscador + editor; /traductor corregir)
- [x] Ver el texto original manteniendo Alt
- [ ] Forge 1.19.2

## Publicación
- [x] Nombre en inglés para CurseForge, descripción en inglés primero
- [x] Ícono 400×400 hecho por el autor
- [x] Changelog, esquema de versiones, repositorio en GitHub
- [x] Subido a CurseForge (v1.0.0)
