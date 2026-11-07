package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Parser WAV puro ([AudioProcessor.decodeWavStream]). Cubre cabeceras reales
 * "raras" que antes fallaban (data de 0xFFFFFFFF de un WAV en streaming) o
 * tumbaban la app (tamaño declarado enorme => OutOfMemoryError al reservar el
 * array antes de leer).
 */
class AudioWavStreamTest {

    private fun le16(v: Int): ByteArray = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le32(v: Long): ByteArray = byteArrayOf(
        (v and 0xFFL).toByte(),
        ((v shr 8) and 0xFFL).toByte(),
        ((v shr 16) and 0xFFL).toByte(),
        ((v shr 24) and 0xFFL).toByte()
    )

    private fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

    private fun pcm16(vararg values: Int): ByteArray {
        val out = ByteArrayOutputStream()
        for (v in values) out.write(le16(v))
        return out.toByteArray()
    }

    private fun wav(
        declaredDataSize: Long,
        audio: ByteArray,
        channels: Int = 1,
        rate: Int = 8000,
        bits: Int = 16,
        format: Int = 1,
        beforeData: ByteArray = ByteArray(0)
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ascii("RIFF")); out.write(le32(0L)); out.write(ascii("WAVE"))
        out.write(ascii("fmt ")); out.write(le32(16L))
        out.write(le16(format)); out.write(le16(channels)); out.write(le32(rate.toLong()))
        out.write(le32((rate * channels * bits / 8).toLong()))
        out.write(le16(channels * bits / 8)); out.write(le16(bits))
        out.write(beforeData)
        out.write(ascii("data")); out.write(le32(declaredDataSize)); out.write(audio)
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray): DecodedPcm? =
        AudioProcessor.decodeWavStream(ByteArrayInputStream(bytes)) { }

    @Test
    fun `wav 16-bit mono normal`() {
        val audio = pcm16(1000, -1000, 500, -500)
        val decoded = parse(wav(audio.size.toLong(), audio))
        assertNotNull(decoded)
        assertEquals(8000, decoded!!.sampleRateHz)
        assertEquals(1, decoded.channelCount)
        assertArrayEquals(shortArrayOf(1000, -1000, 500, -500), decoded.samples)
    }

    @Test
    fun `data con tamano 0xFFFFFFFF (wav en streaming) se lee hasta el final`() {
        val audio = pcm16(1, 2, 3, 4, 5, 6)
        val decoded = parse(wav(0xFFFFFFFFL, audio))
        assertNotNull(decoded)
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5, 6), decoded!!.samples)
    }

    @Test
    fun `data con tamano 0 se lee hasta el final`() {
        val audio = pcm16(7, 8, 9)
        val decoded = parse(wav(0L, audio))
        assertNotNull(decoded)
        assertArrayEquals(shortArrayOf(7, 8, 9), decoded!!.samples)
    }

    @Test
    fun `tamano declarado enorme con pocos bytes reales no revienta la memoria`() {
        val audio = pcm16(10, 20, 30, 40)
        val decoded = parse(wav(0x7FFFFFFFL, audio))
        assertNotNull(decoded)
        assertEquals(4, decoded!!.samples.size)
    }

    @Test
    fun `un chunk extra antes de data se salta`() {
        val list = ascii("LIST") + le32(4L) + ascii("abcd")
        val audio = pcm16(11, 22)
        val decoded = parse(wav(audio.size.toLong(), audio, beforeData = list))
        assertNotNull(decoded)
        assertArrayEquals(shortArrayOf(11, 22), decoded!!.samples)
    }

    @Test
    fun `un resto que no cierra un frame estereo se descarta`() {
        val audio = pcm16(1, 2, 3) // 3 muestras en estereo = 1 frame + 1 muestra sobrante
        val decoded = parse(wav(audio.size.toLong(), audio, channels = 2))
        assertNotNull(decoded)
        assertEquals(2, decoded!!.channelCount)
        assertArrayEquals(shortArrayOf(1, 2), decoded.samples)
    }

    @Test
    fun `wav de 24 bits baja a 16 bits conservando los bits altos`() {
        val audio = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F)
        val decoded = parse(wav(audio.size.toLong(), audio, bits = 24))
        assertNotNull(decoded)
        assertArrayEquals(shortArrayOf(32767), decoded!!.samples)
    }

    @Test
    fun `wav float de 32 bits se convierte a 16 bits`() {
        val audio = le32(java.lang.Float.floatToIntBits(0.5f).toLong() and 0xFFFFFFFFL)
        val decoded = parse(wav(audio.size.toLong(), audio, bits = 32, format = 3))
        assertNotNull(decoded)
        assertEquals((0.5f * Short.MAX_VALUE).toInt(), decoded!!.samples[0].toInt())
    }

    @Test
    fun `bloque fmt con tamano absurdo falla sin reservar memoria`() {
        val out = ByteArrayOutputStream()
        out.write(ascii("RIFF")); out.write(le32(0L)); out.write(ascii("WAVE"))
        out.write(ascii("fmt ")); out.write(le32(0x7FFFFFFFL))
        var reason: String? = null
        val decoded = AudioProcessor.decodeWavStream(ByteArrayInputStream(out.toByteArray())) { reason = it }
        assertNull(decoded)
        assertTrue(reason != null)
    }

    @Test
    fun `un archivo que no es wav devuelve null`() {
        assertNull(parse(ascii("esto no es un wav, es texto cualquiera")))
    }
}
