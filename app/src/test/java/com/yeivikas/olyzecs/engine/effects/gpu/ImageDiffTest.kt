package com.yeivikas.olyzecs.engine.effects.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDiffTest {

    @Test
    fun `dos imagenes iguales no tienen ninguna diferencia`() {
        val pattern = EffectTestPattern.rgba(20, 10)
        val metrics = ImageDiff.compare(pattern, pattern.copyOf())
        assertEquals(0.0, metrics.meanAbsError, 0.0)
        assertEquals(0, metrics.p99)
        assertEquals(0, metrics.maxError)
        assertEquals(20 * 10 * 4, metrics.comparedChannels)
    }

    @Test
    fun `la diferencia es absoluta y trata los bytes como sin signo`() {
        // 0xFF (255) contra 0x00 (0) debe dar 255, no -1.
        val metrics = ImageDiff.compare(byteArrayOf(0xFF.toByte(), 0), byteArrayOf(0, 0xFF.toByte()))
        assertEquals(255, metrics.maxError)
        assertEquals(255.0, metrics.meanAbsError, 0.0)
    }

    @Test
    fun `el error medio es el promedio sobre todos los canales`() {
        val a = ByteArray(8)
        val b = ByteArray(8).also { it[0] = 8 }
        assertEquals(1.0, ImageDiff.compare(a, b).meanAbsError, 1e-12)
    }

    @Test
    fun `el percentil 99 ignora un unico canal atipico pero el maximo no`() {
        val a = ByteArray(400)
        val b = ByteArray(400).also { it[123] = 200.toByte() }   // 1 de 400 canales = 0,25 %
        val metrics = ImageDiff.compare(a, b)
        assertEquals(0, metrics.p99)
        assertEquals(200, metrics.maxError)
    }

    @Test
    fun `el percentil 99 refleja un error extendido`() {
        val a = ByteArray(100)
        val b = ByteArray(100) { 5 }
        assertEquals(5, ImageDiff.compare(a, b).p99)
    }

    @Test
    fun `tamanos distintos o vacios se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { ImageDiff.compare(ByteArray(4), ByteArray(8)) }
        assertThrows(IllegalArgumentException::class.java) { ImageDiff.compare(ByteArray(0), ByteArray(0)) }
    }

    @Test
    fun `los criterios de identidad aceptan una copia exacta y rechazan una alterada`() {
        val pattern = EffectTestPattern.rgba(30, 20)
        assertTrue(EquivalenceCriteria.IDENTITY.evaluate(ImageDiff.compare(pattern, pattern)).passed)
        val altered = pattern.copyOf().also { for (i in it.indices step 4) it[i] = (it[i] + 10).toByte() }
        val verdict = EquivalenceCriteria.IDENTITY.evaluate(ImageDiff.compare(pattern, altered))
        assertFalse(verdict.passed)
        assertTrue(verdict.failures.isNotEmpty())
    }

    @Test
    fun `la identidad tolera unos pocos canales con un nivel de redondeo pero no un error de dos`() {
        val base = ByteArray(1000) { 100 }
        val roundedInFewChannels = ByteArray(1000) { if (it % 50 == 0) 101 else 100 }   // 2 % de los canales, 1 nivel
        assertTrue(EquivalenceCriteria.IDENTITY.evaluate(ImageDiff.compare(base, roundedInFewChannels)).passed)
        val offByTwo = ByteArray(1000) { if (it % 50 == 0) 102 else 100 }
        assertFalse(EquivalenceCriteria.IDENTITY.evaluate(ImageDiff.compare(base, offByTwo)).passed)
    }

    @Test
    fun `un desplazamiento de un nivel en toda la imagen no es una copia fiel`() {
        val base = ByteArray(1000) { 100 }
        assertFalse(EquivalenceCriteria.IDENTITY.evaluate(ImageDiff.compare(base, ByteArray(1000) { 101 })).passed)
    }

    @Test
    fun `el criterio perceptual es mas permisivo que el de identidad y respeta el ADR`() {
        assertEquals(2.0, EquivalenceCriteria.PERCEPTUAL.maxMeanAbsError, 0.0)
        assertEquals(8, EquivalenceCriteria.PERCEPTUAL.maxP99)
        val a = ByteArray(1000) { 100 }
        val b = ByteArray(1000) { 103 }
        assertTrue(EquivalenceCriteria.PERCEPTUAL.evaluate(ImageDiff.compare(a, b)).passed.not())   // 3 > 2 de media
        val c = ByteArray(1000) { 101 }
        assertTrue(EquivalenceCriteria.PERCEPTUAL.evaluate(ImageDiff.compare(a, c)).passed)
    }
}
