# Pendientes técnicos (backlog)

Decisiones tomadas de aplazar a propósito. Cada entrada dice qué falta, por qué se aplazó y cuándo retomarla.

> **Auditoría completa 2026-10-29:** los hallazgos abiertos B-01 a B-12 (publicar en Google Play exige API 36, `EditorScreen` de 18 000 líneas,
> R8, dependencias, capa `api/`, rampas…) están priorizados en [`AUDITORIA_COMPLETA_2026-10-29.md`](AUDITORIA_COMPLETA_2026-10-29.md) §5 y §8.

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

## 4. ~~Cambios de carril de audio sin checkpoint de undo~~ — RESUELTO (2026-10-29)
- **Qué se encontró (distinto de lo que decía este pendiente):** `renameAudioTrack` y los parámetros del reverb YA tomaban checkpoint;
  `reorderTrack` también. Los que faltaban eran **cuatro**: `setAudioGrid`, `toggleAudioOrderLock` y, no listadas aquí, las dos
  confirmaciones de **estirar bordes de clip** (`setAudioClipLength`, `moveAudioClipLeftEdge`), que son las más visibles: estirar un
  clip y luego deshacer otra acción lo revertía en silencio.
- **Corrección:** `recordUndo = true` en la grilla y el candado; en los bordes, checkpoint tras comprobar que el gesto cambia algo
  (un arrastre que no cambia nada no crea un paso de deshacer vacío). Los bordes llegan UNA vez, al soltar el arrastre (la vista previa
  es local), así que no hacen falta checkpoints de gesto.
- **Guardia:** `AudioTrackUndoCoverageTest` recorre `EditorViewModel` y exige checkpoint a toda función que modifique los carriles de
  audio, salvo una lista explícita (infraestructura y los tres mutadores genéricos). Aplicada al código anterior detecta exactamente
  esas cuatro funciones. Sin ejecutar en CI al entregar.
- **Comprobación manual:** estirar el borde derecho y el izquierdo de un clip (con loop), cambiar la rejilla y el candado de orden;
  deshacer cada uno (un paso por acción); hacer otra acción cualquiera y deshacerla: lo anterior debe conservarse.

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

## 7. Modo de trabajo horizontal en tablet (ADR-016)
- **Qué falta:** disposición horizontal del editor (preview + cajón de herramientas lateral + panel de detalles + línea de tiempo
  ancha), aviso inicial «Elige tu modo de trabajo» y pantalla de Ajustes. Hoy la app es solo vertical en todos los dispositivos.
- **Por qué se aplazó (2026-11-03):** no bloquea ninguna publicación — `targetSdk 36` ya cumple lo que Play exige hoy y la salida
  temporal de Android 16 mantiene el vertical en tablets. El editor sigue cambiando (módulos de audio, ventanas flotantes,
  paneles); construir ahora un diseño de tres paneles obligaría a rehacerlo con cada cambio, y el panel de detalles depende de
  qué funciones de edición existan cuando esté maduro.
- **Cómo:** el plan completo, ya decidido con el dueño del producto (D1–D3), está en
  [`adr/ADR-016-modo-de-trabajo-vertical-horizontal.md`](adr/ADR-016-modo-de-trabajo-vertical-horizontal.md): cinco paquetes de trabajo,
  cada uno verificado en CI y en la tablet.
- **Cuándo (lo que ocurra primero):** (a) **antes de subir `targetSdk` a 37** — la propiedad
  `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` desaparece entonces y las tablets pasarían a girar con un diseño vertical
  estirado; el patrón anual de Google Play sugiere agosto de 2027, a confirmar cuando se acerque; (b) cuando el editor esté
  estable y se quiera ofrecer una experiencia de tablet.
- **Mientras tanto:** no añadir código que dependa de que la pantalla sea vertical (medidas fijas del ancho, etc.).

