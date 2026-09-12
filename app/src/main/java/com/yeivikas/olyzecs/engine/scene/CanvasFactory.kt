package com.yeivikas.olyzecs.engine.scene

/**
 * Punto único de creación de [CanvasSpec] — tanto desde un preset del
 * catálogo como desde dimensiones personalizadas (Custom). Es una
 * interfaz para que la validación de límites pueda testearse aislada
 * del resto (ver ADR-005) y para no atar la creación del Canvas a una
 * implementación concreta desde el `ViewModel`/UI que lo consuma.
 */
interface CanvasFactory {

    /** Crea el [CanvasSpec] correspondiente a un preset del catálogo. No falla: un [FormatPreset] ya construido siempre tiene dimensiones válidas (ver validación en el `init` de [CanvasFormat]). */
    fun fromPreset(preset: FormatPreset): CanvasSpec

    /**
     * Crea un [CanvasSpec] personalizado, validando que [widthPx] y
     * [heightPx] estén dentro de [CanvasLimits]. Devuelve
     * `Result.failure` con un mensaje explícito en vez de lanzar una
     * excepción sin capturar o devolver un valor sentinela — así la UI
     * (el diálogo "Introduce el tamaño en píxeles") puede mostrar el
     * error de validación directamente.
     */
    fun custom(widthPx: Int, heightPx: Int): Result<CanvasSpec>
}

/** Implementación por defecto de [CanvasFactory]: sin estado, sin dependencias de Android. */
class DefaultCanvasFactory : CanvasFactory {

    override fun fromPreset(preset: FormatPreset): CanvasSpec = CanvasSpec(
        widthPx = preset.format.widthPx,
        heightPx = preset.format.heightPx,
        originPresetId = preset.id
    )

    override fun custom(widthPx: Int, heightPx: Int): Result<CanvasSpec> {
        val range = CanvasLimits.MIN_DIMENSION_PX..CanvasLimits.MAX_DIMENSION_PX

        if (widthPx !in range) {
            return Result.failure(
                IllegalArgumentException(
                    "El ancho debe estar entre ${CanvasLimits.MIN_DIMENSION_PX} y " +
                        "${CanvasLimits.MAX_DIMENSION_PX} px (recibido: $widthPx)"
                )
            )
        }
        if (heightPx !in range) {
            return Result.failure(
                IllegalArgumentException(
                    "La altura debe estar entre ${CanvasLimits.MIN_DIMENSION_PX} y " +
                        "${CanvasLimits.MAX_DIMENSION_PX} px (recibido: $heightPx)"
                )
            )
        }

        return Result.success(CanvasSpec(widthPx = widthPx, heightPx = heightPx, originPresetId = null))
    }
}
