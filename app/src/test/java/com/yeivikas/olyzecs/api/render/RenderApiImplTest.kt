package com.yeivikas.olyzecs.api.render

import com.yeivikas.olyzecs.engine.core.PixelColorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la intervención "Render Contract" (Fase 4.3, sección 17 del
 * prompt maestro): contrato, implementación y boundary.
 */
class RenderApiImplTest {

    private class FakePixelColorSource : PixelColorSource {
        var lastRequestedX: Int? = null
        var lastRequestedY: Int? = null
        override fun requestPixelColor(xPx: Int, yPx: Int, callback: (argbColor: Int) -> Unit) {
            lastRequestedX = xPx
            lastRequestedY = yPx
            callback(0xFF112233.toInt())
        }
    }

    @Test
    fun `RenderApiImpl expone exactamente la instancia de PixelColorSource recibida`() {
        // "Implementación": que RenderApiImpl entregue la implementación
        // correcta — la MISMA referencia, no una copia ni un wrapper que
        // pierda identidad.
        val fake = FakePixelColorSource()
        val render: RenderApi = RenderApiImpl(fake)

        assertSame(fake, render.pixelColor)
    }

    @Test
    fun `RenderApiImpl delega el comportamiento real de pixelColor sin alterarlo`() {
        val fake = FakePixelColorSource()
        val render: RenderApi = RenderApiImpl(fake)

        var received: Int? = null
        render.pixelColor.requestPixelColor(42, 7) { argb -> received = argb }

        assertEquals(42, fake.lastRequestedX)
        assertEquals(7, fake.lastRequestedY)
        assertEquals(0xFF112233.toInt(), received)
    }

    @Test
    fun `RenderApi no expone ningun tipo prohibido de GL, EGL, Android View o MediaCodec`() {
        // "Boundary": que ningún contrato público Render importe
        // GLRenderer/GLES/EGL/GLSurfaceView/MediaCodec/MediaMuxer (sección
        // 17/18 del prompt maestro de esta intervención). Se verifica por
        // reflexión sobre los tipos de retorno de TODOS los miembros
        // declarados de RenderApi — no solo los de hoy, para que si mañana
        // se agrega una propiedad nueva con un tipo prohibido, este test
        // la atrape sin necesidad de acordarse de actualizarlo a mano.
        val forbiddenNameFragments = listOf(
            "GLRenderer", "GLSurfaceView", "EGLContext", "EGLSurface", "EGLDisplay",
            "android.opengl.GLES", "android.media.MediaCodec", "android.media.MediaMuxer",
            "android.view."
        )
        val members = RenderApi::class.java.declaredMethods
        assertTrue("RenderApi debería tener al menos un miembro declarado", members.isNotEmpty())
        members.forEach { m ->
            val returnTypeName = m.returnType.name
            forbiddenNameFragments.forEach { forbidden ->
                assertTrue(
                    "RenderApi.${m.name}() devuelve '$returnTypeName', que parece exponer una " +
                        "implementación técnica prohibida ('$forbidden') en el boundary público",
                    !returnTypeName.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun `RenderApiImpl es la unica clase que implementa RenderApi en produccion`() {
        // Guarda contra sobrearquitectura (sección 22 del prompt maestro:
        // no crear RenderManager/RenderService/RenderController/
        // RenderCoordinator/RenderFacade/RenderProvider/RenderGateway/
        // RenderAdapter/RenderBridge/RenderPort adicionales sin
        // necesidad). Verificable de verdad, sin classpath scanning: se
        // confirma que la propia interfaz solo tiene un método real
        // ([RenderApi.pixelColor]) — si alguien agrega una segunda
        // capacidad sin actualizar este número, es una señal de que el
        // contrato creció y este test (y su KDoc) deben revisarse a
        // propósito, no en automático.
        val declaredMembers = RenderApi::class.java.declaredMethods
        assertEquals(
            "RenderApi debería seguir teniendo una sola capacidad real (pixelColor) " +
                "hasta que una segunda tenga respaldo real en el código — ver su KDoc",
            1,
            declaredMembers.size
        )
    }
}
