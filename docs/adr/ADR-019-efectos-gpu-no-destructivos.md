# ADR-019 — Efectos no destructivos en la GPU (Fase 5)

- **Estado:** Aprobado (2026-11-08). **Fase 5.0 implementada**; pendiente de comprobar en la tablet (ver §12).
- **Fecha:** 2026-11-07
- **Depende de:** ADR-018 (vista previa progresiva), que este ADR busca volver innecesaria para los efectos portados.
- **Alcance:** efectos de `ImageEffects` (Color Básico, Contorno, Resplandor, Sombra, Reflejo y relacionados).
- **Fuera de alcance:** Recolor, 3D y Distorsión (motores propios; conservan el comportamiento de la Fase 4).

---

## 1. Contexto — hechos verificados en el código

| # | Hecho | Dónde |
|---|-------|-------|
| 1 | Los efectos son **CPU y destructivos**: ~3.470 líneas, ~40 etapas, 72 parámetros. Cada confirmación escribe un PNG nuevo y **reemplaza `sourceUri`**; «Quitar efecto» vuelve a un Uri guardado. | `ImageEffects.kt`, `commitLayerRecolor`, `revertLayerToUri` |
| 2 | Ya existe una tubería **GPU no destructiva en tiempo real** por capa (`lookSettings`: saturación, contraste, exposición, glow, desenfoque, viñeta, grano…), a resolución completa y **compartida con la exportación**. | `ShaderProgram.kt`, `LayerDrawer.drawLayer`, `LookSettings.kt` |
| 3 | Vista previa y exportación usan el **mismo** `LayerDrawer`. Hay paridad por construcción. | `VideoExporter.kt:145`, `ThumbnailRenderer.kt` |
| 4 | El contexto GL es **OpenGL ES 2.0** (`setEGLContextClientVersion(2)`). | `AndroidGLRenderHost.kt:141` |
| 5 | Existe uso de FBO solo para miniaturas; **no hay ningún pase de efectos a textura**. | `ThumbnailRenderer.kt:144` |
| 6 | El shader de capa usa **`precision mediump float`**. `BackdropDrawer` ya resuelve esto con `#ifdef GL_FRAGMENT_PRECISION_HIGH`. | `ShaderProgram.kt:37`, `BackdropDrawer.kt:196` |
| 7 | La persistencia es `kotlinx.serialization` con `ignoreUnknownKeys = true` y `encodeDefaults = true`: **un campo nuevo con valor por defecto es retrocompatible** y tolerante a versiones futuras. | `ProjectStorage.kt:1061` |
| 8 | `lookSettings` viaja por deshacer/rehacer, guardado, `LayerSnapshot` y `RenderSnapshot`: hay un camino probado que replicar. | `EditorViewModel`, `ProjectStorage`, `LayerSnapshot`, `RenderSnapshot` |
| 9 | `ImageEffectsParams` **no es `@Serializable`** y contiene listas (`ContactShadowPoint`). | `ImageEffects.kt:112` |
| 10 | **No existe ninguna prueba instrumentada de GPU.** `androidTestImplementation(ext.junit)` está declarado sin ejecutor ni fuentes, y el CI solo corre `testDebugUnitTest` + build del APK (sin dispositivo ni emulador). | `build.gradle.kts:163`, `android-build.yml:119` |

### Lo que NO se ha verificado (y por qué importa)
- Rendimiento real de cualquier pase en la GPU de la tablet objetivo: no hay medición.
- Que el contexto EGL del exportador soporte FBO con la misma configuración que el de la vista previa (la miniatura sí lo usa, el video no está comprobado).
- Equivalencia visual GPU ↔ CPU: no existe ni un solo shader de efectos todavía.

---

## 2. Objetivo y criterios de aceptación

El problema reportado: **al aplicar un efecto la imagen pierde calidad**. La Fase 4 lo mitiga; esta fase lo elimina de raíz.

Criterios, todos obligatorios para dar un efecto por portado:

1. **Resolución original en todo momento:** la vista previa se calcula a la resolución de la imagen (con el techo físico `GL_MAX_TEXTURE_SIZE`, ya documentado en `GpuTextureLimits`), no a una copia reducida.
2. **Tiempo real:** mover un control actualiza la imagen sin espera de CPU ni escritura a disco.
3. **No destructivo:** el original no se modifica; los efectos son parámetros. Deshacer, quitar y reeditar son exactos, sin re-decodificar ni recomprimir.
4. **Paridad con la exportación:** lo que se ve es lo que se exporta, por la misma ruta de `LayerDrawer`.
5. **Equivalencia con el resultado actual** dentro de tolerancia perceptual medida (sección 7).
6. **Degradación segura:** si un shader no compila, el FBO está incompleto o falta precisión, el efecto cae al camino CPU actual y queda registrado en el «Registro de errores». Nunca una imagen rota.

---

## 3. Opciones evaluadas

| Opción | Descripción | Veredicto |
|--------|-------------|-----------|
| **A** | Acelerar la CPU (multihilo, mosaicos, menos copias) | **Descartada.** Sigue siendo destructiva y no llega a tiempo real a resolución completa con efectos como sombra o resplandor. |
| **B** | Vista previa en GPU + guardado en CPU (híbrido) | **Descartada.** Dos implementaciones del mismo efecto divergen: la vista previa dejaría de coincidir con lo guardado, justo el defecto que se quiere eliminar. |
| **C** | **Etapa de efectos en GPU, no destructiva, con la CPU como respaldo temporal** | **Elegida.** Una sola fuente de verdad para vista previa y exportación. |

---

## 4. Decisión

Se añade una **etapa de efectos por capa que renderiza a textura (FBO)** antes del dibujo normal de la capa, alimentada por parámetros guardados en la propia capa (igual que `lookSettings`). El resultado se **cachea** por `(versión de la textura de origen, parámetros, tamaño)`: durante la reproducción no se recalcula nada; solo cuando cambian los parámetros.

```
sourceUri (original, intacto)
   │ textura
   ▼
[ Etapa de efectos GPU ]  ← Layer.effectParams
   pases: color → desenfoque → light wrap → composición (sombra/glow/contorno/reflejo)
   ▼ textura resultante (cacheada, con margen)
[ LayerDrawer actual: look, parallax, 3D, keyframes ]  ← sin cambios de contrato
   ▼
 pantalla  /  VideoExporter  /  ThumbnailRenderer
```

### 4.1 Geometría: el margen que añaden los efectos
Sombra, resplandor, contorno y reflejo **se extienden más allá de la imagen**. Hoy eso agranda el bitmap guardado y la capa «cambia de tamaño» (el pendiente P4).

Aquí el margen es una función **pura** de los parámetros (`EffectPaddingCalculator`). La textura resultante es mayor que la fuente, y el quad de la capa se **amplía en la misma proporción**, de modo que el sujeto conserva exactamente su tamaño y posición en el lienzo. Esto resuelve P4 por diseño, no por parche.

### 4.2 Modelo de datos
- Nueva propiedad de capa `effectParams`, con valor por defecto «neutro». Mismo recorrido que `lookSettings`: `Layer`, `ProjectModels`, `ProjectStorage`, `LayerSnapshot`, `LayerApiImpl`, snapshots de deshacer en `EditorViewModel`, `RenderSnapshot`.
- Como `ImageEffectsParams` no es serializable (hecho 9), se define un modelo propio, `LayerEffectParams`, **`@Serializable` directamente**, igual que `LookSettings` (convención del proyecto: `ProjectModels` guarda `LookSettings` sin modelo intermedio). *Corrección respecto a la propuesta inicial, que preveía un modelo de persistencia aparte: al leer el código real ese mapeo habría sido código extra sin ventaja.* La evolución es por campos nuevos con valor por defecto; si alguna vez hiciera falta una migración no aditiva se añadirá un `schemaVersion` en ese momento.
- **Retrocompatibilidad:** proyectos existentes cargan con parámetros neutros (hecho 7). Las capas **ya horneadas** siguen siendo lo que son: su `sourceUri` es el PNG con el efecto incluido, y no hay forma de «des-hornear». Solo las ediciones nuevas son no destructivas. Un proyecto sin `effectParams` se exporta idéntico a hoy.

---

## 5. Arquitectura por componentes (una responsabilidad por archivo)

| Componente | Responsabilidad | Android/GL |
|------------|-----------------|-----------|
| `EffectPaddingCalculator` | Margen (px) que necesitan unos parámetros | No — puro, probado en JVM |
| `EffectPassPlan` | Parámetros → lista ordenada de pases a ejecutar (y cuáles se omiten por ser neutros) | No — puro, probado en JVM |
| `EffectCacheSignature` | Clave de caché: `(versión de la textura de origen, parámetros, tamaño)`. *Sustituye al `EffectParamsHash` previsto: `LayerEffectParams` es un `data class`, y su igualdad ya es la clave exacta, sin hash que pueda colisionar.* | No — puro |
| `GpuTexturePool` | Reserva y reutiliza FBO/texturas dentro de un presupuesto de memoria; respeta `GpuTextureLimits`; se recrea al perder el contexto | GL |
| `EffectPassRunner` | Ejecuta un plan: ping-pong entre texturas, un shader por etapa | GL |
| `shaders/*` | Un fuente GLSL por etapa, con `highp` cuando existe | GL |
| `EffectResultCache` | Un resultado por capa, con presupuesto en bytes y expulsión por uso reciente; se invalida cuando cambia la versión de la textura de origen | No — puro |
| `EffectGpuAvailability` | Decide GPU o respaldo CPU; registra el motivo | GL |
| `EffectSelfTest` | Compara GPU contra CPU y reporta métricas (sección 7) | GL |

`LayerDrawer`, `GLRenderer`, `VideoExporter` y `ThumbnailRenderer` solo **consumen** la textura resultante: no conocen las etapas.

---

## 6. Inventario de etapas y dificultad real en ES 2.0

| Etapa (hoy en `ImageEffects`) | Técnica GPU | Riesgo |
|---|---|---|
| Brillo/contraste, saturación, matiz | Operación por píxel, un solo pase (varias son compatibles con el shader de capa existente) | **Bajo** |
| Nitidez (unsharp mask) | Desenfoque + diferencia, 2-3 pases | Bajo |
| Desenfoque de fondo (con máscara de alfa protegida) | Desenfoque separable por mosaico de tamaño fijo, máscara aparte | Medio |
| Sombra, sombra de relleno, resplandor (con degradado y modos de mezcla) | Silueta → desplazamiento → desenfoque separable → tinte → composición | Medio |
| Light wrap | Desenfoque direccional del entorno + máscara | Medio |
| Contorno (fuera / centro / dentro, degradado, suavizado) | Dilatación/erosión con radio arbitrario: en ES 2.0 solo con bucles de **tamaño constante** (filtro mín/máx separable por tramos o *jump flooding* con pases multiplicados) | **Alto** |
| Sombra de contacto, oclusión del suelo | Gradientes procedimentales sobre la silueta | Medio |
| Reflejo (perspectiva, ondas, desenfoque progresivo, Fresnel, grano) | Matriz de perspectiva en la geometría + ondas en shader + desenfoque variable por fila | **Alto** |
| Grano / ruido (sombra y reflejo) | Ruido por *hash* determinista (el de la CPU no es reproducible bit a bit) | Medio |

**Restricciones de ES 2.0 que condicionan el diseño:** sin `texelFetch`, bucles solo de límite constante en el fragmento, sin múltiples destinos de render, precisión de FBO garantizada solo en RGBA8. Implica radios grandes resueltos en varios pases, no en uno.

**Consecuencia sobre el resultado:** el desenfoque de la CPU es de cajas y el de la GPU será gaussiano separable. **No pueden ser idénticos píxel a píxel.** La equivalencia es perceptual y se mide (sección 7); decirlo así es parte de la decisión.

### 6.1 Precisión (riesgo de calidad propio de la GPU)
El shader de capa usa `mediump` (hecho 6). En muchas GPUs móviles eso es `float` de 16 bits: con texturas grandes provoca **ajuste a píxel de las coordenadas y bandas de color**, que es exactamente una pérdida de calidad. Las etapas de efectos se compilan con `highp` (patrón de `BackdropDrawer`). En un dispositivo sin `highp` en fragmento, el efecto cae al respaldo CPU (criterio 6) en vez de mostrar una versión degradada.

### 6.2 Memoria
Una textura RGBA8 de 4096×4096 ocupa 64 MB, y el ping-pong exige al menos dos más la caché. `GpuTexturePool` trabaja con un **presupuesto explícito**; si una capa excede el techo del hardware, el pase corre al máximo soportado por la GPU (la misma política que ya aplica la subida de texturas) y se registra. Nunca una reducción silenciosa.

---

## 7. Verificación — cómo se sabe que un efecto está bien portado

El CI **no tiene GPU** (hecho 10), así que ningún shader puede validarse allí. Se proponen tres capas:

1. **Pruebas unitarias en JVM (CI, ya existe la infraestructura):** `EffectPaddingCalculator`, `EffectPassPlan`, `EffectCacheSignature`, serialización y migración de `effectParams`, y estructurales sobre archivos reales (igual que ADR-017/018).
2. **Autotest dentro de la app (en tu tablet):** `EffectSelfTest` renderiza un conjunto fijo de imágenes de prueba con la **CPU actual (`ImageEffects.apply`, que se conserva como referencia hasta el final)** y con la GPU, y escribe en el «Registro de errores», por efecto, el error medio, el percentil 99 y el máximo. Como el flujo de trabajo es compilar y probar en el dispositivo, esta es la vía que realmente puedes ejecutar sin ordenador.
3. **Prueba de paridad vista previa ↔ exportación:** exportar un fotograma y comparar con la captura de la vista previa de la misma capa.

**Criterio por efecto (a ratificar con las primeras mediciones reales):** error medio ≤ 2/255 y percentil 99 ≤ 8/255 sobre las imágenes de prueba, sin diferencias visibles en bordes. Estas cifras son un punto de partida razonable, no un dato medido; se ajustarán con el primer efecto portado y quedarán documentadas.

Cada efecto se entrega detrás de un **indicador por efecto** con el respaldo CPU activo hasta pasar el criterio. No se retira el camino CPU hasta que todos lo hayan pasado.

---

## 8. Fases y criterio de salida

| Fase | Contenido | Criterio de salida |
|---|---|---|
| **5.0 Cimientos** | `effectParams` persistido y deshacible; `EffectPaddingCalculator`, `EffectPassPlan`, `EffectCacheSignature`; `GpuTexturePool`, `EffectPassRunner`, `EffectResultCache`, `EffectGpuAvailability`, `EffectSelfTest`; integración en `LayerDrawer`/exportador con **un pase identidad**. **Sin ningún efecto visible nuevo.** | Pruebas JVM verdes; con pase identidad, vista previa y exportación idénticas a las de hoy; **FBO verificado en el contexto del exportador**; el autotest ejecuta y reporta. |
| **5.1 Color Básico** | Brillo, contraste, saturación, matiz, nitidez en GPU | Criterio de equivalencia; sin espera al mover controles; «Quitar efecto» exacto sin re-decodificar |
| **5.2 Familia de desenfoque** | Desenfoque de fondo, Sombra, Sombra de relleno, Resplandor | Equivalencia; margen y tamaño del sujeto conservados (cierra P4) |
| **5.3 Contorno** | Fuera / centro / dentro, degradado, suavizado | Equivalencia en bordes |
| **5.4 Reflejo y resto** | Reflejo, light wrap, sombra de contacto, oclusión del suelo | Equivalencia |
| **5.5 Retiro de la CPU** | La interfaz deja de hornear; `ImageEffects.apply` queda solo como referencia de pruebas | Todos los criterios cumplidos en el dispositivo |

**Por qué 5.0 va sola:** concentra el mayor riesgo técnico (FBO en ambos contextos, modelo de datos, caché) **sin** el riesgo de acertar con un efecto a la vez. Si algo falla, falla con un pase identidad, donde el resultado esperado es trivial.

---

## 9. Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| No puedo ejecutar GLES en este entorno: un shader puede compilar mal o verse distinto | Indicador por efecto + respaldo CPU + autotest en el dispositivo; nada se hace predeterminado sin medición |
| FBO inestable en el contexto del exportador | Se comprueba primero en 5.0, con un pase identidad |
| Pérdida de contexto GL (rotación, segundo plano) | `GpuTexturePool` se recrea con `GLRendererLifecycleState`; la caché se invalida |
| Presupuesto de memoria en tablets modestas | Presupuesto explícito en el pool + registro; el respaldo CPU sigue disponible |
| Equivalencia imposible a nivel de bit | Criterio perceptual medido, declarado desde el inicio |
| Alcance enorme (72 parámetros, ~40 etapas) | Fases con criterio de salida; ninguna fase depende de que la siguiente exista |

---

## 10. Consecuencias

- (+) Vista previa a resolución original y tiempo real; desaparece la espera de CPU y la copia reducida (ADR-018 deja de aplicar a lo portado).
- (+) Deshacer, quitar y reeditar exactos y sin pérdida acumulada por recompresión.
- (+) Una sola implementación por efecto para vista previa y exportación.
- (+) P4 resuelto por geometría, no por parche.
- (−) Trabajo grande, repartido en 6 fases; la fase 5.4 y el contorno son los más costosos.
- (−) Durante la transición coexisten dos caminos (GPU y CPU), con un indicador por efecto.
- (−) El resultado GPU no será idéntico bit a bit al de la CPU; sí equivalente dentro de tolerancia medida.

---

## 11. Decisiones que requieren aprobación

1. **Adoptar el modelo no destructivo** (`Layer.effectParams` persistido) en lugar de seguir horneando PNG. Es un cambio de modelo de datos, retrocompatible.
2. **Aceptar la equivalencia perceptual** (no idéntica bit a bit) como criterio de portado.
3. **Verificar con el autotest en la app**, ya que el CI no tiene GPU.
4. **Empezar por 5.0 (cimientos con pase identidad)** y no por un efecto.

---

## 12. Estado de implementación — Fase 5.0 (2026-11-08)

### Construido
- **Modelo de datos:** `Layer.effectParams` (`LayerEffectParams`/`ColorBasicParams`), persistido en el proyecto, sanado al cargar de disco, incluido en deshacer/rehacer (`LayerContentState`, `LayerEditState`), en el snapshot de render y en el de la API; `EditorViewModel.updateEffectParams` (deshacible, sanea en la frontera). Un proyecto guardado antes carga con parámetros neutros.
- **Núcleo puro** (`engine/effects/gpu/`, probado en JVM): `LayerEffectParams`, `EffectPassPlan`/`EffectPassPlanner`, `EffectPaddingCalculator`, `EffectGpuAvailability`, `EffectResultCache`, `GpuTexturePool`, `ImageDiff`, `EquivalenceCriteria`, `EffectTestPattern`.
- **Capa GL** (`engine/effects/gpu/gl/`): `GlStateGuard`, `GlShaderCompiler`, `EffectShaders`, `GlTextureAllocator`, `EffectPassRunner`, `GpuEffectStage`, `GpuEffectSelfTest`.
- **Integración:** una llamada `effectStage.textureIdFor(...)` antes de cada `drawLayer` de capas en `GLRenderer`, `VideoExporter` y `ThumbnailRenderer`. La rejilla de referencia no es una capa y no pasa por la etapa.

### Comportamiento hoy
- **Ninguna capa tiene efectos GPU** (`effectParams` es siempre neutro porque la interfaz aún no lo escribe) y **ningún pase está activado** (`GpuEffectStage` se construye con `enabledPasses = emptySet()`). Para todas las capas `resolve` devuelve la textura sin tocarla: el dibujo es el mismo de antes, por construcción.
- El pase `IDENTITY` solo lo ejecuta el autotest. `COLOR_BASIC` existe en el modelo pero aún no tiene shader; cae al camino CPU (`PASS_NOT_IMPLEMENTED`).

### Desviaciones respecto al diseño (y por qué)
1. Modelo `@Serializable` directo y `EffectCacheSignature` en lugar de `EffectParamsHash` (ver §4.2 y §5).
2. `EffectPassRunner` ejecuta **un pase por llamada** y la etapa solo acepta planes de **un** pase: los planes de varios pases (con ping-pong de texturas) llegan con la fase 5.1, que es cuando hacen falta. Hoy el único pase real es la identidad.
3. El margen que añaden los efectos está calculado (`EffectPaddingCalculator`) pero el dibujo aún no amplía el cuadro de la capa: un plan con margen cae al camino CPU y se registra. Es la fase 5.2.
4. Presupuesto de texturas de 192 MB: valor inicial **sin medir**.

### Qué está verificado y qué no
- **Verificado (estático y JVM):** lógica pura, persistencia (ida y vuelta, proyecto antiguo, bloques futuros, datos corruptos), recorrido completo de `effectParams` por los 9 puntos de `lookSettings`, integración en los tres renderers, restauración del estado GL, núcleo sin dependencias de Android. Las pruebas están escritas pero **no se han ejecutado** (no hay compilador en el entorno de trabajo): las ejecuta el CI.
- **NO verificado — requiere la tablet:** que el código OpenGL compile y funcione, que el FBO sea completo en el contexto del **exportador**, y cualquier medida de rendimiento.

### Cómo comprobarlo en la tablet
1. Abrir la app y entrar al editor con un proyecto. En el **Registro de errores** debe aparecer una línea de la forma
   `[vista previa] FBO=sí highp=sí shader=sí error medio=0.0000 p99=0 máx=0 (de 255) — OK: la tubería copia la imagen sin alterarla`.
2. **Exportar un video** (basta uno corto). Debe aparecer la misma línea con `[exportador]`. Esa línea es el criterio de salida «FBO verificado en el contexto del exportador».
3. Si aparece `NO está disponible` o `FALLA`, enviar el texto de esa línea: indica qué parte (FBO, `highp`, shader o diferencia de imagen) falló.
4. El resto de la app (vista previa, exportación, miniaturas) debe comportarse igual que antes; un video exportado debe salir idéntico.

### Correcciones tras la auditoría (2026-11-09)
- **Vía rápida sin asignaciones:** `GpuEffectStage.resolve` devolvía las capas sin efectos solo *después* de construir un plan (que crea copias saneadas) en cada frame y capa, lo que contradecía el «coste cero» de este ADR. Ahora `isNeutral` (solo lee números) responde antes.
- **Registro de motivos sin texto por frame:** la clave es constante y el mensaje solo se construye la primera vez.
- **Ejecutor perezoso:** `ThumbnailRenderer` crea una etapa por miniatura; el búfer nativo del ejecutor ya solo se reserva si alguna capa tiene efectos.
- **Guarda de textura inválida** (`id <= 0`, tamaño nulo): se deja pasar como una capa sin efectos.
- **Para la fase 5.1:** `EditorViewModel.updateEffectParams` crea un punto de deshacer por llamada. La interfaz deberá agrupar un gesto de arrastre en una sola, como ya hace con `lookSettings`.

### Segunda auditoría (2026-11-14)
- **La etapa recibe el tamaño REAL de la textura**, no el lógico de la capa. `LayerTextureRecord` ahora guarda el tamaño del bitmap subido (puede estar recortado al máximo de la GPU, o ser una vista previa); sin eso, la etapa habría reservado destinos de tamaño incorrecto. El dibujo sigue usando el tamaño lógico para la relación de aspecto.
- **La caché se invalida por versión de la textura de origen, no por revisión de contenido.** Las vistas previas de la Fase 4 suben una textura nueva con la MISMA revisión (y a veces el mismo tamaño); con la clave anterior, una capa con efectos GPU habría mostrado un resultado obsoleto. Tampoco sirve el id de textura (OpenGL lo reutiliza al liberarse). `GLRenderer` asigna un contador por subida (`uploadSerial`).
- **`GpuEffectStage.textureIdFor` devuelve un `Int`** y `ResolvedTexture` desaparece: tras lo anterior solo aportaba el id, y construirla en cada capa y frame contradecía la vía rápida sin asignaciones.

