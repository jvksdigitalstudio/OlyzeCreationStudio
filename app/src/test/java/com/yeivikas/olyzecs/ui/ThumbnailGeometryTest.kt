package com.yeivikas.olyzecs.ui

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometría de la miniatura de capa de la columna izquierda del timeline
 * ([THUMBNAIL_CORNER_RADIUS], [THUMBNAIL_GROUP_BORDER], [THUMBNAIL_CONTENT_INSET]).
 *
 * Regresión: la imagen llenaba el recuadro entero y el borde de color de las capas de grupo, pintado
 * encima con el mismo color que el fondo de la fila, tapaba su contorno: un aro que tocaba el margen de
 * su imagen se veía cortado en plano por los cuatro lados. La imagen debe quedar siempre por dentro del
 * borde y de las esquinas redondeadas, y seguir ocupando casi todo el recuadro.
 */
class ThumbnailGeometryTest {

    @Test
    fun `el margen de la imagen nunca es menor que el grosor del borde de grupo`() {
        assertTrue(
            "el borde taparía la imagen: margen ${THUMBNAIL_CONTENT_INSET.value} dp < borde ${THUMBNAIL_GROUP_BORDER.value} dp",
            THUMBNAIL_CONTENT_INSET >= THUMBNAIL_GROUP_BORDER
        )
    }

    @Test
    fun `la esquina de la imagen queda dentro del contorno interior del borde redondeado`() {
        // El borde redondeado tiene radio R y grosor b: su contorno interior es un arco de radio R - b
        // centrado a R del vértice del recuadro. La esquina de la imagen, a [inset] de cada lado, queda a
        // sqrt(2) * (R - inset) de ese centro; debe estar a R - b o menos para no tocar el borde.
        val radius = THUMBNAIL_CORNER_RADIUS.value
        val border = THUMBNAIL_GROUP_BORDER.value
        val inset = THUMBNAIL_CONTENT_INSET.value
        val distanceToArcCenter = if (inset >= radius) 0f else sqrt(2f) * (radius - inset)
        assertTrue(
            "la esquina de la imagen toca el borde: $distanceToArcCenter dp > ${radius - border} dp",
            distanceToArcCenter <= radius - border
        )
    }

    @Test
    fun `la imagen sigue ocupando casi todo el recuadro de la miniatura`() {
        // El tamaño del recuadro (36 dp) es el que el usuario pidió conservar: el margen solo puede
        // restarle una fracción pequeña.
        val side = THUMBNAIL_BOX_SIZE.value - 2f * THUMBNAIL_CONTENT_INSET.value
        assertTrue("el margen achica demasiado la imagen: $side dp", side / THUMBNAIL_BOX_SIZE.value >= 0.8f)
        assertEquals(30f, side, 1e-4f)
    }
}
