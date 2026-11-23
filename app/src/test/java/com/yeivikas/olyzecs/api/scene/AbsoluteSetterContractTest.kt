package com.yeivikas.olyzecs.api.scene

import com.yeivikas.olyzecs.api.audio.AudioApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test OBLIGATORIO de la Fase 4.2-R5 — Parte R5.1/R5.13 del prompt
 * maestro R5 ("setter absoluto", "no confundir API existe con API
 * usada"): verifica, sobre el propio contrato COMPILADO de [LayerApi] y
 * [AudioApi] (no sobre una implementación), que la frontera pública
 * expone únicamente setters ABSOLUTOS — nunca un método `toggle*` de un
 * solo parámetro (`id`) sin ningún valor deseado explícito.
 *
 * Esto es exactamente la regla que R4.4 corrigió del lado de
 * `EditorViewModel` (`setLayerVisible` dejó de hacer "leer y togglear")
 * y que R5.1/R5.3 usan del lado del consumidor real en `EditorScreen.kt`
 * (`onToggleLayerVisibility` resuelve el valor absoluto ANTES de llamar a
 * `LayerApi.setVisible`, nunca llama a un `toggle*` de la API pública).
 * Si en el futuro alguien agregara, por comodidad, un
 * `toggleVisible(layerId)` a `LayerApi`, este test lo marca como una
 * regresión de contrato inmediatamente — antes de que cualquier
 * consumidor llegue a usarlo y reintroduzca la condición de carrera que
 * ya se corrigió (dos llamadas concurrentes a un toggle pueden dejar el
 * estado invertido; un setter absoluto es idempotente).
 *
 * Por reflexión sobre las interfaces — no hace falta ninguna instancia,
 * ni `Context`, ni `Robolectric`.
 */
class AbsoluteSetterContractTest {

    private fun looksLikeBooleanToggle(method: java.lang.reflect.Method): Boolean {
        // Firma sospechosa de "toggle disfrazado de setter": nombre que
        // empieza con "toggle" (sin importar mayúsculas) recibiendo un id
        // pero SIN ningún parámetro de tipo Boolean para el valor deseado.
        // Nota: los `suspend fun` de Kotlin compilan con un parámetro
        // sintético `Continuation` al final — se lo ignora expresamente
        // acá, si no cualquier `suspend fun toggleX(id: String)` parecería
        // "tener más de un parámetro" sin serlo realmente en Kotlin.
        if (!method.name.startsWith("toggle", ignoreCase = true)) return false
        val realParams = method.parameterTypes.filterNot { it.name == "kotlin.coroutines.Continuation" }
        return realParams.none { it == Boolean::class.java || it == java.lang.Boolean::class.java }
    }

    @Test
    fun `LayerApi no expone ningun metodo toggle sin valor absoluto`() {
        val toggleLike = LayerApi::class.java.declaredMethods.filter { looksLikeBooleanToggle(it) }
        assertTrue(
            "LayerApi expone método(s) tipo toggle sin valor absoluto: ${toggleLike.map { it.name }}",
            toggleLike.isEmpty()
        )
    }

    @Test
    fun `AudioApi no expone ningun metodo toggle sin valor absoluto`() {
        val toggleLike = AudioApi::class.java.declaredMethods.filter { looksLikeBooleanToggle(it) }
        assertTrue(
            "AudioApi expone método(s) tipo toggle sin valor absoluto: ${toggleLike.map { it.name }}",
            toggleLike.isEmpty()
        )
    }

    @Test
    fun `los setters booleanos de LayerApi reciben el valor deseado explicito`() {
        // setVisible/setLocked/setOrderLocked: cada uno debe recibir
        // (String, Boolean) — el id Y el valor absoluto deseado, nunca
        // solo el id. Se ignora el `Continuation` sintético que Kotlin
        // agrega a los `suspend fun` compilados.
        val setterNames = setOf("setVisible", "setLocked", "setOrderLocked")
        val methods = LayerApi::class.java.declaredMethods.filter { it.name in setterNames }
        assertTrue("no se encontraron los 3 setters esperados en LayerApi", methods.size == 3)
        methods.forEach { m ->
            val realParams = m.parameterTypes.filterNot { it.name == "kotlin.coroutines.Continuation" }
            assertTrue(
                "${m.name} debería recibir (String, Boolean) — layerId + valor absoluto",
                realParams.size == 2 &&
                    realParams[0] == String::class.java &&
                    (realParams[1] == Boolean::class.java || realParams[1] == java.lang.Boolean::class.java)
            )
        }
    }

    @Test
    fun `setMuted de AudioApi recibe el valor deseado explicito, no solo un toggle`() {
        val setMuted = AudioApi::class.java.declaredMethods.first { it.name == "setMuted" }
        val realParams = setMuted.parameterTypes.filterNot { it.name == "kotlin.coroutines.Continuation" }
        assertTrue(
            "setMuted debería recibir un Boolean explícito",
            realParams.size == 1 &&
                (realParams[0] == Boolean::class.java || realParams[0] == java.lang.Boolean::class.java)
        )
        assertFalse(
            "no debería existir un toggleMuted() de 0 parámetros en AudioApi",
            AudioApi::class.java.declaredMethods.any { it.name.equals("toggleMuted", ignoreCase = true) }
        )
    }
}
