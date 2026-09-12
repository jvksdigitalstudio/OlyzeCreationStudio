# Trabajo pendiente — "Modo de mezcla" del Resplandor (Outer Glow)

**Estado:** Pendiente de decisión. No accionar sin retomar este documento
primero — hay dos caminos válidos y son mutuamente excluyentes (ver
"Decisión" al final).

**Origen:** reporte del usuario sobre el panel flotante "Resplandor"
(pestaña Efecto → Resplandor). Las 4 combinaciones de "Modo de mezcla"
(Normal/Screen/Add/Lighten) producen exactamente el mismo resultado
visual — confirmado comparando 4 capturas con Intensidad, Difuminado,
Spread, Distancia y Ángulo idénticos, variando solo el modo de mezcla.

## Diagnóstico confirmado (no es apreciación visual, es matemático)

### 1. Dónde vive el control hoy
- Modelo: `GlowBlendMode` (enum `NORMAL, SCREEN, ADD, LIGHTEN`) y el
  campo `glowBlendMode` en `ImageEffectsParams`
  (`engine/effects/ImageEffects.kt`).
- UI: selector `ThreeWayToggle` en `EditorScreen.kt`
  (`EffectsCategoryResplandor`, sección "Modo de mezcla").
- Traducción a compositing nativo: `glowPorterDuffMode()`
  (`ImageEffects.kt`), que mapea a `PorterDuff.Mode.SCREEN/ADD/LIGHTEN`,
  aplicado vía `PorterDuffXfermode` sobre un `Paint` al pintar
  `glowLayer` sobre `canvas` (`ImageEffects.kt`, bloque
  `glowLayer?.let { ... }`).

### 2. Por qué no tiene efecto visual
`ImageEffects` es una herramienta de **bake destructivo**: hornea el
resultado (sombra, contorno, reflejo, resplandor, sujeto) en un bitmap
nuevo que **reemplaza el `sourceUri` de la capa** (ver
`EditorViewModel.applyImageEffects` / flujo de "Guardado definitivo" tipo
`commitLayerRecolor`). Es el mismo patrón que usan Distorsión y 3D en
este proyecto — una vez guardado, es una foto más, sin parámetros vivos.

Dentro de ese bake, el `canvas` sobre el que se pinta `glowLayer` es un
`Bitmap.createBitmap(outW, outH, ARGB_8888)` recién creado —
**completamente transparente** en el punto donde se dibuja el glow (el
sujeto se pinta bastante después, encima, tapando el glow donde se
superponen — orden correcto para un halo exterior, pero significa que el
glow nunca queda debajo de nada opaco salvo 1-2px de antialiasing del
borde del sujeto).

Matemáticamente, componer con `PorterDuff.Mode.SCREEN`/`ADD`/`LIGHTEN`
contra un destino con alpha=0 colapsa a la misma fórmula que `NORMAL`
(src-over): sin destino opaco, no hay "mezcla" posible, el resultado es
idéntico sea cual sea el modo elegido. Por eso las 4 capturas son
pixel-idénticas — es el comportamiento esperado dado cómo está cableado
hoy, no un glitch de un solo punto a parchar.

El fondo verde (`#00B140`) que se ve detrás del sujeto en las capturas
**no es parte de esta capa ni de este bake**: es el color de limpieza
(`glClearColor`) por defecto del lienzo GL, fijado en
`LayerDrawer.ensureInitialized()` — un archivo y una etapa del pipeline
completamente distintos, que ni siquiera existen todavía en el momento en
que `ImageEffects` hornea el glow.

### 3. Conclusión del diagnóstico
Un modo de mezcla necesita un destino contra el cual mezclar. Este bake,
por diseño (capa aislada, exportable/reusable con cualquier fondo, sin
acoplarse a él), no tiene acceso a ningún destino real en el momento en
que se pinta el glow. **No es un bug puntual — es un control que, tal
como está especificado hoy (blend mode dentro de un bake aislado), no
puede cumplir lo que promete con ningún fix quirúrgico.**

## Opciones consideradas

### Opción A — Retirar el control
Sacar `GlowBlendMode`, el campo `glowBlendMode` de
`ImageEffectsParams`, `glowPorterDuffMode()`, el bloque de composición
con `PorterDuffXfermode`, el selector "Modo de mezcla" de
`EditorScreen.kt` (`EffectsCategoryResplandor`) y sus referencias en
`ImageEffectsParamsTest.kt` — sin dejar código muerto. El resto del panel
de Resplandor (Intensidad, Difuminado, Spread, Distancia, Ángulo,
Degradado de 2 colores) queda intacto y sigue funcionando correctamente;
no depende de esto.

**A favor:**
- Es honesto: no deja en producción un control que el usuario cree que
  hace algo y no hace nada.
- Cambio quirúrgico, acotado a los archivos de arriba, sin tocar
  arquitectura ni pipeline de render.
- No compite con la migración a C++ planeada — no invierte esfuerzo en
  `ImageEffects.kt` (Canvas/Bitmap/PorterDuff, 100% Android Graphics API),
  que es justo el tramo que más cambia al migrar el motor de pixel-crunch
  a nativo.

**En contra:**
- Se pierde una funcionalidad que ya estaba visible en la UI (aunque
  nunca funcionó de verdad).

### Opción B — Hacerlo real (glow no destructivo, vía shader)
Ya existe en el motor un glow que sí es en vivo y no destructivo:
`LookSettings.glowIntensity` / `glowThreshold`, calculado por fragment
shader en cada frame (`ShaderProgram.kt`, línea ~170: `glowAmount = max
(brightness - uGlowThreshold, 0.0) * uGlowIntensity`) y subido como
uniforms en `LayerDrawer.drawLayer()`. Migrar el Resplandor (spread,
distancia, ángulo, gradiente de 2 colores, y el blend mode real) a ese
mismo patrón daría acceso genuino al framebuffer/fondo real en cada
frame, donde un blend mode sí tiene sentido matemático.

**A favor:**
- El único camino que hace que "Screen/Add/Lighten" sea una diferencia
  visual real, no cosmética.
- Reutiliza un patrón que ya existe en el motor (no inventa un concepto
  nuevo desde cero).

**En contra / riesgos:**
- Dos glows con nombres/semántica solapada en el mismo shader (el bloom
  existente por brillo vs. un halo exterior con spread/offset/gradiente)
  — riesgo real de romper el bloom actual si no se separa con cuidado.
- Migración de datos: los proyectos ya guardados con Resplandor horneado
  (destructivo) no tienen forma automática de convertirse a parámetros en
  vivo — quedarían con dos comportamientos de "glow" distintos según la
  fecha del proyecto.
- `Lighten` real en GLES2 depende de `glBlendEquation(GL_MAX)`, que a su
  vez depende de la extensión `GL_EXT_blend_minmax` — hay que verificar
  soporte por dispositivo y tener un fallback (candidato: degradar a
  Screen si la extensión no está disponible), no asumir que existe.
- Construye sobre `LayerDrawer`/`ShaderProgram` (GLES2/Kotlin), que es
  justamente la parte del motor con fecha de reemplazo por la migración a
  C++ — riesgo de trabajo tirado si la arquitectura de composición cambia
  (p. ej., a un pase con FBOs y acceso al framebuffer completo en vez de
  un draw call por capa).

## Decisión

**Pendiente.** El usuario está evaluando ordenar el proyecto y planear
una migración del motor a C++ antes de decidir. Recomendación registrada
en la conversación de origen: **Opción A ahora** (retirar el control,
sin dejar código muerto), y revisar la Opción B recién al diseñar el
motor nuevo en C++ — ahí se define de una vez si la composición de glow
pasa a tener acceso real al framebuffer, y con eso un blend mode real
deja de ser una decisión aislada de esta feature.

## Archivos que tocaría cada opción

**Opción A:**
- `app/src/main/java/com/yeivikas/olyzecs/engine/effects/ImageEffects.kt`
  (enum `GlowBlendMode`, campo `glowBlendMode`, `glowPorterDuffMode()`,
  bloque de `PorterDuffXfermode` en la composición del glow)
- `app/src/main/java/com/yeivikas/olyzecs/ui/EditorScreen.kt`
  (selector "Modo de mezcla" en `EffectsCategoryResplandor` y sus estados
  asociados: `glowBlendMode` en el controller/state holder)
- `app/src/test/java/com/yeivikas/olyzecs/engine/effects/ImageEffectsParamsTest.kt`
  (casos de test que referencian `glowBlendMode`/`GlowBlendMode`)

**Opción B (a futuro, no accionar todavía):**
- `app/src/main/java/com/yeivikas/olyzecs/engine/effects/LookSettings.kt`
  (nuevos campos de Resplandor en vivo)
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/ShaderProgram.kt`
  (fragment shader: separar bloom existente de halo nuevo, blend real)
- `app/src/main/java/com/yeivikas/olyzecs/engine/render/LayerDrawer.kt`
  (uniforms nuevos, manejo de `glBlendEquation`/extensión `GL_EXT_blend_minmax`)
- `app/src/main/java/com/yeivikas/olyzecs/engine/effects/ImageEffects.kt`
  (retirar el bake actual del Resplandor una vez migrado, para no dejar
  dos implementaciones vivas del mismo efecto)
- Estrategia de migración de proyectos guardados con Resplandor horneado
  (a definir)
