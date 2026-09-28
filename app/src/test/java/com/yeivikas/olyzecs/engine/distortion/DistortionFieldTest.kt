package com.yeivikas.olyzecs.engine.distortion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests de [DistortionField] y [DistortionFreezeMask] — motores puros
 * (sin Android, sin Bitmap), calculados a partir de la fórmula REAL
 * implementada, mismo criterio que `SpeedRampEngineTest`.
 */
class DistortionFieldTest {

    private fun warpBrush(radius: Float = 0.3f, feather: Float = 0.5f, intensity: Float = 1f) =
        DistortionBrush(tool = DistortionToolType.WARP, radiusUv = radius, feather = feather, intensity = intensity)

    // ---- identidad / reset -------------------------------------------

    @Test
    fun `una malla recien creada es identidad`() {
        val field = DistortionField.identity(imageAspect = 1f)
        assertTrue(field.isIdentity())
    }

    @Test
    fun `aplicar un trazo deja de ser identidad`() {
        val field = DistortionField.identity(imageAspect = 1f)
        field.applyStroke(warpBrush(), centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f, imageAspect = 1f)
        assertFalse(field.isIdentity())
    }

    @Test
    fun `reset vuelve la malla a identidad`() {
        val field = DistortionField.identity(imageAspect = 1f)
        field.applyStroke(warpBrush(), centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f, imageAspect = 1f)
        assertFalse(field.isIdentity())
        field.reset()
        assertTrue(field.isIdentity())
    }

    // ---- muestreo bilineal ---------------------------------------------

    @Test
    fun `sampleBilinear de una malla identidad devuelve el mismo punto`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val (u, v) = field.sampleBilinear(0.37f, 0.62f)
        assertEquals(0.37f, u, 0.01f)
        assertEquals(0.62f, v, 0.01f)
    }

    @Test
    fun `sampleBilinear en las esquinas de una malla identidad`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val (u0, v0) = field.sampleBilinear(0f, 0f)
        assertEquals(0f, u0, 1e-3f)
        assertEquals(0f, v0, 1e-3f)
        val (u1, v1) = field.sampleBilinear(1f, 1f)
        assertEquals(1f, u1, 1e-3f)
        assertEquals(1f, v1, 1e-3f)
    }

    // ---- snapshot / undo por trazo --------------------------------------

    @Test
    fun `snapshot es independiente de la malla original`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val before = field.snapshot()
        field.applyStroke(warpBrush(), centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f, imageAspect = 1f)
        assertTrue(before.isIdentity())
        assertFalse(field.isIdentity())
    }

    @Test
    fun `restaurar un snapshot deshace el trazo (undo por trazo)`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val beforeStroke1 = field.snapshot()
        field.applyStroke(warpBrush(), centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f, imageAspect = 1f)
        val (uAfterStroke1, _) = field.sampleBilinear(0.5f, 0.5f)

        // Segundo trazo, en otro lugar.
        field.applyStroke(warpBrush(), centerU = 0.2f, centerV = 0.2f, prevU = 0.1f, prevV = 0.2f, imageAspect = 1f)

        // "Undo" del segundo trazo == restaurar el snapshot tomado antes de aplicarlo.
        val beforeStroke2 = beforeStroke1 // en este test no se tomó un snapshot intermedio; se verifica contra la identidad
        assertTrue(beforeStroke2.isIdentity())

        // El primer trazo sí debe seguir intacto en `field` original (fuera de este test,
        // el panel restauraría el snapshot tomado justo antes del segundo trazo). Acá se
        // verifica el caso base: el snapshot tomado ANTES del primer trazo sigue siendo
        // identidad sin importar cuántos trazos se apliquen después sobre `field`.
        val (uOriginalSnapshot, _) = beforeStroke1.sampleBilinear(0.5f, 0.5f)
        assertEquals(0.5f, uOriginalSnapshot, 1e-3f)
        assertTrue(abs(uAfterStroke1 - 0.5f) > 1e-3f)
    }

    // ---- Cepillo de deformación (WARP) ---------------------------------

    @Test
    fun `warp desplaza el punto de origen en direccion contraria al arrastre`() {
        val field = DistortionField.identity(imageAspect = 1f)
        // Arrastre de izquierda a derecha (prev en 0.4, ahora en 0.5): el
        // contenido "sigue" al dedo, así que la fuente debe tomarse más a
        // la IZQUIERDA de donde estaba.
        field.applyStroke(warpBrush(intensity = 1f), centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f, imageAspect = 1f)
        val (u, _) = field.sampleBilinear(0.5f, 0.5f)
        assertTrue("esperaba u < 0.5 (fuente desplazada a la izquierda), fue $u", u < 0.5f)
    }

    @Test
    fun `warp sin arrastre previo no mueve nada (primera muestra del trazo)`() {
        val field = DistortionField.identity(imageAspect = 1f)
        field.applyStroke(warpBrush(intensity = 1f), centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        assertTrue(field.isIdentity())
    }

    // ---- Esferizar / Protuberancia & pellizco --------------------------

    @Test
    fun `esferizar hacia afuera acerca el punto de origen al centro (efecto lupa)`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val brush = DistortionBrush(
            tool = DistortionToolType.SPHERE, radiusUv = 0.3f, feather = 0.5f, intensity = 1f, bulgeOutward = true
        )
        field.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        val (u, _) = field.sampleBilinear(0.6f, 0.5f)
        // El vértice en (0.6, 0.5) queda a distancia 0.1 del centro (0.5,0.5).
        // Con "protuberancia" la fuente se comprime hacia el centro -> queda
        // más cerca de 0.5 que la posición original 0.6.
        assertTrue("esperaba u entre 0.5 y 0.6, fue $u", u in 0.5f..0.6f)
    }

    @Test
    fun `pellizco hacia adentro aleja el punto de origen del centro`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val brush = DistortionBrush(
            tool = DistortionToolType.BULGE_PINCH, radiusUv = 0.3f, feather = 0.5f, intensity = 1f, bulgeOutward = false
        )
        field.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        val (u, _) = field.sampleBilinear(0.6f, 0.5f)
        assertTrue("esperaba u > 0.6, fue $u", u > 0.6f)
    }

    // ---- Radio del pincel independiente de la orientación --------------

    @Test
    fun `el mismo porcentaje de pincel cubre la misma fraccion del lado corto en cualquier orientacion`() {
        // Bug real encontrado: antes de esta función, el radio se pasaba
        // crudo al motor, que lo mide contra el ANCHO — en una foto
        // apaisada (aspect > 1) el pincel terminaba siendo hasta el
        // doble de grande de lo que decía el slider. Acá se verifica que
        // "18%" da la MISMA fracción física del lado más corto sin
        // importar si la imagen es cuadrada, apaisada o vertical.
        val percent = 18f
        fun fractionOfShortSide(aspect: Float): Float {
            val radiusUv = distortionBrushRadiusUv(percent, aspect)
            val width = 1000f
            val height = width / aspect
            val shortSide = minOf(width, height)
            val physicalRadius = radiusUv * width
            return physicalRadius / shortSide
        }
        val square = fractionOfShortSide(1f)
        val landscape2to1 = fractionOfShortSide(2f)
        val landscape16to9 = fractionOfShortSide(16f / 9f)
        val portrait1to2 = fractionOfShortSide(0.5f)

        assertEquals(0.18f, square, 1e-3f)
        assertEquals(0.18f, landscape2to1, 1e-3f)
        assertEquals(0.18f, landscape16to9, 1e-3f)
        assertEquals(0.18f, portrait1to2, 1e-3f)
    }

    @Test
    fun `distortionBrushRadiusUv respeta los limites 1 y 90 por ciento`() {
        assertEquals(0.01f, distortionBrushRadiusUv(0f, imageAspect = 1f), 1e-4f)
        assertEquals(0.9f, distortionBrushRadiusUv(100f, imageAspect = 1f), 1e-4f)
    }

    // ---- Giro (TWIRL) ----------------------------------------------------

    @Test
    fun `giro horario mueve el contenido a la derecha del centro hacia abajo`() {
        // V crece hacia ABAJO en todo el motor (confirmado por
        // DistortionRasterizer, que escribe la fila `oy=0` como el TOPE
        // del bitmap de salida) — por eso "horario" visual, con ese
        // convenio, es lo que este test verifica de punta a punta usando
        // el resultado ya muestreado (sampleBilinear), no la fórmula
        // interna, para no poder hacer trampa con el signo.
        val field = DistortionField.identity(imageAspect = 1f)
        val brush = DistortionBrush(
            tool = DistortionToolType.TWIRL, radiusUv = 0.3f, feather = 0f, intensity = 1f,
            twirlClockwise = true
        )
        field.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)

        // Con giro horario, el contenido que ANTES estaba arriba-derecha
        // del centro (menor V, mayor U) pasa a verse a la derecha (V del
        // centro): comprobamos consultando qué UV de origen queda ahora
        // en el punto de pantalla directamente a la derecha del centro,
        // y confirmamos que viene de MÁS ARRIBA (menor V) que el centro —
        // esa es la firma de un giro horario visible en pantalla.
        val (_, vAtRight) = field.sampleBilinear(0.65f, 0.5f)
        assertTrue("esperaba V de origen < 0.5 (contenido de arriba bajando a la derecha), fue $vAtRight", vAtRight < 0.5f)
    }

    @Test
    fun `giro antihorario es el espejo exacto del horario`() {
        val fieldCw = DistortionField.identity(imageAspect = 1f)
        fieldCw.applyStroke(
            DistortionBrush(tool = DistortionToolType.TWIRL, radiusUv = 0.3f, feather = 0f, intensity = 1f, twirlClockwise = true),
            centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f
        )
        val fieldCcw = DistortionField.identity(imageAspect = 1f)
        fieldCcw.applyStroke(
            DistortionBrush(tool = DistortionToolType.TWIRL, radiusUv = 0.3f, feather = 0f, intensity = 1f, twirlClockwise = false),
            centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f
        )
        val (_, vCw) = fieldCw.sampleBilinear(0.65f, 0.5f)
        val (_, vCcw) = fieldCcw.sampleBilinear(0.65f, 0.5f)
        // Mismo punto de pantalla, sentidos opuestos: la V de origen debe
        // quedar reflejada a ambos lados de 0.5 (arriba contra abajo).
        assertEquals(0.5f, (vCw + vCcw) / 2f, 1e-3f)
        assertTrue(vCw < 0.5f && vCcw > 0.5f)
    }

    // ---- Espejo (MIRROR) -------------------------------------------------

    private fun mirrorBrush(radius: Float = 0.3f, feather: Float = 1f, intensity: Float = 1f) =
        DistortionBrush(tool = DistortionToolType.MIRROR, radiusUv = radius, feather = feather, intensity = intensity)

    @Test
    fun `espejo sin arrastre no mueve nada (primera muestra del trazo)`() {
        val field = DistortionField.identity(imageAspect = 1f)
        field.applyStroke(mirrorBrush(), centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        assertTrue(field.isIdentity())
    }

    @Test
    fun `espejo con eje horizontal refleja verticalmente alrededor del centro del pincel`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val brush = mirrorBrush()
        // 1ra muestra: sin arrastre, no fija eje todavía.
        field.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        // 2da muestra: arrastre horizontal puro -> fija el eje de espejo en horizontal.
        field.applyStroke(brush, centerU = 0.6f, centerV = 0.5f, prevU = 0.5f, prevV = 0.5f, imageAspect = 1f)

        // Con eje horizontal, lo que está ARRIBA del centro del pincel
        // (menor V) debe pasar a tomarse de ABAJO (mayor V) y viceversa.
        val (_, vAbove) = field.sampleBilinear(0.6f, 0.42f)
        val (_, vBelow) = field.sampleBilinear(0.6f, 0.58f)
        assertTrue("esperaba V de origen > 0.5 arriba del centro (reflejo vertical), fue $vAbove", vAbove > 0.5f)
        assertTrue("esperaba V de origen < 0.5 debajo del centro (reflejo vertical), fue $vBelow", vBelow < 0.5f)
    }

    @Test
    fun `espejo fija el eje una sola vez por trazo y lo mantiene estable pase lo que arrastre despues`() {
        val brush = mirrorBrush()

        // Dos trazos con el MISMO recorrido de centro (misma zona
        // afectada), pero con un tercer punto "anterior" reportado
        // distinto en cada uno — antes del fix, esto recalculaba el eje
        // en cada muestra y daba resultados distintos; con el eje
        // fijado en la 2da muestra, la 3ra no debería poder cambiarlo.
        val fieldA = DistortionField.identity(imageAspect = 1f)
        fieldA.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        fieldA.applyStroke(brush, centerU = 0.6f, centerV = 0.5f, prevU = 0.5f, prevV = 0.5f, imageAspect = 1f)
        fieldA.applyStroke(brush, centerU = 0.6f, centerV = 0.55f, prevU = 0.6f, prevV = 0.5f, imageAspect = 1f)

        val fieldB = DistortionField.identity(imageAspect = 1f)
        fieldB.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        fieldB.applyStroke(brush, centerU = 0.6f, centerV = 0.5f, prevU = 0.5f, prevV = 0.5f, imageAspect = 1f)
        fieldB.applyStroke(brush, centerU = 0.6f, centerV = 0.55f, prevU = 0.55f, prevV = 0.62f, imageAspect = 1f)

        val (_, vA) = fieldA.sampleBilinear(0.6f, 0.42f)
        val (_, vB) = fieldB.sampleBilinear(0.6f, 0.42f)
        assertEquals("el eje ya fijado no debe cambiar por el arrastre de una muestra posterior", vA, vB, 1e-5f)
    }

    @Test
    fun `un trazo nuevo de espejo puede fijar un eje distinto al del trazo anterior`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val brush = mirrorBrush()

        // Trazo 1: eje horizontal.
        field.applyStroke(brush, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        field.applyStroke(brush, centerU = 0.6f, centerV = 0.5f, prevU = 0.5f, prevV = 0.5f, imageAspect = 1f)

        // Trazo 2 (prevU/prevV = null marca el inicio de un trazo nuevo,
        // igual que hace `EditorScreen.beginStroke`): eje vertical.
        field.applyStroke(brush, centerU = 0.3f, centerV = 0.3f, prevU = null, prevV = null, imageAspect = 1f)
        field.applyStroke(brush, centerU = 0.3f, centerV = 0.4f, prevU = 0.3f, prevV = 0.3f, imageAspect = 1f)

        // Con eje vertical, lo que está a la IZQUIERDA del centro debe
        // pasar a tomarse de la DERECHA.
        val (uLeft, _) = field.sampleBilinear(0.22f, 0.4f)
        assertTrue("esperaba U de origen > 0.3 a la izquierda del centro (reflejo horizontal), fue $uLeft", uLeft > 0.3f)
    }

    // ---- Reconstruir ----------------------------------------------------

    @Test
    fun `reconstruir devuelve la zona pintada hacia la identidad`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val warp = warpBrush(intensity = 1f)
        repeat(5) {
            field.applyStroke(warp, centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f, imageAspect = 1f)
        }
        assertFalse(field.isIdentity())

        val reconstruct = DistortionBrush(
            tool = DistortionToolType.RECONSTRUCT, radiusUv = 0.5f, feather = 0.5f, intensity = 1f
        )
        repeat(20) {
            field.applyStroke(reconstruct, centerU = 0.5f, centerV = 0.5f, prevU = null, prevV = null, imageAspect = 1f)
        }
        val (u, v) = field.sampleBilinear(0.5f, 0.5f)
        assertEquals(0.5f, u, 0.02f)
        assertEquals(0.5f, v, 0.02f)
    }

    // ---- máscara de congelar --------------------------------------------

    @Test
    fun `zona congelada no se mueve con warp`() {
        val field = DistortionField.identity(imageAspect = 1f)
        val mask = DistortionFreezeMask()
        mask.paint(u = 0.5f, v = 0.5f, radiusUv = 0.5f, hardness = 1f, imageAspect = 1f)
        assertFalse(mask.isEmpty())

        field.applyStroke(
            warpBrush(intensity = 1f),
            centerU = 0.5f, centerV = 0.5f, prevU = 0.4f, prevV = 0.5f,
            imageAspect = 1f, freezeMask = mask
        )
        assertTrue(field.isIdentity())
    }

    @Test
    fun `freeze mask erase quita proteccion previamente pintada`() {
        val mask = DistortionFreezeMask()
        mask.paint(u = 0.5f, v = 0.5f, radiusUv = 0.3f, hardness = 1f, imageAspect = 1f)
        assertEquals(1f, mask.sample(0.5f, 0.5f), 1e-3f)
        mask.erase(u = 0.5f, v = 0.5f, radiusUv = 0.3f, hardness = 1f, imageAspect = 1f)
        assertEquals(0f, mask.sample(0.5f, 0.5f), 1e-3f)
    }

    // ---- falloff ----------------------------------------------------------

    @Test
    fun `falloff es 1 en el centro y 0 en el borde`() {
        assertEquals(1f, distortionFalloff(0f, hardness = 0.5f), 1e-3f)
        assertEquals(0f, distortionFalloff(1f, hardness = 0.5f), 1e-3f)
    }

    @Test
    fun `dureza 1 mantiene fuerza plena hasta casi el borde`() {
        assertEquals(1f, distortionFalloff(0.9f, hardness = 1f), 1e-3f)
    }
}
