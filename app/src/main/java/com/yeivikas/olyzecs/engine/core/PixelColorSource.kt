package com.yeivikas.olyzecs.engine.core

/**
 * Contrato mínimo para pedir el color exacto de un pixel del preview en
 * vivo (usado por la herramienta de cuentagotas). Es el primer contrato
 * que vive en `engine.core`: antes de esto, `EditorScreen` guardaba una
 * referencia directa a la clase concreta [com.yeivikas.olyzecs.engine.render.GLRenderer]
 * — la UI conocía la implementación entera del renderer solo para llamar
 * un único método.
 *
 * Con esta interfaz, la UI solo conoce "algo que puede leerme un pixel",
 * y [com.yeivikas.olyzecs.engine.render.GLRenderer] es apenas una de las
 * implementaciones posibles. Es también el tipo de contrato que, más
 * adelante, expondría `EliNer API` en vez del motor concreto.
 */
interface PixelColorSource {
    /**
     * Pide leer el color EXACTO del pixel en ([xPx], [yPx]) — coordenadas
     * de vista (origen arriba-izquierda, igual que un tap de Compose). El
     * resultado llega por [callback] en el hilo principal.
     */
    fun requestPixelColor(xPx: Int, yPx: Int, callback: (argbColor: Int) -> Unit)
}
