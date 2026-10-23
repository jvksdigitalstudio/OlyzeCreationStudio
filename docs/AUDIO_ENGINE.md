# EliNer Audio Engine — Motor de audio de Olyze Creation Studio

Estado: **Fase 1** (motor nativo, transporte, mezcla de todos los clips, reloj maestro), **Fase 2a** (reverb dentro del grafo del
motor) y **Fase 2b** (decodificación en streaming y bucle de vista previa dentro del motor) implementadas.

---

## 1. Por qué existe

El preview anterior usaba **un `MediaPlayer` por carril** (`AudioPreviewPlayer`), sincronizado con un **reloj de UI**
(`delay()` en `viewModelScope`, hilo principal) y corregido con seeks. Tres defectos de diseño, no de ajuste:

| Defecto | Consecuencia audible |
|---|---|
| El reloj lo marca el hilo principal; los reproductores lo "persiguen" (`LivePlaybackPolicy`: tolerancia de deriva + seeks). | Cualquier tirón de Compose mueve el reloj → "deriva" → seek → **corte, salto o retardo**. |
| Solo suena el clip de arriba de cada carril (`AudioTrack.activeClipAt`). | **El preview no coincide con la exportación**, que sí mezcla todos los clips. |
| Cada carril es un reproductor del sistema con su propio búfer y arranque. | Arranques tardíos, "se para y continúa", carriles desalineados. |

Ningún DAW profesional trabaja así. Esos defectos son inherentes al modelo, por eso no se parchea: se reemplaza.

## 2. Qué hacen los DAW profesionales (fuentes consultadas)

- **Ardour** (manual oficial): una *playlist* es una lista ordenada de *regions*; una region **referencia** un fragmento de
  una *source* y nunca la modifica ni la copia. Cortar/copiar no toca el audio en disco.
- **Tracktion Engine** (repo y documentación oficiales): el modelo (`Edit → Track → Clip`) está **separado** del
  procesamiento. Un *builder* convierte el modelo en un grafo de audio y un *playback context* lo ejecuta dentro del
  **callback de audio en tiempo real**, con precisión de muestra. El transporte vive en ese contexto.
- **Guías oficiales de Android** (Oboe / AAudio, "Low latency audio" y `source.android.com`): usar Oboe con
  `PerformanceMode::LowLatency`; **el callback no puede bloquear, asignar memoria ni usar mutex**; la comunicación con el
  resto de la app debe ser sin bloqueos.

De ahí salen las cuatro decisiones del motor: **un reloj**, **una mezcla**, **modelo separado del procesamiento**,
**callback libre de bloqueos**.

## 3. Arquitectura

```
 Kotlin (hilo principal / trabajo)                       C++ (olyzeaudio.so)
 ┌────────────────────────────┐                ┌───────────────────────────────────────────┐
 │ EditorViewModel            │  sync(tracks)  │  jni_bridge.cpp  (hilos de CONTROL)       │
 │   └ AudioPreviewPlayers    │───────────────▶│    └ Engine (core, portable)              │
 │       └ NativeAudioEngine  │  play/pause/   │        ├ SpscQueue<Command>  ──┐          │
 │           (traduce modelo) │  seek          │        ├ mailbox<Snapshot*>  ──┤ sin      │
 │       └ EngineSourceCache  │                │        └ retired<Snapshot*> ◀──┤ bloqueos │
 │           (decodifica a    │◀───────────────│                                 ▼          │
 │            WAV 48 kHz)     │ posición audible│  OboeOutput ── callback RT ─▶ Engine::process
 └────────────────────────────┘  (reloj maestro)│                (mezcla TODO, por muestra) │
                                                └───────────────────────────────────────────┘
```

- **`core/` (portable, sin Android)** — `Engine`, `TimelineSnapshot`/`mixClips`, `Resampler`, `MappedWav`, `SpscQueue`,
  `wav_io`. Se compila y prueba en el host; es el mismo código que va en el APK.
- **`android/`** — `OboeOutput` (stream) y `jni_bridge` (puente). Sin lógica de audio.

### 3.1 Modelo de hilos

| Hilo | Qué hace | Prohibido |
|---|---|---|
| **Audio** (callback de Oboe) | Solo `Engine::process`: lee comandos, adopta la línea de tiempo, mezcla. | Mutex, `new`/`delete`, JNI, E/S, esperas. |
| **Control** (principal/trabajo) | Publica líneas de tiempo, envía comandos, carga fuentes. | Tocar el estado `rt*` del motor. |
| **Prefetch** | Mantiene residente (`madvise` + toque de páginas) la ventana de 3 s por delante del cabezal. | — (aquí sí se puede esperar a disco) |
| **Preparación de fuentes** (Kotlin) | Decodifica y remuestrea archivos nuevos. | — |

Comunicación audio ⇄ control (todo sin bloqueos):
- **Comandos** (play, pause, seek, bucle, fin): `SpscQueue` de 256 entradas. Los productores se serializan con un mutex
  que el hilo de audio **jamás** toca.
- **Línea de tiempo**: `atomic<Snapshot*> mailbox` con *"la última gana"*. El audio la adopta al inicio de un bloque y
  devuelve la anterior por `retired` (cola SPSC) para que la **libere el hilo de control**. El audio nunca libera memoria.
- **Estado hacia fuera**: posición, `playing`, picos, fin de proyecto — atómicos.

### 3.2 Modelo de datos (Ardour: source / region)

- **Fuente** (`PcmBuffer`, inmutable): PCM 16-bit estéreo a 48 kHz, WAV **mapeado en memoria** (`MappedWav`). No ocupa heap;
  el sistema operativo trae páginas a demanda y las descarta bajo presión.
- **Clip** (`ClipSpec` → `ResolvedClip`): referencia a una fuente + inicio, largo, recorte, fundidos, loop, reversa,
  ganancia, balance. Muchos clips comparten una fuente sin costo.
- **Carril** (`TrackSpec` → `ResolvedTrack`): sus clips + un reverb opcional. La mezcla seca del carril pasa por su reverb antes de
  sumarse al máster (el "track" de Tracktion).
- **`TimelineSnapshot`**: conjunto **inmutable** de carriles y clips ya resueltos. Se publica entero: el audio nunca ve un estado a medias.

### 3.3 Transporte (un solo reloj)

La posición es un entero de 64 bits que avanza dentro de `process`. La UI la **lee** (`nativeAudiblePositionFrames` =
posición − latencia de salida medida por el sistema → lo que realmente se oye) y mueve el cabezal con ella.

- **Play/pausa/salto sin clic**: fundido de 5 ms (bajar → saltar → subir). Un `seek` durante la bajada de una pausa deja
  el transporte pausado *en el punto nuevo*.
- **Bucle sin costura** (`setLoop`): vuelta de región dentro de `process`, sin fundido.
- Con el transporte parado, `seek` es inmediato y exacto.

### 3.4 Semántica idéntica a la exportación

`mixClips` reproduce, por muestra, lo que hace `AudioProcessor` al exportar:

| Aspecto | Regla |
|---|---|
| Ganancia | `clampAudioVolume(volume) × normalizeGain` |
| Balance | `L = 1 − max(0, pan)`, `R = 1 − max(0, −pan)` (el centro no toca la señal) |
| Fundidos | Lineales; cada uno ≤ la mitad del tramo audible; el de salida se mide contra el **último frame con audio** |
| Loop | Primera pasada desde `trim`; las siguientes desde el frame 0 del archivo |
| Reversa | Se invierte el archivo completo y luego se aplica el recorte |
| Recorte | `trim ∈ [0, frames−1]` |
| Suma | Todos los clips; saturación a ±1.0 |
| ms → frames | `ms × 48000 / 1000`, truncando |

Diferencia deliberada: la suma se hace en **float** y satura una vez al final; la exportación satura en cada acumulación
entera. Solo difiere cuando se desborda a mitad de la suma (y ahí el float es el más correcto).

### 3.5 Remuestreo

Los archivos de 44,1 kHz son lo normal; los dispositivos suelen trabajar a 48 kHz. El remuestreo lineal atenúa agudos y deja
aliasing. El motor usa **sinc con ventana Kaiser (β = 9, 32 cruces por cero, polifásico exacto)** al *preparar* la fuente
(nunca en el callback). SNR medida: **≈ 89–91 dB** en 44,1→48, 48→44,1 y 22,05→48 kHz.

### 3.6 Salida (Oboe)

Stream `Float`, estéreo, **48 kHz fijos** (la tasa del motor no depende del dispositivo ni de los auriculares; si el
equipo no es 48 kHz nativo, Oboe convierte), `LowLatency`, `Exclusive` (cae a `Shared` solo si hace falta),
`LatencyTuner` (agranda el búfer ante underruns en vez de cortar) y **reapertura automática** si el dispositivo se
desconecta (auriculares/Bluetooth), conservando posición y línea de tiempo. Se cierra tras 15 s en pausa.

### 3.7 Reverb en el grafo (Fase 2a)

`core/reverb.cpp` es un **port fiel** de `StereoReverb.kt` (red de retardo realimentada de 8 líneas, difusores allpass,
reflexiones tempranas, modulación, suavizado de parámetros): cada operación conserva el tipo del original (`Float`→`float`,
`Double`→`double`) y su orden de evaluación, porque un reverb es recursivo y los redondeos se acumulan en la cola. Por eso el
núcleo se compila con `-ffp-contract=off` (la JVM no fusiona multiplicar+sumar; el compilador de ARM64 sí lo haría).
Los bloques genéricos (`DelayLine`, `Biquad`, `AllpassDiffuser`) viven en `core/dsp_primitives.h`, reutilizables por futuros efectos.

- **Cola continua entre publicaciones.** El reverb de un carril se identifica por su `trackId`. Al publicar una línea de tiempo
  nueva, `Engine::acquireReverb` reutiliza la instancia existente si es *compatible* (mismo `size` y `preDelayMs`, que fijan el
  largo de las líneas): editar clips o mover perillas no corta la cola. Un cambio estructural crea una instancia nueva. El
  registro usa `weak_ptr`: la instancia vive mientras alguna línea de tiempo la use.
- **Perillas sin reconstruir nada.** Arrastrar un control (decenas de veces por segundo) llama a `nativeSetReverbParams`, que
  deja el objetivo en un **buzón con cerrojo de secuencia** (datos atómicos, lectura sin espera para el hilo de audio). El audio
  lo recoge en su siguiente bloque y suaviza hacia él (30 ms), igual que `updateParams` en Kotlin: sin saltos ni clics. La firma de la
  línea de tiempo en Kotlin contiene solo lo *estructural* del reverb, así que una perilla nunca provoca una publicación.
- **Saltos y paradas.** Las colas pertenecen al pasaje anterior: se vacían (`reset()`) en un salto de posición, al parar y al
  llegar al final, como en cualquier DAW. Un bucle no las vacía.
- **Sin reverb en un carril**: ruta directa al máster, sin copia intermedia.
- Coste medido: ≈ 0,6 % de un núcleo por reverb (x86 de escritorio, 177× tiempo real). En un teléfono será mayor; medir.

### 3.8 Plan de bucle dentro del motor (Fase 2b)

La vista previa de un clip recorre una lista ordenada de tramos y vuelve al primero (`PreviewLoopPlan` en la app). Antes lo
hacía el ViewModel con un seek en cada vuelta; ahora lo hace el motor (`core/loop_plan.h` + `Engine::process`):

- El plan viaja entero y de una vez al hilo de audio por un `RtPublisher<LoopPlan>` (ver §3.9): nunca se ve a medias.
- Al terminar un tramo el cabezal pasa al inicio del siguiente **dentro del callback, sin fundido ni seek**. Verificado muestra a
  muestra: con una fuente cuyo valor es su propio índice de frame, la salida sigue exactamente `1000..1499, 4000..4299, 1000…`.
- Si el cabezal queda fuera de todo tramo (el plan se activa o cambia), salta al siguiente con el mismo fundido que cualquier
  salto (`redirectTo`, rutina única compartida con `seek`).
- El ViewModel ya no confina el cursor ni fuerza seeks cuando el motor lleva el reloj; solo conserva ese camino si el reloj es el
  de la UI (respaldo, rampas de velocidad).

### 3.9 Publicación sin bloqueos (`RtPublisher<T>`)

Un solo patrón, `core/rt_publisher.h`, entrega objetos inmutables del control al audio y garantiza que el audio **nunca libera
memoria**: buzón "la última gana" + cola de retiro SPSC + liberación en el hilo de control. Lo usan la línea de tiempo y el plan de
bucle (antes estaba escrito a mano solo para la línea de tiempo).

### 3.10 Preparación de fuentes en streaming (Fase 2b)

Antes, preparar un archivo lo decodificaba **entero** a un `ShortArray` (más una copia al fusionar los trozos): un tema largo podía
agotar el heap de la JVM. Ahora el audio nunca está entero en la JVM:

```
URI ──▶ AudioProcessor.decodeStreaming ──▶ PcmSink ──▶ StereoWavFileSink ──▶ WAV intermedio (disco)
        ├ WavStreamDecoder  (parser WAV propio)        │ remapChannels por bloque     │
        └ MediaStreamDecoder (MediaExtractor+MediaCodec)                             ▼
                                                  motor nativo: ventana gapless + remuestreo sinc ──▶ WAV final (caché, mmap)
```

Responsabilidades, una por archivo (`engine/audio/decode/`): `PcmSink`/`PcmAccumulator` (contrato de salida), `SampleFormat`/
`SampleConverter` (la ÚNICA conversión a 16 bits), `PcmEmitter` (la ÚNICA alineación a frames), `GaplessWindow` (la ÚNICA regla del
recorte gapless, en frames, válida para un arreglo o un archivo), `WavStreamDecoder`, `MediaStreamDecoder`, `WavHeader`/
`WavStreamWriter`.

**Sin duplicación:** exportación y análisis usan el mismo decodificador a través de `PcmAccumulator`; `decodeWavStream` y
`trimGapless` siguen existiendo como envoltorios finos, así que `AudioWavStreamTest` y `AudioGaplessTrimTest` ahora validan el
código nuevo. La conversión nativa recibe la ventana gapless y la aplica en la misma pasada que el remuestreo.

Las fuentes que el proyecto deja de referenciar se descargan del motor (`nativeUnloadSource`); antes el mapa de fuentes crecía
con cada archivo importado en la sesión.

## 4. Reloj maestro en el ViewModel

`startPlaybackLoop` ya no manda sobre el audio: con el motor sonando, el cabezal toma `masterPlayheadMs()` (con
`coerceAtLeast(playheadMs)` para que la fluctuación de la latencia nunca lo haga retroceder).
Se mantiene el reloj de UI —y el motor recibe un seek solo si el salto supera 250 ms— cuando el tiempo del proyecto **no es
lineal** respecto del audio (rampas de velocidad, cuadros congelados) o el motor no está disponible. En la vista previa en bucle
el cabezal también sigue al motor (que da la vuelta él); allí no se aplica el `coerceAtLeast`, porque volver atrás es lo correcto.
Tras un play/seek, 60 ms de margen mientras el audio aplica el comando (evita un tirón del cabezal hacia atrás).

## 5. Respaldo

Si `System.loadLibrary("olyzeaudio")` falla (ABI sin soporte) o el dispositivo de salida no abre, `NativeAudioEngine.isUsable`
pasa a `false` y los carriles suenan como antes: sin reverb por `AudioPreviewPlayer`, con reverb por `LiveReverbTrackPlayer`
(con su `DryTrackCache`). Ese camino se conserva solo como respaldo; con el motor activo se libera por completo.
La app nunca se queda sin audio. **Dos implementaciones del reverb coexisten** (Kotlin: exportación y respaldo; C++: preview): la
equivalencia no se da por supuesta, la garantiza la prueba de referencia del §6.

## 6. Verificación

Núcleo en el host: `make -C app/src/test/cpp test` (sin dependencias externas). `make test-sanitizers` ejecuta ASan+UBSan y TSan.
Además `python3 tools/check_jni_signatures.py` compara cada `external fun` de Kotlin con su función C++ (un desajuste JNI no
falla al compilar: revienta en ejecución). Todo corre en CI antes de Gradle.

| Prueba | Qué garantiza |
|---|---|
| Cola SPSC (2 M elementos con dos hilos) | Orden, capacidad, sin pérdidas |
| **Paridad con la exportación** (300 configuraciones aleatorias: trim fuera de rango, loop, reversa, fundidos, balance) | Desvío ≤ 2 LSB por clip contra un port literal de `buildProjectSamples`/`applyVolumeAndFades`/`applyBalancePan` |
| **Independencia del troceado** (bloques de 1 a 700 frames) | Salida **idéntica bit a bit** sin importar el tamaño de bloque |
| Transporte | Posición exacta tras N bloques (sin deriva); arranque/salto/pausa sin discontinuidad |
| Bucle y fin de proyecto | Región periódica exacta; el fin avisa una sola vez |
| Publicación en caliente (400 líneas de tiempo con el audio corriendo) | **Ninguna fuente se libera en el hilo de audio; ninguna se fuga** |
| Remuestreador | SNR 89–91 dB en las conversiones reales; longitud exacta; copia idéntica a igual tasa |
| `MappedWav` | Lectura, rechazo de archivos inválidos, paridad con fuente en memoria |
| Tiempo real (32 clips simultáneos, bloque de 10 ms) | ≈ 0,2 % del presupuesto por bloque (peor caso ≈ 0,7 %) medido en un x86 de escritorio; **en un teléfono será mayor: medirlo en el equipo** |
| Reverb: mix 0, in situ, troceado | `mix = 0` sale idéntico bit a bit; in situ == fuera de lugar; trocear no cambia ni un bit |
| Reverb: RT60 (250–700 Hz) | Mismo método y misma tolerancia (±10 %) que `StereoReverbTest.kt`: medido 96–102 % a 44,1 y 48 kHz con 0,8 y 2 s |
| Reverb: estabilidad y `reset()` | Sin NaN ni explosión con parámetros extremos y ruido a escala completa; tras `reset()` salida exactamente 0 |
| Reverb: perillas en vivo | Escalón máximo 0,014 con suavizado frente a 0,318 con corte duro (control que demuestra que la medida distingue) |
| Reverb: perillas concurrentes (40 000 cambios con el audio corriendo) | Cerrojo de secuencia sin carreras ni valores a medias |
| Motor + reverb | La cola suena tras el clip; se vacía exactamente en un salto o pausa; instancia reutilizada ante cambio continuo |
| **Paridad con Kotlin** (`StereoReverbGoldenTest` → `make golden`) | Misma señal, mismos parámetros y mismo cambio de parámetros a mitad: desvío < 2e-5. **Se ejecuta en CI**, no aquí |
| Plan de bucle: geometría | Normaliza (descarta inválidos, ordena, recorta solapes); `rangeAt`, `startAfter`, `nextStartFrom` |
| **Plan de bucle: secuencia exacta** | Con una fuente cuyo valor es su índice de frame, la salida es EXACTAMENTE `1000..1499, 4000..4299, 1000…` (muestra a muestra) y el cabezal nunca sale de los tramos |
| Plan de bucle: salto desde fuera, cambio en caliente, desactivación; 6000 publicaciones concurrentes con el audio | Salta al siguiente tramo; sin plan el transporte sigue de largo; sin carreras ni fugas |
| Conversión con ventana gapless | Copia exacta de la ventana a igual tasa; acotada al archivo; ventana vacía falla con motivo y sin dejar archivo; longitud correcta al remuestrear |
| JVM — `SampleConverterTest`, `PcmEmitterTest`, `GaplessWindowTest`, `WavStreamingTest` | Cada formato a 16 bits (U8/S16/S24/S32/F32/F64, con signo y límites); trocear la entrada en cualquier tamaño no cambia nada; WAV de 600 KB llega en varios bloques idéntico; el escritor corrige la cabecera al cerrar. **Se ejecutan en CI (Gradle), no aquí** |
| ASan + UBSan, TSan | 0 errores de memoria, 0 comportamiento indefinido, **0 carreras de datos** |

**Paridad del reverb con Kotlin — estado honesto:** el port cumple las propiedades medibles del reverb, y la tubería de comparación
está validada (acepta una referencia correcta, rechaza una alterada, falla si el archivo falta). Pero el archivo de referencia lo
produce `StereoReverbGoldenTest` en Gradle, que aquí no se puede ejecutar. Hasta que el CI lo confirme, "suena idéntico" es una
hipótesis fuerte, no un hecho. (El RT60 medido a 0,8 s es 96,1 %, algo por debajo del 0,97× que documenta `StereoReverb.kt`; con el
método de la prueba entra de sobra en ±10 %, pero la comparación con el archivo de referencia es la que decide.)

**Lo que NO está verificado en este entorno** (sin NDK ni dispositivo): la compilación contra los headers reales de Oboe
(`oboe_output.cpp` y `jni_bridge.cpp` se verificaron contra maquetas de `jni.h` y de la API de Oboe), la carga de la
biblioteca en un dispositivo, la latencia real y el comportamiento en cada fabricante. Eso lo confirma el CI y la prueba en
el equipo.

## 7. Diagnóstico

`NativeAudioEngine.streamStats()` devuelve `[underruns, latenciaFrames, framesPorRáfaga, tamañoBúfer, bajaLatencia]`.
Logcat, etiqueta `EliNerAudio`: API usada, tasa, ráfaga, búfer, modo de compartición. Si `underruns` sube durante la
reproducción, el `LatencyTuner` agranda el búfer; si no estabiliza, el problema es del dispositivo, no de la mezcla.

## 8. Cambio de comportamiento a tener presente

El preview ahora **mezcla todos los clips** de un carril (antes: solo el de arriba). Es lo que hace la exportación y lo que
hace cualquier DAW; el comportamiento anterior fue una decisión explícita por la limitación de `MediaPlayer`, que el motor
elimina.

## 9. Límites conocidos

1. **Rampas de velocidad y cuadros congelados**: el tiempo del proyecto deja de ser lineal respecto del audio y el reloj lo sigue
   marcando la UI (con seeks solo ante saltos > 250 ms). No hay definición de producto de cómo debe sonar el audio bajo una rampa de
   velocidad; es una decisión de diseño pendiente, no de implementación.
2. **Acotado al final del proyecto**: el núcleo lo soporta (con paridad probada) pero el preview en vivo no lo aplica, porque el
   proyecto puede ampliarse mientras reproduce (`growIfApproachingEnd`).
3. Primer sonido de una fuente nueva: espera su preparación (decodificar + remuestrear); el clip suena en cuanto está lista. Un
   archivo largo tarda más en estar listo, pero ya no puede agotar el heap.
4. La conversión de tasa/ventana en C++ mantiene en memoria nativa la fuente remuestreada (no en el heap de la JVM) mientras
   escribe; un remuestreo en bloques lo reduciría si hiciera falta con archivos de muchas horas.
