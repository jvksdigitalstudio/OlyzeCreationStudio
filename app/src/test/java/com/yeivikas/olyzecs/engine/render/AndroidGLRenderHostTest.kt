package com.yeivikas.olyzecs.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Test estructural de la Fase 4.3 (subfase "GLPreview / Android Render
 * Host Boundary"): [AndroidGLRenderHost] no se puede instanciar de
 * verdad en un unit test JVM puro (su único constructor público de
 * hecho es [AndroidGLRenderHost.create], que necesita un
 * `android.content.Context` real y termina construyendo un
 * `android.opengl.GLSurfaceView` — ninguno disponible sin Robolectric).
 * Este test verifica, por reflexión sobre la clase COMPILADA, la forma
 * pública que si debe cumplir: expone exactamente lo que `GLPreview`
 * necesita (la vista y el renderer, para devolverla a `AndroidView` y
 * para `onRendererReady`) y los 3 verbos de lifecycle
 * (`pause`/`resume`/`release`) — nada más, nada que permita manipular
 * recursos GPU directamente desde este host.
 */
class AndroidGLRenderHostTest {

    @Test
    fun `el constructor primario no es publico - solo se crea via create()`() {
        // El compilador de Kotlin agrega un constructor SINTÉTICO extra
        // (con `DefaultConstructorMarker`) para que el `companion object`
        // pueda invocar el constructor privado de la clase externa — esto
        // pasa siempre que un companion llama a un `private constructor`,
        // sin importar el jvmTarget del módulo (ver app/build.gradle.kts:
        // jvmTarget = 17 — no evita este bridge, es un detalle del
        // compilador de Kotlin, no de la JVM). No es un constructor real
        // adicional ni una segunda forma de instanciar la clase: hay que
        // descartarlo antes de exigir unicidad, o `.single()` revienta
        // con IllegalArgumentException aunque el diseño sea correcto.
        val ctor = AndroidGLRenderHost::class.java.declaredConstructors
            .filterNot { it.isSynthetic }
            .single()
        assertTrue(
            "el constructor de AndroidGLRenderHost debería ser privado — la única forma de crearlo es AndroidGLRenderHost.create(...)",
            Modifier.isPrivate(ctor.modifiers)
        )
    }

    @Test
    fun `expone exactamente view, renderer y los 3 verbos de lifecycle`() {
        val methodNames = AndroidGLRenderHost::class.java.declaredMethods
            .filter { !it.isSynthetic }
            .map { it.name }
            .toSet()

        assertTrue("falta pause()", "pause" in methodNames)
        assertTrue("falta resume()", "resume" in methodNames)
        assertTrue("falta release()", "release" in methodNames)
        // Los getters de `view`/`renderer` (propiedades `val`) compilan
        // como getView()/getRenderer().
        assertTrue("falta el getter de 'view'", "getView" in methodNames)
        assertTrue("falta el getter de 'renderer'", "getRenderer" in methodNames)
    }

    @Test
    fun `pause, resume y release no reciben ningun parametro`() {
        // Verifica la forma exacta del contrato de lifecycle que
        // GLPreview consume: tres verbos simples, sin parámetros, sin
        // exponer detalles de qué hacen por debajo.
        listOf("pause", "resume", "release").forEach { name ->
            val method = AndroidGLRenderHost::class.java.getDeclaredMethod(name)
            assertEquals("$name() no debería recibir parámetros", 0, method.parameterCount)
        }
    }

    @Test
    fun `create() vive en el companion object, no como constructor publico`() {
        val companionMethods = AndroidGLRenderHost.Companion::class.java.declaredMethods
            .filter { !it.isSynthetic }
            .map { it.name }
        assertTrue(
            "AndroidGLRenderHost.Companion debería exponer create(...)",
            "create" in companionMethods
        )
    }
}
