package com.yeivikas.olyzecs.engine.effects.gpu

/**
 * Imagen de prueba determinista para el autotest de la GPU. Tiene dimensiones que NO son potencia de dos, degradados
 * en los tres canales y transparencia variable: si una etapa recorta, desplaza, voltea o altera un canal, se nota.
 */
object EffectTestPattern {
    const val WIDTH = 257
    const val HEIGHT = 131

    /** RGBA, 4 bytes por píxel, fila 0 abajo (el orden de OpenGL), de tamaño [width] x [height]. */
    fun rgba(width: Int = WIDTH, height: Int = HEIGHT): ByteArray {
        require(width > 0 && height > 0) { "Dimensiones inválidas: ${width}x$height" }
        val data = ByteArray(width * height * 4)
        var offset = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                data[offset++] = (x * 255 / maxOf(1, width - 1)).toByte()                 // rojo: degradado horizontal
                data[offset++] = (y * 255 / maxOf(1, height - 1)).toByte()                // verde: degradado vertical
                data[offset++] = (((x + y) * 7) and 0xFF).toByte()                        // azul: diagonal que da la vuelta
                data[offset++] = (64 + (((x xor y) and 0x7F) * 3) / 2).toByte()          // alfa: entre 64 y 254 (sin desbordar un byte)
            }
        }
        return data
    }
}
