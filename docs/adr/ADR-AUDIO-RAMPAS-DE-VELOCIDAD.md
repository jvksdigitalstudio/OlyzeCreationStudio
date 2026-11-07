# ADR — Audio bajo rampas de velocidad y cuadros congelados

- **Fecha:** 2026-10-29
- **Estado:** PROPUESTA. Requiere una decisión de producto; este documento no cambia código.
- **Resuelve:** el límite conocido nº 1 de `docs/AUDIO_ENGINE.md` §9 y la fila "Rampas de velocidad y cuadros congelados" de
  `docs/AUDITORIA_MOTOR_AUDIO.md`; recoge la decisión que dejó abierta `docs/AUDITORIA_PROFESIONAL_2026-10-08_R2.md` (hallazgo 8).

## Qué es esto
`SpeedRampEngine` cambia **qué tan rápido avanza el tiempo base** del proyecto al reproducir y exportar (cámara lenta, acelerado, y
`FreezeFrame`: congelar la imagen N ms). El eje base es el mismo donde viven los keyframes de cámara **y la posición de cada clip de audio**
(`timelineStartMs`). Sin rampas, tiempo base = tiempo de salida y todo coincide; con rampas dejan de coincidir.

## Estado actual (verificado en el código, 2026-10-29)
1. **Nadie puede crearlas.** El panel de velocidad/freeze se retiró y las funciones del ViewModel que las creaban ya no existen (solo
   quedan `getSpeedKeyframes`/`getFreezeFrames`, de lectura); `AnimationApi` solo CALCULA (`speedAt`, `step`, `computeOutputDurationMs`).
   Entran únicamente por un proyecto **guardado**: `ProjectStorage` sigue leyendo `speedKeyframes` y `freezeFrames`. Consecuencia
   importante: **un proyecto creado cuando el panel existía puede tenerlas, y entonces todo lo que sigue está vivo.**
2. **Vista previa.** Con rampas, el reloj maestro del audio se desactiva (`EditorViewModel`: `audioClockMs = null`) y el cabezal lo marca
   la UI en tiempo base. El audio avanza a 1× por su cuenta y `AudioPreviewPlayers` lo **vuelve a situar con un salto** en cuanto se
   desvía más de 250 ms del cabezal (`NATIVE_RESYNC_JUMP_MS`). A 2× el audio saltaría hacia adelante unas cuatro veces por segundo; a 0,5×
   se adelanta y salta hacia atrás.
3. **Exportación.** `VideoExporter` mezcla el audio a 1×, con cada clip en su posición BASE, dentro de una pista de la duración de SALIDA
   (esa duración sí se corrigió para que coincida con el video). No hay saltos ni remapeo: un clip anclado a un evento visual suena en otro
   instante; si la salida es más corta, el audio se recorta; si es más larga, queda silencio al final.
4. **Vista previa y exportación no coinciden** bajo una rampa (saltos en una, deriva constante en la otra). Hoy lo que se oye al ensayar
   no es lo que sale en el archivo.

No hay prueba que fije ninguno de estos comportamientos: son consecuencia de no haber decidido, no una decisión.

## Decisión 1 — producto (la que bloquea todo lo demás)
| Opción | Qué implica | Coste | Riesgo |
|---|---|---|---|
| **R. Restaurar la UI** | Volver a ofrecer rampas y congelados | Alto (UI + Decisión 2 completa) | Medio-alto: toca el reloj de reproducción |
| **L. Dejarlas latentes** | Sin UI; el código y los datos siguen. Se documenta el comportamiento actual como "no definido para audio" | Nulo | Bajo, pero la máquina muerta sigue en el bucle de reproducción y en la exportación |
| **X. Retirar de extremo a extremo** | Quitar modelo, motor, exportación, la rama del reloj y las pruebas | Medio | **Hay que migrar**: un proyecto antiguo con rampas dejaría de tenerlas y su video exportado cambiaría de duración |

Antes de elegir X hace falta saber si algún proyecto real tiene rampas. Un dato barato: registrar al cargar un proyecto cuántas tiene
(`AppLogger`, una línea) y revisarlo en uso real.

## Decisión 2 — política de audio (solo si la 1 es R)
| | A. Reloj de pared (estado actual, formalizado) | B. Velocidad variable | C. Audio a 1×, inicios remapeados |
|---|---|---|---|
| Sincronía con eventos visuales | No: deriva | Sí | **Sí en el inicio de cada clip** |
| Tono | Intacto | **Cambia** (más grave en cámara lenta) | Intacto |
| Música continua | Correcta | Se ralentiza con la imagen | Correcta |
| Coste | 0 | Alto: remuestreo de razón variable en el callback en tiempo real, con antialias por encima de 1×, y paridad en la exportación | **Medio**: solo reubica inicios; sin DSP |
| Congelado | La música sigue (correcto) | La música se detiene | La música sigue; lo posterior se retrasa lo que dure el congelado (correcto) |

Estirar sin cambiar el tono (WSOLA o vocoder de fase) queda **descartado**: es otro proyecto, con su propia calidad y latencia.

### Recomendación
- **Decisión 1:** no restaurar la UI sin un plan concreto. Mientras tanto, **L**, con el registro de proyectos que traen rampas, y revisar
  al cabo de un tiempo: si ninguno las usa, **X**; si alguno las usa, mantenerlas hasta migrarlo.
- **Decisión 2 (si R):** **C**. Es lo que hacen los editores de video con la música ante un congelado, conserva el tono, no toca el
  motor de audio y es comprobable con una función pura. **B** como opción por clip ("seguir la velocidad") más adelante, no como
  comportamiento por defecto.

### Cómo se implementaría C (no se hace hasta decidir)
1. Función pura `outputTimeOfBaseMs(mapping, fps, baseMs)`: el primer fotograma de salida cuyo tiempo base alcanza `baseMs`
   (el mapeo ya es monótono no decreciente; un congelado es una meseta y el clip empieza al comienzo de la meseta).
2. Exportación: `buildEncodedTrackForProject` recibe esa función y reubica el **inicio** de cada clip; la longitud no cambia.
   Primero este paso: es pequeño, y deja la salida correcta aunque la vista previa aún no lo esté.
3. Vista previa: la línea de tiempo del motor de audio pasa a estar en tiempo de SALIDA y el cabezal base se deriva de su reloj. Es el paso
   de riesgo (arrastrar el cabezal, bucle de vista previa, selecciones y regla de tiempo convierten entre ambos ejes).
4. Pruebas: la función pura con y sin congelados y con rampas que aceleran y frenan; un proyecto con rampa cuyo audio ancla un clip a un
   evento (el inicio del clip y el evento visual deben coincidir en el archivo exportado); sin rampas, salida **idéntica byte a byte**
   a la actual (cero regresión).

### Cómo se retiraría X (no se hace hasta decidir)
Dejar de LEER `speedKeyframes`/`freezeFrames` al cargar (un proyecto antiguo se abre sin rampas, registrando cuántas descartó), y quitar
`SpeedRamp.kt`, sus pruebas, `AnimationApi` (los tres métodos), los parámetros de `VideoExporter`, la rama `audioClockMs = null` y los
campos del estado. Mantener el campo en `ProjectData` mientras se acepten proyectos antiguos.

## Qué se verificó y qué no
Verificado leyendo el código: los cuatro puntos del estado actual, que no existe ningún creador de rampas en la interfaz ni en la API, y
las constantes citadas. **No ejecutado:** ningún comportamiento en dispositivo (por ejemplo, oír los saltos de la vista previa con un
proyecto que tenga una rampa); eso requiere un proyecto de prueba con rampas guardadas, que hoy no se puede crear desde la aplicación.
