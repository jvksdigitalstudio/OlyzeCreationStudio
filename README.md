# Olyze Creation Studio

**YeiViKas Digital Company**
Motor: **EliNer** (EliNer API · EliNer Engine · EliNer Render · EliNer Audio · EliNer Core)

App Android nativa para crear "películas" a partir de imágenes estáticas
(PNG con transparencia) usando un motor de cámara virtual con keyframes —
el efecto tipo documental de History Channel / Ken Burns, pero con control
manual completo por capa: pan, zoom, rotación, opacidad y parallax
multi-capa.

## Stack técnico

| Área | Elección | Por qué |
|---|---|---|
| Lenguaje | Kotlin 100% | Estándar moderno de Android |
| UI | Jetpack Compose + Material3 | Más rápido de iterar que XML Views |
| Motor de render | OpenGL ES 2.0 (GLSurfaceView) | Control total de la matriz de cámara por capa, máximo rendimiento |
| Carga de imágenes | Coil (coroutines-first) + BitmapFactory para decodificación a GPU | Ligero y moderno |
| Exportación a video | `MediaCodec` + EGL manual + `MediaMuxer` (fase 2) | Máxima eficiencia, sin dependencias externas |
| Persistencia | `kotlinx.serialization` (JSON) en almacenamiento privado (fase 4) | Proyecto 100% autocontenido, autoguardado con debounce |
| Arquitectura | MVVM (ViewModel + StateFlow) | Separación clara UI / lógica / datos |
| CI/CD | GitHub Actions | Compilación automática del APK en cada push |

> Nota histórica: en una etapa temprana el proyecto declaraba
> `media3-transformer`/`media3-effect`/`media3-common` en `build.gradle.kts`,
> pero el exportador terminó implementándose a mano con `MediaCodec` (ver
> Fase 2). Esas dependencias no se usaban en ningún archivo del código y
> ya fueron eliminadas — ver "Fase 4.0" más abajo. `build.gradle.kts` y
> `proguard-rules.pro` ya no las mencionan.

## Estado actual (Fase 1 — completada en este esqueleto)

- ✅ Importación múltiple de PNG vía Storage Access Framework
- ✅ Cada imagen importada = una capa 100% independiente (`Layer`)
- ✅ Motor de render OpenGL ES con blending alpha real (transparencia del PNG respetada)
- ✅ Sistema de keyframes por capa (`CameraTrack` + `Keyframe`) con curvas de easing (linear, ease-in, ease-out, ease-in-out, cubic)
- ✅ Parallax multi-capa (factor de atenuación de movimiento por capa, para simular profundidad)
- ✅ Preview en tiempo real con scrubber de tiempo y reproducción
- ✅ UI para fijar/quitar keyframes en el instante actual del playhead

## Fase 2 — completada: Exportación a video real

- ✅ `LayerDrawer`: lógica de dibujo extraída de `GLRenderer` y compartida con el exportador (el video exportado es fiel al preview)
- ✅ `VideoExporter`: contexto EGL manual + `MediaCodec` (H.264, superficie de entrada) + `MediaMuxer` → `.mp4` real, sin dependencias externas
- ✅ Botón "Exportar video" con barra de progreso en vivo
- ✅ Compartir el resultado con cualquier app vía `FileProvider` (WhatsApp, Drive, galería, etc.)
- ✅ Export a 1080×1920 @ 30fps por defecto (configurable en `ExportSettings`)

El archivo final queda en el almacenamiento privado de la app
(`/Android/data/com.yeivikas.olyzecs/files/exports/`), por lo
que no requiere pedir permisos de almacenamiento en ningún Android.

## Fase 3 — completada: Look cinematográfico

- ✅ Shader ampliado en un solo pase (sin coste extra de framebuffers): saturación, contraste, temperatura de color, viñeta, grano de película animado y glow en zonas brillantes
- ✅ `LookSettings` es global al proyecto y se aplica igual en preview y en export — el video final se ve exactamente como el preview
- ✅ Preset rápido "Sci-Fi oscuro" (pensado para el tipo de imágenes con brillos de energía azul que usaste de prueba)
- ✅ Sliders en vivo para cada parámetro con botón de reseteo

## Fase 3.5 — completada: UX de manipulación directa

- ✅ Preview **fijo** en la parte superior (46% de la pantalla): ya no desaparece al hacer scroll en los controles
- ✅ Panel de controles independiente y scrollable debajo
- ✅ **Manipulación directa con gestos** sobre el preview: arrastrar con un dedo mueve la capa (pan), pellizcar hace zoom, girar con dos dedos rota — todo en tiempo real, sin pasar por los sliders
- ✅ Los gestos y los sliders están sincronizados al mismo estado: puedes mover con el dedo y luego afinar con el slider, o al revés
- ✅ Botón de reseteo rápido del encuadre de la capa seleccionada

## Fix crítico — closures obsoletas en el preview

Los sliders y gestos calculaban bien los valores nuevos, pero el
`GLRenderer` nunca los veía: `AndroidView.factory` en Compose se ejecuta
**una sola vez**, así que las lambdas que le pasábamos quedaban atadas
para siempre a los valores que existían en el instante exacto de la
primera composición (antes incluso de importar imágenes). Solucionado
envolviendo cada lambda en `rememberUpdatedState`, el patrón estándar de
Compose para este problema exacto — ahora el renderer siempre lee el
valor más reciente en cada frame.

## Fase 3.6 — completada: reemplazar imagen e importar fondo

- ✅ Botón **"Reemplazar imagen"** en la capa seleccionada: cambia el PNG sin perder los keyframes ya puestos
- ✅ Botón dedicado **"F" (fondo)** junto al de importar: manda la imagen directo al fondo de la pila (zIndex más bajo) con parallax reducido por defecto, como en apps profesionales de composición
- ✅ Liberación correcta de la textura anterior en GPU al reemplazar (sin fugas de memoria)

## Fase 3.7 — completada: auto-guardado + panel de capas profesional

**Fix de UX crítico — la posición se "reseteaba" al reproducir:**
Mover la imagen con gestos/sliders nunca se guardaba como keyframe hasta
pulsar "Fijar keyframe aquí"; al no haber keyframes, el playhead mostraba
la transformación identidad (0,0,1,0,1) apenas arrancaba la reproducción,
dando la sensación de que "todo volvía al centro". Ahora **cada gesto y
cada slider guardan automáticamente** un keyframe en el instante actual
del playhead — igual que la manipulación directa de un editor profesional
(Premiere, After Effects). El botón "Fijar keyframe aquí" se mantiene
como acción explícita adicional, ya no es obligatorio.

- ✅ Panel de capas (ícono ☰ arriba a la derecha del preview): lista todas las capas cargadas, ordenadas de la que está más al frente a la que está más atrás
- ✅ Reordenar capas con ▲ / ▼ (cambia el zIndex, es decir qué capa tapa a cuál)
- ✅ Bloquear/desbloquear cada capa (🔒/🔓): una capa bloqueada no se puede mover con gestos ni sliders, protegiendo el encuadre ya afinado
- ✅ Seleccionar una capa directamente tocándola en el panel

## Fase 3.8 — completada: look por capa + UI profesional pulida

- ✅ **Ícono de capas real** (SVG vectorial propio, tres rombos apilados) en vez del ícono de hamburguesa — sin depender de librerías de íconos externas, cero riesgo de build roto por un ícono inexistente
- ✅ Quitados los chips redundantes al pie del preview: la selección de capa vive únicamente en el panel de capas (☰), gana espacio la pantalla
- ✅ **Look cinematográfico ahora es por capa**, no global: cada imagen tiene su propia saturación, contraste, temperatura, viñeta, grano y glow. Tocar una capa en el panel sincroniza AMBOS paneles de ajuste (cámara y color) a esa capa específica, de forma completamente independiente
- ✅ Presets de look ("Sci-Fi oscuro") se aplican solo a la capa seleccionada

## Fase 3.9 — completada: panel de capas pulido (sin emojis)

- ✅ **Miniatura real** de cada capa en el panel (usa Coil para cargarla directo desde el archivo original, sin decodificar de más)
- ✅ Reemplazados TODOS los emojis (🔒/🔓/▲/▼) por íconos SVG vectoriales propios: candado cerrado, candado abierto, flecha arriba, flecha abajo — coherentes en trazo y estilo con el ícono de capas
- ✅ El candado activo se resalta en ámbar para que sea evidente qué capa está protegida de un vistazo

## Fase 3.10 — completada: fix crítico de sincronización + ocultar/eliminar

**Fix crítico — bloquear/reordenar no se reflejaba en pantalla:**
`Layer` es una `data class` con propiedades `var` mutables. Las funciones
del ViewModel mutaban esas propiedades in-place y luego reasignaban
`_uiState.value = _uiState.value.copy(layers = layers.toList())` para
"forzar" la recomposición. El problema: como los objetos `Layer` ya
estaban mutados en memoria ANTES de esa comparación, `StateFlow` (que
compara el valor nuevo contra el guardado estructuralmente) veía el
estado "antes" y "después" como estructuralmente idénticos — porque de
hecho apuntan a los mismos objetos, ya mutados en ambos lados — y
silenciosamente NO emitía la actualización. Por eso bloquear una capa o
reordenarla cambiaba el dato real (por eso el render en vivo, que no
depende de Compose, sí se veía bien) pero la UI de Compose (el ícono del
candado, el orden visual de la lista) nunca se enteraba.

Arreglado con un contador `revision` en `EditorUiState` que se incrementa
en cada mutación de capa, garantizando que el estado nunca sea
estructuralmente igual al anterior y la UI siempre recomponga.

**Nuevas funciones del panel de capas:**
- ✅ Ocultar/mostrar capa (ícono de ojo) — afecta preview, reproducción Y exportación de video
- ✅ Eliminar capa (ícono de papelera) — quita la capa por completo del proyecto
- ✅ Botón de cerrar panel (×) en el header
- ✅ Reordenar (▲/▼) y bloquear/desbloquear (🔒/🔓 vectoriales) ahora sí reflejan el cambio al instante
- ✅ Todos los íconos son SVG vectoriales propios, dibujados a mano — cero emojis en toda la app

## Fase 3.11 — completada: reproductor integrado al preview

- ✅ Controles de reproducción (play/pause + línea de tiempo) ahora viven **dentro** del preview, superpuestos abajo con degradado oscuro — igual que cualquier reproductor de video profesional (YouTube, VLC, etc.), no como una sección aparte del formulario
- ✅ Botón de reproducir/pausar es un ícono SVG propio (triángulo/dos barras) sobre un círculo translúcido, no texto
- ✅ Timecode en formato `m:ss / m:ss` en vez del crudo "X ms / Y ms"
- ✅ El hint de gestos se movió a la esquina superior izquierda para no chocar con la nueva barra inferior

## Fase 3.12 — completada: reproductor tipo cámara profesional + grabación en vivo

- ✅ **Play grande y destacado**, centrado, sobre círculo de color de marca — ya no es un botón chico entre otros
- ✅ **Botón de grabar** (aro blanco = inactivo, aro + punto rojo = grabando): al activarlo, el playhead avanza solo y **cada gesto o slider que apliques sobre la capa seleccionada se graba como keyframe en tiempo real**, capturando un movimiento de cámara "interpretado a mano" sin tener que fijar keyframes uno por uno. Sigue funcionando exactamente igual si prefieres seguir ajustando manualmente con sliders sin grabar — es 100% opcional
- ✅ **Pantalla completa** opcional: el ícono de expandir agranda el preview a toda la pantalla, ocultando el panel de controles; se puede volver a tocar para salir
- ✅ Todos los íconos son SVG vectoriales propios (play, pausa, grabar, pantalla completa) — cero emojis, cero librerías externas

## Fase 3.13 — corrección: grabar solo captura con movimiento real

La primera versión de "grabar" escribía un keyframe cada ~100ms por
temporizador, sin importar si el usuario tocaba algo — inundaba el
timeline de keyframes idénticos incluso estando quieto. Corregido: ahora
**no hay temporizador**. Al pulsar grabar, el botón se pone rojo y el
playhead avanza solo (armado, "esperando"), pero el keyframe real solo se
escribe cuando hay un cambio de verdad:
- Un gesto sobre el preview (arrastrar / pellizcar / rotar con los dedos), o
- Mover cualquier slider de cámara (Pan X/Y, Zoom, Rotación, Opacidad)

Ambas vías son válidas y equivalentes — puedes grabar arrastrando con el
dedo, o grabar ajustando sliders mientras el tiempo corre, o combinar
ambas. Si no tocas nada, no se graba nada.

## Fase 3.14 — corrección: armado vs. grabando de verdad

Faltaba distinguir dos momentos que antes se trataban como uno solo:

- **Armado** (rojo fijo): al pulsar grabar, el ícono se pone rojo pero el
  playhead **no avanza ni un milisegundo** — está en espera.
- **Capturando** (rojo parpadeando): en cuanto detecta el primer
  movimiento real (gesto sobre el preview o un slider de cámara), recién
  ahí arranca el playhead y el ícono empieza a parpadear para dejar claro
  que ahora sí se está grabando.

Esto se maneja con dos banderas separadas en el estado (`isRecording` =
armado, `isCapturing` = grabando de verdad), y el parpadeo es una
animación infinita de opacidad que solo corre mientras `isCapturing` es
true — en los otros dos estados (apagado / armado) el ícono se queda
estático.

## Fase 3.15 — fix: arrastre roto al grabar + parpadeo poco visible

**Arrastrar la imagen dejaba de funcionar al grabar (solo servían los
sliders):** el detector de gestos (`pointerInput`) no incluía `editKey`
entre sus claves de reinicio. Cuando arrancaba la grabación, se creaban
variables nuevas para Pan X/Y/Zoom/Rotación (necesario para que el
arrastre no se reinicie a mitad de camino), pero el gesto seguía atado a
las variables VIEJAS de antes de grabar — exactamente el mismo tipo de
bug de "closure obsoleta" que ya habíamos resuelto en el preview, esta
vez en el gesto táctil. Arreglado incluyendo `editKey` en las claves del
`pointerInput`, así el detector de gestos se realinea con las variables
correctas apenas cambia el estado de grabación.

**El parpadeo del ícono de grabar era difícil de notar:** ahora, además
del ícono, todo el círculo del botón pulsa en rojo — mucho más notorio e
inconfundible que solo la opacidad del ícono cambiando.

## Fase 3.16 — completada: pestañas Cámara / Look cinematográfico

Antes había que hacer scroll largo para llegar al look cinematográfico,
debajo de toda la sección de cámara. Ahora son **dos pestañas** ("Cámara"
y "Look cinematográfico") justo debajo del selector de capa: tocas una y
solo se muestran sus controles, sin scroll extra. Cambiar de capa
reinicia la pestaña a "Cámara" por defecto.

## Fase 3.17 — completada: pestañas fijas + grading de nivel estudio

- ✅ Las pestañas "Cámara" / "Look cinematográfico" ahora quedan **fijas** arriba del panel; solo el contenido de la pestaña activa hace scroll debajo — cero scroll extra para cambiar de sección
- ✅ Nuevos controles profesionales de grading, organizados por categoría:
  - **Exposición y color**: exposición (f-stop), saturación, contraste, temperatura, tinte verde/magenta
  - **Sombras y luces**: levantar sombras (look "película desteñida"), suavizar luces altas (evita quemados), split-tone cinematográfico (sombras frías / luces cálidas, la técnica clásica de blockbusters de Hollywood)
  - **Efectos de lente y film**: viñeta, grano, glow (sin cambios, ya existían)
- ✅ 5 presets de estudio en una fila con scroll horizontal: Resetear, Sci-Fi oscuro, Teal & Naranja, Neón Cyberpunk, Película vintage

## Fase 3.18 — completada: cámara de perspectiva real (tilt 3D, enfoque, handheld)

Hasta ahora el motor era 100% 2D (pan/zoom/rotación plana). Esta fase
monta una **cámara virtual de perspectiva real** matemáticamente
equivalente a una cámara física, no un truco visual:

- ✅ **Tilt vertical y horizontal en 3D real**: inclina la capa con
  foreshortening de verdad (los bordes más cerca de la "cámara" se ven
  más grandes que los que se alejan), no un simple giro plano como la
  rotación de siempre. Matemáticamente elegido para que con tilt=0 el
  resultado sea idéntico al render anterior — cero regresión en proyectos
  existentes.
- ✅ **Enfoque / rack focus**: desenfoque de profundidad animable en el
  tiempo, igual que cuando en cine se "jala" el foco de un punto a otro.
- ✅ **Vibración de cámara (handheld)**: jitter procedural de varias
  frecuencias sumadas, para una sensación de cámara en mano orgánica, no
  un patrón repetitivo.
- ✅ Todo esto es 100% compatible con capas existentes: los campos nuevos
  tienen valor por defecto 0, así que cualquier proyecto viejo se ve
  exactamente igual hasta que decidas usarlos.

## Fase 3.19 — completada: paquete completo de efectos de cámara y lente "nivel estudio"

**Movimiento de cámara**
- ✅ **Dolly zoom (efecto Vértigo)**: la cámara virtual se mueve de verdad en profundidad, con el FOV autocompensado para que el sujeto (capa con parallax 1.0) mantenga su tamaño exacto — el warp real ocurre en las capas de fondo, según su profundidad derivada del parallaxFactor. Matemáticamente correcto, no una simulación.
- ✅ **Motion blur direccional automático**: calcula la velocidad real de la cámara entre un frame y el anterior, y borronea en esa dirección — más rápido el movimiento, más blur, exactamente como el obturador de una cámara real.
- ✅ **Curvas de animación personalizadas (bezier)**: motor de easing tipo `cubic-bezier(x1,y1,x2,y2)` (el mismo estándar de After Effects/CSS), resuelto por bisección. Ya disponible en el modelo de datos (`EasingType.CUSTOM_BEZIER` + 4 puntos de control en cada keyframe).

**Óptica de lente**
- ✅ **Distorsión de lente** (barril/cojín): simula gran angular real o compresión tipo teleobjetivo.
- ✅ **Aberración cromática**: desfase de color en los bordes, el sello de lentes de cine reales.
- ✅ **Lens flare anamórfico**: destello horizontal en zonas brillantes, el look clásico de acción/sci-fi — se ve especialmente bien con las armas de energía azul.
- ✅ **Bokeh anamórfico**: estira el desenfoque de profundidad horizontalmente, el look "Hollywood" del rack focus con lentes anamórficas.

**Composición**
- ✅ **Guías de composición** (regla de tercios + centro): overlay exclusivo del editor — nunca se renderiza ni se exporta al video, es solo una ayuda visual mientras trabajas.

**Profundidad real entre capas**
- Cada capa ahora vive en un plano Z real (no solo simulado): el fondo (parallax bajo) se posiciona físicamente más atrás que el sujeto (parallax 1.0). Esto es lo que hace posible que el dolly zoom y el tilt 3D generen paralaje de verdad.

### Pendiente para la siguiente fase
Quedan fuera de esta entrega, porque requieren cambios de arquitectura del
timeline (no de cámara/shader) y prefiero no mezclarlos a medias:
**speed ramping** (velocidad variable en distintos tramos) y **freeze
frame** (congelar un instante). También queda pendiente la UI de edición
visual de la curva bezier (por ahora se controla por datos; falta el
control deslizante/pad de 2 puntos en pantalla).

## Fase 4 — completada: persistencia real de proyectos ("Mis proyectos")

Hasta ahora, todo lo hecho en la Fase 3 vivía únicamente en memoria (`StateFlow`
del `EditorViewModel`): cerrar la app, o que Android matara el proceso en
background, borraba el proyecto entero sin aviso. Esta fase resuelve eso de
raíz con un sistema de guardado con estándar de app de video profesional
(CapCut / LumaFusion / Premiere Rush), no solo un `SharedPreferences` básico.

- ✅ **Pantalla "Mis proyectos"** como puerta de entrada: grid de tarjetas con
  la miniatura REAL de cada proyecto (con el look cinematográfico ya
  aplicado — grading, viñeta, grano, glow), nombre, fecha relativa
  ("hace 5 min", "ayer") y cantidad de capas
- ✅ **Autoguardado con debounce** (900ms de inactividad): cada cambio real
  (mover una capa, ajustar el look, agregar un keyframe) programa un guardado;
  si llegan más cambios antes de que se cumpla el plazo, se reinicia el
  conteo — diez ajustes seguidos de un slider generan un solo guardado a
  disco, no diez. Indicador discreto "Guardando… / Guardado" bajo el nombre
  del proyecto en la barra superior, mismo lenguaje visual que Google Docs
- ✅ **Guardado inmediato al salir**: el botón atrás (de la app y el del
  sistema operativo) fuerza un guardado sin esperar el debounce antes de
  volver a "Mis proyectos"
- ✅ **Proyecto 100% autocontenido**: cada imagen importada se copia una
  sola vez al almacenamiento privado del proyecto en vez de depender para
  siempre del `Uri` original de Storage Access Framework (que puede
  invalidarse si el usuario mueve/borra el archivo original o Android
  revoca el permiso persistente)
- ✅ Nombre de proyecto editable (tocando el título en la barra superior o
  desde el menú "⋮" de cada tarjeta), duplicar proyecto, eliminar con
  confirmación
- ✅ `kotlinx.serialization` para el formato del proyecto (`project.json`) en
  vez de mapear todo a mano — `Keyframe`, `LookSettings` y `EasingType` son
  directamente serializables, cero código de mapeo manual propenso a bugs
- ✅ Se reemplazó la dependencia `datastore-preferences` (declarada desde la
  Fase 1 pero nunca usada realmente) por la persistencia real descrita arriba

### Detalle técnico: por qué la miniatura no es solo la imagen de portada

`ThumbnailRenderer` corre el mismo `LayerDrawer` que usan el preview en vivo
y el exportador, pero en un contexto EGL propio con una superficie *pbuffer*
(un framebuffer fuera de pantalla) — así la miniatura de "Mis proyectos"
muestra el frame real ya graduado, no solo la foto cruda de la capa de
arriba. Es el mismo patrón de aislamiento de contexto que ya usaba
`VideoExporter` desde la Fase 2, aplicado ahora también a las miniaturas.

## Fase 4.0 — auditoría profesional completa + 5 correcciones

Se revisó el proyecto completo (19 archivos Kotlin, 25 XML, el shader
GLSL línea por línea, y el pipeline de Gradle/CI) buscando bugs reales,
no solo errores de compilación. Se encontraron y corrigieron 5 puntos:

1. **Emojis usados como íconos en `ProjectsScreen.kt`** (🎬 y 🎞️) —
   reemplazados por dos íconos SVG propios (`ic_film`, `ic_image_placeholder`),
   coherentes con el resto de la app.
2. **Eliminar una capa no pedía confirmación** — ahora muestra un diálogo
   ("¿Eliminar esta capa?") antes de borrar, con el nombre de la capa y
   aviso de que la acción no se puede deshacer.
3. **Imágenes cargadas a resolución completa** (riesgo real de quedarse
   sin memoria con fotos de cámara moderna, sobre todo al reabrir un
   proyecto con varias capas): se creó `ImageDecoding.kt`, una utilidad
   compartida que reduce cualquier imagen a un máximo de 2048px en su
   lado más largo — de sobra para exportar a 1080x1920 con margen de
   zoom, sin cargar el original de 4000x6000+ a memoria. Aplicado tanto
   al importar por primera vez como al reabrir un proyecto guardado.
4. **Posible condición de carrera al guardar** (dos escrituras al mismo
   `project.json` solapándose): se agregó un `Mutex` por proyecto — cada
   `projectId` tiene su propio candado, así que proyectos distintos no se
   bloquean entre sí, pero el mismo proyecto nunca se escribe dos veces
   en simultáneo.
5. **Dependencias de Media3 sin usar** (`media3-transformer`,
   `media3-common`, `media3-effect` no aparecían en ningún lado del
   código — el exportador de video es 100% manual con `MediaCodec`):
   quitadas del `build.gradle.kts`, reduciendo tamaño de APK y tiempo de
   compilación sin perder nada.

## Fase 5 — completada: timeline profesional (undo/redo + pista visual)

- ✅ **Undo/Redo real**, accesible desde dos botones en la barra superior.
  Cubre transform de cámara (keyframes), look cinematográfico, bloqueo,
  visibilidad, orden de capas y **color/degradado/modo blanco y negro**
  (ver FASE 1 más abajo — antes de esa fase estas propiedades de color no
  viajaban en el snapshot de undo, así que deshacer un cambio de color no
  revertía nada). Los cambios continuos (arrastrar un slider
  o la imagen con el dedo) se fusionan en una ventana de 600ms para que
  diez ajustes seguidos del mismo gesto sean un solo paso de deshacer, no
  diez; las acciones discretas (un tap: bloquear, reordenar, quitar
  keyframe) siempre generan su propio checkpoint. Historial de hasta 50
  pasos por sesión de edición
- ✅ **Timeline visual** (`TimelineView.kt`): una pista horizontal por capa
  (mismo orden que el panel de capas), con un diamante por keyframe.
  Tocar un diamante selecciona esa capa y salta el playhead a ese
  instante; arrastrarlo horizontalmente retoca su timing en vivo. Un
  playhead vertical compartido cruza todas las pistas a la vez, y tocar
  en cualquier punto de la regla de tiempo mueve el playhead directo ahí
- ✅ `CameraTrack.replaceAll()`: reemplaza todos los keyframes de una capa
  preservando la identidad del objeto (necesario para que el undo pueda
  restaurar un snapshot sin perder la textura GL ya subida a esa capa)

### Detalle técnico: por qué el undo no reemplaza los objetos Layer

Cada [Layer] tiene una textura ya subida a GPU (ver `GLRenderer`). Si el
undo reemplazara el objeto `Layer` completo por uno nuevo reconstruido
desde el snapshot, se perdería esa textura y forzaría un re-decode +
re-upload en el siguiente frame — visible como un parpadeo. En cambio,
`restoreSnapshot()` muta las propiedades de los objetos `Layer` ya
existentes en su lugar (incluido `cameraTrack.replaceAll()` en vez de
reasignar `cameraTrack`), así el undo es instantáneo y sin parpadeo.

## Fase 6 — completada: audio real + exportación configurable

- ✅ **Pista de audio de fondo mezclada de verdad en el .mp4** — no es un
  efecto visual ni un simulacro: el video exportado tiene una segunda
  pista de audio real, reproducible en cualquier player. Pipeline propio
  (`AudioProcessor.kt`) que decodifica el archivo importado a PCM,
  recorta/loopea hasta la duración exacta del proyecto, aplica volumen y
  fade in/out sample por sample, y lo re-encodea a AAC-LC antes de
  muxearlo junto al video en el mismo `MediaMuxer`
- ✅ Controles de audio: volumen (0–150%), silenciar, punto de inicio del
  recorte dentro del archivo original, repetir en loop si el audio es más
  corto que el proyecto, fade in/out independientes. Todo en la pestaña
  **Audio** nueva del panel de edición
- ✅ **Exportación configurable**: duración del proyecto (3–60s, antes fija
  en 8s), calidad (Borrador/HD/Full HD+, cada una con su propio bitrate) y
  formato de salida (9:16 Reels/TikTok, 1:1 feed, 16:9 YouTube) — pensado
  para elegir directamente el destino final del video, no solo un tamaño
  de píxeles abstracto
- ✅ El proyecto sigue siendo 100% autocontenido: el audio importado se
  copia una sola vez a `audio/` dentro de la carpeta del proyecto, mismo
  patrón que las imágenes desde la Fase 4

### Detalle técnico: por qué el audio no forma parte del undo/redo

El audio es una sola pista a nivel de PROYECTO (no por capa, como sí lo es
la cámara), y sus ediciones —volumen, recorte, fade— son ajustes de
"mezcla" más que de puesta en escena de una toma. Meterlo en el mismo
historial que mueve keyframes de cámara hubiera complicado bastante los
snapshots de undo sin un beneficio claro para el flujo real de edición;
por ahora el audio queda fuera de esa pila, con su propio ciclo de
guardado in-place.

### Detalle técnico: por qué el audio se pre-codifica entero antes de mezclar

En vez de intercalar la codificación de audio y video en tiempo real
durante el mismo loop de frames (mucho más complejo de sincronizar sin
introducir bugs sutiles), el audio completo del proyecto se decodifica,
procesa y re-encodea a AAC ANTES de tocar el video — es totalmente
razonable en memoria porque un clip de fondo de 8-60s en PCM ocupa, como
mucho, unos pocos MB. El resultado son dos tracks independientes dentro
del mismo `.mp4`: el reproductor los sincroniza por su propio
`presentationTimeUs`, no por el orden en que se escribieron al muxer.

- ✅ **Preview de audio en vivo** (`AudioPreviewPlayer.kt`): al presionar
  Play en el editor ahora también se escucha el audio de fondo, sincronizado
  con el playhead — antes la Fase 6 solo mezclaba el audio en el `.mp4`
  final, sin forma de monitorearlo mientras se edita. Es un motor separado
  del pipeline de exportación (usa `MediaPlayer` para aproximar en tiempo
  real, no busca ser frame-accurate como sí lo es `AudioProcessor` en el
  export final) y respeta el mismo recorte/loop/volumen configurados

- ✅ **Modo Grabar corregido**: hasta ahora, mover la imagen con el dedo o
  tocar cualquier slider de cámara escribía un keyframe SIEMPRE, sin
  importar si el botón rojo de grabar estaba activo — imposible ensayar
  un encuadre sin dejar rastro. Ahora, fuera de grabación, esos mismos
  gestos y sliders solo actualizan el preview en vivo (para poder probar
  libremente cómo se ve cada efecto); recién se escribe un keyframe real
  cuando el modo Grabar está encendido. El botón explícito "Fijar
  keyframe aquí" sigue funcionando siempre, grabando o no — es una acción
  manual intencional, no un gesto continuo
- ✅ **Switches del panel de Audio reparados**: el mute y el loop dejaron
  de mutar el `AudioClip` in-place y ahora reemplazan la instancia
  completa en cada cambio — el patrón correcto para que Compose detecte
  el cambio sin ninguna ambigüedad, sobre todo tras recargar un proyecto
  guardado
- ✅ **Posar antes de grabar, sin que se resetee**: el fix anterior de
  "no grabar sin el botón rojo" tenía un efecto secundario — activar
  Grabar reseteaba los sliders al valor del keyframe existente en ese
  punto (o al neutro, si todavía no había ninguno), perdiendo la pose que
  el usuario acababa de ajustar a mano para arrancar la grabación desde
  ahí. La causa: el mecanismo que resincroniza los sliders con el modelo
  usaba una sola "key" que, sin querer, también cambiaba de forma al
  activar/desactivar Grabar. Separado en dos disparadores explícitos e
  independientes — cambiar de capa/undo-redo, y mover el playhead a mano
  fuera de grabación — ninguno de los cuales se activa al presionar el
  botón rojo en sí

## Exportación rediseñada: ícono en la barra superior + diálogo modal

La configuración de exportación dejó de estar siempre visible al pie del
panel de edición. Ahora hay un ícono de exportar en la esquina superior
derecha (junto a deshacer/rehacer) que abre un diálogo modal: elegís la
calidad ahí mismo, confirmás, y ese mismo diálogo pasa a mostrar el
progreso y después el resultado (compartir o cerrar) — el flujo estándar
de cualquier editor de video "serio".

### Duración y formato se eligen al crear el proyecto, no al exportar

La duración total y el formato de salida (9:16 Reels/TikTok, 1:1 feed,
16:9 YouTube) dejaron de vivir en el diálogo de exportación y pasaron al
diálogo **"Nuevo proyecto"**, en "Mis proyectos". Tiene sentido: son
propiedades del PROYECTO en sí —afectan todo el timeline, las rampas de
velocidad, los freeze frames— no algo que dependa del momento de exportar.
El diálogo de exportar quedó con lo único que sí es pura configuración
del archivo final: la calidad (bitrate/resolución en píxeles).

## Timeline con playhead en vivo + preview más limpio

- ✅ El texto a la izquierda de la regla de tiempo del timeline (antes fijo
  en "0:00") ahora muestra el **playhead real, actualizándose en vivo**
  mientras avanza — durante reproducción, grabación o al arrastrar el
  scrubber. El extremo derecho sigue mostrando la duración total del
  proyecto, sin cambios.
- ✅ Se quitó el hint "Arrastra · pellizca · gira con 2 dedos" que
  aparecía superpuesto arriba a la izquierda del preview — quedaba
  redundante. El aviso de "Capa bloqueada" se conserva, porque es
  información que sí cambia y vale la pena mostrar.

## Pantalla de edición más limpia: controles arriba, scrubber en pausa

- ✅ **Grabar y Play/Pausa se movieron arriba**, en una barra fija sobre el
  preview (antes vivían abajo, superpuestos a la imagen con un degradado).
  Quedan siempre visibles y accesibles sin importar qué tan larga sea la
  imagen que se está editando.
- ✅ **El scrubber de tiempo simple y el botón de pantalla completa se
  comentaron** (no se eliminaron: el código sigue completo en
  `EditorScreen.kt`, envuelto en `/* ... */`, listo para reengancharse) —
  se van a reubicar más adelante en una pantalla de preview de
  exportación aparte. La pantalla de edición en tiempo real queda más
  despejada mientras tanto: el timeline con las capas y sus keyframes
  (`TimelineView`) sigue exactamente igual, esto solo afectó al scrubber
  simple que vivía pegado al preview.

## Refinamientos de "Mis proyectos" y del diálogo de creación

- ✅ El botón flotante "+" solo aparece una vez que ya existe al menos un
  proyecto — con la lista vacía, mostrar ese "+" junto al CTA central
  "Crear tu primer proyecto" era redundante. En cuanto se crea el primero,
  el FAB aparece fijo (comportamiento nativo del `Scaffold`: no se mueve
  al scrollear la grilla de proyectos)
- ✅ **Duración máxima ampliada a 7 minutos** (antes 60 segundos)
- ✅ **FPS configurable al crear el proyecto**: 30/60/90/120fps, elegido en
  el mismo diálogo "Nuevo proyecto" junto al formato de salida. El
  bitrate de exportación se escala proporcionalmente al fps elegido —
  60fps reparte el mismo presupuesto de datos entre el doble de frames
  que 30fps, así que sin este ajuste la nitidez por frame caería; con
  120fps un proyecto de calidad "HD" exporta con ~4x el bitrate base para
  mantener una nitidez comparable
- ✅ Se corrigió de paso un bug de persistencia: el formato de salida
  elegido al crear un proyecto nunca se guardaba en disco — al reabrir el
  proyecto, siempre volvía al default (9:16) sin importar lo elegido.
  Ahora `aspectRatio` y `fps` viajan en `project.json` como cualquier otra
  propiedad del proyecto

## Fase 6.1 — corrección: flash al cambiar de capa + fps real de playback

**Fix — "resto" visual de la otra capa al cambiar de selección:** no era
caché, era una condición de carrera. El override en vivo que le pasa
`EditorScreen` a `GLPreview` (`getLiveOverride`) capturaba `selectedLayer`
como un `val` normal, congelado en los valores de la ÚLTIMA composición.
`GLSurfaceView` corre en su propio hilo de render, en su propio reloj,
totalmente desacoplado de Compose — y `translateX/Y/scale/...` sí se
actualizan al toque (son `MutableState`, visibles de inmediato), pero el
`id` de la capa capturado en ese `val` recién se refrescaba en la
siguiente recomposición. Resultado: por uno o más frames el hilo de GL
combinaba el id de la capa VIEJA con la transformación de la capa NUEVA,
y dibujaba la capa vieja saltando a la posición/escala de la nueva — el
"flash" reportado. Arreglado leyendo el id a través de
`latestSelectedLayerForDrag` (un `rememberUpdatedState` que ya existía en
el archivo, usado para el mismo problema en el hit-test de drag), cuyo
`.value` se lee en el momento exacto de la llamada del hilo de GL, no al
crear el lambda.

**Fix — el preview no se sentía más fluido aunque el proyecto estuviera a
90/120fps:** dos causas independientes.
1. El playhead avanzaba con un tick fijo de 16ms (~60fps) sin importar el
   fps configurado del proyecto — un proyecto a 120fps igual solo movía
   el playhead ~60 veces por segundo. Ahora el intervalo objetivo se
   calcula desde `projectFps` real, y el avance usa tiempo real
   transcurrido (`System.nanoTime`) en vez de sumar siempre el mismo
   intervalo nominal, para no acumular desfase por el jitter normal de
   `delay()`.
2. La app nunca pedía el modo de refresco alto de la pantalla — sin ese
   pedido explícito, Android puede dejar la app corriendo a 60Hz aunque
   el panel soporte más. Se agregó `DisplayRefreshRate.kt`, que detecta
   el máximo real del equipo y se lo pide a la ventana en `onCreate`
   (`preferredDisplayModeId`). Es un pedido, no una garantía — el sistema
   puede ignorarlo por ahorro de batería o política del fabricante.

**Selector de fps consciente del dispositivo:** no existe un "máximo
universal" de fps en mobile — depende de la pantalla física de cada
equipo (mayoría 60Hz, gama alta 90–120Hz, algunos gamer 144Hz+). El
diálogo "Nuevo proyecto" ahora ofrece 24/30/60fps siempre, más 90/120/144
solo si la pantalla del equipo realmente los soporta (`DisplayRefreshRate.
availableProjectFps`), con una advertencia clara si el fps elegido supera
lo que el PREVIEW en vivo puede refrescar (el video exportado siempre
tiene el fps real elegido, sin importar la pantalla — esa limitación es
solo del preview).

**CI (`.github/workflows/android-build.yml`) endurecido:** se agregó
`android-actions/setup-android@v3` (evita builds rotos si el runner de
GitHub cambia qué SDK/build-tools trae preinstalados), un paso de tests
unitarios antes de compilar, `concurrency` para cancelar builds viejos
redundantes de la misma rama, y se sube el reporte de tests como artifact
solo si algo falla.

## Fase 6.2 — soporte multiarquitectura (ABI): armeabi-v7a + arm64-v8a

El catálogo de arquitecturas de CPU soportadas vive en su propio archivo
exclusivo: `buildSrc/src/main/kotlin/olyze/abi/AbiCatalog.kt` (Kotlin
plano, sin ningún import de Android — solo el objeto `SupportedAbis`).
`app/build.gradle.kts` lo conecta con la Android Gradle Plugin en dos
bloques cortos y claramente marcados (buscar "Soporte multiarquitectura
(ABI)" en ese archivo): `ndk.abiFilters` y `splits.abi`. Ver
`build-config/abi/README.md` para el detalle completo, incluyendo por qué
está organizado así.

Qué hace: genera un APK liviano por arquitectura (`armeabi-v7a`,
`arm64-v8a`) en vez de un único APK "gordo" con las dos adentro, más un
APK universal de respaldo; y restringe `ndk.abiFilters` a esas mismas
ABIs (para cuando el proyecto sume código nativo — hoy no tiene ninguno).

Diseñado para crecer sin reestructurar: sumar una arquitectura nueva
(x86_64, etc.) es agregar una línea al catálogo en `AbiCatalog.kt` —
ningún otro archivo del proyecto necesita tocarse. El workflow de CI ya
estaba preparado para esto: el step que sube el APK como artifact sube
todos los `.apk` de `app/build/outputs/apk/debug/` por patrón, no por
nombre fijo, así que no rompe al pasar de 1 a 3 archivos generados por
build.

**Tres intentos fallaron en CI antes de este.** Los primeros dos, por la
misma causa raíz: la config vivía en un archivo `.gradle.kts` exclusivo
(`build-config/abi/AbiSupport.gradle.kts`) aplicado con
`apply(from = ...)`, que necesitaba importar tipos de la Android Gradle
Plugin. Gradle **no comparte las clases de un plugin declarado vía
`plugins {}` con scripts aplicados por `apply(from = ...)`** — una
limitación real y documentada de Gradle. Un segundo intento agregó un
`buildscript { classpath(...) }` para intentar exponer esas clases, y
tampoco alcanzó. La solución que sí funciona, garantizada por el propio
diseño de Gradle: mover el catálogo a **buildSrc** (compilado y
disponible automáticamente en cualquier build.gradle.kts del proyecto, sin
trucos) y conectar los tipos de Android directamente en
`app/build.gradle.kts`, que es el único script que efectivamente aplica
el plugin — eso resolvió el problema de fondo.

El tercer intento ya compilaba casi todo (bajó de 10 errores a 1), pero
falló en un detalle puntual de tipos: la parte que asignaba un
`versionCode` distinto a cada APK dividido usaba una firma de la API vieja
de variant outputs de AGP (`output.getFilter(OutputFile.ABI)`) que no
coincidía con el tipo real esperado (`VariantOutput.FilterType`, no
`String`) en esta versión de AGP. En vez de arriesgar un cuarto intento
adivinando la firma exacta sin poder compilar localmente, esa pieza se
sacó del build: no es necesaria hoy (el workflow de CI solo genera APKs
debug para pruebas, no builds firmados para Play Store) y queda anotada
en `build-config/abi/README.md` para retomarla — probándola primero en
Android Studio — el día que haga falta publicar APKs sueltos.

**Fase 7 — Profundidad simulada**
- Blur diferencial (foreground nítido / fondo desenfocado) para reforzar sensación 3D desde imágenes 2D


## Cómo compilar

### Opción A — GitHub Actions (recomendado, ya configurado)
Cada `git push` a `main` dispara `.github/workflows/android-build.yml`, que compila
los APKs debug (uno por arquitectura — `armeabi-v7a`, `arm64-v8a` — más un
universal, ver `build-config/abi/`) y los deja disponibles como artifact
descargable desde la pestaña **Actions** del repositorio.

### Opción B — Local (Android Studio)
Abrir la carpeta del proyecto directamente en Android Studio (Hedgehog o superior),
dejar que sincronice Gradle, y ejecutar `Run`.

### Opción C — Termux
```bash
# Este proyecto no incluye gradlew/gradle-wrapper.jar binario (se generó sin red).
# Para compilar localmente en Termux necesitas Gradle instalado o generar el wrapper:
gradle wrapper --gradle-version 8.6
./gradlew assembleDebug
```

## Estructura del proyecto

```
app/src/main/java/com/yeivikas/olyzecs/
├── MainActivity.kt              # Entry point, picker de imágenes (SAF), wiring de dependencias
├── engine/                      # El motor (Etapa 2 de la refactorización: organizado por responsabilidad)
│   ├── core/                    # Contratos compartidos ENTRE módulos del motor (ej. PixelColorSource)
│   ├── scene/                   # Layer.kt, AspectRatioPreset.kt — la escena/canvas del proyecto
│   ├── camera/                  # CameraTrack.kt, Keyframe.kt, CameraFrame.kt, CameraFrameInterpolation.kt
│   ├── animation/               # Easing.kt, SpeedRamp.kt
│   ├── render/                  # GLRenderer.kt, LayerDrawer.kt, ShaderProgram.kt, RenderSnapshot.kt,
│   │                             # GpuTextureLimits.kt + (FASE 3) GLRendererLifecycleState.kt,
│   │                             # GpuHandle.kt, GridTextureCacheState.kt — ver docs/fases/FASE_3_RENDER_GL_LIFECYCLE.md
│   ├── audio/                   # AudioClip.kt, AudioProcessor.kt, AudioPreviewPlayer.kt
│   ├── effects/                 # LookSettings.kt
│   ├── timeline/                # ThumbnailRenderer.kt
│   ├── export/                  # VideoExporter.kt, ExportQuality.kt
│   └── future/                  # Vacío — reservado para bindings JNI (ver su README.md)
├── api/                         # Vacío — reservado para EliNer API (ver su README.md, Etapa 4)
├── data/
│   ├── ProjectStorage.kt         # Persistencia real del proyecto (project.json + assets copiados)
│   ├── ProjectModels.kt
│   ├── LayerRepository.kt        # Importación de URIs -> Layer con bitmap decodificado
│   └── ImageDecoding.kt
├── timeline/                     # TimelineDurationManager + política de expansión (estado puro, sin dibujo)
├── viewmodel/
│   ├── EditorViewModel.kt        # Orquesta el editor — único punto de contacto con engine/*
│   ├── EditorViewModelFactory.kt
│   ├── ProjectsViewModel.kt      # Orquesta "Mis proyectos" — único punto de contacto con ProjectStorage
│   └── ProjectsViewModelFactory.kt
└── ui/
    ├── EditorScreen.kt            # Pantalla principal Compose del editor
    ├── ProjectsScreen.kt          # "Mis proyectos"
    ├── GLPreview.kt               # Puente Compose <-> GLSurfaceView
    ├── TimelineView.kt            # Línea de tiempo visual
    └── AudioAndExportPanels.kt    # Paneles de audio y exportación
```

## Identidad del proyecto

| | |
|---|---|
| Empresa | **YeiViKas Digital Company** |
| Aplicación | **Olyze Creation Studio** (`com.yeivikas.olyzecs`) |
| Motor tecnológico | **EliNer** — EliNer API · EliNer Engine · EliNer Render · EliNer Audio · EliNer Core |

El `namespace`/`applicationId` de la app es `com.yeivikas.olyzecs`, definido en
`app/build.gradle.kts`. El paquete Kotlin sigue la misma ruta:
`app/src/main/java/com/yeivikas/olyzecs/`.

### Arquitectura futura: EliNer

Hoy todo el motor (render OpenGL, audio, exportación) vive dentro del
módulo `:app`, bajo `com.yeivikas.olyzecs.engine`. La migración de identidad
deja preparado (pero **sin crear código todavía**) el terreno para separar
ese motor en sus propios módulos Gradle bajo la marca **EliNer**:

```
Olyze Creation Studio (YeiViKas Digital Company)
│
├── app                # Kotlin + Jetpack Compose — SOLO UI y orquestación
│
├── eliner-api          # Contrato público entre la app y el motor
├── eliner-core         # Modelos y utilidades compartidas del motor
├── eliner-engine        # Motor de cámara/keyframes/timeline
├── eliner-render        # Pipeline de render (OpenGL hoy, JNI/C++ a futuro)
└── eliner-audio         # Procesamiento y mezcla de audio
```

Regla de dependencia (para cuando estos módulos existan): `app` depende
únicamente de `eliner-api`, nunca de `eliner-engine`/`eliner-render`/
`eliner-audio` directamente. Eso permite, a futuro, reemplazar la
implementación interna de EliNer (por ejemplo migrando el render a un
motor C++ vía JNI) sin tocar la capa de UI.

```
Kotlin Android (app)
        │
        ▼
    EliNer API
        │
        ▼
Motor Kotlin (hoy)  →  JNI Bridge → EliNer Engine en C++ (futuro)
```

Ver `settings.gradle.kts` para los `include(...)` (comentados) de estos
módulos futuros.

**Estado de esta preparación:** `engine/` ya está organizado internamente
por los mismos límites que tendrían estos módulos Gradle (`engine/core`,
`engine/render`, `engine/audio`, etc. — ver árbol de arriba), y existe un
paquete `api/` reservado para cuando `EliNer API` se implemente. El plan
concreto de qué migrar primero a C++ y qué hay que resolver antes (tipos
de datos que cruzan JNI, candidato de módulo, riesgo detectado en el
acoplamiento UI→cámara) está documentado en
`engine/future/README.md`. El soporte de arquitecturas de CPU (ABI) para
cuando exista una librería `.so` real ya está andando desde la Fase 6.2
(ver más abajo) y no necesitó cambios para esto.
