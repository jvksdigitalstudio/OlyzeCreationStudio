package com.yeivikas.olyzecs.engine.scene

/**
 * Orientación de un [CanvasFormat]/[CanvasSpec]. A propósito, NO es un
 * campo que se persista en ningún lado — se deriva siempre a partir de
 * `widthPx`/`heightPx` (ver [of]) para que nunca pueda contradecir las
 * dimensiones reales del canvas (ver ADR-005, punto 11 del prompt
 * maestro original: "no quiero que orientación y dimensiones puedan
 * entrar fácilmente en contradicción").
 */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
    SQUARE;

    companion object {
        fun of(widthPx: Int, heightPx: Int): Orientation = when {
            widthPx == heightPx -> SQUARE
            widthPx < heightPx -> PORTRAIT
            else -> LANDSCAPE
        }
    }
}
