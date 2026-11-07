# ADR — Planificador de preparación de fuentes del motor de audio

- **Fecha:** 2026-10-26
- **Estado:** Fase A (núcleo + integración) compilada y abierta en tablet por el usuario. Fase B (estado visible en la interfaz)
  aplicada, pendiente de compilar y de verificar en dispositivo. Pruebas JUnit de ambas fases por ejecutar en CI.
- **Archivos:** `engine/audio/nativeengine/SourcePrepQueue.kt` (nuevo), `SourcePrepScheduler.kt` (nuevo), `EngineSourceCache.kt`
  (reescrito como fachada), `NativeAudioEngine.kt`, `engine/audio/PreviewWavCache.kt` (purga). Pruebas:
  `SourcePrepQueueTest`, `SourcePrepSchedulerTest`, `PreviewWavCachePurgeTest`, y en C++ `testConcurrentConversionsAreIndependent`.
- **Fase B:** `engine/audio/AudioSourceState.kt` (nuevo, modelo público), `nativeengine/AudioSourceStateMapping.kt` (nuevo),
  `ui/AudioSourceStatusBadge.kt` (nuevo), `ui/AudioTrackRow.kt`, `ui/EditorScreen.kt`, `viewmodel/EditorViewModel.kt`,
  `res/values{,-en}/strings.xml`. Pruebas: `AudioSourceStateMappingTest`, `AudioSourceStatusWiringTest` y ampliaciones de
  `SourcePrepQueueTest` / `SourcePrepSchedulerTest`.

## Problema (verificado en el código, no supuesto)
`EngineSourceCache` preparaba las fuentes con **un solo hilo en orden FIFO** y recordaba los fallos en un conjunto `failed` que **solo
crecía**:

1. Un archivo largo retrasaba a todos los siguientes, incluido el clip que el usuario acababa de soltar. Mientras tanto el motor
   omitía el clip en silencio (`++missing` en `timeline.cpp`).
2. Cualquier `Throwable` condenaba la fuente hasta cerrar la app, también los fallos que se resuelven solos (disco lleno un momento,
   memoria justa). `onDone` ignoraba `ok = false` y la interfaz no tenía ningún estado "preparando / falló".
3. Colateral: `failed` y el mapa de claves crecían con cada archivo importado y quitado; y un motor creado mientras otro tenía una
   fuente en curso nunca recibía el aviso de que terminó (el callback era por petición y la petición repetida se ignoraba).

## Decisión
**Separar el orden del trabajo (puro y comprobable) de los hilos y del audio.**

- `SourcePrepQueue<P>`: máquina de estados PURA (sin hilos, sin Android, el tiempo entra como parámetro). Estados QUEUED / RUNNING /
  FAILED; "lista" no existe en la cola, la verdad es el archivo en disco.
- `SourcePrepScheduler<P>`: dos hilos demonio (uno por carril), creados al primer uso. Nunca invoca `work` ni `onSettled` con el cerrojo
  tomado.
- `EngineSourceCache`: solo lo que sabe de audio (cómo preparar, dónde guardar, a quién avisar).

**Prioridad = carriles, no una heurística de cercanía al cabezal.** Preparar cuesta en proporción a la duración. Una fuente de 5 min
o más (`LONG_SOURCE_MS`) va al carril largo; el resto, al corto. El carril corto nunca toma una larga, y el largo prefiere las largas y
ayuda con las cortas cuando no hay. Resultado: una corta nunca espera a una larga (sin preempción), y **nunca hay más de una
preparación larga en curso** (los decodificadores compiten por CPU y memoria con la UI y la exportación: el paralelismo máximo es 2, a
propósito). Dentro de un carril, orden de petición; pedir de nuevo una clave NO la reordena (la vista previa pregunta sin parar).

*Alternativa descartada:* ordenar por cercanía al cabezal. Exige que el motor conozca el cabezal en cada publicación (API nueva a través
de `AudioPreviewPlayers` y el ViewModel), no resuelve la preparación larga YA en curso (no hay preempción) y reordena sin cesar.

**Fallos.** `PrepOutcome` distingue Transient / Permanent / Cancelled. Transitorio (E/S, memoria, decodificación sin causa conocida,
conversión nativa fallida): hasta 3 intentos con espera de 2 s y luego 6 s. Permanente (sin permiso, error de programación): directo a
FAILED. FAILED no se reintenta sola; solo `retry(key)` (el usuario) la reabre. *Costo asumido:* el decodificador devuelve `null` sin
distinguir "archivo dañado" de "error de E/S a mitad"; se trata como transitorio, de modo que un archivo realmente dañado se decodifica
hasta 3 veces (casi siempre falla en la cabecera, barato).

**Cancelación.** Cada motor declara qué fuentes usa (`retain(owner, keys)`); lo que nadie usa se descarta de la cola (también las
FAILED, así volver a importar da un intento limpio) y, si está en curso, se cancela de forma cooperativa (`Task.isCancelled` llega al
decodificador). Volver a pedir lo que se estaba cancelando anula la cancelación. Al cerrar el motor, `release(owner)` cancela lo que solo
él necesitaba (mismo criterio que `DryTrackCache.retain`).

**Avisos.** Los oyentes se registran por motor (`addListener`) y reciben `(key, ok)` de cada intento; antes eran por petición.

**Corrección necesaria para poder paralelizar.** `PreviewWavCache.purgeOld` borraba TODO `.tmp` de la carpeta. Con un solo hilo era
correcto; con dos preparaciones a la vez borraba el temporal de la otra justo antes de su `rename` y esa fuente fallaba sin culpa. Ahora
`purgeDirectory` solo borra temporales e intermedios abandonados (más de 30 min sin escribirse) y los intermedios `.src.wav` no cuentan
como WAV de caché. La prueba estructural `la purga de los WAV de la vista previa respeta los que siguen en uso` se mantiene.

## No-cambios
Formato y contenido de los WAV de caché, claves de fuente (`uri#duración@tasa`), motor nativo (C++), reproducción, exportación.

## Fase B: el estado llega a la pantalla
**Problema.** Tras la fase A la caché ya sabía si una fuente se preparaba, reintentaba o había fallado, pero ningún componente lo
mostraba: un clip cuya fuente falló seguía mudo sin motivo visible.

**Decisión.**
- **Flujo de estado en la caché, no en `EditorUiState`.** `EngineSourceCache.states` es un `StateFlow<Map<AudioSourceId, AudioSourceState>>`.
  Cambia por trabajo de fondo, no por ediciones, así que meterlo en el estado del editor lo mezclaría con deshacer y guardado. El
  ViewModel solo lo expone (`audioSourceStates`) y ofrece `retryAudioSource(clipId)`, que NO es una edición (no toca proyecto ni historial).
- **Modelo público propio para la UI.** `AudioClipEditActions` es una clase pública y no puede recibir tipos `internal`; por eso la UI ve
  `AudioSourceId` (uri + duración, sin la tasa del motor, que no conoce) y `AudioSourceState` (Preparing / Retrying / Failed), y una función pura
  traduce desde el estado interno de la cola. Un segundo intento en curso se muestra como "reintentando", igual que su espera, para que
  la insignia no parpadee entre ambos.
- **Avisos sin carreras.** El planificador notifica cada cambio (`onChanged`) fuera de su cerrojo; la caché lee y asigna el estado bajo
  un mismo cerrojo propio, de modo que dos hilos que avisan a la vez nunca dejan un estado viejo por encima de uno nuevo. La cola
  expone `pending()` con el payload de cada clave, así no hay un mapa paralelo clave → identidad que pueda desincronizarse.
- **Insignia en el clip** (`AudioSourceStatusBadge`): "Preparando" (tras 400 ms, porque casi siempre dura una fracción de segundo y
  parpadearía en cada clip), "Reintentando n/3" (al instante) y "Reintentar" en rojo. Solo la de fallo es tocable, con un margen táctil
  alrededor (mide 14 dp); la zona de arrastre de la cabecera y los gestos del cuerpo no cambian. En clips estrechos (< 96 dp) solo
  muestra el ícono. Textos en `strings.xml` (es/en) con descripción de accesibilidad.

**No cambia:** la exportación (no usa esta caché), los clips silenciados (el motor no pide su fuente, así que no muestran nada), el
formato guardado del proyecto.

**Límites conocidos (fase B).**
- El motivo del fallo (`Failed.reason`) NO se muestra: es un texto interno en español sin traducir; queda en el registro
  (`EngineSourceCache`). Mostrarlo bien exige una clasificación localizable (p. ej. "sin permiso", "sin espacio", "formato no soportado").
- La insignia de fallo cubre la parte baja del clip; mientras dura, un toque ahí reintenta en vez de seleccionar. Seleccionar sigue
  disponible en la cabecera.
- Hasta que no se compile y se pruebe en dispositivo, el aspecto real (tamaños, contraste sobre cada color de pista) está sin verificar.

## Hallazgo en prueba real: dónde puede fallar un audio, y el aviso de importación
Al probar con un archivo de texto renombrado a `.wav` (2026-10-28) el clip ni siquiera se creó. Es el comportamiento correcto y define
DOS puntos de fallo distintos:

1. **Al importar** (`importAudio`, `replaceAudioClipSource`): si `MediaMetadataRetriever` no lee una duración, se descarta el archivo y no
   se crea ni se modifica ningún clip. Antes lo hacía **en silencio** (solo una línea en el registro): el usuario no podía distinguir "no
   hizo nada" de "falló". Ahora se emite `EditorMessage.AudioImportFailed(nombre)` y la pantalla lo muestra como `Toast` (es/en). Mismo
   patrón de eventos de un solo disparo que `TimelineEvent`.
2. **Al preparar la fuente** (esta ADR): solo fallan los archivos que SÍ pasaron el paso 1, es decir los que tienen una cabecera válida
   pero no se pueden leer o decodificar después (el archivo original se movió o borró, se revocó el permiso, no hay espacio en disco,
   cuerpo corrupto). Un archivo que no es audio nunca llega aquí, así que **no sirve para probar la insignia**.

**Cómo provocar la insignia de fallo en un dispositivo:** importar un audio real, guardar, borrar el archivo original del almacenamiento y
vaciar la caché de la app (Ajustes de Android → Apps → Almacenamiento → Borrar caché; NO "Borrar datos": la caché `elinerSources` vive
en `cacheDir`, y si sigue ahí la fuente se reutiliza sin necesidad del original), y volver a abrir el proyecto. Esperado: "Reintentando
2/3", "Reintentando 3/3" y, tras unos 8 s, "Reintentar" en rojo.

## Qué NO resuelve (ni esta fase ni la A)
- Una preparación **larga** que se atasca sigue bloqueando a otras largas (por diseño: máximo una a la vez).
- Rampas de velocidad y cuadros congelados: decisión de producto aparte (ADR pendiente).

## Verificación
Ejecutado aquí:
- C++ (`make test`): 2385 comprobaciones, 0 fallos. Sanitizadores (ASan+UBSan y TSan, modo rápido): 1748, 0 fallos. Nueva prueba:
  varias conversiones simultáneas dan bytes idénticos a las de una en una y un fallo en una no toca a las demás.
- Modelo ejecutable de la cola y el planificador (Python, misma lógica y mismos cerrojos), 40 corridas aleatorias de 60 fuentes con
  fallos aleatorios: sin interbloqueos ni despertares perdidos, máximo 1 preparación larga a la vez, el carril corto nunca toma una larga,
  nunca más de 3 intentos.
- `tools/check_kotlin_static.py` y `tools/check_jni_signatures.py`: OK.

**No ejecutado (requiere tu entorno):** compilación Kotlin y las pruebas JUnit nuevas (`./gradlew testDebugUnitTest`), y todo lo que
dependa del dispositivo. Las pruebas Kotlin están escritas pero NO se han corrido.
