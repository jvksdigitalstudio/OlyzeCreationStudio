package com.yeivikas.olyzecs.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 3.1 / 3.1.2 / 3.1.3 / 3.1.3-R1 — Cierre y hardening de ownership de
 * GPU + content revision + commit atómico + identidad de instancia.
 *
 * Igual criterio que [RendererLifecycleScenarioTest]: no se puede levantar
 * un `GLSurfaceView`/EGL real en JUnit puro sin Robolectric (no está en
 * las dependencias del proyecto), así que estos tests reproducen — con
 * una copia mínima y fiel del protocolo real de `GLRenderer` — los
 * escenarios de ownership, rechazo de resultados obsoletos, commit
 * atómico, y ahora (Fase 3.1.3-R1) identidad de INSTANCIA de capa.
 *
 * FASE 3.1.3-R1 — defecto real que esta fase cierra: la validación final
 * de Fase 3.1.3 comparaba `sourceUri`/`contentRevision` releyendo la
 * MISMA referencia de capa capturada al principio del trabajo, nunca
 * volvía a preguntar "¿esta instancia sigue siendo la capa viva del
 * proyecto?". `removeLayer()` (real) nunca muta los campos del objeto que
 * elimina — solo lo saca de la lista — así que esa comparación pasaba
 * trivialmente para una capa ya eliminada. Por eso el fake de este
 * archivo ahora modela DOS conceptos distintos y los mantiene separados,
 * igual que el código real:
 *
 * - [FakeLayerInstance]: una instancia concreta de "capa" (equivalente a
 *   un objeto `Layer`), con identidad REFERENCIAL propia — dos
 *   instancias con el mismo `sourceUri`/`contentRevision` numéricos NO
 *   son la misma capa si son objetos distintos.
 * - [FakeProjectState]: el "proyecto" — qué instancia es la VIVA para
 *   cada `layerId`, en este momento. Reemplazar la entrada de un id
 *   simula un `.copy()`/reemplazo real de instancia (p. ej.
 *   `replaceLayer` con `preserveRenderState = false`); quitarla simula
 *   `removeLayer()`; mutar los campos de la MISMA instancia sin
 *   reemplazarla simula `restoreSnapshot` (undo/redo, que muta la capa
 *   viva en el lugar — ver su propio código real).
 *
 * [FakeLayerTextureRegistry.processLayer] reproduce la validación final
 * real: NUNCA relee los campos de la instancia capturada — siempre hace
 * una búsqueda fresca en `project.liveLayers` por id, y exige identidad
 * REFERENCIAL (`===`) contra la instancia capturada, además de la
 * igualdad de valores de `sourceUri`/`contentRevision`/generación —
 * exactamente como `GLRenderer.currentLayerIfStillRequested`.
 */
class LayerTextureRegistryScenarioTest {

    /** Falso "recurso GPU": en vez de un id GLES real, un contador incremental — alcanza para verificar identidad/cantidad de subidas y borrados. */
    private class FakeGpuUploads {
        private var nextId = 1
        val deleted = mutableListOf<Int>()
        val uploaded = mutableListOf<Int>()

        fun upload(): Int {
            val id = nextId++
            uploaded += id
            return id
        }

        fun delete(id: Int) {
            deleted += id
        }
    }

    /** Espejo de `GLRenderer.LayerTextureRecord` — id GL + la contentRevision exacta que representa. */
    private data class TextureRecord(val glId: Int, val contentRevision: Int)

    /**
     * FASE 3.1.3-R1 — PROBLEMA 16 del informe: espejo de las dimensiones
     * lógicas (`Layer.widthPx`/`heightPx`) que `GLRenderer` escribe como
     * efecto secundario de un commit exitoso. Se modela por separado del
     * registro de texturas GPU porque, en el código real, es
     * exactamente eso: una escritura sobre el propio objeto `Layer`, no
     * sobre `layerTextures`. Debe quedar en `null` (nunca escrita) hasta
     * el primer commit exitoso, y NUNCA debe reflejar las dimensiones de
     * un upload que terminó en rollback.
     */
    private data class Dimensions(val width: Int, val height: Int)

    /**
     * Espejo de una instancia concreta de [com.yeivikas.olyzecs.engine.scene.Layer].
     * A propósito NO es un `data class`: lo que importa acá es la
     * identidad REFERENCIAL del objeto (`===`), no la igualdad
     * estructural de sus campos — dos [FakeLayerInstance] con los mismos
     * valores siguen siendo dos objetos distintos, tal como dos
     * instancias reales de `Layer` con el mismo `sourceUri`/
     * `contentRevision` por coincidencia (ver TEST 21).
     */
    private class FakeLayerInstance(var sourceUri: String, var contentRevision: Int) {
        // FASE 3.1.3-R1 — espejo de `Layer.widthPx`/`Layer.heightPx`.
        var dimensions: Dimensions? = null
    }

    /**
     * El "proyecto" simulado: qué instancia es la VIVA para cada
     * `layerId` en este momento — equivalente a `EditorViewModel.getLayers()`
     * visto desde `GLRenderer`.
     */
    private class FakeProjectState {
        val liveLayers = mutableMapOf<String, FakeLayerInstance>()
    }

    /**
     * Reproduce, con las piezas reales de esta fase, el protocolo
     * completo de `GLRenderer.uploadTextureIfNeeded` tras el cierre de
     * Fase 3.1.3-R1: captura de identidad (instancia + valores) →
     * consumo atómico → "upload" → validación final (búsqueda fresca +
     * identidad referencial + valores) → commit atómico O rollback con
     * `glDeleteTextures` de la textura recién creada.
     */
    private class FakeLayerTextureRegistry(private val gpu: FakeGpuUploads) {
        val layerTextures = mutableMapOf<String, TextureRecord>()
        val discardedStaleBitmaps = mutableListOf<String>()
        val rolledBackCommits = mutableListOf<String>()
        var contextGeneration = 1

        /** Equivalente a GLRenderer.onSurfaceCreated: contexto EGL nuevo, se descarta todo sin llamar delete (ids de un contexto ya muerto). */
        fun onNewEglContext() {
            layerTextures.clear()
            contextGeneration++
        }

        /** Equivalente al bloque de poda al principio de onDrawFrame. */
        fun pruneRemovedLayers(liveLayerIds: Set<String>) {
            val stale = layerTextures.keys - liveLayerIds
            for (id in stale) {
                layerTextures.remove(id)?.let { gpu.delete(it.glId) }
            }
        }

        /**
         * [capturedLayer] es la instancia que INICIÓ este trabajo — la
         * misma referencia que `GLRenderer.uploadTextureIfNeeded` recibe
         * como parámetro `layer`. [project] es la fuente de verdad de
         * qué instancia es la viva AHORA para [layerId] — se consulta
         * DOS VECES en el flujo real: para capturar la identidad al
         * principio (acá ya se recibe pre-capturada vía `capturedLayer`,
         * simulando que el llamador ya hizo esa primera lectura) y para
         * la validación final, DESPUÉS del "upload" — nunca releyendo
         * `capturedLayer` directamente.
         *
         * [duringUpload] simula al hilo principal corriendo en paralelo
         * mientras `gpu.upload()` "tarda": los tests lo usan para
         * reemplazar la entrada de [project] (nueva instancia),
         * eliminarla, o mutar la instancia existente en el lugar.
         */
        fun processLayer(
            layerId: String,
            capturedLayer: FakeLayerInstance,
            project: FakeProjectState,
            invalidation: TextureInvalidationRequest,
            pending: SingleResourceHandoff<String>,
            // FASE 3.1.3-R1 — dimensiones que este "upload" en particular
            // subiría (equivalente a `bitmap.width`/`bitmap.height` en el
            // código real) — un valor por llamada, para poder distinguir
            // en los tests "las dimensiones del intento que se rechazó"
            // de "las dimensiones legítimamente comprometidas".
            uploadDimensions: Dimensions = Dimensions(100, 100),
            duringUpload: () -> Unit = {},
            // FASE 3.1.3-R2 — hook de test que simula "el tiempo pasa"
            // ENTRE que la primera validación (justo después del upload)
            // dio positivo y que se vuelve a chequear justo antes de
            // escribir el registro — el escenario exacto de TEST 25/26
            // del informe de esta fase ("validation passed → mutation →
            // commit rejected"), que `duringUpload` (antes de la primera
            // validación) no puede representar por sí solo.
            afterFirstValidationBeforeCommit: () -> Unit = {}
        ) {
            // FASE 3.1.3 — consumir el pedido de invalidación es solo una
            // bandera; no dispara ningún borrado por sí sola (ver PROBLEMA
            // 12, cerrado en Fase 3.1.3: el borrado de una textura vieja
            // solo ocurre al comprometer una nueva, más abajo).
            invalidation.consume()

            val capturedRevision = capturedLayer.contentRevision
            val capturedSourceUri = capturedLayer.sourceUri
            val capturedGeneration = contextGeneration

            // FASE 3.1.3-R2 — "COMMIT GATE": la MISMA condición de
            // autorización, expresada como función reutilizable en vez de
            // un `Boolean` calculado una sola vez — reproduce exactamente
            // `GLRenderer.uploadTextureIfNeeded.stillAuthorizedToCommit()`.
            // Se llama DOS VECES: apenas termina el "upload" (barata,
            // permite saltar directo al rollback), y otra vez más,
            // literalmente adyacente a la escritura del registro — sin
            // ninguna sentencia intermedia — cerrando el caso "la
            // validación dio bien, pero el estado cambió justo después,
            // antes de escribir".
            fun stillAuthorizedToCommit(): Boolean {
                val liveLayer = project.liveLayers[layerId]
                return liveLayer != null &&
                    liveLayer === capturedLayer &&
                    liveLayer.sourceUri == capturedSourceUri &&
                    liveLayer.contentRevision == capturedRevision &&
                    contextGeneration == capturedGeneration
            }

            when (val consumption = pending.takeIfCurrent(capturedRevision)) {
                is SingleResourceHandoff.Consumption.Ready -> {
                    val previous = layerTextures[layerId]
                    val newId = gpu.upload() // equivalente a drawer.uploadTexture(bitmap)

                    duringUpload() // el hilo principal "corre" acá, en medio de la subida

                    if (!stillAuthorizedToCommit()) {
                        // ROLLBACK temprano — ya quedó obsoleto apenas terminó
                        // el upload, ni hace falta la segunda validación.
                        gpu.delete(newId)
                        rolledBackCommits += consumption.value
                        return
                    }

                    // FASE 3.1.3-R2 — el hilo principal "corre" ACÁ, entre
                    // la primera validación (ya positiva) y la segunda —
                    // el escenario específico que TEST 25 reproduce.
                    afterFirstValidationBeforeCommit()

                    // SEGUNDA validación — la ÚLTIMA operación antes de
                    // escribir, sin nada en el medio.
                    if (!stillAuthorizedToCommit()) {
                        gpu.delete(newId)
                        rolledBackCommits += consumption.value
                        return
                    }

                    // ATOMIC COMMIT — la metadata sale de lo CAPTURADO al
                    // principio (`capturedRevision`), nunca de una relectura
                    // posterior. La textura anterior (si había) recién se
                    // borra ACÁ, DESPUÉS de confirmar el reemplazo.
                    layerTextures[layerId] = TextureRecord(newId, capturedRevision)
                    if (previous != null && previous.glId != newId) {
                        gpu.delete(previous.glId)
                    }
                    // FASE 3.1.3-R1 — PROBLEMA 16 del informe: equivalente a
                    // `layer.widthPx = bitmap.width; layer.heightPx = bitmap.height`
                    // del código real — se escribe ACÁ, DENTRO de la rama de
                    // commit exitoso, nunca antes de confirmar autorización
                    // (ver TEST 16 más abajo, que verifica explícitamente que
                    // un rollback no contamina esto).
                    capturedLayer.dimensions = uploadDimensions
                }
                is SingleResourceHandoff.Consumption.Stale -> discardedStaleBitmaps += consumption.discarded
                SingleResourceHandoff.Consumption.Empty -> Unit
            }
        }
    }

    // ------------------------------------------------------------------
    // Escenarios heredados de Fase 3.1 / 3.1.2 (re-expresados con el
    // nuevo modelo de instancia+proyecto) — nada se elimina.
    // ------------------------------------------------------------------

    @Test
    fun `subir una textura nueva para una capa la deja en el registro`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-decodificado", revision = 1)

        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), pending)

        assertTrue(registry.layerTextures.containsKey("layer-1"))
        assertTrue(gpu.deleted.isEmpty())
    }

    /** TEST 29 del informe (Fase 3.1.3-R1): un upload legítimo — misma instancia, mismo id, misma URI, misma revisión, mismo contexto — tiene que comprometerse igual que antes; la corrección de identidad no puede bloquear el camino feliz. */
    @Test
    fun `TEST 29 - un upload legitimo sin ningun cambio de estado se compromete normalmente`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap", revision = 1)

        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), pending)

        assertTrue(registry.layerTextures.containsKey("layer-1"))
        assertEquals(1, registry.layerTextures.getValue("layer-1").contentRevision)
        assertTrue(registry.rolledBackCommits.isEmpty())
    }

    @Test
    fun `reemplazar la imagen de una capa borra la textura vieja despues de comprometer la nueva`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val firstPending = SingleResourceHandoff<String>()
        firstPending.publish("primera-imagen", revision = 1)
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), firstPending)
        val firstTextureId = registry.layerTextures.getValue("layer-1").glId

        layer.sourceUri = "uri-2"
        layer.contentRevision = 2
        val secondPending = SingleResourceHandoff<String>()
        secondPending.publish("segunda-imagen", revision = 2)
        val invalidation = TextureInvalidationRequest().also { it.request() }
        registry.processLayer("layer-1", layer, project, invalidation, secondPending)

        assertTrue(gpu.deleted.contains(firstTextureId))
        assertTrue(registry.layerTextures.getValue("layer-1").glId != firstTextureId)
        assertEquals(2, registry.layerTextures.getValue("layer-1").contentRevision)
    }

    /** Criterio de aceptación: "no existen nuevas memory leaks" — capa eliminada del ViewModel libera su textura GPU. */
    @Test
    fun `una capa eliminada del proyecto libera su textura GPU en el proximo frame`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("imagen", revision = 1)
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), pending)
        val textureId = registry.layerTextures.getValue("layer-1").glId

        registry.pruneRemovedLayers(liveLayerIds = emptySet())

        assertFalse(registry.layerTextures.containsKey("layer-1"))
        assertTrue(gpu.deleted.contains(textureId))
    }

    @Test
    fun `una capa que sigue viva no se poda aunque otras hayan sido eliminadas`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layerA = FakeLayerInstance("a", 1)
        val layerB = FakeLayerInstance("b", 1)
        project.liveLayers["layer-A"] = layerA
        project.liveLayers["layer-B"] = layerB
        registry.processLayer("layer-A", layerA, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("a", 1) })
        registry.processLayer("layer-B", layerB, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("b", 1) })

        registry.pruneRemovedLayers(liveLayerIds = setOf("layer-A"))

        assertTrue(registry.layerTextures.containsKey("layer-A"))
        assertFalse(registry.layerTextures.containsKey("layer-B"))
    }

    @Test
    fun `recrear el contexto EGL descarta todas las texturas sin intentar borrarlas`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("a", 1)
        project.liveLayers["layer-1"] = layer
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("a", 1) })
        assertTrue(registry.layerTextures.isNotEmpty())

        registry.onNewEglContext()

        assertTrue(registry.layerTextures.isEmpty())
        assertTrue(gpu.deleted.isEmpty())
    }

    @Test
    fun `un pedido de invalidacion sin un bitmap nuevo listo mantiene la textura vieja hasta poder reemplazarla`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("imagen", 1) })
        val textureId = registry.layerTextures.getValue("layer-1").glId

        // Se pide invalidar, pero todavía no hay ningún bitmap nuevo listo
        // (el decode está en camino, por ejemplo).
        val invalidation = TextureInvalidationRequest()
        invalidation.request()
        registry.processLayer("layer-1", layer, project, invalidation, SingleResourceHandoff())

        assertEquals("la textura vieja se mantiene hasta tener un reemplazo válido", textureId, registry.layerTextures.getValue("layer-1").glId)
        assertTrue(gpu.deleted.isEmpty())

        // Cuando el reemplazo llega de verdad, RECIÉN AHÍ se borra la vieja.
        layer.sourceUri = "uri-2"
        layer.contentRevision = 2
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("imagen-nueva", 2) })

        assertTrue(gpu.deleted.contains(textureId))
        assertEquals(2, registry.layerTextures.getValue("layer-1").contentRevision)
    }

    @Test
    fun `un bitmap pendiente ya consumido no se vuelve a subir en el siguiente frame`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("imagen", revision = 1)

        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), pending)
        val textureId = registry.layerTextures.getValue("layer-1").glId
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), pending)

        assertEquals(textureId, registry.layerTextures.getValue("layer-1").glId)
        assertTrue(gpu.deleted.isEmpty())
        assertNull(pending.peek())
    }

    // ------------------------------------------------------------------
    // FASE 3.1.3 — protocolo de commit atómico / rechazo TOCTOU.
    // ------------------------------------------------------------------

    /** TEST A / TEST 22 del informe: la revisión cambia DESPUÉS de capturar la identidad pero ANTES de terminar de subir — no debe comprometerse. */
    @Test
    fun `TEST A - la revision cambia durante el upload y el resultado no se compromete`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 10)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-rev10", revision = 10)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = {
                // El hilo principal reemplaza la imagen MIENTRAS "sube" la textura
                // — mutando la MISMA instancia (equivalente a `layer.sourceUri = uri`
                // seguido de `layer.contentRevision++` en el código real).
                layer.sourceUri = "uri-2"
                layer.contentRevision = 11
            }
        )

        assertFalse("nunca se compromete un resultado obsoleto", registry.layerTextures.containsKey("layer-1"))
        assertEquals(listOf("bitmap-rev10"), registry.rolledBackCommits)
    }

    /** TEST B del informe: la textura ya se subió a GPU pero quedó obsoleta antes del commit — hay que borrarla explícitamente. */
    @Test
    fun `TEST B - una textura subida que queda obsoleta antes del commit se borra de la GPU`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 10)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-rev10", revision = 10)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { layer.contentRevision = 11 }
        )

        val newTextureId = gpu.uploaded.single()
        assertTrue("la textura recién subida se tiene que borrar explícitamente", gpu.deleted.contains(newTextureId))
        assertFalse(registry.layerTextures.containsKey("layer-1"))
    }

    /** TEST C del informe: un bitmap de revisión 10 jamás puede terminar registrado con metadata de revisión 11. */
    @Test
    fun `TEST C - un bitmap de revision 10 nunca queda registrado con metadata de revision 11`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 10)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-rev10", revision = 10)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { layer.contentRevision = 11 }
        )

        assertNull("no debe haber ningún registro comprometido para esta capa todavía", registry.layerTextures["layer-1"])
    }

    /** TEST D / TEST 24 del informe: el contexto EGL cambia en medio del upload — el resultado no puede comprometerse al contexto nuevo. */
    @Test
    fun `TEST D - el contexto EGL cambia durante el upload y el resultado no se compromete`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 5)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-context-5", revision = 5)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { registry.onNewEglContext() } // recrea el contexto EGL a mitad del upload
        )

        assertFalse(registry.layerTextures.containsKey("layer-1"))
        val newTextureId = gpu.uploaded.single()
        assertTrue(gpu.deleted.contains(newTextureId))
    }

    /**
     * TEST E / TEST 20 del informe — el escenario CENTRAL de esta fase
     * (3.1.3-R1): la capa se elimina del proyecto (deja de existir en
     * [FakeProjectState.liveLayers]) mientras la textura se está
     * subiendo. `sourceUri`/`contentRevision`/`contextGeneration` de la
     * instancia capturada NO cambian (nadie los tocó, el objeto sigue
     * "intacto" en memoria) — la única forma de detectar esto es la
     * búsqueda fresca por id, que ahora devuelve `null`.
     */
    @Test
    fun `TEST E - la capa se elimina del proyecto durante el upload y el resultado no se compromete`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap", revision = 1)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { project.liveLayers.remove("layer-1") } // ViewModel.removeLayer() corrió en el medio
        )

        assertFalse(registry.layerTextures.containsKey("layer-1"))
        val newTextureId = gpu.uploaded.single()
        assertTrue("la textura de una capa eliminada se borra igual, nunca queda comprometida", gpu.deleted.contains(newTextureId))
    }

    /** TEST F / TEST 25 del informe: resultados de varias revisiones llegan fuera de orden — solo la vigente puede comprometerse. */
    @Test
    fun `TEST F - revisiones fuera de orden, solo la vigente se compromete`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-4", 4) // la capa YA está en la revisión final (4) cuando se procesan los resultados
        project.liveLayers["layer-1"] = layer

        // Los 4 resultados "llegan" en orden arbitrario (3, 1, 4, 2) — cada uno
        // se procesa contra la revisión VIGENTE (4) en su propio pending handoff.
        for ((label, revision) in listOf("C" to 3, "A" to 1, "D" to 4, "B" to 2)) {
            val pending = SingleResourceHandoff<String>()
            pending.publish("bitmap-$label", revision = revision)
            registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), pending)
        }

        assertEquals(4, registry.layerTextures.getValue("layer-1").contentRevision)
        // Orden real de llegada: C, A, D, B — D es la única Ready (coincide con
        // la revisión vigente, 4) y se compromete; las otras tres se descartan
        // como Stale, en el orden en que llegaron.
        assertEquals(listOf("bitmap-C", "bitmap-A", "bitmap-B"), registry.discardedStaleBitmaps)
        assertEquals(1, gpu.uploaded.size)
    }

    /**
     * TEST 21 del informe — EL test central de esta fase: mismo `layerId`,
     * pero la instancia que INICIÓ el upload ("oldLayer") ya NO es la
     * instancia viva del proyecto cuando el upload termina — fue
     * reemplazada por "newLayer", una instancia DISTINTA, aunque tenga
     * exactamente el mismo `sourceUri` y la misma `contentRevision`
     * (la coincidencia numérica que la validación de Fase 3.1.3, sin
     * identidad referencial, no podía detectar). El resultado de
     * "oldLayer" tiene que rechazarse igual.
     */
    @Test
    fun `TEST 21 - mismo layer id pero distinta instancia con los mismos valores se rechaza`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val oldLayer = FakeLayerInstance("uri-1", 5)
        project.liveLayers["layer-1"] = oldLayer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-de-oldLayer", revision = 5)

        registry.processLayer(
            "layer-1", oldLayer, project, TextureInvalidationRequest(), pending,
            duringUpload = {
                // Reemplazo de INSTANCIA con los mismos valores numéricos —
                // el caso que la validación por valores, sola, no detecta.
                val newLayer = FakeLayerInstance("uri-1", 5)
                project.liveLayers["layer-1"] = newLayer
            }
        )

        assertFalse(
            "un reemplazo de instancia con los mismos valores tiene que rechazarse igual (identidad referencial)",
            registry.layerTextures.containsKey("layer-1")
        )
        assertEquals(listOf("bitmap-de-oldLayer"), registry.rolledBackCommits)
    }

    /**
     * TEST 22 del informe (variante explícita, complementaria a TEST A):
     * misma instancia, la revisión capturada era 10 y la viva pasó a 11.
     */
    @Test
    fun `TEST 22 - misma instancia con revision viva distinta a la capturada se rechaza`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 10)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-rev10", revision = 10)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { layer.contentRevision = 11 }
        )

        assertFalse(registry.layerTextures.containsKey("layer-1"))
    }

    /**
     * TEST 23 del informe: misma instancia, mismo número de revisión, pero
     * `sourceUri` cambió — la identidad completa (uri + revision) tiene
     * que rechazar esto igual, no alcanza con comparar solo la revisión.
     */
    @Test
    fun `TEST 23 - misma instancia con sourceUri distinto al capturado se rechaza aunque la revision numerica coincida`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-A", 7)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-de-A", revision = 7)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { layer.sourceUri = "uri-B" } // la revisión numérica (7) queda igual a propósito
        )

        assertFalse("un cambio de sourceUri con la misma revision numerica tiene que rechazarse igual", registry.layerTextures.containsKey("layer-1"))
        assertEquals(listOf("bitmap-de-A"), registry.rolledBackCommits)
    }

    /**
     * TEST 26 del informe — undo/redo con un upload en curso.
     * `restoreSnapshot` (el código real) muta la MISMA instancia de capa
     * en el lugar, nunca la reemplaza — así que este escenario se modela
     * mutando los campos de la instancia capturada, nunca reemplazándola
     * en `project.liveLayers` (a diferencia de TEST 21, que sí reemplaza
     * la instancia — esa es justamente la distinción que hay que
     * documentar: undo/redo cambia VALORES sobre la misma identidad;
     * `removeLayer`/reemplazo real cambia la IDENTIDAD misma).
     *
     * Semántica verificada, tal como pide el informe sin asumir de más:
     * revision 10 → upload empieza → replace a revision 11 → undo vuelve
     * a revision 10 (MISMA instancia, MISMO sourceUri que el original) →
     * el upload viejo termina. Acá el diseño actual SÍ permite que ese
     * resultado viejo se considere válido de nuevo, porque
     * `sourceUri + contentRevision + identidad referencial` vuelven a
     * coincidir EXACTAMENTE con lo capturado — es indistinguible, con la
     * identidad disponible en el sistema, de "nunca cambió". Esto se
     * documenta explícitamente como una propiedad conocida del diseño,
     * no como un bug: `contentRevision` no es un contador monotónico
     * global sin retorno, es una identidad de versión que el propio
     * undo/redo restaura a propósito a su valor exacto histórico (ver
     * `LayerEditState.contentRevision`/`LayerContentState.contentRevision`
     * en `EditorViewModel.kt`).
     */
    @Test
    fun `TEST 26 - undo que restaura exactamente el sourceUri y la revision original vuelve a permitir el commit`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("A", 10)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap-A-rev10", revision = 10)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = {
                // replace -> rev11 -> undo -> vuelve a rev10, MISMO sourceUri "A",
                // MISMA instancia (restoreSnapshot muta en el lugar).
                layer.sourceUri = "B"
                layer.contentRevision = 11
                layer.sourceUri = "A"
                layer.contentRevision = 10
            }
        )

        // Al terminar el `duringUpload`, el estado vivo coincide EXACTAMENTE
        // con lo capturado (misma instancia, mismo sourceUri, misma revisión)
        // — el diseño actual, honestamente, no puede distinguir esto de
        // "nunca cambió", y por diseño (ver KDoc del test) lo acepta.
        assertTrue(registry.layerTextures.containsKey("layer-1"))
        assertEquals(10, registry.layerTextures.getValue("layer-1").contentRevision)
        assertTrue(registry.rolledBackCommits.isEmpty())
    }

    /**
     * TEST H / TEST 26 (variante que SÍ debe rechazar) — a diferencia del
     * test anterior, acá el undo deja la capa en una revisión DISTINTA a
     * la capturada (revision 3, una identidad nueva para "volver a
     * representar A" — el criterio real que documenta
     * `EditorViewModel.restoreSnapshot`: cada punto del historial tiene su
     * propia `contentRevision`, no se reutiliza un número viejo salvo que
     * sea, de verdad, exactamente el mismo punto del historial).
     */
    @Test
    fun `TEST H - undo y redo con uploads en curso mantienen la revision y el registro consistentes`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("A", 1)
        project.liveLayers["layer-1"] = layer
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("A", 1) })

        // -> B rev2, pero el undo llega DURANTE el upload de B, a una revisión
        // NUEVA (3) — no la 1 original.
        layer.sourceUri = "B"
        layer.contentRevision = 2
        val pendingB = SingleResourceHandoff<String>()
        pendingB.publish("B", revision = 2)
        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest().also { it.request() }, pendingB,
            duringUpload = {
                layer.sourceUri = "A"
                layer.contentRevision = 3
            }
        )
        assertEquals(1, registry.layerTextures.getValue("layer-1").contentRevision) // B nunca se comprometió; sigue A rev1

        // El redecode de A (ahora rev3) llega normalmente, sin carrera.
        val pendingUndo = SingleResourceHandoff<String>()
        pendingUndo.publish("A", revision = 3)
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest().also { it.request() }, pendingUndo)
        assertEquals(3, registry.layerTextures.getValue("layer-1").contentRevision)

        // redo: vuelve a representar B, revision 4.
        layer.sourceUri = "B"
        layer.contentRevision = 4
        val pendingRedo = SingleResourceHandoff<String>()
        pendingRedo.publish("B", revision = 4)
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest().also { it.request() }, pendingRedo)
        assertEquals(4, registry.layerTextures.getValue("layer-1").contentRevision)
    }

    /**
     * TEST 16 del informe (Fase 3.1.3-R1, PROBLEMA 16): un upload que
     * termina en rollback NUNCA puede dejar `widthPx`/`heightPx` (acá,
     * `FakeLayerInstance.dimensions`) contaminados con las dimensiones
     * del recurso rechazado — deben seguir reflejando el último commit
     * VÁLIDO (o `null`, si todavía no hubo ninguno).
     */
    @Test
    fun `TEST 16 - un rollback no contamina las dimensiones logicas de la capa`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer

        // Primer upload, legítimo, deja las dimensiones "reales" (200x150).
        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(),
            SingleResourceHandoff<String>().apply { publish("v1", 1) },
            uploadDimensions = Dimensions(200, 150)
        )
        assertEquals(Dimensions(200, 150), layer.dimensions)

        // Segundo upload, con dimensiones DISTINTAS (999x999), que termina
        // en rollback (la revisión cambia mientras "sube").
        layer.sourceUri = "uri-2"
        layer.contentRevision = 2
        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest().also { it.request() },
            SingleResourceHandoff<String>().apply { publish("v2", 2) },
            uploadDimensions = Dimensions(999, 999),
            duringUpload = { layer.contentRevision = 3 } // queda obsoleto antes del commit
        )

        assertEquals(
            "las dimensiones del intento rechazado (999x999) nunca deben pisar las del último commit válido",
            Dimensions(200, 150),
            layer.dimensions
        )
    }

    /**
     * TEST 25 del informe (Fase 3.1.3-R2) — EL escenario central de esta
     * fase, explícitamente distinto de TEST A/22 (que mutan el estado
     * DURANTE el "upload", ANTES de la primera validación): acá la
     * PRIMERA validación (justo después del upload) da POSITIVO — la capa
     * seguía siendo válida en ESE momento exacto — y la mutación ocurre
     * DESPUÉS, en la ventana entre esa validación ya positiva y el commit
     * real. La compuerta de commit en dos etapas
     * (`GLRenderer.uploadTextureIfNeeded`, ver su comentario "COMMIT GATE
     * EN DOS ETAPAS") tiene que rechazar esto en la SEGUNDA validación —
     * nunca confiar ciegamente en la primera.
     */
    @Test
    fun `TEST 25 - una mutacion posterior a una primera validacion ya positiva igual rechaza el commit`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap", revision = 1)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            // duringUpload vacío A PROPÓSITO: nada cambia todavía — la
            // PRIMERA validación (justo después del "upload") tiene que
            // dar POSITIVO, no rechazar acá.
            duringUpload = {},
            // La mutación ocurre DESPUÉS de esa primera validación
            // positiva — exactamente "validation passed → mutation →
            // commit attempted" del checklist de esta fase.
            afterFirstValidationBeforeCommit = { layer.contentRevision = 2 }
        )

        assertFalse(
            "una mutación posterior a una validación ya positiva tiene que rechazarse igual en la segunda validación",
            registry.layerTextures.containsKey("layer-1")
        )
        assertEquals(listOf("bitmap"), registry.rolledBackCommits)
        assertTrue("la textura recién subida se borra igual, no queda flotando", gpu.deleted.contains(gpu.uploaded.single()))
    }

    /** TEST 27 del informe: rollback con textura anterior existente — debe preservarse intacta, sin ventana sin textura. */
    @Test
    fun `TEST 27 - la textura anterior se preserva cuando el reemplazo resulta obsoleto`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 10)
        project.liveLayers["layer-1"] = layer
        registry.processLayer("layer-1", layer, project, TextureInvalidationRequest(), SingleResourceHandoff<String>().apply { publish("v10", 10) })
        val oldTextureId = registry.layerTextures.getValue("layer-1").glId

        layer.sourceUri = "uri-2"
        layer.contentRevision = 11
        val pending11 = SingleResourceHandoff<String>()
        pending11.publish("v11", revision = 11)
        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest().also { it.request() }, pending11,
            duringUpload = { layer.contentRevision = 12 } // rev11 queda obsoleta antes de comprometerse
        )

        assertEquals("la textura anterior (rev10) sigue intacta", oldTextureId, registry.layerTextures.getValue("layer-1").glId)
        assertEquals(10, registry.layerTextures.getValue("layer-1").contentRevision)
        assertFalse(gpu.deleted.contains(oldTextureId))
    }

    /** TEST 28 del informe: sin textura previa, un upload obsoleto se borra igual y el registro queda vacío. */
    @Test
    fun `TEST 28 - sin textura previa, un upload obsoleto se borra y el registro queda vacio`() {
        val gpu = FakeGpuUploads()
        val registry = FakeLayerTextureRegistry(gpu)
        val project = FakeProjectState()
        val layer = FakeLayerInstance("uri-1", 1)
        project.liveLayers["layer-1"] = layer
        val pending = SingleResourceHandoff<String>()
        pending.publish("bitmap", revision = 1)

        registry.processLayer(
            "layer-1", layer, project, TextureInvalidationRequest(), pending,
            duringUpload = { layer.contentRevision = 2 }
        )

        assertFalse(registry.layerTextures.containsKey("layer-1"))
        assertTrue(gpu.deleted.contains(gpu.uploaded.single()))
    }
}
