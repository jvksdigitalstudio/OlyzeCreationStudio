# FASE 22 — Clip de audio: arrastre por cabecera, selección por clip y líneas de loop

> Alcance: `engine/audio/AudioClip.kt`, `viewmodel/EditorViewModel.kt`,
> `ui/AudioTrackRow.kt`, `ui/EditorScreen.kt` + tests.
> **Estado de verificación:** no se pudo compilar ni ejecutar tests en esta
> sesión (sin toolchain). La aritmética de los tests se validó con una réplica
> numérica de las funciones puras (259 muestras). La verificación real es
> `gradle testDebugUnitTest assembleDebug` (CI) + prueba en dispositivo
> (checklist al final).

## Requisitos (pedido del usuario, con capturas de FL Studio Mobile)
1. Mover el clip arrastrando **la cabecera** (la barra con el nombre), desde
   **cualquier punto** de ella, solo en horizontal y dentro de su carril.
   Perfeccionar la sensación del arrastre.
2. Tiradores de estirado en loop: **líneas gruesas por fuera** del clip (no
   botones, no dentro). Con loop apagado el clip **no se puede estirar** y
   queda en su largo original. Con loop activo, arrastrar cualquiera de las
   dos líneas repite el audio hacia ese lado.
3. Las líneas aparecen **solo si el clip está seleccionado** (y tiene loop).
   Un clip sin seleccionar no muestra borde ni líneas.
4. **Toque largo** sobre la señal de audio (cuerpo) abre el menú del clip.

## Diagnóstico del arrastre "limitado/feo"
| Causa | Efecto | Solución |
|---|---|---|
| Los tiradores de recorte (hasta 16 dp por lado) vivían **dentro** del clip y tapaban los extremos de la cabecera | Tocar la barra "a veces sí, a veces no" | Los tiradores salen del clip; la cabecera queda libre de borde a borde |
| El gesto se registraba con claves que cambian (`timelineStartMs`, `pxPerMs`, `trackWidthPx`) | El gesto se reiniciaba/cortaba si cambiaba la geometría durante el arrastre | Claves estables + `rememberUpdatedState` para leer siempre el valor vigente |
| `detectDragGestures` (ambos ejes) | Un movimiento vertical iniciaba un arrastre horizontal y bloqueaba el scroll del timeline | `detectHorizontalDragGestures`: lo vertical queda para el scroll |
| El desplazamiento se acumulaba **sin límite** y se acotaba solo al dibujar | "Zona muerta": pasarse del inicio/final obligaba a devolver todo el exceso con el dedo | Se acota **mientras se acumula** |
| Franja de 12 dp como único objetivo táctil | Difícil de acertar con el dedo | Zona de arrastre de 16 dp (`CLIP_DRAG_ZONE_HEIGHT`), todo el ancho del clip |
| Sin feedback al agarrar | No se notaba que el clip estaba tomado | La cabecera se ilumina mientras se arrastra |

## Diseño

### Dominio (`AudioClip.kt`, funciones puras con primitivos)
- `clipLengthAfterRightEdgeMs`: sin loop devuelve siempre el largo actual; con
  loop acota a `[MIN_AUDIO_CLIP_LENGTH_MS, resto del proyecto]`.
  `AudioClip.clampClipLengthMs` delega en ella.
- `leftEdgeDeltaRangeMs` / `moveLeftEdge`: borde izquierdo **solo con loop**.
  Estirar a la izquierda mueve la **fase** del loop, no el contenido:

      timelineStart' = timelineStart + d
      trimStart'     = (trimStart + d) mod sourceDuration     // en [0, D)
      clipLength'    = clipLength - d

  El audio no se corre de lugar: en todo instante ya cubierto suena lo mismo
  (propiedad testeada). `sourcePositionAtProjectMs`, el preview en vivo y
  `AudioProcessor.buildProjectSamples` (export) resuelven `trimStart'` con la
  misma cuenta (primera pasada desde `trimStart`, luego reinicio en 0), por lo
  que **no se tocó** ninguno de ellos.
- Se eliminó `clampLeftEdgeDeltaMs` (recorte del inicio sin loop): contradice la
  regla "sin loop el clip queda original". Recortar sigue siendo posible con
  *Dividir en el cursor* + *Eliminar*.

### ViewModel
`trimAudioClipLeftEdge` → `moveAudioClipLeftEdge` (misma función pura que la
vista previa). `setAudioClipLength` no cambió: la regla de loop vive en
`clampClipLengthMs`, así que también protege contra llamadas que no vengan del
gesto.

### Selección por clip
Antes la selección era por **carril**. Ahora `EditorScreen` guarda
`selectedAudioClipId` y lo entrega por `AudioClipEditActions.selectedClipId` /
`onSelectClip`. `clipSelected = carrilSeleccionado && selectedClipId == clip.id`.
Tocar la fila o el carril vacío limpia la selección de clip. El clip
seleccionado sube con `zIndex(1f)` para quedar por encima de sus vecinos, y la
selección de tiempo con `zIndex(2f)` para seguir por encima de todo.

### UI (`AudioTrackRow.kt`)
- **Cabecera = única zona de arrastre** (+ toque = seleccionar).
- **Cuerpo (señal)**: toque = seleccionar, **toque largo = menú** (tiempo
  estándar del sistema, `ViewConfiguration.longPressTimeout`).
- **Líneas de loop** (`LoopStretchHandle`): barra de 5 dp con puntas redondeadas,
  a 2 dp del clip, zona táctil de 24 dp **toda por fuera**. Solo con clip
  seleccionado + loop + archivo con duración válida. El contenedor del clip
  incluye las zonas táctiles dentro de sus propios límites (no se depende de
  eventos fuera de los límites del padre). El espacio libre se mide con la
  geometría **confirmada**, no la de la vista previa, para que la línea no
  desaparezca a mitad del gesto. Si el clip está pegado al borde del carril,
  esa línea no se ofrece.
- Sin borde cuando el clip no está seleccionado (referencia FL Studio Mobile).

## Módulos de audio sobre el clip seleccionado
Los módulos flotantes (Módulos → Audio: Volumen, Silencio, Recorte, **Loop**,
Fade) y *Renombrar* actuaban sobre el clip bajo el cabezal. Con selección por
clip eso podía cambiar un clip distinto al visible como seleccionado (y las
líneas de loop no aparecerían). Nuevo `AudioTrack.moduleTargetClip(playheadMs,
selectedClipId)`: con un clip seleccionado (y el carril seleccionado) los módulos
lo editan a ÉL; si no, se conserva el comportamiento anterior (clip bajo el
cabezal, o el más cercano).

## Revisión final (auditoría del código implementado) — hallazgos y correcciones
Antes de entregar se auditó todo el diff (dominio, ViewModel, pantalla, UI de
gestos, tests). Se encontraron y corrigieron, de forma quirúrgica:

| # | Hallazgo | Riesgo | Corrección |
|---|---|---|---|
| 1 | El clip seleccionado se **reordenaba en la composición** (para dibujarlo encima) y la selección ocurre al empezar a arrastrar | Mover un nodo con un gesto en curso es frágil (gesto cortado / salto) | Se reemplazó por `zIndex` (solo cambia dibujo/toque, no mueve nodos). La selección de tiempo pasa a `zIndex(2f)` |
| 2 | Un **toque sin arrastrar** sobre una línea de loop llegaba al carril, que lo toma como "tocar el carril vacío" | Deseleccionaba el clip (las líneas desaparecían bajo el dedo); un toque largo habría abierto el menú del carril | La línea consume el toque (`detectTapGestures`) antes de que llegue al carril |
| 3 | Estados de arrastre con `remember(claves)` capturados por gestos de clave fija | Los gestos habrían escrito en una instancia vieja del estado (vista previa congelada) | Estados estables, cada gesto los deja en 0 al terminar/cancelar |
| 4 | Seleccionar al **empezar** cada arrastre | Recomposición de pantalla en cada arrastre | Solo se selecciona si el clip aún no lo está |
| 5 | Contenedor desplazado en px y clip interior compensado en dp | Posible salto de 1 px al seleccionar/deseleccionar | Zonas en píxeles enteros y ambos desplazamientos en px |
| 6 | Toque largo sin respuesta táctil | Sensación poco pulida | Vibración corta (`HapticFeedbackType.LongPress`) al abrir el menú |

Verificado por lectura: imports y símbolos usados/definidos, constantes, balance
de llaves de los 4 archivos tocados, y la aritmética de los tests (réplica
numérica, 259 muestras).

## Corrección de CI posterior (primer build real de esta fase)
El primer CI tras esta fase **compiló** (Kotlin, tests y `assembleDebug`) y
ejecutó 571 tests con 1 fallo, ajeno al código de la fase:

- **Test:** `ProjectStorageManifestSecurityTest > una entrada que supera el tamano
  descomprimido maximo por entrada se rechaza` — `OutOfMemoryError` en la línea
  del `ByteArray(...)`.
- **Causa raíz:** la prueba reservaba de una vez un arreglo de
  `MAX_UNCOMPRESSED_ENTRY_BYTES + 1024` (~300 MB). Los workers de test de
  Gradle usan 512 MB de heap por defecto (el `-Xmx4096m` de `gradle.properties`
  es del *daemon*, no de los tests); un bloque contiguo de 300 MB en ese heap,
  tras cientos de tests, no es fiable. No hay historial de CI a mano para saber
  si antes pasaba por suerte; el diseño de la prueba era frágil en cualquier caso.
- **Corrección (solo la prueba):** la entrada gigante se genera en streaming,
  en bloques de 1 MB de ceros (`buildZipWithHugeZeroEntry`); memoria constante.
  Sigue ejercitando exactamente el mismo límite: `ZipInputStream` no conoce el
  tamaño comprimido de esa entrada (`compressedSize = -1`), así que el chequeo de
  ratio se omite y se alcanza el límite de 300 MB por entrada. Código de
  producción sin cambios.
- Un escaneo de `app/src/test` no encontró otras reservas de memoria de ese orden.

## Tests
`AudioClipLoopEdgesTest` (JUnit puro): sin loop (derecho e izquierdo fijos),
rango y acotado con loop, estirar/acortar a la izquierda, envoltura de más de
una vuelta, mínimo de largo, y la **propiedad de no-desplazamiento** sobre 6
combinaciones (con `trimStart` ≠ 0, estirar y acortar, más de una vuelta).
Se conserva `ContextMenuPositionTest` (FASE 21).

## Límites conocidos / decisiones abiertas (NO tocadas)
- **Apagar el loop de un clip ya estirado** no devuelve su largo original: el
  clip conserva el largo, suena una pasada y queda en silencio. Si se quiere
  "volver al original" al apagar el loop, es una decisión de producto aparte.
- Un clip con loop apagado y largo mayor al del archivo (de versiones
  anteriores) ya no se puede acortar arrastrando; hay que activar el loop.
- El borde izquierdo no usa imán (igual que antes); el derecho conserva el imán
  a rejilla, clips vecinos y repeticiones completas.
- La edición de audio sigue fuera de undo/redo por diseño (ver FASE 20).

## Checklist de prueba en dispositivo
1. Clip sin seleccionar: sin borde ni líneas. Tocarlo: borde blanco.
2. Con loop apagado y clip seleccionado: **no** aparecen líneas.
3. Activar loop (módulo Loop): aparecen dos líneas por fuera, casi pegadas.
4. Arrastrar la derecha y la izquierda: el clip se estira repitiendo el audio;
   soltar y reproducir: lo que se oye coincide con lo que se ve.
5. Arrastrar la **cabecera** desde su extremo izquierdo, centro y derecho:
   siempre mueve el clip; pasarse del inicio/final y volver responde de inmediato.
6. Arrastrar en vertical sobre la cabecera: scrollea el timeline, no mueve el clip.
7. Tocar/arrastrar la señal (cuerpo): **no** mueve el clip; toque largo abre
   el menú al costado.
8. Dos clips contiguos: el seleccionado queda por encima y sus líneas son
   agarrables.
9. Clip pegado al inicio (0): la línea izquierda no se ofrece; la derecha sí.
10. Tocar una línea de loop **sin arrastrar**: el clip sigue seleccionado.
11. Seleccionar/deseleccionar un clip: no se mueve ni un píxel.
12. Toque largo en la señal: vibra y abre el menú al costado.
