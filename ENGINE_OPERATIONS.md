# Operaciones del Engine

> Inventario de capacidades REALES que el motor puede realizar hoy,
> agrupado por dominio. Es la base directa para diseñar la superficie
> de EliNer API v1 (ver `docs/adr/ADR-004-eliner-boundary.md`) — **no**
> es todavía esa API: no asumir que cada ítem de esta lista se
> convierte 1:1 en una función pública.

## Scene / Layers
Crear capa (desde imagen importada), eliminar, reordenar (`zIndex`),
bloquear orden, visibilidad, mover/transformar (keyframes de
`CameraFrame`), aplicar look (`LookSettings`: saturación/contraste/
temperatura), recolorear (`Extrude3D` + `ColorExtraction`), aplicar
extrusión 3D (`Extrude3D.render`).

## Camera
Posición (`translateX`/`Y`), escala, rotación, alpha, interpolación
entre keyframes (`Easing`/`EasingType`).

## Animation
Keyframes de cámara, curvas de easing, velocidad variable + freeze
frame (`SpeedRampEngine`: `speedAt`, `step`, `buildTimeMapping`,
`computeOutputDurationMs`).

## Timeline
Duración inicial/expansión progresiva por tramos
(`TimelineExpansionPolicy`), límites (`TimelineLimits`), notificación
de techo alcanzado (`TimelineDurationManager.events`). No hay un "motor
de reproducción" central único — ver `ARCHITECTURE.md`, sección de
estado, para las 3 fuentes de verdad de tiempo hoy sincronizadas
manualmente por `EditorViewModel` (deuda documentada, no resuelta).

## Render
Composición de capas vía OpenGL ES (`GLRenderer`+`LayerDrawer`+
`ShaderProgram`), cuentagotas de color de píxel (`PixelColorSource`),
generación de miniatura offscreen (`ThumbnailRenderer`).

## Audio
Decodificación/trimming/loop/fade (`AudioProcessor`), reproducción de
preview sincronizada (`AudioPreviewPlayer`), mezcla con video en
exportación (`VideoExporter`).

## Effects
`LookSettings` (saturación, contraste, temperatura) aplicado por capa.

## Mesh3D / 3D
Extrusión completa con bisel configurable (`Extrude3D`: profundidad,
bisel, rotación X/Y/Z, calidad live vs. final).

## Export
Render+encode+mux con MediaCodec/MediaMuxer/EGL, sincronización audio/
video, calidad configurable (`ExportQuality`), progreso
(`ExportProgress`).

## Resource management
Copiar imágenes importadas a almacenamiento local (`LayerRepository`,
fuera del Engine), generar/guardar miniatura (`ProjectStorage` +
`ThumbnailRenderer`), reciclar bitmaps explícitamente en los puntos
correctos.

---

Ver `docs/adr/ADR-004-eliner-boundary.md` para el principio rector de
qué de esta lista se expone como RESULTADO (lo que EliNer API
mostraría) versus qué queda como detalle interno (GLES, MediaCodec,
Mesh3D crudo).
