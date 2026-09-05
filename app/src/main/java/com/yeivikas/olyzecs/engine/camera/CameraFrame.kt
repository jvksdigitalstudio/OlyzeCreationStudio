package com.yeivikas.olyzecs.engine.camera

import kotlinx.serialization.Serializable

/**
 * El resultado interpolado de un track en un instante dado; lo que el
 * renderer necesita para construir la matriz de transformación de la capa.
 */
@Serializable
data class CameraFrame(
    val translateX: Float,
    val translateY: Float,
    val scale: Float,
    val rotationDeg: Float,
    val alpha: Float,
    val tiltXDeg: Float = 0f,
    val tiltYDeg: Float = 0f,
    val focusBlur: Float = 0f,
    val dollyZoom: Float = 0f,
    // --- Estirado independiente de ancho/alto, agregado para las manijas
    // "estirar ancho" (lateral derecha) y "estirar alto" (inferior
    // central) del modo "Edición > Imagen" (ver EdicionMenu en
    // EditorScreen.kt). Completamente separado de [scale] (que sigue
    // siendo la escala UNIFORME de siempre — pellizco de dos dedos y la
    // manija de la esquina inferior derecha): el ancho final en pantalla
    // es fitScaleX * scale * scaleX, el alto es fitScaleY * scale *
    // scaleY (ver LayerDrawer.kt). En 1f (default) el comportamiento es
    // IDÉNTICO a antes de que existieran estos dos campos — por eso son
    // seguros de agregar al final con default sin romper ningún proyecto
    // guardado antes de esta versión ni ningún llamado posicional
    // existente a este constructor.
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)
