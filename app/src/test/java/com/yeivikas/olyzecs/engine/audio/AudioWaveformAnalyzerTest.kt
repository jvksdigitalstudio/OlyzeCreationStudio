package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre el análisis de forma de onda ([AudioWaveformAnalyzer.analyze]) y su
 * remuestreo a columnas ([AudioWaveform.sampleColumns]) como JVM unit test
 * puro: trabaja sobre [DecodedPcm] sintético, sin archivos ni Android.
 *
 * Números elegidos para que las cuentas sean redondas y legibles: con
 * sampleRate = 1000 Hz, 1 frame = 1 ms, y la cubeta objetivo de 5 ms son
 * exactamente 5 frames.
 */
class AudioWaveformAnalyzerTest {

    private val sampleRate = 1000

    private fun mono(vararg values: Int) =
        DecodedPcm(ShortArray(values.size) { values[it].toShort() }, sampleRate, channelCount = 1)

    @Test
    fun `una senal constante a fondo de escala da picos y rms de 1`() {
        val pcm = DecodedPcm(ShortArray(100) { Short.MAX_VALUE }, sampleRate, 1)
        val wf = AudioWaveformAnalyzer.analyze(pcm)

        assertEquals(20, wf.bucketCount) // 100 frames / 5 frames por cubeta
        assertEquals(5.0, wf.bucketDurationMs, 1e-9)
        assertEquals(100L, wf.durationMs)
        wf.peaks.forEach { assertEquals(1f, it, 1e-3f) }
        wf.rms.forEach { assertEquals(1f, it, 1e-3f) }
    }

    @Test
    fun `un audio grabado bajo se normaliza contra su propio pico`() {
        // Máximo del archivo = 4000 (no 32767): tiene que terminar en 1.0, y lo demás en proporción.
        val values = IntArray(20) { if (it == 7) 4000 else 1000 }
        val wf = AudioWaveformAnalyzer.analyze(mono(*values))

        assertEquals(1f, wf.peaks.max(), 1e-6f)
        assertEquals(0.25f, wf.peaks.min(), 1e-3f) // 1000 / 4000
    }

    @Test
    fun `el silencio absoluto queda en cero sin dividir por cero`() {
        val wf = AudioWaveformAnalyzer.analyze(DecodedPcm(ShortArray(50), sampleRate, 1))
        wf.peaks.forEach { assertEquals(0f, it, 0f) }
        wf.rms.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `en estereo el pico es el maximo entre canales`() {
        // 5 frames = 1 cubeta. Izquierdo bajo, derecho alto.
        val interleaved = shortArrayOf(100, 8000, 100, 8000, 100, 8000, 100, 8000, 100, 8000)
        val wf = AudioWaveformAnalyzer.analyze(DecodedPcm(interleaved, sampleRate, channelCount = 2))

        assertEquals(1, wf.bucketCount)
        assertEquals(1f, wf.peaks[0], 1e-6f) // normalizado: 8000 era el máximo
        assertTrue("el rms tiene que ser menor que el pico (hay un canal mucho más bajo)", wf.rms[0] < wf.peaks[0])
    }

    @Test
    fun `Short MIN_VALUE no desborda al tomar el valor absoluto`() {
        val wf = AudioWaveformAnalyzer.analyze(DecodedPcm(ShortArray(5) { Short.MIN_VALUE }, sampleRate, 1))
        assertEquals(1f, wf.peaks[0], 1e-6f)
    }

    @Test
    fun `un archivo vacio devuelve una forma de onda vacia`() {
        val wf = AudioWaveformAnalyzer.analyze(DecodedPcm(ShortArray(0), sampleRate, 1))
        assertTrue(wf.isEmpty)
        assertEquals(0L, wf.durationMs)
    }

    @Test
    fun `un audio muy largo no supera el tope de cubetas`() {
        val pcm = DecodedPcm(ShortArray(400_000) { 1000 }, sampleRate, 1)
        val wf = AudioWaveformAnalyzer.analyze(pcm)
        assertTrue("cubetas=${wf.bucketCount}", wf.bucketCount <= 60_000)
        // Aun con cubeta ensanchada, la duración total representada tiene que seguir cerrando.
        assertEquals(400_000L, wf.durationMs)
        assertTrue(wf.bucketCount * wf.bucketDurationMs >= wf.durationMs)
    }

    @Test
    fun `al alejar el zoom un transitorio aislado no desaparece`() {
        // 1000 frames de silencio con UN solo golpe en el frame 503.
        val values = IntArray(1000)
        values[503] = 20000
        val wf = AudioWaveformAnalyzerHelper.analyzeMono(values, sampleRate)

        // 1 sola columna que cubre TODO el archivo: tiene que ver el golpe.
        val peakOut = FloatArray(1)
        val rmsOut = FloatArray(1)
        wf.sampleColumns(startMs = 0.0, msPerColumn = 1000.0, columns = 1, peakOut = peakOut, rmsOut = rmsOut)
        assertEquals(1f, peakOut[0], 1e-6f)
    }

    @Test
    fun `el recorte de inicio desplaza el tramo que se muestra`() {
        // Primera mitad silencio, segunda mitad fuerte.
        val values = IntArray(1000) { if (it >= 500) 10000 else 0 }
        val wf = AudioWaveformAnalyzerHelper.analyzeMono(values, sampleRate)

        val peakOut = FloatArray(2)
        val rmsOut = FloatArray(2)
        // Sin recorte: la primera columna (0..100 ms) cae en el silencio.
        wf.sampleColumns(0.0, 100.0, 2, peakOut, rmsOut)
        assertEquals(0f, peakOut[0], 1e-6f)
        // Con recorte de 500 ms: la MISMA primera columna ya cae en la parte fuerte.
        wf.sampleColumns(500.0, 100.0, 2, peakOut, rmsOut)
        assertEquals(1f, peakOut[0], 1e-6f)
    }

    @Test
    fun `las columnas mas alla del final del archivo quedan en cero`() {
        val wf = AudioWaveformAnalyzerHelper.analyzeMono(IntArray(100) { 5000 }, sampleRate)

        val peakOut = FloatArray(4)
        val rmsOut = FloatArray(4)
        // 4 columnas de 50 ms sobre un archivo de 100 ms: las dos últimas están fuera.
        wf.sampleColumns(0.0, 50.0, 4, peakOut, rmsOut)
        assertEquals(1f, peakOut[0], 1e-6f)
        assertEquals(1f, peakOut[1], 1e-6f)
        assertEquals(0f, peakOut[2], 0f)
        assertEquals(0f, peakOut[3], 0f)
        assertEquals(0f, rmsOut[3], 0f)
    }

    @Test
    fun `el rms nunca supera al pico de la misma columna`() {
        // Señal alternante: pico alto, energía media menor.
        val values = IntArray(500) { if (it % 10 == 0) 30000 else 3000 }
        val wf = AudioWaveformAnalyzerHelper.analyzeMono(values, sampleRate)

        val peakOut = FloatArray(10)
        val rmsOut = FloatArray(10)
        wf.sampleColumns(0.0, 50.0, 10, peakOut, rmsOut)
        for (i in 0 until 10) {
            assertTrue("columna $i: rms=${rmsOut[i]} pico=${peakOut[i]}", rmsOut[i] <= peakOut[i] + 1e-6f)
        }
    }
}

/** Pequeño helper de test para no repetir la construcción del PCM mono. */
private object AudioWaveformAnalyzerHelper {
    fun analyzeMono(values: IntArray, sampleRate: Int): AudioWaveform =
        AudioWaveformAnalyzer.analyze(
            DecodedPcm(ShortArray(values.size) { values[it].toShort() }, sampleRate, channelCount = 1)
        )
}
