package com.yeivikas.olyzecs.engine.effects.gpu.gl

import android.opengl.GLES20

/**
 * Ejecuta un bloque de OpenGL y deja el estado EXACTAMENTE como estaba, pase lo que pase.
 *
 * La etapa de efectos se ejecuta en mitad de un frame (o antes de leer píxeles, en las miniaturas): si cambiara el
 * framebuffer, el viewport o algún interruptor y no lo devolviera, el dibujo siguiente saldría mal sin ningún error
 * visible en el código que lo causó. Aquí se concentra todo lo que se guarda y se restaura.
 *
 * Solo restaura lo que la etapa toca: viewport, framebuffer, programa, buffer de vértices, textura 2D de la unidad 0,
 * unidad activa, máscara de color y los interruptores de mezcla, recorte, profundidad y descarte de caras.
 */
internal object GlStateGuard {

    fun <T> preserving(block: () -> T): T {
        val viewport = IntArray(4).also { GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, it, 0) }
        val framebuffer = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, it, 0) }
        val program = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, it, 0) }
        val arrayBuffer = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, it, 0) }
        val activeUnit = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, it, 0) }
        // La textura que se guarda es la de la unidad 0, que es la que la etapa usa; la unidad activa se devuelve aparte.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        val texture2d = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, it, 0) }
        val colorMask = BooleanArray(4).also { GLES20.glGetBooleanv(GLES20.GL_COLOR_WRITEMASK, it, 0) }
        val blend = GLES20.glIsEnabled(GLES20.GL_BLEND)
        val scissor = GLES20.glIsEnabled(GLES20.GL_SCISSOR_TEST)
        val depth = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        val cull = GLES20.glIsEnabled(GLES20.GL_CULL_FACE)
        try {
            return block()
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
            GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            GLES20.glUseProgram(program[0])
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, arrayBuffer[0])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture2d[0])
            GLES20.glActiveTexture(activeUnit[0])
            GLES20.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3])
            setEnabled(GLES20.GL_BLEND, blend)
            setEnabled(GLES20.GL_SCISSOR_TEST, scissor)
            setEnabled(GLES20.GL_DEPTH_TEST, depth)
            setEnabled(GLES20.GL_CULL_FACE, cull)
        }
    }

    private fun setEnabled(capability: Int, enabled: Boolean) {
        if (enabled) GLES20.glEnable(capability) else GLES20.glDisable(capability)
    }
}
