package com.yeivikas.olyzecs.engine.effects.gpu.gl

import android.opengl.GLES20
import com.yeivikas.olyzecs.engine.effects.gpu.TextureAllocator

/**
 * Crea texturas RGBA de 8 bits aptas para ser destino de render, y las destruye. Implementa el contrato que usa
 * [com.yeivikas.olyzecs.engine.effects.gpu.GpuTexturePool], que es lo que se prueba en JVM.
 *
 * Respeta `GL_MAX_TEXTURE_SIZE`: pedir más lanza en vez de crear una textura inservible (el pool propaga el error y la
 * etapa cae al respaldo CPU; nunca una reducción silenciosa).
 */
internal class GlTextureAllocator : TextureAllocator {

    private var cachedMaxTextureSize = 0

    /** El contexto cambió: el máximo se vuelve a consultar. */
    fun onContextLost() {
        cachedMaxTextureSize = 0
    }

    private fun maxTextureSize(): Int {
        if (cachedMaxTextureSize == 0) {
            val value = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0)
            cachedMaxTextureSize = value[0]
        }
        return cachedMaxTextureSize
    }

    override fun create(width: Int, height: Int): Int {
        val max = maxTextureSize()
        check(width <= max && height <= max) { "Textura de ${width}x$height excede el máximo de la GPU ($max)" }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        check(id != 0) { "glGenTextures no devolvió ninguna textura" }
        val previous = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, previous, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previous[0])
        val glError = GLES20.glGetError()
        if (glError != GLES20.GL_NO_ERROR) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            error("OpenGL devolvió 0x${glError.toString(16)} al reservar una textura de ${width}x$height")
        }
        return id
    }

    override fun delete(textureId: Int) {
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}
