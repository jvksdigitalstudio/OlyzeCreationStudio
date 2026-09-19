package com.yeivikas.olyzecs.engine.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * Reproduce, con lógica 100% pura (sin GLES, sin `android.graphics.Bitmap`
 * real — se usa `Any()` como identidad de bitmap, todo lo que a esta
 * clase le importa es la referencia, nunca el contenido), el bug real
 * encontrado en la auditoría de esta fase: [GLRenderer] decidía si tenía
 * que resubir la textura de la cuadrícula de composición comparando SOLO
 * la identidad del bitmap, sin enterarse nunca de que el contexto EGL se
 * había recreado por detrás — dejando la cuadrícula invisible/corrupta
 * después de volver de segundo plano o reabrir un proyecto. Ver el
 * comentario de clase de [GridTextureCacheState] para el detalle
 * completo.
 */
class GridTextureCacheStateTest {

    @Test
    fun `arranca sin necesitar upload mientras la cuadricula esta apagada`() {
        val cache = GridTextureCacheState()

        assertFalse(cache.needsReconciliation(bitmapIdentity = null, currentGeneration = 0))
    }

    @Test
    fun `una cuadricula nueva (bitmap no nulo) siempre necesita subirse la primera vez`() {
        val cache = GridTextureCacheState()
        val bitmap = Any()

        assertTrue(cache.needsReconciliation(bitmapIdentity = bitmap, currentGeneration = 0))
    }

    @Test
    fun `mismo bitmap y misma generacion - no hace falta resubir (caso normal, 60fps)`() {
        val cache = GridTextureCacheState()
        val bitmap = Any()
        cache.recordUpload(id = 10, bitmapIdentity = bitmap, currentGeneration = 1)

        assertFalse(cache.needsReconciliation(bitmapIdentity = bitmap, currentGeneration = 1))
    }

    @Test
    fun `cambiar de forma o color de cuadricula (nueva identidad de bitmap) exige resubir`() {
        val cache = GridTextureCacheState()
        val bitmapViejo = Any()
        val bitmapNuevo = Any()
        cache.recordUpload(id = 10, bitmapIdentity = bitmapViejo, currentGeneration = 1)

        assertTrue(cache.needsReconciliation(bitmapIdentity = bitmapNuevo, currentGeneration = 1))
    }

    @Test
    fun `BUG REAL - mismo bitmap pero contexto EGL recreado exige resubir igual`() {
        val cache = GridTextureCacheState()
        val bitmap = Any()
        // Subida bajo el contexto viejo (generación 1).
        cache.recordUpload(id = 10, bitmapIdentity = bitmap, currentGeneration = 1)

        // Android recreó el contexto EGL — misma instancia de bitmap
        // (Compose no volvió a rasterizar la cuadrícula), pero el
        // texture id de antes pertenece a un contexto ya destruido.
        val needsReconciliation = cache.needsReconciliation(bitmapIdentity = bitmap, currentGeneration = 2)

        assertTrue(
            "Antes del fix, esto daba false y la cuadrícula quedaba invisible/corrupta tras la recreación de contexto",
            needsReconciliation
        )
    }

    @Test
    fun `apagar la cuadricula (bitmap pasa a null) exige reconciliar una vez y despues no de nuevo`() {
        val cache = GridTextureCacheState()
        val bitmap = Any()
        cache.recordUpload(id = 10, bitmapIdentity = bitmap, currentGeneration = 1)

        assertTrue(cache.needsReconciliation(bitmapIdentity = null, currentGeneration = 1))
        cache.recordCleared()
        assertFalse(cache.needsReconciliation(bitmapIdentity = null, currentGeneration = 1))
    }

    @Test
    fun `recordUpload deja el handle valido bajo la generacion registrada`() {
        val cache = GridTextureCacheState()
        cache.recordUpload(id = 99, bitmapIdentity = Any(), currentGeneration = 5)

        assertTrue(cache.handle.isValid(5))
        assertFalse(cache.handle.isValid(6))
    }

    @Test
    fun `invalidateForNewContext deja el handle invalido sin importar la generacion`() {
        val cache = GridTextureCacheState()
        cache.recordUpload(id = 99, bitmapIdentity = Any(), currentGeneration = 5)

        cache.invalidateForNewContext()

        assertFalse(cache.handle.isValid(5))
        assertFalse(cache.handle.isValid(6))
    }

    @Test
    fun `recordCleared deja el estado vacio y listo para una proxima cuadricula`() {
        val cache = GridTextureCacheState()
        val bitmapViejo = Any()
        cache.recordUpload(id = 10, bitmapIdentity = bitmapViejo, currentGeneration = 1)

        cache.recordCleared()

        assertFalse(cache.handle.isValid(1))
        // Una cuadrícula nueva después de apagarla debe volver a pedir upload.
        assertTrue(cache.needsReconciliation(bitmapIdentity = Any(), currentGeneration = 1))
    }
}
