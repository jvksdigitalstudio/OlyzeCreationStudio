package com.yeivikas.olyzecs.engine.effects.gpu.gl

import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.effects.gpu.EffectPassKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Ejecuta UN pase de efectos: dibuja una textura de origen sobre una textura de destino con el shader del pase.
 * Responsabilidad única: la mecánica de OpenGL (programas, framebuffer, dibujo, lectura). Qué pases correr, en qué orden y
 * con qué caché lo decide quien lo usa.
 *
 * - Los programas se compilan al primer uso. Si uno no compila o no enlaza, queda en [failedPasses] (no se reintenta).
 * - Todo lo que toca del estado de OpenGL se devuelve intacto ([GlStateGuard]).
 * - Solo debe usarse desde el hilo con el contexto GL activo. Tras perder el contexto, llamar a [onContextLost].
 */
internal class EffectPassRunner {

    private class PassProgram(val program: Int, val aPosition: Int, val aTexCoord: Int, val uTexture: Int)

    private val programs = HashMap<EffectPassKind, PassProgram>()
    private val failed = HashSet<EffectPassKind>()
    private var framebuffer = 0
    private var fboProbe: Boolean? = null
    private var highPrecision: Boolean? = null

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    /** Pases que tienen shader en el código (aunque en este dispositivo luego pudieran no compilar). */
    fun implementedPasses(): Set<EffectPassKind> = IMPLEMENTED

    /** Pases cuyo shader falló al compilar o enlazar en este dispositivo. */
    fun failedPasses(): Set<EffectPassKind> = failed

    /** Compila ya el programa de [kind] (si no lo estaba), para que un fallo conste antes de decidir GPU o CPU. */
    fun prepare(kind: EffectPassKind) {
        ensureProgram(kind)
    }

    /** `true` si se puede renderizar a una textura (FBO completo). Se sondea una vez por contexto. */
    fun fboUsable(): Boolean = fboProbe ?: probeFbo().also { fboProbe = it }

    /** `true` si el fragment shader dispone de `highp`. Se consulta una vez por contexto. */
    fun hasHighPrecisionFragment(): Boolean = highPrecision ?: queryHighPrecision().also { highPrecision = it }

    /**
     * Dibuja [source] sobre [destination] (ambas de [width] x [height]) con el pase [kind]. Devuelve `false`, sin alterar
     * nada, si no pudo (programa no disponible, FBO incompleto o error de OpenGL).
     */
    fun runPass(kind: EffectPassKind, source: Int, destination: Int, width: Int, height: Int): Boolean =
        GlStateGuard.preserving {
            val pass = ensureProgram(kind) ?: return@preserving false
            if (!attachDestination(destination)) return@preserving false
            drainErrors()
            GLES20.glViewport(0, 0, width, height)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glColorMask(true, true, true, true)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glUseProgram(pass.program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source)
            GLES20.glUniform1i(pass.uTexture, 0)

            val positionWasEnabled = isAttributeEnabled(pass.aPosition)
            val texCoordWasEnabled = isAttributeEnabled(pass.aTexCoord)
            quad.position(0)
            GLES20.glVertexAttribPointer(pass.aPosition, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, quad)
            GLES20.glEnableVertexAttribArray(pass.aPosition)
            quad.position(2)
            GLES20.glVertexAttribPointer(pass.aTexCoord, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, quad)
            GLES20.glEnableVertexAttribArray(pass.aTexCoord)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            if (!positionWasEnabled) GLES20.glDisableVertexAttribArray(pass.aPosition)
            if (!texCoordWasEnabled) GLES20.glDisableVertexAttribArray(pass.aTexCoord)

            detachDestination()
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                AppLogger.e(TAG, "OpenGL devolvió 0x${error.toString(16)} ejecutando el pase $kind")
                drainErrors()
                return@preserving false
            }
            true
        }

    /** Lee la textura [texture] (RGBA, [width] x [height], fila 0 abajo) a memoria. `null` si no se pudo. */
    fun readPixels(texture: Int, width: Int, height: Int): ByteArray? =
        GlStateGuard.preserving {
            if (!attachDestination(texture)) return@preserving null
            drainErrors()
            val buffer = ByteBuffer.allocateDirect(width * height * RGBA_BYTES).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            detachDestination()
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
                drainErrors()
                return@preserving null
            }
            ByteArray(buffer.capacity()).also { buffer.rewind(); buffer.get(it) }
        }

    /** El contexto GL se perdió o se recreó: todo identificador anterior es inválido y se olvida sin destruir. */
    fun onContextLost() {
        programs.clear()
        failed.clear()
        framebuffer = 0
        fboProbe = null
        highPrecision = null
    }

    /** Cierre normal: destruye programas y framebuffer. */
    fun dispose() {
        programs.values.forEach { GLES20.glDeleteProgram(it.program) }
        programs.clear()
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            framebuffer = 0
        }
    }

    private fun ensureProgram(kind: EffectPassKind): PassProgram? {
        programs[kind]?.let { return it }
        if (kind in failed) return null
        // Un pase sin shader aún no es un FALLO de compilación: simplemente no está implementado.
        val fragment = EffectShaders.fragmentFor(kind) ?: return null
        val program = GlShaderCompiler.buildProgram(TAG, "pase $kind", EffectShaders.VERTEX, fragment)
        if (program == 0) {
            failed += kind
            return null
        }
        val aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        val aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        val uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        if (aPosition < 0 || aTexCoord < 0 || uTexture < 0) {
            AppLogger.e(TAG, "El shader del pase $kind no expone todos sus parámetros (posición/coordenadas/textura)")
            GLES20.glDeleteProgram(program)
            failed += kind
            return null
        }
        return PassProgram(program, aPosition, aTexCoord, uTexture).also { programs[kind] = it }
    }

    private fun ensureFramebuffer(): Boolean {
        if (framebuffer != 0) return true
        val ids = IntArray(1)
        GLES20.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        return framebuffer != 0
    }

    /** Enlaza el framebuffer propio con [texture] como destino de color. `false` si no queda completo. */
    private fun attachDestination(texture: Int): Boolean {
        if (!ensureFramebuffer()) {
            fboProbe = false
            return false
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            AppLogger.w(TAG, "Framebuffer incompleto (estado 0x${status.toString(16)}): no se puede renderizar a textura")
            fboProbe = false
            detachDestination()
            return false
        }
        return true
    }

    private fun detachDestination() {
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0)
    }

    private fun probeFbo(): Boolean = GlStateGuard.preserving {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        if (ids[0] == 0) return@preserving false
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        var usable = false
        if (ensureFramebuffer()) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ids[0], 0)
            usable = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
            detachDestination()
        }
        GLES20.glDeleteTextures(1, ids, 0)
        drainErrors()
        usable
    }

    private fun queryHighPrecision(): Boolean {
        val range = IntArray(2)
        val precision = IntArray(1)
        GLES20.glGetShaderPrecisionFormat(GLES20.GL_FRAGMENT_SHADER, GLES20.GL_HIGH_FLOAT, range, 0, precision, 0)
        // precision[0] == 0 significa que el dispositivo no ofrece `highp` en fragmento.
        return precision[0] != 0
    }

    private fun isAttributeEnabled(index: Int): Boolean {
        val value = IntArray(1)
        GLES20.glGetVertexAttribiv(index, GLES20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, value, 0)
        return value[0] != 0
    }

    /** Descarta errores pendientes de otros para no atribuirnos uno ajeno (y limpia los nuestros). */
    private fun drainErrors() {
        var guard = 0
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR && guard++ < MAX_DRAIN) { /* vaciar */ }
    }

    private companion object {
        const val TAG = "EffectPassRunner"
        const val FLOAT_BYTES = 4
        const val RGBA_BYTES = 4
        const val STRIDE_BYTES = 4 * FLOAT_BYTES
        const val MAX_DRAIN = 16

        /** Pases con shader hoy. Debe coincidir con los que devuelve `EffectShaders.fragmentFor` distintos de null. */
        val IMPLEMENTED: Set<EffectPassKind> = EffectPassKind.entries.filter { EffectShaders.fragmentFor(it) != null }.toSet()

        /** x, y, u, v por vértice: un cuadrado de recorte completo, orientado igual en origen y destino. */
        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }
}
