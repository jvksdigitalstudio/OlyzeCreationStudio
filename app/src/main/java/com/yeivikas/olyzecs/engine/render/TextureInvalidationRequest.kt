package com.yeivikas.olyzecs.engine.render

import java.util.concurrent.atomic.AtomicBoolean

/**
 * FASE 3.1 — Cierre y hardening de ownership de GPU.
 *
 * Bandera atómica de UN SOLO PEDIDO: el hilo principal/ViewModel la usa
 * para pedir "invalidá la textura GPU vigente de esta capa", sin escribir
 * jamás un handle GPU real — reemplaza la escritura directa
 * `layer.glTextureId = -1` que hacía `EditorViewModel` hasta la Fase 3 (el
 * hallazgo real de esta auditoría: la documentación de Fase 3 afirmaba
 * "GL thread ownership" para ese campo, pero el hilo principal lo escribía
 * directamente en varios lugares).
 *
 * `request()`/`consume()` son, cada una, una sola operación atómica
 * (`AtomicBoolean.getAndSet`) — no hace falta más que eso: da igual pedir
 * la invalidación una vez o varias veces seguidas antes de que el
 * consumidor llegue a atenderla, el resultado que importa es binario ("sí,
 * hay que invalidar" / "no hace falta"), no un conteo de pedidos.
 *
 * Extraída como clase propia (en vez de vivir como un `AtomicBoolean`
 * suelto dentro de `Layer`) para poder testearla de forma aislada con
 * JUnit puro — ver [TextureInvalidationRequestTest] — sin depender de
 * `android.net.Uri`/`android.graphics.Bitmap`, que sí hacen falta para
 * construir un `Layer` real y no son testeables sin Robolectric.
 */
class TextureInvalidationRequest {

    private val requested = AtomicBoolean(false)

    /** Productor (cualquier hilo): deja pedida una invalidación. */
    fun request() {
        requested.set(true)
    }

    /**
     * Consumidor único (pensado para el hilo de GL): toma el pedido de
     * forma atómica — una vez atendido, vuelve a `false` hasta el próximo
     * [request].
     */
    fun consume(): Boolean = requested.getAndSet(false)
}
