# Pendientes técnicos (backlog)

Decisiones tomadas de aplazar a propósito. Cada entrada dice qué falta, por qué se aplazó y cuándo retomarla.

## 1. Persistir la importación de audio pendiente y el cursor (robustez)
- **Qué falta:** si Android mata el proceso mientras el selector de archivos está abierto, el callback `onAudioPicked`
  (campo de `MainActivity`) y el cursor (no persistido) se pierden: al volver, el audio elegido se descarta en silencio.
  Sin pérdida ni corrupción de datos.
- **Cómo:** guardar la petición pendiente (`startMs`, `targetTrackId`) en el estado que Android conserva; aplicarla
  SOLO cuando el proyecto ya esté cargado (importar antes lo haría sobre estado vacío y un autoguardado podría pisarlo);
  guardar el cursor por proyecto (de paso reabre donde se dejó).
- **Antes de empezar:** verificar en el código que el editor reabre el proyecto tras ese cierre del sistema.
- **Prueba:** matar la app con el selector abierto (`adb shell am kill`).
- **Cuándo:** fase de endurecimiento previa al lanzamiento.

## 2. EliNer API con varios carriles de audio
- **Qué falta:** el contrato público ve UN audio (el del carril principal); `getAudioTracks()` es interno (export).
- **Por qué se aplazó:** la API no tiene consumidor externo real; diseñar un contrato público sin uso real es adivinar y
  un contrato publicado es caro de cambiar.
- **Cómo (cuando haga falta):** métodos nuevos y aditivos (listar carriles, agregar/quitar clip indicando carril); los
  actuales no cambian.
- **Cuándo:** cuando exista un consumidor concreto que necesite varios carriles.

## 3. Value object de color común a `Layer` y `AudioTrack`
- **Qué falta:** los ocho campos de color (`customColorArgb`, extremos, ángulo, forma, modo Negro y Blanco,
  `useGradientColor`, `gradientGroup`) se copian a mano en unas diez estructuras (guardado, autoguardado, deshacer,
  semillas del diálogo) para capas y para audio.
- **Por qué se aplazó:** extraerlo toca todo el modelo, el guardado y el deshacer; se hizo el cambio de degradado de
  grupo (ADR-015) sin ampliar ese riesgo.
- **Cómo:** un `data class RowColor` inmutable con las transformaciones de `LayerColor.kt`/`AudioTrackColor.kt`, y
  mapeo único a `LayerData`/`AudioTrackData`.
- **Cuándo:** antes de añadir otro campo de color.

## 4. Cambios de identidad de carril de audio sin checkpoint de undo (nombre, grilla, candado de orden)
- **Qué falta:** `withName`, la grilla (`gridBpm`/`gridStep`) y `toggleAudioOrderLock` llaman a `replaceAudioTracks` sin
  checkpoint. Es el mismo defecto que tenía el color de carril (corregido el 2026-10-17, ver ADR-015): el snapshot de
  undo restaura `audioTracks` completo, así que esos cambios no son deshacibles y un deshacer ajeno los revierte.
- **Cómo:** pasar `recordUndo = true` a `replaceAudioTracks` en esas tres funciones (el parámetro ya existe). Revisar
  antes si el reordenamiento por arrastre de filas de audio tiene el mismo problema.
- **Prueba:** cambiar el nombre de un carril, deshacer otra acción, comprobar que el nombre se conserva; y que un
  deshacer propio lo revierte.
- **Cuándo:** junto con la próxima pasada sobre el undo.

## 5. Realce de fila: un mismo color se ve distinto en una fila suelta y en una de grupo
- **Qué falta:** una fila suelta se pinta con el realce de `GroupGradientEngine.strongAccent` (saturación al máximo,
  brillo +5 %); una fila de grupo con su color exacto (ADR-015, decisión 5). Elegir un tono pastel para una fila suelta
  muestra un tono más saturado que el elegido en el diálogo, y distinto al que daría el mismo color dentro de un grupo.
- **Por qué se aplazó:** normalizar el realce para todas las filas cambia el aspecto de todos los proyectos con colores
  propios; es una decisión de producto, no un bug.
- **Cómo:** o bien mostrar siempre el color exacto (se pierde el "color pintado" de la fila), o bien aplicar el mismo
  realce también a las filas de grupo y migrar. Decidir y actualizar la decisión 5 del ADR-015.

## 6. Un degradado repartido entre varias filas descarta la opacidad
- **Qué falta:** el diálogo permite elegir opacidad en cada extremo, pero `GroupGradientEngine` devuelve siempre colores
  opacos (contrato documentado), así que repartir un degradado semitransparente entre varias filas las deja opacas,
  mientras que un color sólido o el degradado propio de una sola fila sí conservan la opacidad.
- **Cómo:** o interpolar el alfa en el motor (cambia el contrato y las pruebas de `GroupGradientEngineTest`), o
  deshabilitar el control de opacidad en el diálogo cuando hay varias filas marcadas.
- **Antes de empezar:** decidir cuál de las dos; hoy es una limitación conocida, no un error silencioso del motor.

