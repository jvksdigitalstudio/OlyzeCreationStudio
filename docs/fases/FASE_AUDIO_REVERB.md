# Fase — Módulo de audio «Reverb» (2026-10-19)

Primer módulo de efectos de audio: un **Reverb algorítmico de estudio**, cargado **por carril** desde el cajón «Módulos».
Cada carril lleva su propia cadena de efectos (vacía por defecto); ningún carril hereda los de otro. Un carril sin efectos
suena y se exporta exactamente igual que antes.

## Decisiones de diseño

| Decisión | Motivo |
|---|---|
| **Kotlin puro** para el DSP (no C++/NDK) | El proyecto no tiene código nativo; introducir CMake+JNI+2 ABIs por un efecto es un costo desproporcionado y el DSP se prueba con JUnit. El motor en vivo corre sobre `AudioTrack` en streaming con hilo de audio dedicado, bloques de 1 024 muestras y sin asignar memoria por bloque. C++/Oboe solo se justificaría si las mediciones en dispositivo (cortes, latencia) lo exigen: ahí se porta y las pruebas actuales sirven de oráculo. |
| **Insert por carril**, no por clip | El cajón actúa sobre el carril; la cola del reverb cruza de un clip al siguiente y no hay una instancia por clip. |
| **Una sola mezcla** (`AudioProcessor.mixTracks`) para exportar y para la vista previa | Lo que se oye es lo que se exporta; no hay dos caminos que mantener. |
| **Vista previa en tiempo real** (`LiveReverbTrackPlayer`) | `MediaPlayer` no puede aplicar el DSP al vuelo y los efectos del sistema Android suenan distinto en cada dispositivo. El carril con Reverb se lee SECO desde disco y se procesa en vivo con la misma matemática de la exportación: mover una perilla se oye casi al instante y no renderiza nada. |
| Efectos como **lista de `AudioEffect`** (sealed) con DTO genérico (`type` + `params`) | Delay/EQ/compresor se enchufan sin cambiar el esquema del proyecto; un tipo desconocido se omite sin romper la carga. |

## Responsabilidades (una por archivo)

| Capa | Archivo | Responsabilidad |
|---|---|---|
| Dominio / DSP | `engine/audio/fx/ReverbParams.kt` | Parámetros, rangos (única fuente de verdad), acotado, cola. |
| | `fx/ReverbPresets.kt` | Catálogo de presets de fábrica (ids estables, sin textos). |
| | `fx/StereoReverb.kt` | El algoritmo. |
| | `fx/Biquad.kt`, `DelayLine.kt`, `AllpassDiffuser.kt` | Primitivas DSP. |
| | `fx/AudioEffect.kt` | Contrato de efecto + `isActive`/`tailSeconds`. |
| | `fx/AudioEffectChain.kt` | Aplica la cadena a PCM de 16 bits. |
| Carril | `engine/audio/AudioTrackEffects.kt` | Operaciones puras sobre `AudioTrack.effects`. Dependencia en un solo sentido: `audio → fx`. |
| Exportación / mezcla | `AudioProcessor.mixTracks` | Mezcla por carril; los carriles con efectos usan un bus propio. |
| Vista previa | `DryTrackMix.kt` | Reglas puras de la mezcla seca: clave (SIN efectos), duración, qué mezcla suena (`resolveDryMix`). |
| | `DryTrackCache.kt` | Render en segundo plano de la mezcla seca del carril (misma `mixTracks` que la exportación, sin efectos), con *debounce*, cancelación y fallo sin reintento en bucle. Borra la caché del bake anterior una vez. |
| | `PreviewWavCache.kt` | Almacén en disco compartido con `ReversedAudioCache` (SHA-256, escritura atómica, purga). |
| | `fx/LiveReverbStream.kt`, `fx/PcmSource.kt`, `WavFilePcmSource.kt` | Motor en vivo (Kotlin puro): lee el audio seco por bloques, aplica `StereoReverb`, crossfade en cambios estructurales, seek con pre-roll. |
| | `LiveReverbTrackPlayer.kt` | Reproductor Android en streaming: `AudioTrack` + hilo de audio propio (único dueño del dispositivo y del motor); órdenes del hilo principal por referencias atómicas, sin locks. |
| | `LivePlaybackPolicy.kt` | Reglas puras: cuándo arrancar/pausar/reposicionar (`planLiveSync`), cabezal → muestra, contador de posición del `AudioTrack` (`HeadPositionCounter`). |
| | `AudioPreviewPlayers.kt` | Elige, por carril: motor en vivo (con Reverb y mezcla seca lista) o `MediaPlayer` (resto, o mientras no hay mezcla seca utilizable). |
| Persistencia | `data/AudioEffectCodec.kt` | Único lugar que conoce los nombres de campo del archivo; saneo de la frontera de confianza. |
| ViewModel | `EditorViewModel` (`addReverb`, `setReverbParams`, `applyReverbPreset`, `setReverbEnabled`, `removeReverb`) | Orquestación + deshacer. |
| UI | `ReverbModuleIcon.kt`, `ReverbModulePanel.kt`, `ReverbFloatingWindow.kt`, `AudioModulesList.kt` | Glifo, controles, ventana (sobre `FloatingToolWindow`), fila del cajón. |

## El algoritmo

```
entrada → pasa-altos (Low cut) → pre-delay ─┬→ reflexiones tempranas (8 tomas/canal) ────────┐
                                            └→ difusión (4 pasa-todo/canal) → FDN de 8 líneas ┤
seco ───────────────────────────────────────────────────────────────────────────→ suma ← ancho (M/S) ← pasa-bajos (High cut)
```

Cola tardía: red de retardos realimentados (FDN, Jot) de 8 líneas primas entre sí, matriz de Householder (ortogonal),
ganancia por línea para caer 60 dB en `decaySeconds`, pasa-bajos de un polo por línea (amortiguación de agudos) y vibrato
lento por línea con interpolación cúbica de Hermite. Mezcla seco/húmedo de **potencia constante**: `mix = 0` es la señal
original **bit a bit**. Procesa en simple precisión (filtros en doble), sin asignaciones por muestra, con anulación de
subnormales. Determinista.

Valores medidos sobre la respuesta al impulso (réplica numérica del algoritmo; las pruebas JUnit los fijan con margen):

- RT60 de graves/medios (250–700 Hz) = `decaySeconds` entre 0.97× y 1.06× a 22.05/44.1/48 kHz.
- El pre-delay es exacto: la primera salida cae en `pre-delay + 1.ª reflexión temprana`.
- Nivel en régimen estable: la salida húmeda varía solo ×1.3 entre RT60 0.3 s y 10 s (compensación de colas largas).
- Con `damping = 2.5 kHz` los agudos caen ~46 dB más rápido que los graves.
- Decorrelación L/R de la cola ≈ −0.05; con `width = 0` el reverb es mono exacto; estable con parámetros extremos.

## Orden de proceso y cola

Clip (reverso/recorte/loop → volumen, balance, fundidos) → **suma del carril** → **cadena de efectos** → mezcla general.
El efecto actúa sobre la mezcla del carril. En la exportación la cola cae dentro del buffer del proyecto (se recorta al fin
del proyecto); en la vista previa el audio seco dura `fin del carril` y el motor en vivo agrega la cola del efecto (`LiveReverbStream.totalFrames`), que sigue los parámetros vigentes.

## Deshacer y persistencia

- Cargar/quitar el módulo, elegir preset y bypass: un paso de deshacer cada uno. Un arrastre de slider = un paso
  (`pushUndoCheckpointForGesture`).
- `AudioTrackData.effects` (por defecto vacío: los proyectos viejos abren sin cambios). Al cargar: parámetro ausente = valor
  de fábrica, fuera de rango = acotado, tipo desconocido = omitido, ids repetidos = el primero. `sanitizeProjectData` limita
  16 efectos por carril y el tamaño de ids/nombres/parámetros.
- Los 5 puntos de costura del campo: `AudioTrack`, `AudioTrackData`, snapshot de guardado, carga y estado de deshacer.

## Ventana flotante

Mismo marco que Zoom X. Ampliación **mínima y compatible** de `FloatingToolWindow`:
`windowId` (identidad ante el registro de minimizadas; por defecto el título, así ninguna otra ventana cambia) y presets
**controlados por el módulo** (`selectedPresetIndex` + `onPresetSelected`): la ventana ya no guarda un preset que luego puede
dejar de coincidir con los valores; al tocar un parámetro la cabecera vuelve a «Presets» (a medida).

## Vista previa: comportamiento

Un carril con el módulo Reverb cargado (activo o en bypass) suena por el motor en vivo desde su **mezcla seca**
(`DryTrackCache`): todos sus clips mezclados como en la exportación (volumen, balance, fundidos, recortes, bucles) y sin
efectos. El Reverb se aplica al vuelo:

- **Parámetros continuos** (mix, decaimiento, amortiguamiento, cortes, difusión, modulación, ancho, tempranas): cambian al
  instante con suavizado, sin renderizar nada. Latencia de un cambio ≈ un búfer (~93 ms).
- **`size` y pre-delay** (estructurales): crossfade de 80 ms hacia una instancia nueva.
- **Bypass**: el `mix` baja a 0 con suavizado; con `mix = 0` la salida es la entrada bit a bit. No cambia de reproductor.
- **Seek / arranque**: el reverb se calienta con 1.5 s de audio previo, así la cola ya suena donde se llega. Arrancar siempre
  posiciona y descarta el búfer.
- **Sincronía con el cabezal**: se corrige con un seek solo si el desvío supera 150 ms (`LIVE_DRIFT_TOLERANCE_MS`).

La mezcla seca solo se vuelve a renderizar si cambian los clips o su volumen/balance/fundidos/recorte (los cambios se
agrupan 350 ms y un render obsoleto se cancela solo). Mientras tanto sigue sonando la anterior. Si aún no hay mezcla seca
(primer render), si falló, o si el motor en vivo falló (se reintenta a los 3 s), el carril suena por el `MediaPlayer` con el
audio real, sin reverb, nunca una mezcla desactualizada. Carriles sin Reverb: sin ningún cambio.

## Limitaciones conocidas

1. En un carril con Reverb, el volumen/balance/fundidos de sus clips van dentro de la mezcla seca: un cambio se oye tras re-renderizarla en segundo plano (~350 ms más el render). La exportación lo aplica siempre.
2. Cuando la mezcla seca cambia de archivo, el reproductor del carril se reemplaza: hay un corte breve (preparación y pre-roll), sin crossfade entre ambos.
2b. El render de la mezcla seca decodifica las fuentes del carril a memoria (como la exportación): el consumo de RAM es transitorio y en segundo plano; el hilo de audio no depende de él.
3. Un carril lleva como máximo un Reverb (el modelo admite una lista; la interfaz ofrece uno).
4. Un efecto guardado por una versión más nueva de la app se omite al abrirlo aquí y se pierde al guardar.

## Pruebas

`StereoReverbTest`, `DspPrimitivesTest`, `ReverbParamsAndPresetsTest`, `AudioEffectChainTest`, `AudioTrackEffectsTest`,
`DryTrackMixTest`, `DryMixResolutionTest`, `LivePlaybackPolicyTest`, `LiveReverbStreamTest`, `StereoReverbLiveParamsTest`, `WavFilePcmSourceTest`, `AudioEffectCodecTest` (incluye saneo), `ReverbModuleStructureTest` (cableado, textos ES/EN, formato).
Helpers compartidos: `testutil/ProjectFiles.kt` y `fx/DspTestSignals.kt`.

## Verificación pendiente (sin SDK/Gradle en esta sesión)

1. `./gradlew assembleDebug testDebugUnitTest`.
2. En dispositivo: cargar Reverb en un carril → mover sliders (un solo deshacer por gesto) → exportar y comparar con la vista previa.
3. Dos carriles con reverb distinto; borrar el módulo arrastrando su bola a «Eliminar»; guardar/abrir el proyecto.
4. Medir el tiempo de render de la mezcla seca de un carril largo (log `DryTrackCache`).
5. En dispositivo, con el carril sonando: arrastrar cada perilla (sin clics ni cortes), activar/desactivar el módulo, mover `size` y pre-delay (crossfade), mover el cabezal, pausar/reanudar, silenciar el carril, y con 2+ carriles con Reverb comprobar sincronía. Revisar el log `LiveReverbTrackPlayer` por «Cortes de audio» (underruns): si aparecen, subir `BUFFER_BLOCKS`.
6. Comparar a oído la vista previa con la exportación (deben coincidir; el test `lo que suena es identico a lo que exporta` lo garantiza para el motor).

---

## Auditoría técnica del módulo de audio (análisis estático)

Alcance: ruta del Reverb, preview (bake), mezcla/exportación y guardado. La auditoría original es análisis estático. Después, el núcleo en vivo y la lógica pura de 1b se compilaron con `kotlinc` 2.0.21 y sus tests JUnit se ejecutaron (ver «Verificación»); el código con APIs de Android se verificó de tipos y su lógica de hilos con una prueba de humo contra stubs, pero NO en dispositivo ni con Gradle.

### Corregido
| # | Defecto | Corrección |
|---|---------|------------|
| 5 | Un carril con efectos y TODOS sus clips silenciados seguía sonando por su bake anterior (el clip sintético del bake nunca está silenciado). Un render fallido dejaba sonando una mezcla desactualizada. | Regla pura `resolveDryMix` (`DryTrackMix.kt`), `DryTrackCache.hasFailed` y su uso en `AudioPreviewPlayers` (originalmente `resolvePlaybackBake` del bake, retirado en 1b). Tests: `DryMixResolutionTest`. |

| 2 | `StereoReverb` tenía todos los parámetros fijos en el constructor: imposible moverlos mientras suena. | `StereoReverb.updateParams` (mix, ancho, tempranas, difusión, modulación, decaimiento, amortiguamiento y cortes, con suavizado a tasa de control de 32 muestras y τ = 30 ms, independiente del tamaño de bloque) y `Biquad.setCutoff` (conserva el estado). `size` y `preDelayMs` son estructurales: `updateParams` devuelve `false` sin aplicar nada y el motor de reproducción crea una instancia y hace crossfade. Tests: `StereoReverbLiveParamsTest`, `BiquadLiveCutoffTest`. |

| 1a | El preview re-renderizaba el carril entero tras cada cambio (retardo). | Núcleo del motor en tiempo real, Kotlin puro: `LiveReverbStream` (lee el audio seco por bloques desde un `PcmSource`, aplica `StereoReverb` en vivo, crossfade de 80 ms para cambios de `size`/pre-delay, seek con pre-roll de 1.5 s, sin asignar memoria por bloque), `PcmSource`/`ShortArrayPcmSource` y `WavFilePcmSource` (lectura aleatoria de un WAV en disco: el carril no vive entero en RAM). Garantía probada: sin cambios, el streaming es idéntico muestra a muestra a la exportación (`applyEffectChain`). Tests: `LiveReverbStreamTest`, `ShortArrayPcmSourceTest`, `WavFilePcmSourceTest`. |

| 1b | La vista previa re-renderizaba el carril entero con efectos tras CADA cambio de parámetro (retardo audible al mover una perilla). | **Retirado el bake** (`TrackBake.kt`, `TrackBakeCache.kt` y sus tests). `DryTrackCache`/`DryTrackMix`: mezcla SECA del carril en disco, con clave independiente de los efectos. `LiveReverbTrackPlayer`: `AudioTrack` en streaming + hilo de audio de prioridad `URGENT_AUDIO`, lock-free con el hilo principal, posición exacta por `playbackHeadPosition` (`HeadPositionCounter`), fallo contenido con retorno al `MediaPlayer`. `AudioPreviewPlayers` enruta cada carril (motor en vivo o `MediaPlayer`). Con el módulo en bypass el carril no cambia de reproductor (`mix → 0`). Tests: `DryTrackMixTest`, `LivePlaybackPolicyTest`, `AudioTrackEffectsTest` (`liveReverbParams`), `WavFilePcmSourceTest` (tasa), `ReverbModuleStructureTest` (cableado y hilo de audio). |

### Pendiente (en orden de ejecución propuesto)
1. ~~**Motor de preview en tiempo real**~~ (hecho: 1a núcleo y 1b reproductor, caché seca, integración y retirada del bake; ver tabla).
2. ~~`StereoReverb` con parámetros en vivo~~ (hecho, ver tabla). El crossfade A/B para `size`/`preDelayMs` ya está en `LiveReverbStream`.
3. **Caché de PCM decodificado**: cada render vuelve a decodificar las fuentes.
4. **Memoria**: la reproducción en vivo ya lee por bloques desde disco; falta el render de la mezcla seca y la exportación, que siguen decodificando y mezclando el carril entero en RAM (`ShortArray` + 2 `FloatArray`): procesar por bloques.
5. **Calidad**: mezcla en float antes del reverb (hoy suma saturada en 16 bits) y remuestreo de mayor calidad que el lineal.
6. **Deuda estructural**: `EditorScreen.kt` y `EditorViewModel.kt` (riesgo de `VerifyError`).
7. **Interfaz**: panel con perillas rotativas, gráfica de decaimiento y medidores; luego Freeze/Tone/Output en el motor.

## Auditoría del reproductor en vivo (2026-10-21): cortes al reproducir

**Síntoma:** el clip suena "entrecortado" con Reverb activo.

**Causa raíz:** latencia de arranque/seek. El calentamiento del reverb (preroll de 1,5 s) tarda ~22 ms con JIT pero cientos de ms en código sin compilar (debug, arranque en frío). El audio nacía tarde, la deriva superaba la tolerancia y el sincronizador encadenaba seeks ("tormenta de seeks"), que se oyen como cortes.

**Correcciones (todas en código real, sin parches):**
1. `LiveReverbTrackPlayer`: compensación por reloj de pared al arrancar (`catchUp`), calentamiento previo de la pista, buffer adaptativo ante underruns, contador de cabeza con wrap de 32 bits y reset por flush.
2. `planLiveSync` (política pura y testeada): ventana de asentamiento (`LIVE_SETTLE_MS`) tras cada reposicionamiento y tolerancia de deriva; no se reposiciona mientras el motor aún se estabiliza.
3. `AudioPreviewPlayers`: traspaso sin huecos entre reproductor viejo y nuevo (`handoverComplete`), reintento tras fallo.
4. Ruta MediaPlayer: `seekTo` asíncrono gestionado (`seekPending`, arranque en `onSeekComplete`).
5. **Nuevo:** `LiveReverbStream.seekTo(frame, warmupBudgetNanos)`. Tras el primer bloque se mide la velocidad real del DSP; si el preroll completo no cabe en el presupuesto (`SEEK_WARMUP_BUDGET_NANOS` = 120 ms en el reproductor), se calienta solo el tramo más reciente que cabe, contiguo hasta el destino. Sin presupuesto el resultado es determinista e idéntico al anterior. Elimina el hueco de ~350 ms al reanudar a mitad de clip con DSP lento.

**Verificación realizada (entorno de ingeniería, no dispositivo):** 129 pruebas unitarias con kotlinc real, comprobación de tipos contra stubs de Android, simulaciones de dispositivo (steady/drag/pause/loop, también con intérprete puro y posición de cabeza obsoleta: 0 seeks espurios, 0 saltos) y orquestación completa (primera reproducción, dry listo, volumen, bypass, quitar efecto, pausa: 0 huecos, solapes ≤ 30 ms en traspaso).

**No verificado:** compilación Gradle y audio en dispositivo real. Pendiente de prueba del usuario; el log `LiveReverbTrackPlayer` reporta "Cortes de audio" si persisten underruns.

**Limitaciones conocidas:** al arrastrar `size`/pre-delay se crea una instancia en frío con crossfade (cola breve ausente, por diseño); tras el primer autosave se re-renderiza el dry una vez y se cambia de reproductor; la latencia de salida no se compensa en la posición audible.

## Rendimiento en dispositivo: APK `perf` y medidor de carga (2026-10-21)

**Hallazgo medido:** el DSP del reverb cuesta ~44 ms por 3 s de audio (≈68× tiempo real) con JIT en un servidor, pero solo ≈2,4× tiempo real ejecutado sin compilar. Un APK `debug` es *debuggable*: ART lo optimiza menos y el DSP por muestra (hilo de audio) y Compose rinden mucho peor que en la versión final. Varios carriles con Reverb a la vez pueden no alcanzar tiempo real en una tablet con ese APK, lo que se oye como cortes continuos y la interfaz se traba (los hilos de audio compiten con el de UI).

**Cambios:**
1. `buildTypes.perf` (app/build.gradle.kts): no debuggable, sin R8, firmada con la clave debug (se instala encima del debug). CI genera `olyze-perf-apks` además de `olyze-debug-apks`. **La fluidez se juzga con el APK `perf`.**
2. `DspLoadMeter` (LivePlaybackPolicy.kt, con prueba): el hilo de audio mide cada 5 s la carga del DSP y la registra en `LiveReverbTrackPlayer` ("Carga del DSP ... media X %, pico Y %; 100 % = no alcanza tiempo real"). Permite distinguir con datos un corte por CPU de otra causa.

**Pendiente según el dato del dispositivo:** si con `perf` la carga sigue cerca o por encima de 100 %, el siguiente paso es abaratar el DSP (interpolación y filtros) o limitar carriles simultáneos con Reverb en vivo; no se hace a ciegas.

**Corrección (CI #662):** la variante `perf` (no debuggable) activa `lintVital`, que falló por 12 errores `FullBackupContent` que ya existían en `backup_rules.xml` y `data_extraction_rules.xml` (`<exclude>` sobre rutas no incluidas y dominio `cache` inválido). Se corrigieron en origen quitando los `exclude` redundantes: lo respaldado no cambia (con `<include>` solo se respalda lo incluido). Esto también habría roto un `assembleRelease`.
