# ADR-017 — Vista de edición: fondo de trabajo, ajuste de la imagen y vista no destructiva

- **Fecha:** 2026-11-06
- **Estado:** IMPLEMENTADO — pendiente de verificación en CI y en la tablet (ver §8).
- **Origen:** petición del dueño del producto tras usar el modo edición de una capa (pantalla con Color / 3D / Efecto).

## 1. Problema
1. El modo edición ocupaba toda la pantalla pero conservaba la forma del lienzo del proyecto: el verde era un rectángulo con franjas negras arriba y abajo, y la imagen se veía pequeña (con el tamaño que tiene en el proyecto).
2. No había forma de cambiar el fondo para juzgar la imagen (transparencias, brillos, colores).
3. Mover o hacer zoom en el modo edición escribía en la capa del proyecto (`commitLiveFrame`), y deshacer/rehacer reponía posición y escala desde el modelo en mitad de la edición.

## 2. Decisiones (acordadas con el dueño del producto)
| # | Decisión |
|---|---|
| D1 | En modo edición el fondo ocupa **toda el área de trabajo** (sin franjas) y la imagen se **ajusta al 90 %** del visor: una apaisada ocupa el 90 % del ancho; una vertical o cuadrada queda limitada por el alto, para no salirse. |
| D2 | Es solo una **vista**: al salir, la imagen conserva el mismo tamaño y posición en el canvas; solo queda aplicada la edición (color, 3D, efecto). La exportación no cambia. |
| D3 | El modo edición tiene **su propio fondo de trabajo**, elegible, con el **color del lienzo por defecto**: color del lienzo · tablero claro · tablero oscuro · negro · gris medio · blanco · personalizado. Un toque rota entre lienzo → tablero claro → negro → blanco; la pulsación larga abre la lista completa. Es una preferencia de la app (no del proyecto). |

## 3. Diseño: una responsabilidad por pieza
```
engine/render/
  PreviewBackdrop.kt     Modelo PURO del fondo (CanvasColor | Solid | Checkerboard). Sin Android ni OpenGL.
  BackdropDrawer.kt      ÚNICO que habla con OpenGL para el fondo: limpia el frame y, si es tablero, lo dibuja
                         con un shader procedural. Fallo de compilación → degrada a fondo liso, nunca rompe.
  GLRenderer.kt          Recibe `getBackdrop` y llama a backdropDrawer.draw(...) en lugar de drawer.clear().
data/
  EditBackgroundSettings.kt  Vocabulario de la preferencia + (de)serialización validada. Puro.
  EditBackgroundPrefs.kt     Adaptador de SharedPreferences (misma convención que UserColorPrefs).
ui/editview/
  EditViewFit.kt         Matemática pura del ajuste (invierte el cálculo de LayerDrawer.drawLayer).
  EditViewSession.kt     Posición/escala de origen + cuándo (re)aplicar el ajuste. Puro.
  EditBackdropMapping.kt Preferencia → PreviewBackdrop, paleta y rotación rápida. Puro.
  EditBackgroundState.kt Estado observable + persistencia inyectada (sin Android en la lógica).
  EditBackgroundButton.kt Presentación: botón, miniaturas y menú. No conoce el renderer ni el almacenamiento.
EditorScreen.kt          Solo CABLEA: sesión, guardas, efecto de ajuste, botón y diálogo de color.
```
Dependencias en un solo sentido: `ui → data`, `ui → engine`; `data` y `engine.render.PreviewBackdrop` no conocen la UI.

### 3.1 Garantías de que el proyecto y la exportación no se tocan
- `VideoExporter`, `ThumbnailRenderer` y `LayerDrawer` **no se modificaron** y no referencian el fondo (prueba estructural). `LayerDrawer.clear()` solo hacía `glClear(COLOR_BUFFER_BIT)`, que es exactamente lo que hace ahora `BackdropDrawer`.
- `commitLiveFrame()` sale sin escribir cuando hay modo edición: no se modifica la capa ni se graban keyframes.
- `syncSlidersFromModel(keepEditViewTransform)` **sin valor por defecto**: cada llamador debe decidir; en modo edición deshacer/rehacer y el playhead ya no reponen posición/escala (antes sí, y la imagen saltaba).
- Al cambiar de capa, el modo edición se cierra ANTES de resincronizar.
- Al salir se restauran posición **y escala** desde `EditViewSession`.

### 3.2 Por qué el ajuste es del visor y no de la capa
`drawLayer` ya encaja la imagen ("contain") cuando `scale = 1`; `EditViewFit` devuelve el `scale` que hace que el rectángulo (con estiramiento y rotación) mida el 90 % del visor, calculado en el mismo espacio normalizado en que rota la GPU. Se reaplica si el visor cambia de tamaño (giro, teclado) mientras el usuario no haya hecho zoom; si lo hizo, se respeta.

## 4. Alternativas descartadas
| Alternativa | Por qué no |
|---|---|
| Fondo en Compose bajo un GL transparente | Un `SurfaceView` abre un hueco en la ventana: lo dibujado debajo no se ve; además rompe el cuentagotas y la paridad con la exportación. |
| Tablero con ~900 `glClear` + `glScissor` por frame | Cientos de llamadas GL por frame; un shader procedural es una sola. |
| Cambiar `LayerDrawer.clear(color)` | `LayerDrawer` lo comparten exportación y miniaturas: se evita tocarlos. |
| Mutar la escala de la capa al entrar y restaurar al salir (sin guardas) | Deshacer/rehacer y el playhead la reponían desde el modelo; un cierre inesperado dejaría la escala de edición guardada. |
| Redirigir todos los gestos a un estado de vista aparte | ~8 puntos de escritura en un `pointerInput` de miles de líneas; las guardas dan el mismo resultado con 3 cambios puntuales. |

## 5. Cambio de comportamiento (intencional)
Antes, hacer zoom o mover la imagen **dentro del modo edición** modificaba la capa del proyecto. Ahora es solo una vista para mirarla de cerca y no persiste. Lo que persiste es la edición aplicada.

## 6. Riesgos conocidos
1. **Compilación no ejecutada** (sin compilador en este entorno): igual que en las Fases 1–2, la verificación la hace CI. Se comprobaron llaves, imports, firmas reales (`ProfessionalColorPickerDialog`, `AppLogger.e`, `Layer.widthPx`) y todas las aserciones de las pruebas contra los archivos reales.
2. **Vista 3D / perspectiva:** se calculan con la forma del visor. Al ser más ancho que el lienzo, la perspectiva puede diferir ligeramente del encuadre final. Revisar en la tablet.
3. **Shader del tablero:** no se pudo probar en un GPU real. Si no compila en algún dispositivo se registra en «Registro de errores» y se muestra un fondo liso claro.
4. **Flash de un frame:** el `Box` de Compose conserva el verde detrás del GL; con otro fondo puede verse un frame de verde al entrar. Cosmético.

## 7. Pruebas
Lógica pura: `EditViewFitTest` (apaisada/cuadrada/vertical, estiramiento, giro, propiedad «nunca desborda» sobre 480 combinaciones), `EditViewSessionTest`, `EditBackgroundSettingsTest` (ida y vuelta y valores corruptos), `EditBackdropMappingTest`, `PreviewBackdropTest` (incluye que el verde croma coincida con el de `LayerDrawer`), `EditBackgroundStateTest`. Estructural: `EditViewportStructureTest` (guardas, orden de cierre/resincronización, restauración, exportación intacta, modelo puro, preferencia fuera del proyecto).

## 8. Verificación en dispositivo
- [ ] Entrar al modo edición: el fondo ocupa todo el área, sin franjas negras, y la imagen queda al ~90 % (ancho si es apaisada, alto si es vertical/cuadrada).
- [ ] Botón de fondo arriba a la derecha: un toque rota lienzo → tablero claro → negro → blanco. Pulsación larga: lista completa, incluido «Personalizado…» (abre el selector de color).
- [ ] El fondo elegido se recuerda al cerrar y abrir la app, y es el mismo en todos los proyectos.
- [ ] Con un PNG recortado, el tablero muestra la transparencia; los bordes se ven nítidos.
- [ ] Aplicar una edición de color/efecto y salir: **la imagen vuelve al tamaño y posición que tenía en el canvas**, con la edición aplicada.
- [ ] Hacer zoom/mover dentro del modo edición y salir: la capa NO cambió en el canvas.
- [ ] Deshacer/rehacer dentro del modo edición: la imagen no salta de tamaño.
- [ ] Girar la tablet o abrir el teclado dentro del modo edición: la imagen se reajusta si no se hizo zoom.
- [ ] Pestaña 3D: la perspectiva se ve correcta.
- [ ] Exportar un video: idéntico al de antes (fondo verde del lienzo, sin tablero ni color de trabajo).

## 9. Correcciones posteriores a la auditoría (2026-11-06)
Auditoría del código real tras la primera prueba en tablet. Se corrige por fases, cada una con sus pruebas.

### Fase 1 — El ajuste respeta la profundidad de la capa (P1)
- **Defecto:** `LayerDrawer.drawLayer` multiplica el tamaño por `PerspectiveCameraMath.depthCompensationFor(parallaxFactor)`; `EditViewFit` lo ignoraba. Una capa de fondo (`parallaxFactor < 1`, p. ej. el fondo importado con 0,35) se dibujaba a ~132 % del visor en vez de 90 %, saliéndose de la pantalla. Una capa de sujeto (parallax 1) no se veía afectada, por eso pasó desapercibido.
- **Corrección:** `EditViewFit.fitScale` recibe `parallaxFactor` (obligatorio, sin valor por defecto) y divide por la misma función que usan el renderer y el overlay de selección: una sola fuente de verdad. Nuevo `EditViewFit.provisionalScale(parallaxFactor)` para la entrada al modo edición, antes de medir el visor. Un parallax no finito se trata como sujeto (1,0).
- **Sin cambios en:** `LayerDrawer`, exportación, miniaturas, persistencia.
- **Pruebas:** `EditViewFitTest` (compensación, coherencia con lo que dibuja el renderer para 1 / 0,8 / 0,35 / 0, parallax corrupto, provisional = definitiva) y `EditViewportStructureTest` (el cableado pasa el parallax real; el ajuste y `LayerDrawer` usan la misma función).
- **Verificar en tablet:** importar un fondo, entrar al modo edición sobre él: debe quedar al 90 %, igual que una capa de sujeto.

### Fase 2 — El botón de fondo se reconoce con cualquier fondo (P2)
- **Defecto:** la miniatura del botón era del mismo color que el fondo y no tenía marco ni ícono: con verde o negro parecía un cuadro vacío. Nada anunciaba la pulsación larga y el área táctil era de 44 dp.
- **Corrección, una responsabilidad por archivo:**
  - `EditBackdropContrast.kt` — lógica **pura** (sin Compose ni Android): elige ícono negro o blanco según el contraste WCAG 2.x con el fondo (tablero = promedio de sus casillas). Garantiza ≥ 4,5 : 1 con cualquier color.
  - `BackdropSwatch.kt` — solo **dibuja** el relleno de la miniatura (antes era privada dentro del botón).
  - `EditBackgroundButton.kt` — **gestos y menú**: marco de dos tonos (blanco + línea negra translúcida) que se distingue sobre cualquier fondo, ícono «cambiar» con el color de `EditBackdropContrast`, marca triangular en la esquina que anuncia la pulsación larga, y área táctil de 48 dp. Las miniaturas del menú llevan un marco fino para distinguir blanco y negro.
- **Sin cambios en:** `EditorScreen` (misma firma del botón), renderer, exportación, persistencia.
- **Pruebas:** `EditBackdropContrastTest` (extremos, cada fondo de la paleta, promedio del tablero, barrido de color ≥ 4,5 : 1, el alfa no altera la decisión) y `EditBackgroundButtonStructureTest` (área táctil ≥ 48 dp, marco de dos tonos, ícono por contraste, marca de esquina, separación de responsabilidades, sin OpenGL ni almacenamiento en la presentación).
- **Verificar en tablet:** con verde, negro, blanco y tablero (claro y oscuro) el botón se ve como botón; la marca de esquina es visible; un toque rota, la pulsación larga abre la lista.

### Fase 3 — Sin destello de color al entrar al modo edición (P3, riesgo 4 de §6)
- **Defecto:** el contenedor de Compose que rodea a la superficie GL conservaba el verde del lienzo; con un fondo de trabajo distinto (negro, blanco, tablero) podía asomar un frame de verde antes del primer frame GL.
- **Corrección, una regla en un solo sitio:** nuevo `EditViewportBackdrop.kt` (lógica **pura**, sin Android ni Compose) con `effective(isEditing, editBackdrop)` —qué fondo ve el visor según el modo— y `behindSurfaceArgb(...)` —el color del contenedor, igual al color con que el renderer limpia el frame—. Renderer y contenedor consumen la misma función, así no pueden divergir. `EditorScreen` solo cablea: antes repetía la regla en `getBackdrop`.
- **Efecto secundario positivo:** `getBackdrop` ya no lee estado de Compose desde el hilo GL; devuelve un valor ya calculado en la recomposición.
- **Sin cambios en:** `GLRenderer`, `BackdropDrawer`, exportación, miniaturas, persistencia. Fuera del modo edición el color es el mismo de siempre (la prueba de paridad exige que `ChromaKeyGreen` del tema y `CANVAS_CHROMA_KEY_ARGB` del renderer coincidan). El velo de carga del proyecto (otro `ChromaKeyGreen`) no se toca: es otro asunto.
- **Pruebas:** `EditViewportBackdropTest` (fuera del modo edición siempre lienzo; en edición el elegido; contenedor = color de limpieza del renderer para los 6 fondos; negro/blanco sin verde; tablero = casilla clara) y `EditViewportStructureTest` (una sola regla en `EditorScreen`, el contenedor usa `EditViewportBackdrop`, paridad de verdes).
- **Verificar en tablet:** con fondo negro, blanco o tablero, entrar al modo edición no debe mostrar ni un frame de verde; el color del lienzo sigue igual fuera del modo edición.

