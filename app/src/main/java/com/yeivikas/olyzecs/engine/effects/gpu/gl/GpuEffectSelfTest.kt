package com.yeivikas.olyzecs.engine.effects.gpu.gl

import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.effects.gpu.EffectPassKind
import com.yeivikas.olyzecs.engine.effects.gpu.EffectTestPattern
import com.yeivikas.olyzecs.engine.effects.gpu.EquivalenceCriteria
import com.yeivikas.olyzecs.engine.effects.gpu.ImageDiff
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Autotest de la tubería GPU DENTRO de la app (ADR-019 §7): como el CI no tiene GPU, esta es la forma de comprobar, en
 * el dispositivo real y sin ordenador, que el renderizado a textura funciona y no altera la imagen.
 *
 * Sube una imagen de prueba, la pasa por un pase de identidad (un framebuffer, un shader, una textura de destino) y
 * compara lo leído con el original. Escribe el resultado en el «Registro de errores» con el contexto en el que corrió.
 * Se ejecuta una vez por proceso y contexto (vista previa, exportador), nunca lanza y no altera el estado de OpenGL.
 *
 * Pensado para llamarse con el contexto GL ya activo.
 */
object GpuEffectSelfTest {

    private const val TAG = "GpuEffectSelfTest"
    private val alreadyRun = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Corre el autotest la primera vez que se pide para [contextLabel]; las siguientes no hacen nada. */
    fun runOnce(contextLabel: String) {
        if (alreadyRun.add(contextLabel)) run(contextLabel)
    }

    /** Corre el autotest ahora. Devuelve `true` si la tubería copia la imagen fielmente. */
    fun run(contextLabel: String): Boolean {
        val runner = EffectPassRunner()
        val allocator = GlTextureAllocator()
        var source = 0
        var destination = 0
        return try {
            GlStateGuard.preserving {
                val fbo = runner.fboUsable()
                val highp = runner.hasHighPrecisionFragment()
                runner.prepare(EffectPassKind.IDENTITY)
                val shaderOk = EffectPassKind.IDENTITY !in runner.failedPasses()
                val header = "[$contextLabel] FBO=${yesNo(fbo)} highp=${yesNo(highp)} shader=${yesNo(shaderOk)}"
                if (!fbo || !shaderOk) {
                    AppLogger.w(TAG, "$header — la tubería de efectos GPU NO está disponible en este contexto; los efectos usarán la CPU")
                    return@preserving false
                }

                val width = EffectTestPattern.WIDTH
                val height = EffectTestPattern.HEIGHT
                val pattern = EffectTestPattern.rgba(width, height)
                source = allocator.create(width, height)
                destination = allocator.create(width, height)
                upload(source, pattern, width, height)

                if (!runner.runPass(EffectPassKind.IDENTITY, source, destination, width, height)) {
                    AppLogger.w(TAG, "$header — el pase de identidad falló al ejecutarse")
                    return@preserving false
                }
                val actual = runner.readPixels(destination, width, height)
                if (actual == null) {
                    AppLogger.w(TAG, "$header — no se pudo leer el resultado")
                    return@preserving false
                }
                val metrics = ImageDiff.compare(pattern, actual)
                val verdict = EquivalenceCriteria.IDENTITY.evaluate(metrics)
                val summary = "$header error medio=${"%.4f".format(java.util.Locale.ROOT, metrics.meanAbsError)} " +
                    "p99=${metrics.p99} máx=${metrics.maxError} (de 255)"
                if (verdict.passed) {
                    AppLogger.i(TAG, "$summary — OK: la tubería copia la imagen sin alterarla")
                } else {
                    AppLogger.w(TAG, "$summary — FALLA: ${verdict.failures.joinToString("; ")}")
                }
                verdict.passed
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "[$contextLabel] el autotest de efectos GPU lanzó una excepción", t)
            false
        } finally {
            runCatching {
                if (source != 0) allocator.delete(source)
                if (destination != 0) allocator.delete(destination)
                runner.dispose()
            }
        }
    }

    private fun upload(texture: Int, rgba: ByteArray, width: Int, height: Int) {
        val buffer = ByteBuffer.allocateDirect(rgba.size).order(ByteOrder.nativeOrder()).put(rgba).also { it.position(0) }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
    }

    private fun yesNo(value: Boolean) = if (value) "sí" else "NO"
}
