package com.yeivikas.olyzecs.ui.editview

/**
 * Lo que la vista de edición toma "prestado" de la capa mientras se edita, para devolverlo intacto al salir.
 *
 * ## Por qué existe
 * Al entrar al modo edición, la capa se muestra centrada y ajustada al visor (ver [EditViewFit]). Eso cambia los
 * valores locales de posición y escala que `EditorScreen` usa para dibujar, pero NO debe cambiar la capa del
 * proyecto: al salir, la imagen vuelve a su sitio y a su tamaño en el lienzo, y lo único que persiste es la
 * edición aplicada (color, 3D, efecto) que ya quedó escrita en el bitmap.
 *
 * Esta clase guarda los valores de origen y recuerda qué ajuste automático aplicó, para distinguir "el visor
 * cambió de tamaño y el usuario no tocó nada" (se reajusta) de "el usuario ya hizo zoom a mano" (se respeta).
 *
 * Estado puro y sin Android: probado en JVM.
 */
class EditViewSession(
    val originalTranslateX: Float,
    val originalTranslateY: Float,
    val originalScale: Float
) {
    /** Último ajuste automático aplicado a `scale`; null mientras no se haya aplicado ninguno. */
    var appliedFitScale: Float? = null
        private set

    /**
     * ¿Hay que (re)aplicar el ajuste automático? Sí si nunca se aplicó, o si la escala actual sigue siendo
     * exactamente la que puso el ajuste (el usuario no ha hecho zoom). La comparación exacta de Float es
     * intencional: el valor se escribe una vez y se compara tal cual, sin aritmética intermedia.
     */
    fun shouldApplyFit(currentScale: Float): Boolean {
        val applied = appliedFitScale
        return applied == null || currentScale == applied
    }

    fun markFitApplied(scale: Float) {
        appliedFitScale = scale
    }
}
