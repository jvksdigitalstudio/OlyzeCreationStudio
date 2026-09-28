package com.yeivikas.olyzecs.engine.scene

/**
 * Relación de aspecto representada como un par de enteros normalizado
 * por su máximo común divisor (MCD) — no como `String` (`"16:9"`, que
 * obliga a comparar/parsear texto para una operación que es aritmética)
 * ni como un enum fijo (que no escala: cada relación nueva requeriría
 * tocar código, ver ADR-005).
 *
 * `ratio` (ancho/alto como `Float`) es la representación que de verdad
 * importa para el cálculo (comparar aspectos, decidir orientación,
 * escalar dimensiones) — `widthUnits`/`heightUnits` son solo la forma
 * "legible" (ej. 16:9) que se muestra en UI.
 *
 * Ver ADR-005, decisión D1 (opción C).
 */
data class AspectRatio(val widthUnits: Int, val heightUnits: Int) {

    init {
        require(widthUnits > 0) { "widthUnits debe ser positivo (recibido: $widthUnits)" }
        require(heightUnits > 0) { "heightUnits debe ser positivo (recibido: $heightUnits)" }
    }

    /** Ancho/alto como número real — la única representación que se debe usar para comparar o calcular, nunca `widthUnits`/`heightUnits` directamente. */
    val ratio: Float
        get() = widthUnits.toFloat() / heightUnits.toFloat()

    /** Representación legible tipo "16:9", solo para UI/logs — nunca para comparar (usar [ratio]). */
    override fun toString(): String = "$widthUnits:$heightUnits"

    companion object {
        /**
         * Deriva la relación de aspecto normalizada a partir de
         * dimensiones reales en píxeles. Ej.: `of(1920, 1080)` ->
         * `AspectRatio(16, 9)`. Funciona también para relaciones que no
         * simplifican a un par "bonito" (ej. cine 2.39:1): el par queda
         * más grande, pero [ratio] sigue siendo el valor correcto.
         */
        fun of(widthPx: Int, heightPx: Int): AspectRatio {
            require(widthPx > 0) { "widthPx debe ser positivo (recibido: $widthPx)" }
            require(heightPx > 0) { "heightPx debe ser positivo (recibido: $heightPx)" }
            val divisor = gcd(widthPx, heightPx)
            return AspectRatio(widthPx / divisor, heightPx / divisor)
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
