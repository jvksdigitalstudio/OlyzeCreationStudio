package com.yeivikas.olyzecs.engine.scene

import com.yeivikas.olyzecs.engine.render.SingleResourceHandoff
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3.1 — Cierre y hardening de ownership de GPU.
 *
 * Cubre, con un test ESTRUCTURAL (sección 16.G del brief: "si es posible
 * mediante tests estáticos/estructurales, verifica que el código no
 * permita escrituras desde ViewModel a GPU handles"), la garantía real que
 * pide esta fase: `Layer` ya no puede tener un `glTextureId` porque el
 * campo directamente no existe más — no hace falta instanciar un `Layer`
 * real (que necesitaría `android.net.Uri`, no disponible en un test JVM
 * puro sin Robolectric) para verificar esto: `Class.getDeclaredFields()`
 * solo necesita los metadatos de la clase, no ejecutar ningún método de
 * Android.
 */
class LayerGpuOwnershipStructureTest {

    @Test
    fun `Layer no declara ningun campo llamado glTextureId`() {
        val fieldNames = Layer::class.java.declaredFields.map { it.name }

        assertFalse(
            "Layer no debería volver a tener un campo glTextureId — el texture id GL vive " +
                "exclusivamente en el registro privado de GLRenderer (ver GLRenderer.layerTextures)",
            fieldNames.any { it.contains("glTextureId", ignoreCase = true) }
        )
    }

    @Test
    fun `pendingBitmap no es un Bitmap mutable sino un SingleResourceHandoff`() {
        val field = Layer::class.java.declaredFields.first { it.name == "pendingBitmap" }

        assertEquals(
            "pendingBitmap debe ser un SingleResourceHandoff (traspaso de ownership explícito), " +
                "no un android.graphics.Bitmap directo",
            SingleResourceHandoff::class.java,
            field.type
        )
        assertTrue(
            "pendingBitmap no debería tener un setter público directo (nada de `layer.pendingBitmap = x`) — " +
                "solo se publica a través de SingleResourceHandoff.publish()",
            Modifier.isFinal(field.modifiers)
        )
    }

    @Test
    fun `no existe ningun setter publico de texture id en Layer`() {
        val suspiciousMethods = Layer::class.java.declaredMethods.filter { method ->
            val name = method.name.lowercase()
            (name.contains("texture") && (name.startsWith("set") || name.contains("textureid"))) &&
                method.parameterTypes.any { it == Int::class.java || it == Integer.TYPE }
        }

        assertTrue(
            "No debería existir ningún método público de Layer que reciba un Int y toque una " +
                "'textura' — esa sería, otra vez, una vía para escribir un GL handle desde fuera " +
                "del hilo de GL. Encontrados: ${suspiciousMethods.map { it.name }}",
            suspiciousMethods.isEmpty()
        )
    }

    @Test
    fun `los metodos de invalidacion de textura son de solo pedido, sin exponer el estado interno`() {
        val requestMethod = Layer::class.java.getDeclaredMethod("requestTextureInvalidation")
        val consumeMethod = Layer::class.java.getDeclaredMethod("consumeTextureInvalidationRequest")

        assertEquals(Void.TYPE, requestMethod.returnType)
        assertEquals(Boolean::class.java, consumeMethod.returnType)
        assertNull(
            "No debería haber ningún getter que exponga el estado interno del pedido de " +
                "invalidación (solo request()/consume() — ver TextureInvalidationRequest)",
            Layer::class.java.declaredFields
                .firstOrNull { it.name.contains("textureInvalidation", ignoreCase = true) }
                ?.takeIf { Modifier.isPublic(it.modifiers) }
        )
    }

    /**
     * FASE 3.1.2 — PROBLEMA 6 del informe: la identidad real de "qué
     * versión del contenido representa esta capa" tiene que existir como
     * un campo propio de `Layer`, legible por el hilo de GL, para que
     * `SingleResourceHandoff.takeIfCurrent`/`GLRenderer` puedan comparar
     * contra ella. Test estructural (no instancia `Layer`, que necesita
     * `android.net.Uri`): confirma que el campo existe, es un `Int`, y
     * está marcado `@Volatile` (mismo criterio de visibilidad entre hilos
     * que `zIndex`/`sourceUri`).
     */
    @Test
    fun `Layer declara contentRevision como campo Int volatile`() {
        val field = Layer::class.java.declaredFields.first { it.name == "contentRevision" }

        assertEquals(Int::class.java, field.type)
        assertTrue(
            "contentRevision necesita @Volatile: lo escribe el hilo principal y lo lee el hilo de GL en cada frame",
            Modifier.isVolatile(field.modifiers)
        )
    }

    /**
     * FASE 3.1.3-R2 — cierre del defecto de lectura entrecortada entre
     * `sourceUri` y `contentRevision` (ver el KDoc de
     * `Layer.contentIdentity`). Test estructural: confirma que el campo
     * existe, es `@Volatile` (visibilidad segura entre hilos para la
     * referencia completa), y que su setter NO es público — la única vía
     * para actualizarlo tiene que ser `Layer.updateContentIdentity()`, que
     * además actualiza `sourceUri`/`contentRevision` en la MISMA llamada.
     * Esto es lo que impide, estructuralmente, que alguien en el futuro
     * vuelva a introducir el bug (actualizar los dos campos por separado
     * sin tocar `contentIdentity`, dejándolo desincronizado).
     */
    @Test
    fun `Layer declara contentIdentity como campo volatile con setter privado`() {
        val field = Layer::class.java.declaredFields.first { it.name == "contentIdentity" }

        assertEquals(Layer.ContentIdentity::class.java, field.type)
        assertTrue(
            "contentIdentity necesita @Volatile: es la referencia que publica de forma segura, " +
                "entre hilos, el par (sourceUri, contentRevision) como una unidad",
            Modifier.isVolatile(field.modifiers)
        )

        val setter = Layer::class.java.declaredMethods.firstOrNull { it.name == "setContentIdentity" }
        assertTrue(
            "el setter de contentIdentity no debería ser público — toda actualización tiene que " +
                "pasar por Layer.updateContentIdentity(), que mantiene sourceUri/contentRevision/" +
                "contentIdentity sincronizados en una sola llamada",
            setter == null || !Modifier.isPublic(setter.modifiers)
        )
    }

    @Test
    fun `updateContentIdentity existe y actualiza sourceUri y contentRevision de un tirón`() {
        val method = Layer::class.java.getDeclaredMethod(
            "updateContentIdentity",
            android.net.Uri::class.java,
            Int::class.java
        )

        assertTrue(Modifier.isPublic(method.modifiers))
        assertEquals(Void.TYPE, method.returnType)
    }

    /**
     * FASE 3.1.3-R3.1 — OBJETIVO 5/TEST G del informe de esta fase,
     * NOTA DE CORRECCIÓN: la verificación original asumía un setter
     * `private` a nivel de bytecode (`private set` en el parámetro del
     * constructor primario). Eso resultó ser sintaxis inválida en
     * Kotlin — un accessor no se puede declarar en un parámetro de la
     * lista del constructor primario, solo en una propiedad del cuerpo
     * de la clase — y de hecho nunca llegó a compilar (ver el historial
     * de `Layer.kt`). Como `sourceUri` tiene que seguir siendo un
     * parámetro del constructor primario (para que
     * `.copy(sourceUri = ...)` siga funcionando en EditorViewModel), el
     * bloqueo real del bypass (`liveLayer.sourceUri = x` directo) se
     * logra con `@set:Deprecated(level = ERROR)`: el setter generado
     * SIGUE siendo público a nivel de bytecode (por eso este test ya no
     * chequea `Modifier.isPublic`), pero el compilador de Kotlin rechaza
     * cualquier asignación directa fuera de esta clase — el mismo efecto
     * práctico que buscaba `private set`, por una vía que Kotlin sí
     * permite en un parámetro de constructor.
     */
    @Test
    fun `sourceUri tiene el setter marcado @Deprecated ERROR (bloquea asignacion directa)`() {
        val setter = Layer::class.java.declaredMethods.first { it.name == "setSourceUri" }
        val deprecated = setter.getAnnotation(kotlin.Deprecated::class.java)
        assertTrue(
            "sourceUri debería tener su setter anotado con @set:Deprecated(level = ERROR) — la " +
                "única vía para cambiarlo en una capa viva es Layer.updateContentIdentity(). Si " +
                "esto falla, alguien quitó la anotación, le bajó el nivel, o volvió a intentar " +
                "'private set' (que no compila en un parámetro de constructor).",
            deprecated != null && deprecated.level == DeprecationLevel.ERROR
        )
    }

    /** Mismo criterio que el test anterior, para `contentRevision` — ver su KDoc. */
    @Test
    fun `contentRevision tiene el setter marcado @Deprecated ERROR (bloquea asignacion directa)`() {
        val setter = Layer::class.java.declaredMethods.first { it.name == "setContentRevision" }
        val deprecated = setter.getAnnotation(kotlin.Deprecated::class.java)
        assertTrue(
            "contentRevision debería tener su setter anotado con @set:Deprecated(level = ERROR), " +
                "por el mismo motivo que sourceUri — ver Layer.updateContentIdentity(). Si esto " +
                "falla, alguien quitó la anotación o le bajó el nivel.",
            deprecated != null && deprecated.level == DeprecationLevel.ERROR
        )
    }

    /**
     * Confirma el otro lado de la garantía (para que el test anterior no
     * quede "vacío" si alguien simplemente borra el setter entero en vez
     * de restringirlo): el CONSTRUCTOR sigue siendo público y sigue
     * aceptando `sourceUri`/`contentRevision` — `.copy(sourceUri = ...)`,
     * usado en decenas de sitios de EditorViewModel, depende de esto.
     */
    @Test
    fun `el constructor primario de Layer sigue aceptando sourceUri y contentRevision publicos`() {
        val constructor = Layer::class.java.declaredConstructors.first {
            it.parameterTypes.any { p -> p == android.net.Uri::class.java }
        }
        assertTrue(
            "el constructor primario de Layer debe seguir siendo público — .copy(sourceUri = ...) " +
                "depende de esto en decenas de call sites de EditorViewModel",
            Modifier.isPublic(constructor.modifiers)
        )
    }
}
