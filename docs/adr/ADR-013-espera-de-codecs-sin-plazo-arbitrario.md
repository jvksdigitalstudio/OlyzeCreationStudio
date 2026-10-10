# ADR-013 — Espera de codecs sin plazo arbitrario: fin por EOS, salida por cancelación cooperativa

**Estado:** Decidido e implementado (FASE 24).
**Archivos:** `engine/export/VideoExporter.kt`, `engine/audio/AudioProcessor.kt`, `engine/audio/AudioWaveform.kt`.

## Contexto
Tres bucles esperaban a que un `MediaCodec` vaciara su cola tras el fin de la entrada, y los tres
resolvían "¿y si nunca termina?" con un **plazo fijo**:

| Bucle | Plazo | Efecto al vencer |
|---|---|---|
| `VideoExporter.drainEncoder` | 3 000 vueltas × 10 ms ≈ 30 s | Export falla (`Failed`) |
| `AudioProcessor.encodeToAac` | 5 s sin salida | `outputDone = true` → **AAC truncado, tomado por válido** |
| `AudioProcessor.decodeToPcm` | 5 s sin salida | `outputDone = true` → **PCM truncado, tomado por completo** |

Un plazo fijo no distingue un codec **lento** (equipo modesto, 4K, archivo largo) de uno **colgado**.
En el primer caso aborta un export válido; en los dos de audio el efecto es peor: el recorte es
silencioso (audio cortado en el export o forma de onda/reversa incompletas, sin ningún error).

## Decisión
1. **Única condición de fin:** `BUFFER_FLAG_END_OF_STREAM`. Es el contrato de `MediaCodec`: tras
   `signalEndOfInputStream()`/EOS de entrada, el codec lo emite siempre.
2. **Salida de emergencia = cancelación cooperativa**, consultada en cada vuelta de espera:
   - Video: `drainEncoder(isCancelled)` lanza `ExportCancelledException` (privada); `export()` la
     captura **antes** del `catch (Throwable)` y emite `ExportProgress.Cancelled` (nunca `Failed`);
     el `finally` existente libera EGL/codec/muxer y borra el `.mp4` incompleto.
   - Audio: `decodeToPcm`/`decodeForAnalysis` reciben `isCancelled` y devuelven `null` **sin**
     registrar fallo; los llamadores distinguen `null`-por-cancelación de `null`-por-error
     (`if (isCancelled()) null else fail(...)`), para no mostrar un error falso por algo que el
     usuario pidió.
   - Forma de onda: `AudioWaveform.load` pasa `{ !isActive }`: si la corrutina se cancela, el
     decodificador suelta el candado en la siguiente vuelta.
3. Se eliminan `EOS_DRAIN_MAX_IDLE_ROUNDS`, `DRAIN_TIMEOUT_NS` y todos los `lastProgressNs`.

## Consecuencias
- Un driver realmente defectuoso que nunca emita EOS deja la operación en espera **hasta que el
  usuario cancele** (el botón Cancelar está visible durante audio y video). Es preferible a abortar
  exportaciones válidas o entregar audio recortado sin aviso.
- Limitación conocida: `ReversedAudioCache.render` corre en un `Executor` sin cancelación y usa el
  `isCancelled` por defecto (`{ false }`). Cancelarlo requiere un handle de job por clave; queda
  como trabajo futuro y no se resuelve con un plazo.
- `MediaCodec` no es simulable en JUnit puro: esta lógica se valida en dispositivo (ver FASE 24).
