# Auditoría del motor de audio EliNer (Fases 1, 2a, 2b y 2c)

Alcance: todo el código nuevo o modificado en el motor nativo (`app/src/main/cpp`), su puente JNI, la fachada Kotlin
(`engine/audio/nativeengine`), el decodificador en streaming (`engine/audio/decode`), `AudioPreviewPlayers`, el foco de audio y los
cambios en `EditorViewModel`. Método: relectura completa del código real (no de la memoria de él), búsqueda mecánica de código sin uso,
y —para cada defecto de comportamiento— una prueba que **falla con el defecto y pasa con la corrección** (se reintrodujo el defecto
a propósito para comprobarlo).

## 1. Hallazgos corregidos

| # | Severidad | Área | Hallazgo | Impacto | Corrección | Verificación |
|---|---|---|---|---|---|---|
| 1 | **Alta** | Núcleo / lag | El hilo de prefetch retenía su mutex **mientras esperaba a disco**; `publishTimeline` (llamado desde el hilo principal vía JNI) necesitaba ese mismo mutex | La interfaz se bloqueaba esperando E/S. Medido: **750 ms** al publicar con una fuente lenta | `Prefetcher` copia su estado (punteros compartidos) y **suelta el mutex antes de tocar disco** | Prueba de regresión: con el defecto reintroducido tarda 750 ms y falla; con el código real, 0,005 ms |
| 2 | **Alta** | Núcleo / cortes | El prefetch solo vigilaba "delante del cabezal": tras un **seek** o al **saltar de tramo en un bucle** las páginas no estaban residentes y el hilo de audio podía quedarse esperando un fallo de página | Cortes audibles al saltar | `hint(frame)` en cada seek (la zona de destino se carga mientras el audio baja el fundido) y ventana en el inicio de cada tramo del bucle | 3 pruebas: ventana de posición, salto anunciado, tramos de bucle, recorte, reversa y vuelta de loop |
| 3 | **Alta** | Kotlin / lag | `syncTimeline` se llama en **cada tick** del cabezal (20 Hz) y recalculaba claves (concatenando URIs) y firmas de **todos** los clips en el hilo principal | Trabajo y basura constantes durante la reproducción | Salida rápida por identidad: el estado del proyecto es inmutable, misma lista y mismo solo = nada cambió | Revisión de la ruta; el cambio real sigue pasando por la firma |
| 4 | **Alta** | Kotlin / retardo | Abrir el stream de Oboe (decenas de ms) ocurría **sincrónicamente al pulsar play**, en el hilo principal | Retardo perceptible al reproducir | La salida se abre en segundo plano al sincronizar; `release()` espera a ese hilo antes de destruir el motor nativo (sin retener el cerrojo que él necesita) | Revisión de concurrencia (orden de bloqueos); `play()` conserva la apertura sincrónica como respaldo idempotente |
| 5 | **Alta** | Kotlin / robustez | Un solo fallo al abrir la salida de audio dejaba `isUsable = false` **para toda la sesión** | Una ruta Bluetooth transitoria condenaba la app al reproductor viejo (con todos sus defectos) | `isUsable` se calcula con reintento tras 5 s; los fallos de creación sí son permanentes | Revisión; la ruta de respaldo ya liberaba/recuperaba reproductores |
| 6 | Media | Diseño | `Engine` mezclaba **cuatro responsabilidades** (transporte en tiempo real, publicación, identidad de reverbs, hilo de prefetch) | Difícil de razonar y de probar por separado | Extraídos `ReverbRegistry` y `Prefetcher`; `Engine` solo transporte y publicación | Toda la batería existente + pruebas propias de cada clase |
| 7 | Media | Núcleo | `publishTimeline` emparejaba cada clip con su fuente **comparando punteros de datos** (frágil y O(clips × fuentes)) | Heurística que puede fallar silenciosamente | `ResolvedClip::sourceIndex`, asignado al construir la instantánea | Batería completa |
| 8 | Media | Núcleo | `process` drenaba los **comandos antes de adoptar** lo publicado: un `seek` se resolvía contra el plan de bucle y los reverbs **anteriores** | Salto a un tramo que ya no existe | Primero se adopta la línea de tiempo y el plan, después se drenan los comandos | Prueba de regresión propia: con el orden antiguo la posición queda en 100; con el real, en 2000 |
| 9 | Baja | Núcleo | `seek` fuera del plan de bucle saltaba primero a una posición fuera del plan y luego se corregía; `completeFadeOut` reiniciaba los reverbs **dos veces** | Trabajo innecesario y un estado intermedio inválido | `redirectTo` ajusta el destino al plan **al pedirlo**; un único reinicio de reverbs | Pruebas del bucle (nota: no era un doble fundido, lo verifiqué: el segundo salto ocurría con la rampa en 0) |
| 10 | Baja | Kotlin | `refreshTimeline` duplicaba a `syncTimeline` | Dos caminos para lo mismo | Ambos delegan en `updateLocked` | — |
| 11 | Baja | Código flotante | Sin consumidor en producción: picos, fin de proyecto (`setEndFrame`, `consumeReachedEnd`), `projectEndFrame`, `endFrame()`, `rampFrames_`, 6 funciones JNI, `streamStats()` y `rateHz` públicos | Superficie muerta que parece funcionalidad | Eliminado; `streamStats` se cableó a una línea de registro por sesión (underruns, latencia, ráfaga, búfer); el puente JNI pasa de 22 a 17 funciones, todas usadas | Verificación mecánica de miembros sin uso (0); `check_jni_signatures.py` |
| 12 | Baja | Pruebas | Una prueba **decorativa** (`seek` anuncia al prefetcher: solo dormía y comprobaba un `true`) y una línea `|| true` siempre verdadera | Falsa sensación de cobertura | Reescritas para observar de verdad los pedidos al prefetcher | Las nuevas fallan si se rompe el aviso |
| 13 | Baja | Mantenimiento | Función muerta en `check_kotlin_static.py`; comentarios desactualizados (`nativeEngine`, `syncTimeline`) | — | Eliminados/corregidos | — |
| 14 | Baja | Mantenimiento | Cierre del stream tras 15 s de pausa: obligaba a reabrir en la mayoría de las pausas | Reapertura frecuente | 60 s | — |

## 2. Propiedades que antes se afirmaban y ahora están **demostradas**

| Propiedad | Demostración |
|---|---|
| El hilo de audio **no asigna memoria** | Se cuenta cada `operator new` dentro de `process()` en un escenario exigente (reproducir, saltar, pausar, bucle de dos tramos, reverb, adoptar líneas de tiempo y planes nuevos, bloques de 77, 480, 960 y 1024 frames): **0**. Con una asignación introducida a propósito: 88 y la prueba falla |
| El hilo de audio **no bloquea** | Se interpone `pthread_mutex_lock/trylock` y se cuentan las llamadas dentro de `process()`: **0**. Con un mutex introducido a propósito: 88 y la prueba falla |
| Publicar no espera a disco | Hallazgo 1 |
| La conversión de un archivo largo no usa memoria proporcional a su largo | Mayor asignación individual: 256 KB tanto con 1 MB como con 16 MB de audio |
| Contrato entre lenguajes (la duplicación Kotlin ↔ C++ no puede divergir en silencio) | Cabecera WAV (44 bytes) y `ReverbParams` (orden y valores por defecto de los 11 campos): **el mismo literal** en una prueba de C++ y una de Kotlin |

## 3. Duplicación que permanece, y por qué

| Qué | Dónde | Por qué | Cómo se gobierna |
|---|---|---|---|
| Reverb | `StereoReverb.kt` (exportación y respaldo) y `reverb.cpp` (preview) | El export usa Kotlin y el motor C++; no puede compartirse código entre ambos | RT60, estabilidad, bypass y perillas por separado + **`make golden`**: misma señal y mismos parámetros → desvío < 2e-5 (**se ejecuta en CI**) |
| Cabecera WAV, `ReverbParams` | Kotlin y C++ | Frontera de lenguajes | Contratos con literal compartido (arriba) |

## 4. No corregido (con motivo)

| Tema | Motivo |
|---|---|
| Un archivo largo retrasa la preparación de los siguientes | **Resuelto en el núcleo (2026-10-26)** con dos carriles por duración, ver `docs/adr/ADR-AUDIO-PREPARACION-DE-FUENTES.md`. Pendiente de verificar en CI y en dispositivo |
| Una fuente cuya preparación falla no se reintenta durante la sesión | **Resuelto en el núcleo (2026-10-26)**: reintento automático de fallos transitorios y estado consultable. La interfaz lo muestra con una insignia en el clip (fase B, pendiente de verificar en dispositivo) |
| `AppLogger.lastUserFacingError` queda con el motivo del parser WAV si el respaldo (`MediaExtractor`) sí logra decodificar | Comportamiento preexistente de la exportación, fuera del alcance del motor |
| Rampas de velocidad y cuadros congelados | Falta una definición de producto de cómo debe sonar el audio |
| Los clips no se acotan al final del proyecto en el preview | El proyecto puede ampliarse mientras reproduce; documentado |

## 5. Lo que esta auditoría NO puede afirmar

- **El Kotlin no se compiló.** Se verifica estáticamente (`tools/check_kotlin_static.py`: llaves, imports internos, JNI), que no detecta errores de tipos.
- **Oboe, NDK y el dispositivo** no se probaron: ni latencia real, ni comportamiento por fabricante, ni el efecto real de los hallazgos 4 y 5.
- **La paridad del reverb con Kotlin** depende de `make golden` en tu CI.
