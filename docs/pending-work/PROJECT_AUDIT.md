# Auditoría general del proyecto

**Estado:** Auditoría de código completada (revisión estática, sin correr
la app en dispositivo). Complementa — no reemplaza — a
`GLOW_BLEND_MODE.md` (hallazgo puntual del Resplandor) y
`EFFECTS_AUDIT.md` (barrido de los 8 efectos). Este documento mira el
proyecto completo: arquitectura, seguridad, configuración de build,
cobertura de tests y mantenibilidad — insumo directo para planear el
reordenamiento antes de la migración a C++.

**Tamaño del proyecto:** 82 archivos Kotlin en `app/src/main`, ~42.500
líneas. 17 archivos de test (13 antes de Fase 3, +4 agregados en Fase 3
— ver actualización correspondiente más abajo; el conteo de "9" de la
auditoría original ya estaba desactualizado desde Fase 2).

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

- El único bug funcional confirmado en todo el código auditado (efectos
  incluidos) sigue siendo el Modo de mezcla del Resplandor —
  `GLOW_BLEND_MODE.md`.
- No se encontró ningún otro control en la UI que prometa un
  comportamiento que el pipeline no pueda cumplir (mismo método de
  búsqueda: `PorterDuff`/`Xfermode`/`BlendMode` en todo `engine/`, cero
  resultados fuera de `ImageEffects.kt`).

---

## Resumen ejecutivo

| Área | Estado |
|---|---|
| Seguridad | Sin hallazgos críticos — Zip Slip ya blindado y testeado |
| Build/release | Sin hallazgos críticos — release bien configurado, base de ABI lista para NDK |
| Tests | Cobertura baja (~11% de archivos antes de Fase 3, 17/86 después) y angosta — `EditorViewModel` y el pipeline de `ImageEffects` sin tests directos |
| Arquitectura | `EditorScreen.kt` (17.4k líneas) es el problema real de mantenibilidad del proyecto — el resto del árbol está razonablemente organizado |
| Render / GL lifecycle | 2 bugs reales de recreación de contexto EGL + ausencia de puente Activity↔GLSurfaceView, corregidos en Fase 3 — ver `docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md` |
| Efectos (Contorno/Resplandor/Sombra/Reflejo/Distorsión/Color/3D) | 1 bug confirmado (Resplandor), ya documentado aparte |

**Para el reordenamiento que estás planeando, el orden de prioridad
recomendado es:** primero partir `EditorScreen.kt` en archivos por
categoría (impacto directo en mantenibilidad, cero riesgo de romper
lógica de motor), después ampliar tests sobre `EditorViewModel`/
`ImageEffects` (red de seguridad antes de tocar el motor), y recién
después encarar la migración a C++ — con el proyecto ya ordenado, esa
migración va a ser bastante más simple de planear.
