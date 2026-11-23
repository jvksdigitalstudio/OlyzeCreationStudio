# Auditoría general del proyecto

**Estado:** Auditoría de código completada (revisión estática, sin correr
la app en dispositivo). Complementa — no reemplaza — a
`GLOW_BLEND_MODE.md` (hallazgo puntual del Resplandor) y
`EFFECTS_AUDIT.md` (barrido de los 8 efectos). Este documento mira el
proyecto completo: arquitectura, seguridad, configuración de build,
cobertura de tests y mantenibilidad — insumo directo para planear el
reordenamiento antes de la migración a C++.

**Tamaño del proyecto:** 82 archivos Kotlin en `app/src/main`, ~42.500
líneas. 21 archivos de test (13 antes de Fase 3, +4 en Fase 3, +4 en
Fase 3.1 — ver actualización correspondiente más abajo; el conteo de "9"
de la auditoría original ya estaba desactualizado desde Fase 2).

---

## 1. Seguridad

**Sin hallazgos críticos.**

- Sin credenciales/API keys/secrets hardcodeados en el código (barrido
  completo del árbol `app/src/main/java`).
- Permisos del manifest mínimos y justificados: solo `VIBRATE`, con un
  comentario explícito de por qué (feedback háptico real al arrastrar un
  ícono sobre "Eliminar" en `FloatingToolWindow`) — sin permisos de
  cámara/almacenamiento/red pedidos de más.
- `MainActivity` es el único componente `exported="true"`, y es el
  esperado (el launcher) — no hay otros componentes expuestos sin
  necesidad.
- Existe un test dedicado a **Zip Slip**
  (`data/ProjectStorageZipSlipTest.kt`) — es decir, ya se identificó y se
  blindó explícitamente la vulnerabilidad clásica de path traversal al
  descomprimir archivos de proyecto. Buena señal: no es una app que
  descomprime ZIPs de usuario sin validar rutas.
- **Actualización FASE 1 (estabilización P0/P1):** Zip Slip protegía la
  EXTRACCIÓN del zip, pero no los valores de texto de `project.json`
  (`imageFileName`/`audioFileName`/`coverImageFileName`/fotos de elenco),
  que se usaban para resolver rutas de archivo sin ninguna validación —
  path traversal real a través del manifest, no del zip. También se
  agregaron límites contra ZIP bombs (entradas/tamaño/ratio de
  compresión) que antes no existían. Ver
  `docs/fases/FASE_1_ESTABILIZACION_P0_P1.md` para el detalle
  completo.
- **Actualización FASE 2 (concurrencia, render, playback, duración):**
  auditoría del runtime confirmó una condición de carrera real (no
  teórica) entre el hilo de GL y el hilo principal sobre
  `CameraTrack.keyframes` — reproducible con un test de concurrencia
  real (dos hilos), ver `CameraTrackConcurrencyTest.kt`. También se
  confirmó un bug real de exportación: la duración usada para construir
  la pista de audio usaba `fps = 30` fijo en vez del fps real del
  proyecto, produciendo desincronismo audio/video en proyectos con fps
  distinto de 30 combinado con rampas de velocidad o freeze frames. Y un
  bug de reproducción: no existía ningún `Job` rastreado para el loop de
  playback, permitiendo loops duplicados con toques rápidos de Play/
  Pause. Ver `docs/fases/FASE_2_CONCURRENCIA_RENDER_PLAYBACK.md` para el
  detalle completo.
- **Actualización FASE 3 (render / GL lifecycle):** auditoría del ciclo
  de vida gráfico confirmó dos bugs reales de recreación de contexto EGL
  — el shader program de `LayerDrawer` no se reconstruía después de que
  Android recreara el contexto (misma instancia de `LayerDrawer`
  sobrevive a la app entera; `ensureInitialized()` solo reconstruye "si
  es null"), y la textura de la cuadrícula de composición quedaba
  apuntando a un texture id de un contexto ya destruido por el mismo
  motivo (comparación solo por identidad de bitmap, ciega a la
  generación de contexto). También se confirmó la ausencia total de un
  puente entre el lifecycle real de Android y `GLSurfaceView.onPause()`/
  `.onResume()` — con `RENDERMODE_CONTINUOUSLY`, el hilo de GL seguía
  renderizando en segundo plano sin frenarse nunca. Los tres se
  corrigieron con lógica pura y testeable (`GpuHandle`/
  `GpuContextGeneration`, `GLRendererLifecycleState`,
  `GridTextureCacheState`) más un `DisposableEffect`/`LifecycleEventObserver`
  en `GLPreview.kt`. Ver `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md` para
  el detalle completo.
- **Actualización FASE 3.1 (cierre de ownership GPU/bitmap):** una
  auditoría de cierre encontró que las afirmaciones de "GL thread
  ownership" de Fase 3 para `Layer.glTextureId`/`Layer.pendingBitmap` no
  se correspondían con el código real — el hilo principal
  (`EditorViewModel`) escribía `glTextureId` directamente en varios
  lugares, y `pendingBitmap` tenía una ventana de *lost update* real
  entre lectura y limpieza. También se encontró un bug más serio: la
  textura de la cuadrícula de composición reciclaba un `Bitmap` que
  Compose seguía considerando de su propiedad (vía `remember`),
  produciendo un crash real (`IllegalStateException`) al recrear el
  contexto EGL. Se corrigieron los tres: `glTextureId` ya no existe como
  campo de `Layer` (vive en un registro privado de `GLRenderer`, indexado
  por id); `pendingBitmap` migró a un handoff atómico de un solo slot
  (`SingleResourceHandoff`); y `GLRenderer` dejó de reciclar el Bitmap de
  la cuadrícula. De paso se corrigió un memory leak preexistente
  (texturas GPU de capas eliminadas nunca se liberaban). Ver
  `docs/fases/FASE_3_1_CIERRE_OWNERSHIP.md` para el detalle completo.

## 2. Configuración de build

**Sin hallazgos críticos. Un par de observaciones para la migración.**

- `release`: `isMinifyEnabled = true` + `isShrinkResources = true` +
  ProGuard con reglas propias — build de release correctamente
  configurado, no quedó en default.
- Ya existe un catálogo de ABIs soportadas (`buildSrc/.../AbiCatalog.kt`)
  con splits por arquitectura (`armeabi-v7a`/`arm64-v8a`) + universal de
  respaldo, y el propio `build.gradle.kts` tiene preparado el bloque
  `ndk { abiFilters... }` con un comentario explícito: *"hoy Olyze no
  tiene código nativo... es preparación para cuando lo tenga"*. Esto es
  relevante directo para tu plan de migrar a C++: la base de ABIs ya
  está lista, no arrancás de cero ahí.
- `versionName = "0.1.0-alpha"` — coherente con que el proyecto todavía
  está en una etapa temprana; solo dejarlo anotado para no perder de
  vista actualizar el versionado cuando se declare estable.
- `android:largeHeap="true"` en el manifest: razonable para una app que
  maneja bitmaps grandes de foto/video, pero es una bandera a vigilar —
  apoyarse en heap grande en vez de en gestión de memoria más ajustada
  (recicle de bitmaps a tiempo, límites de textura ya presentes en
  `GpuTextureLimits`) puede esconder picos de memoria reales en vez de
  resolverlos. No es un hallazgo por sí solo, pero conviene tenerlo en
  la cabeza si en el futuro aparecen OOM en dispositivos de gama baja.

## 3. Cobertura de tests

**Hallazgo real: cobertura baja y concentrada en pocas áreas.**

9 archivos de test contra 82 de producción (~11% de archivos con test
directo). Lo que SÍ está testeado: parámetros de efectos
(`ImageEffectsParamsTest`, `ImageEffectsAngleTest`), duración/expansión
de timeline (`TimelineDurationManagerTest`,
`TimelineExpansionPolicyTest`), campo de distorsión
(`DistortionFieldTest`), rampa de velocidad (`SpeedRampEngineTest`),
API de animación (`AnimationApiImplTest`), y persistencia/seguridad de
proyecto (`ProjectStorageZipSlipTest`, `ProjectDataSerializationTest`).

Lo que **no** tiene ningún test directo, y es lógica no trivial:
- `EditorViewModel.kt` (2.733 líneas) — el orquestador central de casi
  toda la edición (capas, commits, autoguardado, cancelación de jobs
  concurrentes). Cero tests unitarios.
- `ProjectStorage.kt` (1.214 líneas) más allá del Zip Slip y la
  serialización — el resto del guardado/carga de proyecto no tiene
  cobertura visible.
- La mayor parte de `ImageEffects.kt` (3.363 líneas): los tests
  existentes cubren parámetros y ángulos, pero no hay evidencia de tests
  sobre el pipeline de composición en sí (sombra, reflejo, contorno,
  glow) — es lógica de píxeles pura, testeable con JUnit normal (de
  hecho ya hay un comentario en el propio código sobre por qué eligieron
  JUnit puro sin Robolectric para poder testear sin runtime real de
  Android), así que agregar cobertura ahí es viable sin infraestructura
  nueva.

No es una app sin disciplina de testing (la elección deliberada de JUnit
puro + el fix documentado de `Color.rgb()` para que los tests corran sin
Robolectric muestra que sí se pensó en testabilidad) — es cobertura
pareja pero angosta. Antes de una migración a C++, ampliar tests sobre
`EditorViewModel` y el pipeline de `ImageEffects` da una red de seguridad
real para detectar regresiones durante el reemplazo del motor.

## 4. Arquitectura y mantenibilidad

**Hallazgo real, y es el más relevante para tu plan de reordenamiento.**

- `EditorScreen.kt` tiene **17.408 líneas** en un solo archivo. Es, con
  diferencia, el archivo más grande del proyecto (el segundo,
  `ImageEffects.kt`, tiene 3.363). Un archivo Compose de este tamaño es
  un riesgo concreto, no solo estético:
  - Tiempo de compilación/recomposición de Compose se degrada con
    archivos gigantes.
  - Cualquier cambio pequeño en una sección arrastra revisar/tocar un
    archivo enorme — más probabilidad de conflictos de merge si en algún
    momento trabaja más de una persona, y más difícil de auditar (esta
    misma conversación es evidencia: encontrar el selector "Modo de
    mezcla" en este archivo requirió grep por número de línea, no
    navegación natural).
  - Es, con confianza, la causa raíz de tu propia sensación de
    "desorden" que mencionaste — no es percepción, es un archivo que
    hace demasiadas cosas a la vez (paneles de Efectos, Timeline UI,
    diálogos de color, ventanas flotantes, headers de herramientas, y
    más, todo en el mismo archivo).
  - Recomendación concreta: dividir por categoría de panel (uno por
    efecto — `EffectsPanelContorno.kt`, `EffectsPanelResplandor.kt`,
    etc. — y otro por ventana/diálogo), antes o en paralelo al
    reordenamiento general que estás planeando. Esto es puramente
    Kotlin/Compose, no depende de la migración a C++, así que se puede
    hacer ya sin esperar.
- El resto del árbol (`engine/`, `api/`, `data/`, `viewmodel/`) está
  organizado por dominio de forma consistente, con una frontera
  explícita y documentada (`docs/adr/ADR-004-eliner-boundary.md`,
  ADR-001 a ADR-003 sobre timeline, contexto Android, y manejo de
  errores) — señal de que el resto del proyecto sí sigue un criterio
  claro. El desorden real está concentrado en `ui/EditorScreen.kt`
  (y en menor medida `ui/TimelineView.kt`, 2.728 líneas), no repartido
  parejo por todo el proyecto.
- Patrón consistente y sano detectado en todo el motor: casi cada
  cambio de comportamiento no trivial está documentado inline como
  "BUG REAL corregido" con el motivo — buena disciplina de trazabilidad,
  pero también señal indirecta de que el proyecto ya pasó por bastante
  iteración reactiva (arreglar sobre la marcha) más que diseño
  arquitectónico previo — coherente con por qué ahora tiene sentido un
  reordenamiento deliberado antes de seguir creciendo.

## 5. Consistencia con los hallazgos ya documentados

- El bug funcional confirmado en el pipeline de efectos sigue siendo el
  Modo de mezcla del Resplandor — `GLOW_BLEND_MODE.md`.
- No se encontró ningún otro control en la UI que prometa un
  comportamiento que el pipeline no pueda cumplir (mismo método de
  búsqueda: `PorterDuff`/`Xfermode`/`BlendMode` en todo `engine/`, cero
  resultados fuera de `ImageEffects.kt`).
- **Actualización septiembre 2026 (fuera del pipeline de efectos):**
  se confirmó y corrigió un segundo bug funcional real, esta vez en el
  flujo de creación de proyecto, no en el motor de render.
  `LayerRepository.importAsLayers` asignaba a la capa de fondo el tamaño
  NATURAL del bitmap decodificado, sin relación con `CanvasSpec` del
  proyecto — si la imagen elegida como fondo (Imagen/Cámara) no tenía la
  proporción exacta del lienzo, el verde chroma-key de zona vacía
  (`CHROMA_KEY_GREEN_ARGB`, fondo por defecto intencional de todo
  proyecto nuevo) quedaba asomando en los bordes. Ver
  `docs/adr/ADR-006-fondo-ajustado-al-lienzo.md` para el detalle
  completo de la causa raíz y la solución (paso de ajuste de encuadre
  obligatorio antes de confirmar Imagen/Cámara como fondo, reutilizando
  y generalizando la lógica de `CoverAdjustDialog` en `ImageFitDialog`).
  El camino de Color nunca tuvo este bug (genera el bitmap ya al tamaño
  exacto del canvas).
- **Actualización FASE 4 (memoria y sincronización UI↔GPU al reabrir):**
  se confirmaron y corrigieron dos bugs reales distintos, ambos con el
  mismo síntoma visible (verde chroma-key cubriendo el lienzo COMPLETO,
  no solo los bordes como en el hallazgo de ADR-006 de arriba) pero
  causas raíz no relacionadas entre sí: (1) `ImageDecoding` atrapaba
  `OutOfMemoryError` con un `catch (t: Throwable)` genérico y descartaba
  la capa en silencio, agravado por `ProjectStorage.loadProject`
  decodificando TODAS las capas del proyecto en paralelo a resolución
  completa (pico de memoria = suma de todas, no una por una) — de ahí
  que el fondo desapareciera del todo de forma intermitente, dependiendo
  del estado de memoria del dispositivo en el instante de reabrir; y (2)
  el overlay de carga de `EditorScreen` se retiraba apenas terminaba el
  decode en CPU, antes de que la GPU terminara de subir y pintar la
  textura real — un destello verde de milisegundos incluso en aperturas
  exitosas. Ver `docs/fases/FASE_4_MEMORIA_Y_CARGA_PROYECTO.md` para el
  detalle completo de ambas causas raíz y sus correcciones.
- **Actualización septiembre 2026 (auditoría de la guía de
  encuadre/posicionamiento de fondo, capturas del cliente sobre el
  proyecto "Cr7"):** se confirmó y corrigió un tercer bug real, mismo
  síntoma que ADR-006 (verde asomando en los bordes, no cubriendo el
  lienzo completo) pero por una puerta de entrada distinta que ADR-006
  nunca cerró: el botón **"Importar fondo" del editor** (proyecto ya
  abierto, `MainActivity.kt` → `onImportBackgroundClick`) llamaba a
  `EditorViewModel.importAsBackground()` directo con el `Uri` crudo del
  picker, sin pasar por `BackgroundAdjustDialog` — el mismo encuadre
  obligatorio que ADR-006 sí exige para el picker equivalente dentro de
  "Nuevo proyecto". Se confirmó además que la compensación de
  profundidad de ADR-007 (`LayerDrawer.drawLayer`) es matemáticamente
  correcta y no relacionada con este bug — se verificó numéricamente
  (proyección de perspectiva simulada) que un fondo YA ajustado al 100%
  del lienzo llena el cuadro exacto en reposo para cualquier
  `parallaxFactor`; el problema de las capturas del cliente era que el
  fondo importado por ese botón nunca llegaba a estar ajustado al
  lienzo en primer lugar. Ver `docs/adr/ADR-010-importar-fondo-editor-sin-encuadre.md`
  para el detalle completo, y `docs/pending-work/AUDITORIA_ENCUADRE_FONDO_2026-09.md`
  para el informe de auditoría completo de esta ronda (alcance revisado,
  diagnóstico de cada área tocada, y verificación pendiente en
  dispositivo real).

---

## Resumen ejecutivo

| Área | Estado |
|---|---|
| Seguridad | Sin hallazgos críticos — Zip Slip ya blindado y testeado |
| Build/release | Sin hallazgos críticos — release bien configurado, base de ABI lista para NDK |
| Tests | Cobertura baja (~11% de archivos antes de Fase 3, 21/88 después de Fase 3.1) y angosta — `EditorViewModel` y el pipeline de `ImageEffects` sin tests directos |
| Arquitectura | `EditorScreen.kt` (17.4k líneas) es el problema real de mantenibilidad del proyecto — el resto del árbol está razonablemente organizado |
| Render / GL lifecycle | 2 bugs reales de recreación de contexto EGL + ausencia de puente Activity↔GLSurfaceView (Fase 3), + 3 bugs reales de ownership GPU/bitmap + 1 memory leak preexistente (Fase 3.1) — ver `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md` y `docs/fases/FASE_3_1_CIERRE_OWNERSHIP.md` |
| Efectos (Contorno/Resplandor/Sombra/Reflejo/Distorsión/Color/3D) | 1 bug confirmado (Resplandor), ya documentado aparte |
| Memoria / carga de proyecto | 2 bugs reales corregidos: `OutOfMemoryError` silencioso en decode paralelo al reabrir, y overlay de carga retirado antes del pintado real en GPU (Fase 4) — ver `docs/fases/FASE_4_MEMORIA_Y_CARGA_PROYECTO.md` |

**Para el reordenamiento que estás planeando, el orden de prioridad
recomendado es:** primero partir `EditorScreen.kt` en archivos por
categoría (impacto directo en mantenibilidad, cero riesgo de romper
lógica de motor), después ampliar tests sobre `EditorViewModel`/
`ImageEffects` (red de seguridad antes de tocar el motor), y recién
después encarar la migración a C++ — con el proyecto ya ordenado, esa
migración va a ser bastante más simple de planear.
