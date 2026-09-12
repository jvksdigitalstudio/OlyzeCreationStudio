package com.yeivikas.olyzecs.engine.render

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * FASE 3.1.3-R3 — Real Atomic GPU Commit Gate.
 *
 * ============================================================
 * QUÉ PROBLEMA CIERRA ESTA CLASE (y qué NO cerraba R2)
 * ============================================================
 *
 * Hasta Fase 3.1.3-R2, `GLRenderer.uploadTextureIfNeeded` protegía la
 * ventana `validation → commit` con DOS lecturas consecutivas de
 * `stillAuthorizedToCommit()`, la segunda como la última expresión antes
 * de escribir `layerTextures[layer.id] = ...`. Eso es una defensa
 * OPTIMISTA real (reduce la ventana al mínimo estructural: cero
 * sentencias intermedias) pero, tal como el propio comentario de esa
 * fase ya reconocía, NO es sincronización formal — nada le impide al
 * hilo principal ejecutar una mutación relevante de `Layer` (reemplazo
 * de instancia, `removeLayer`, o `updateContentIdentity`) exactamente
 * entre esa segunda lectura y la escritura del registro. Dos lecturas
 * seguidas, sin ninguna primitiva de exclusión mutua entre ellas, siguen
 * siendo dos lecturas — la ventana de tiempo entre "leer" y "escribir"
 * nunca se cerró, solo se acortó.
 *
 * [LayerGpuCommitGate] es esa primitiva de exclusión mutua que faltaba.
 * Es, a propósito, la pieza MÁS PEQUEÑA posible: un único
 * [ReentrantLock] compartido entre:
 *
 *   A) el hilo de GL, que lo adquiere justo antes de la validación FINAL
 *      (la que autoriza el commit) y lo mantiene adquirido hasta terminar
 *      de escribir `layerTextures[layer.id] = ...` — ver
 *      `GLRenderer.uploadTextureIfNeeded`.
 *
 *   B) el hilo principal (ViewModel), que lo adquiere alrededor de CADA
 *      mutación que pueda invalidar la identidad que el hilo de GL podría
 *      estar validando en ese instante — ver el detalle completo (qué
 *      mutaciones exactamente) en `EditorViewModel.kt`, sección "FASE
 *      3.1.3-R3".
 *
 * Mientras cualquiera de los dos hilos tiene el gate adquirido, el otro
 * bloquea hasta que se libera — eso es, precisamente, lo que impide que
 * "validación" y "mutación" puedan intercalarse: o la mutación ya
 * terminó antes de que el commit empiece a validar (CASO A del informe
 * de esta fase), o el commit ya terminó — con o sin éxito — antes de que
 * la mutación pueda empezar (CASO B). Nunca un resultado intermedio.
 *
 * ============================================================
 * QUÉ *NO* PROTEGE ESTE GATE (a propósito)
 * ============================================================
 *
 * Este gate NO envuelve, en ningún caso:
 *
 *   - el decode de la imagen (`ImageDecoding.decodeSampledFromUri`, IO);
 *   - la subida a GPU (`drawer.uploadTexture` — clamp + `glTexImage2D` +
 *     `glFinish()`, la parte realmente costosa del pipeline);
 *   - el resto del frame de render (`onDrawFrame` dibuja fuera de este
 *     gate por completo, capa por capa);
 *   - ninguna operación de IO/autosave/exportación del ViewModel.
 *
 * La sección crítica real, del lado del hilo de GL, es exactamente:
 * "releer el estado vigente de la capa + compararlo contra lo capturado
 * + escribir una entrada en un `Map` en memoria" — nanosegundos, no
 * milisegundos. Del lado del hilo principal es, según la mutación,
 * "reasignar `StateFlow.value`" o las tres asignaciones de
 * `Layer.updateContentIdentity()` — tampoco hace IO ni trabajo pesado.
 * Un lock tan acotado, sostenido durante tan poco tiempo, no introduce
 * jank perceptible ni en la UI ni en el hilo de GL (ver sección 25 del
 * informe de esta fase: "si la UI puede quedar esperando glTexImage2D/
 * glFinish/decode/IO, la implementación es incorrecta" — acá la UI, en
 * el peor caso, espera a que el hilo de GL termine de escribir un valor
 * en un Map, nunca a que termine de subir una textura).
 *
 * ============================================================
 * POR QUÉ [ReentrantLock] Y NO OTRA COSA
 * ============================================================
 *
 * - No hace falta un lock global de más alto nivel (`synchronized` sobre
 *   todo `EditorViewModel`, o sobre todo `GLRenderer`): eso violaría
 *   directamente la sección 23 del informe de esta fase ("NO GLOBAL
 *   LOCK") — bloquearía trabajo completamente no relacionado (undo/redo
 *   de campos que no tocan identidad GPU, exportación, autosave, etc.).
 *   Este gate es una instancia propia, con un único propósito, inyectada
 *   explícitamente solo donde hace falta.
 * - `ReentrantLock` (vs. `synchronized` a secas) se eligió por
 *   `tryLock`/introspección testeable (`isLocked`/`isHeldByCurrentThread`)
 *   sin depender de bloquear el hilo de test para demostrar exclusión —
 *   ver `LayerGpuCommitGateTest`. La reentrancia en sí no es necesaria
 *   hoy (ningún camino real readquiere el gate desde el mismo hilo
 *   mientras ya lo tiene), pero tampoco es incorrecta tenerla disponible:
 *   es más segura por defecto que un lock no reentrante si algún llamador
 *   futuro llegara a anidar una adquisición por error — fallaría de forma
 *   obvia (deadlock inmediato) con un lock no reentrante contra el propio
 *   hilo, mientras que con reentrancia simplemente sería un no-op extra;
 *   preferible para un componente de concurrencia que debe fallar de
 *   forma predecible, nunca en silencio.
 * - No es un `Mutex` de corrutinas (`kotlinx.coroutines.sync.Mutex`)
 *   porque el hilo de GL (`GLSurfaceView.Renderer`) NO es una corrutina
 *   — es un hilo de Android nativo administrado por `GLSurfaceView`, sin
 *   `CoroutineScope` propio. Un `Mutex` de corrutinas solo puede
 *   adquirirse de forma suspendida (`lock()` suspende) o con `tryLock()`
 *   — ninguna de esas dos encaja con un `Renderer.onDrawFrame()`
 *   sincrónico que nunca debe suspenderse ni devolver el control a un
 *   scheduler de corrutinas a mitad de frame. Un lock JVM clásico
 *   (bloqueante, de hilo real) es la herramienta correcta para dos hilos
 *   nativos reales — Main (o el `Dispatcher` en el que corra
 *   `viewModelScope`, siempre respaldado por hilos reales) y GL.
 *
 * ============================================================
 * INVARIANTE QUE ESTA CLASE HACE DEMOSTRABLE
 * ============================================================
 *
 * "El commit GPU y las mutaciones relevantes de identidad de Layer están
 * serializados mediante el mismo commit gate." — NO "no hay ninguna
 * condición de carrera en el proyecto" (afirmación general que esta
 * clase, sola, no puede sostener) — ver `docs/fases/FASE_3_1_3_R3_COMMIT_GATE.md`
 * para el alcance exacto de esta garantía y sus límites conocidos.
 */
class LayerGpuCommitGate {

    private val lock = ReentrantLock()

    /**
     * Ejecuta [block] con el gate adquirido — punto único de entrada,
     * tanto para el hilo de GL (validación final + commit) como para el
     * hilo principal (mutaciones relevantes de identidad). Usar SIEMPRE
     * esta función, nunca [acquire]/[release] sueltos desde código de
     * producción (esos dos existen solo para los tests de exclusión, que
     * necesitan controlar el momento exacto de adquirir/liberar desde
     * hilos distintos — ver `LayerGpuCommitGateTest`).
     *
     * Cualquier excepción lanzada dentro de [block] libera el gate igual
     * (semántica estándar de `ReentrantLock.withLock`, garantizada por
     * `try/finally`) — un commit o una mutación que fallan no pueden
     * dejar el gate tomado para siempre.
     *
     * A PROPÓSITO no es `inline`: una función pública `inline` no puede
     * acceder a un miembro `private` (acá, [lock]) — el compilador de
     * Kotlin lo rechaza directamente (`"Public-API inline function
     * cannot access non-public-API property"`). Ganar la inline-ización
     * de un lambda para evitar UNA asignación de objeto no vale la pena
     * frente a exponer `lock` como `internal`/`@PublishedApi` solo para
     * permitirlo — esta función ya se documenta como parte de una
     * sección crítica de microsegundos (ver el KDoc de la clase), así
     * que el costo de una llamada de función normal es irrelevante.
     */
    fun <T> withGate(block: () -> T): T = lock.withLock(block)

    /**
     * Adquisición bloqueante explícita — SOLO para tests de protocolo que
     * necesitan controlar, desde otro hilo, el instante exacto en que el
     * gate queda libre otra vez (ver [release]). Código de producción real
     * debe usar [withGate].
     */
    fun acquire() = lock.lock()

    /** Contraparte de [acquire] — ver la misma advertencia. */
    fun release() = lock.unlock()

    /**
     * Intento de adquisición NO bloqueante — usado por los tests de
     * exclusión para demostrar, sin bloquear el hilo de test, que un
     * segundo hilo efectivamente NO puede entrar mientras el primero
     * mantiene el gate.
     */
    fun tryAcquire(): Boolean = lock.tryLock()

    /** Diagnóstico/tests: ¿hay algún hilo (cualquiera) con el gate tomado ahora mismo? */
    val isLocked: Boolean get() = lock.isLocked

    /** Diagnóstico/tests: ¿el hilo que llama a esto es, específicamente, el que tiene el gate tomado? */
    val isHeldByCurrentThread: Boolean get() = lock.isHeldByCurrentThread
}
