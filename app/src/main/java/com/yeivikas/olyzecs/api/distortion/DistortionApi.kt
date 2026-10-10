package com.yeivikas.olyzecs.api.distortion

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.distortion.DistortionField

/**
 * Contrato público de EliNer para el efecto de "Distorsión" (Liquify):
 * las 9 herramientas de deformación de malla del panel dedicado (ver
 * `ui/EditorScreen.kt` → `DistortionPanel`).
 *
 * Respaldo real: `engine.distortion.DistortionRasterizer.render`.
 * [DistortionField] se reutiliza directo como parámetro — es la malla
 * ya acumulada con todos los trazos de la sesión (ver
 * `engine.distortion.DistortionField`), así que esta API no tiene que
 * reinventar un DTO propio para describir "qué se deformó".
 *
 * Mismo criterio que `Mesh3DApi` (ver ADR-004): consumidor real desde el
 * primer commit — `EditorViewModel.renderDistortion` delega acá en vez
 * de llamar `DistortionRasterizer.render` directo desde la UI.
 */
interface DistortionApi {

    /**
     * Renderiza [field] sobre [source]. [outWidth]/[outHeight] permiten
     * pedir una resolución de salida distinta a la de [source] — la
     * vista previa en vivo del panel los usa para render rápido sobre un
     * bitmap chico mientras se arrastra el dedo; el guardado final al
     * soltar pide la resolución completa. Operación pesada: suspende.
     */
    suspend fun render(
        source: Bitmap,
        field: DistortionField,
        outWidth: Int = source.width,
        outHeight: Int = source.height
    ): Bitmap
}
