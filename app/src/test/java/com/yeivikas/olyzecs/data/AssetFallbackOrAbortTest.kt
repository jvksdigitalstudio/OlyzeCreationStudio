package com.yeivikas.olyzecs.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Test OBLIGATORIO de la Fase 4.2 — punto 4.1/5/25.A del prompt maestro:
 * verifica [resolveAssetOrAbort], la regla de decisión real que ahora usa
 * [ProjectStorage.saveProject] para capas y audio, en vez del `mapNotNull`
 * que hacía desaparecer una capa en silencio cuando su copia de imagen
 * fallaba.
 */
class AssetFallbackOrAbortTest {

    @Test
    fun `si la copia nueva tuvo exito, se usa esa sin mirar el fallback`() {
        val result = resolveAssetOrAbort(fresh = "nuevo.png", previousValid = "viejo.png") {
            "no debería llamarse este mensaje"
        }
        assertEquals("nuevo.png", result)
    }

    @Test
    fun `si la copia nueva fallo pero hay una copia previa valida, se reutiliza esa`() {
        // Este es exactamente el escenario del punto 4.1: `ensureLocalImage`
        // devuelve null (falló el refresco), pero la capa YA tenía una
        // copia local válida del guardado anterior — la capa NO debe
        // desaparecer de este guardado.
        val result = resolveAssetOrAbort(fresh = null, previousValid = "viejo.png") {
            "no debería llamarse este mensaje"
        }
        assertEquals("viejo.png", result)
    }

    @Test
    fun `si la copia nueva fallo y no hay ninguna copia previa valida, se aborta con la excepcion de integridad`() {
        // Capa nueva que nunca se pudo copiar con éxito, sin ningún
        // guardado anterior al que volver: el guardado entero debe
        // abortar en vez de persistir un proyecto con una capa menos.
        val ex = assertThrows(ProjectAssetIntegrityException::class.java) {
            resolveAssetOrAbort<String>(fresh = null, previousValid = null) {
                "se aborta el guardado para no perder la capa"
            }
        }
        assertEquals("se aborta el guardado para no perder la capa", ex.message)
    }
}
