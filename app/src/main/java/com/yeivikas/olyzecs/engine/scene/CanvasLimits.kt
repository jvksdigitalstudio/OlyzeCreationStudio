package com.yeivikas.olyzecs.engine.scene

/**
 * Límites de dimensiones para un Canvas personalizado. Mismo rango que
 * ya se le mostró al usuario como referencia (diálogo "Introduce el
 * tamaño en píxeles", Mín. 100 / Máx. 4096) — no un límite inventado
 * en esta fase.
 *
 * Esto es responsabilidad del dominio del Canvas (ver ADR-005, §8 de
 * la propuesta técnica: "qué límites deberían ser responsabilidad del
 * modelo de proyecto vs. del renderer vs. del dispositivo"). El clamp
 * real de textura GPU (`GLRenderer`) y el redondeo a múltiplo de 2 del
 * encoder (`computeExportDimensions`) son límites de OTRAS capas y no
 * se mueven acá — este es solo el rango que tiene sentido a nivel de
 * "espacio de composición del proyecto", independiente del hardware.
 */
object CanvasLimits {
    const val MIN_DIMENSION_PX = 100
    const val MAX_DIMENSION_PX = 4096
}
