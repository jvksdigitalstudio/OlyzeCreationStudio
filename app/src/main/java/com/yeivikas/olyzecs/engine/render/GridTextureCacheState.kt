package com.yeivikas.olyzecs.engine.render

/**
 * FASE 3 — Render / GL Lifecycle.
 *
 * BUG REAL encontrado en la auditoría de esta fase: [GLRenderer] decidía
 * si tenía que volver a subir la textura de la cuadrícula de composición
 * comparando SOLO la identidad del bitmap (`bitmap === lastGridBitmapIdentity`,
 * ver comentario original en `GLRenderer.updateGridTextureIfNeeded`) —
 * una optimización correcta mientras el contexto EGL no cambia (la
 * cuadrícula no se mueve mientras se arrastra una capa, no hace falta
 * resubirla en cada uno de los ~60 frames por segundo), pero rota en
 * cuanto Android recrea el contexto EGL (volver de "Mis proyectos",
 * volver de segundo plano tras perder el contexto, etc.): el bitmap de
 * Compose sigue siendo el MISMO objeto de siempre, así que la
 * comparación por identidad seguía diciendo "no hace falta resubir" —
 * pero el `gridTextureId` que se pensaba reutilizar pertenecía al
 * contexto EGL VIEJO, ya destruido. Resultado: la cuadrícula quedaba
 * invisible (o, peor, mostrando basura de memoria de GPU reciclada) tras
 * cualquier recreación de contexto, mientras que las texturas de las
 * capas normales SÍ se recuperaban bien (esas sí se invalidan
 * explícitamente en `onSurfaceCreated`, ver `Layer.glTextureId = -1`).
 *
 * Esta clase encapsula la decisión correcta como lógica pura (sin
 * ninguna llamada a GLES ni dependencia de `android.graphics.Bitmap` —
 * usa `Any?` para la identidad, así es 100% testeable en JVM normal):
 * hay que resubir si la identidad del bitmap cambió O SI la generación
 * de contexto bajo la que se subió la última vez ya no es la actual.
 */
class GridTextureCacheState {

    var handle: GpuHandle = GpuHandle.INVALID
        private set

    // Arranca en null a propósito (no con un sentinel distinto): una
    // instancia recién creada, con la cuadrícula apagada desde el
    // arranque (bitmapIdentity == null), no tiene absolutamente nada que
    // reconciliar — el handle ya está INVALID por defecto y no hay
    // ningún bitmap previo. Un sentinel != null acá haría que ESE caso
    // (cache nueva + cuadrícula apagada) devolviera erróneamente `true`
    // en needsReconciliation (bug real encontrado por el propio test
    // `arranca sin necesitar upload mientras la cuadricula esta
    // apagada` — ver GridTextureCacheStateTest — que hizo fallar el
    // build de CI real).
    private var lastBitmapIdentity: Any? = null

    /**
     * true si el estado GPU de la cuadrícula ya no coincide con lo que
     * pide [bitmapIdentity] bajo [currentGeneration] — hay que llamar a
     * [recordUpload] (si [bitmapIdentity] no es null) o a [recordCleared]
     * (si lo es). [bitmapIdentity] es la referencia del bitmap actual (o
     * null si la cuadrícula está apagada) — se compara por identidad
     * (`!==`), nunca por contenido, a propósito: Compose entrega el
     * MISMO objeto de bitmap mientras nada relevante de la cuadrícula
     * cambió (ver comentario original en GLRenderer), así que comparar
     * por identidad es lo que evita resubir en cada frame.
     *
     * Cubre TRES motivos de reconciliación, no solo uno:
     * 1. La cuadrícula se prendió/apagó, o cambió de forma/color/tamaño
     *    (identidad de bitmap distinta).
     * 2. El contexto EGL se recreó: el handle vigente quedó taggeado con
     *    una generación anterior aunque el bitmap sea el mismo objeto de
     *    siempre — este es el bug real de esta fase (ver comentario de
     *    clase).
     */
    fun needsReconciliation(bitmapIdentity: Any?, currentGeneration: Int): Boolean {
        if (bitmapIdentity !== lastBitmapIdentity) return true
        // Misma identidad de bitmap: si sigue habiendo cuadrícula
        // encendida, falta confirmar que el handle sobrevivió al
        // contexto actual. Si `bitmapIdentity` es null (apagada y ya se
        // había registrado como tal), no hay nada que reconciliar.
        return bitmapIdentity != null && !handle.isValid(currentGeneration)
    }

    /** Registra que [id] quedó subido para [bitmapIdentity] bajo [currentGeneration]. */
    fun recordUpload(id: Int, bitmapIdentity: Any?, currentGeneration: Int) {
        handle = GpuHandle(id, currentGeneration)
        lastBitmapIdentity = bitmapIdentity
    }

    /** Registra que la cuadrícula se apagó (bitmap == null): no hay textura que dibujar. */
    fun recordCleared() {
        handle = GpuHandle.INVALID
        lastBitmapIdentity = null
    }

    /**
     * Descarta el handle GPU sin intentar liberarlo (`glDeleteTexture`)
     * — se llama SOLO desde `onSurfaceCreated`, donde el id viejo ya
     * pertenece a un contexto EGL destruido; invocar una función GLES
     * sobre un id de un contexto muerto es, en el mejor caso, un no-op y
     * en el peor, comportamiento indefinido según el driver. En la
     * práctica `needsReconciliation()` ya detecta esto solo por el cambio de
     * generación (no hace falta llamar a este método para que la
     * próxima textura se resuba) — existe para dejar el estado
     * explícito en vez de depender únicamente de la comparación de
     * generación.
     */
    fun invalidateForNewContext() {
        handle = GpuHandle.INVALID
    }
}
