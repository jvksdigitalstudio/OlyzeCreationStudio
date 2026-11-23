package com.yeivikas.olyzecs.ui.editview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditViewSessionTest {

    @Test
    fun `conserva intactos los valores de origen`() {
        val session = EditViewSession(originalTranslateX = 0.3f, originalTranslateY = -0.2f, originalScale = 0.25f)
        session.markFitApplied(0.9f)
        assertEquals(0.3f, session.originalTranslateX, 0f)
        assertEquals(-0.2f, session.originalTranslateY, 0f)
        assertEquals(0.25f, session.originalScale, 0f)
    }

    @Test
    fun `sin ajuste previo siempre hay que aplicarlo`() {
        val session = EditViewSession(0f, 0f, 1f)
        assertNull(session.appliedFitScale)
        assertTrue(session.shouldApplyFit(currentScale = 0.25f))
    }

    @Test
    fun `si la escala sigue siendo la del ajuste se reajusta al cambiar el visor`() {
        val session = EditViewSession(0f, 0f, 1f)
        session.markFitApplied(0.9f)
        assertTrue(session.shouldApplyFit(currentScale = 0.9f))
    }

    @Test
    fun `si el usuario hizo zoom a mano el ajuste se respeta y no se pisa`() {
        val session = EditViewSession(0f, 0f, 1f)
        session.markFitApplied(0.9f)
        assertFalse(session.shouldApplyFit(currentScale = 1.4f))
    }

    @Test
    fun `tras un reajuste el nuevo valor pasa a ser la referencia`() {
        val session = EditViewSession(0f, 0f, 1f)
        session.markFitApplied(0.9f)
        session.markFitApplied(0.7f)
        assertTrue(session.shouldApplyFit(currentScale = 0.7f))
        assertFalse(session.shouldApplyFit(currentScale = 0.9f))
    }
}
