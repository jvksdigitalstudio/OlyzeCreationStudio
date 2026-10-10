# ADR-014 — El timeline siempre cubre el audio; el cabezal usa un reloj sin pérdida de fracciones

**Estado:** Decidido e implementado (FASE 26).
**Archivos:** `viewmodel/EditorViewModel.kt`, `engine/timeline/PlaybackClock.kt`,
`engine/audio/AudioPreviewPlayer.kt`, `engine/audio/AudioPreviewSeekPolicy.kt`, `engine/audio/AudioProcessor.kt`.

## Contexto
1. La duración del proyecto crece por tramos (1 → 10 → 60 → 180 min) y **solo cuando el cabezal se acerca al final**.
   El export construye video **y audio** hasta esa duración. Un clip de audio puede ser más largo que el timeline
   vigente (importar una canción de 3:28 en un proyecto de 1 min), y todo lo que excede se perdía en silencio.
2. El cabezal avanzaba con división entera de milisegundos por tick, perdiendo la fracción en cada vuelta: un reloj
   sistemáticamente lento frente al audio real, que obligaba a corregir con `seekTo` (salto audible).

## Decisión
1. **Invariante:** `projectDurationMs >= AudioTrack.endMs()`. Toda operación que ubica o agranda un clip
   (importar, pegar, duplicar, mover, API programática) y la **carga de un proyecto guardado** lo garantizan mediante
   `ensureTimelineCoversAudio`, que reutiliza la política de crecimiento existente (sin una regla paralela).
   El recorte en el export deja de ser silencioso (`warnIfClipExceedsProject`).
2. **Reloj del cabezal con acumulador** (`PlaybackClock`): el tiempo real se convierte a ms enteros conservando el
   resto en nanosegundos. Es una clase pura, probada con JUnit; el tope por tick ante bloqueos se mantiene.
3. **Política de seek del preview** extraída a una función pura (`previewNeedsSeek`): un seek solo se hace cuando es
   necesario; las uniones continuas entre clips del mismo archivo no se reposicionan.

## Alternativas descartadas
- *Alargar el timeline solo al exportar:* ocultaría el problema (la UI mostraría un clip fuera del timeline) y no
  repararía el arrastre ni el borde derecho del carril.
- *Exportar `max(projectDuration, audioEnd)` sin ampliar el timeline:* video y timeline dejarían de coincidir.
- *Reloj maestro = audio:* elimina la deriva de raíz, pero obliga a invertir el mapeo clip→proyecto (loops, trims,
  rampas) en cada tick. Con el reloj sin pérdida la deriva residual es del orden de ppm; se reevaluará si el preview
  pasa a mezclar varios clips en tiempo real.

## Consecuencias
- El video exportado dura el timeline completo (múltiplo de minuto, como cualquier proyecto); el audio sale íntegro.
- Proyectos antiguos con audio fuera del timeline se reparan al abrirlos (se persiste en el siguiente autoguardado).
- Sin cambios de formato de datos ni de API pública.
