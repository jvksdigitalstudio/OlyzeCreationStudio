package com.yeivikas.olyzecs.engine.audio.decode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SampleConverterTest {

    private fun convert(bytes: ByteArray, format: SampleFormat): ShortArray {
        val out = ShortArray(bytes.size / format.bytesPerSample)
        val count = SampleConverter.toInt16(bytes, bytes.size, format, out)
        assertEquals(out.size, count)
        return out
    }

    private fun le(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    @Test
    fun `8 bits sin signo se centra y escala a 16 bits`() {
        assertArrayEquals(shortArrayOf(-32768, 0, 32512), convert(byteArrayOf(0, 128.toByte(), 255.toByte()), SampleFormat.U8))
    }

    @Test
    fun `16 bits pasa tal cual`() {
        val bytes = le(6) { putShort(-1234); putShort(0); putShort(32767) }
        assertArrayEquals(shortArrayOf(-1234, 0, 32767), convert(bytes, SampleFormat.S16))
    }

    @Test
    fun `24 bits conserva los 16 mas significativos y respeta el signo`() {
        // 0x7FFFFF -> 0x7FFF ; 0x800000 (el mínimo) -> 0x8000 ; 0x000100 -> 1 ; -1 (0xFFFFFF) -> -1
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0x7F,
            0x00, 0x00, 0x80.toByte(),
            0x00, 0x01, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        assertArrayEquals(shortArrayOf(32767, -32768, 1, -1), convert(bytes, SampleFormat.S24))
    }

    @Test
    fun `32 bits entero conserva los 16 mas significativos`() {
        val bytes = le(12) { putInt(0x7FFF0000); putInt(Int.MIN_VALUE); putInt(0x00010000) }
        assertArrayEquals(shortArrayOf(32767, -32768, 1), convert(bytes, SampleFormat.S32))
    }

    @Test
    fun `flotante de 32 bits se acota a mas menos uno antes de escalar`() {
        val bytes = le(16) { putFloat(0.5f); putFloat(-0.5f); putFloat(3.0f); putFloat(-3.0f) }
        assertArrayEquals(
            shortArrayOf(16383, -16383, 32767, -32767),
            convert(bytes, SampleFormat.F32)
        )
    }

    @Test
    fun `flotante de 64 bits se acota a mas menos uno antes de escalar`() {
        val bytes = le(24) { putDouble(0.0); putDouble(2.0); putDouble(-2.0) }
        assertArrayEquals(shortArrayOf(0, 32767, -32767), convert(bytes, SampleFormat.F64))
    }

    @Test
    fun `un resto menor que una muestra se ignora`() {
        val out = ShortArray(2)
        assertEquals(1, SampleConverter.toInt16(byteArrayOf(1, 0, 9), 3, SampleFormat.S16, out))
    }

    @Test
    fun `el formato de un WAV sale de su codigo y su profundidad`() {
        assertEquals(SampleFormat.F32, SampleFormat.ofWav(isFloat = true, bitsPerSample = 32))
        assertEquals(SampleFormat.F64, SampleFormat.ofWav(isFloat = true, bitsPerSample = 64))
        assertEquals(SampleFormat.U8, SampleFormat.ofWav(isFloat = false, bitsPerSample = 8))
        assertEquals(SampleFormat.S24, SampleFormat.ofWav(isFloat = false, bitsPerSample = 24))
        assertEquals(SampleFormat.S32, SampleFormat.ofWav(isFloat = false, bitsPerSample = 32))
        assertEquals(SampleFormat.S16, SampleFormat.ofWav(isFloat = false, bitsPerSample = 16))
        assertEquals(SampleFormat.S16, SampleFormat.ofWav(isFloat = true, bitsPerSample = 16)) // combinación rara: 16 bits
    }
}
