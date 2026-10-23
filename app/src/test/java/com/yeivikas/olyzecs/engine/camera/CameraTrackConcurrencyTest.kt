package com.yeivikas.olyzecs.engine.camera

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 2 — Concurrencia, Estado de Render, Reproducción y Duración.
 *
 * A diferencia de [CameraTrackSnapshotIndependenceTest] (Fase 1 — prueba
 * la propiedad de la foto UNA vez tomada, en un solo hilo), este test
 * reproduce el escenario REAL que motivó el fix: un hilo "editor"
 * mutando estructuralmente [CameraTrack] (`addOrReplace`/`remove`/
 * `replaceAll`, igual que hace el usuario arrastrando keyframes o
 * deshaciendo/rehaciendo) AL MISMO TIEMPO que un hilo "GL" llama a
 * `frameAt()` en un loop apretado — exactamente como hace
 * `GLRenderer.onDrawFrame` ~60 veces por segundo.
 *
 * Antes del fix de Fase 2 (ver el comentario grande en CameraTrack.kt),
 * `frameAt()` leía directo de la `ArrayList` mutable interna: con las
 * dos mutaciones estructurales de abajo (`removeAll`+`add`+`sortBy` en
 * `addOrReplace`, `clear`+`addAll` en `replaceAll`) corriendo en paralelo
 * con cientos de llamadas a `frameAt()` por segundo, este test hacía
 * saltar `IndexOutOfBoundsException` de forma reproducible en la
 * inmensa mayoría de las corridas en una máquina real (no un caso de
 * borde raro — la ventana de carrera es del tamaño de la duración
 * completa de `sortBy`/`clear`, muy por encima de la duración de un
 * `frameAt()` individual). Con el fix (snapshot inmutable publicado con
 * `@Volatile`), el mismo escenario no debe lanzar NINGUNA excepción.
 */
class CameraTrackConcurrencyTest {

    private fun kf(timeMs: Long, tx: Float) = Keyframe(timeMs = timeMs, translateX = tx, translateY = 0f, scale = 1f)

    @Test
    fun `frameAt no lanza excepcion mientras otro hilo muta keyframes estructuralmente`() {
        val track = CameraTrack(initialKeyframes = (0..20).map { kf(it * 100L, it.toFloat()) })

        val stop = AtomicBoolean(false)
        val readerError = AtomicReference<Throwable?>(null)
        val writerError = AtomicReference<Throwable?>(null)
        val readerReady = CountDownLatch(1)

        // Hilo "GL": exactamente lo que hace onDrawFrame, en loop apretado.
        val readerThread = Thread {
            readerReady.countDown()
            try {
                var t = 0L
                while (!stop.get()) {
                    track.frameAt(t)
                    track.frameAt((t - 33L).coerceAtLeast(0L)) // motion blur previousFrame
                    t = (t + 17L) % 2100L
                }
            } catch (e: Throwable) {
                readerError.set(e)
            }
        }

        // Hilo "editor": mutaciones estructurales reales, sin pausa,
        // igual que arrastrar keyframes muy rápido o un undo/redo en loop.
        val writerThread = Thread {
            readerReady.await()
            try {
                repeat(20_000) { i ->
                    when (i % 3) {
                        0 -> track.addOrReplace(kf((i % 21) * 100L, i.toFloat()))
                        1 -> track.remove((i % 21) * 100L)
                        else -> track.replaceAll((0..20).map { kf(it * 100L, (it + i).toFloat()) })
                    }
                }
            } catch (e: Throwable) {
                writerError.set(e)
            } finally {
                stop.set(true)
            }
        }

        readerThread.start()
        writerThread.start()
        writerThread.join(TimeUnit.SECONDS.toMillis(30))
        readerThread.join(TimeUnit.SECONDS.toMillis(5))

        assertNull("El hilo de GL (frameAt) no debe lanzar ninguna excepción por una mutación concurrente", readerError.get())
        assertNull("El hilo editor no debe lanzar ninguna excepción propia", writerError.get())
        assertTrue("Ambos hilos deben haber terminado dentro del timeout (ningún deadlock)", !readerThread.isAlive && !writerThread.isAlive)
    }

    @Test
    fun `una referencia a keyframes tomada en un instante no cambia aunque el CameraTrack siga mutando en otro hilo`() {
        // Complementa CameraTrackSnapshotIndependenceTest (Fase 1, un solo
        // hilo): acá la referencia se toma en un hilo mientras OTRO hilo
        // sigue mutando de verdad, en paralelo, no en secuencia.
        val track = CameraTrack(initialKeyframes = listOf(kf(0L, 0f), kf(1000L, 1f)))
        val capturedRef = AtomicReference<List<Keyframe>>()
        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)

        val capturer = Thread {
            capturedRef.set(track.keyframes) // la propiedad publicada, sin `.toList()` extra
            ready.countDown()
            done.await()
        }
        capturer.start()
        ready.await()

        val mutator = Thread {
            repeat(5_000) { i -> track.addOrReplace(kf(500L + i, i.toFloat())) }
        }
        mutator.start()
        mutator.join(TimeUnit.SECONDS.toMillis(10))
        done.countDown()
        capturer.join(TimeUnit.SECONDS.toMillis(5))

        val captured = capturedRef.get()
        assertTrue(
            "La referencia capturada antes de las 5000 mutaciones concurrentes debe seguir teniendo el tamaño original (2)",
            captured.size == 2
        )
    }
}
