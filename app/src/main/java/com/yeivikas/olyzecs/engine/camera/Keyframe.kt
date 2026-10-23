package com.yeivikas.olyzecs.engine.camera

import com.yeivikas.olyzecs.engine.animation.EasingType
import kotlinx.serialization.Serializable

/**
 * Un keyframe de "cámara" para una capa individual: en el instante [timeMs],
 * la capa debe tener este encuadre. Todo lo demás (pan, zoom, rotate, dolly)
 * es resultado de interpolar entre dos de estos.
 *
 * scale: 1.0 = tamaño original de la capa dentro del canvas virtual.
 * translateX/Y: en coordenadas normalizadas del canvas (-1..1), donde 0,0 es el centro.
 * rotationDeg: rotación en grados, sentido horario (giro plano, sobre el propio eje Z).
 * alpha: opacidad 0..1, útil para fundidos entre capas.
 */
@Serializable
data class Keyframe(
    val timeMs: Long,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val alpha: Float = 1f,
    // Tilt 3D real (no es solo un giro plano): inclina la capa en el eje
    // X (vertical, como una cámara mirando hacia arriba/abajo) o en el
    // eje Y (horizontal, como un paneo lateral en perspectiva). A
    // diferencia de rotationDeg (que gira sobre el propio plano de la
    // imagen), esto usa una proyección de cámara real, así que los
    // bordes se acercan/alejan con perspectiva de verdad.
    val tiltXDeg: Float = 0f,
    val tiltYDeg: Float = 0f,
    // Desenfoque de profundidad (rack focus): 0 = nítido, 1 = muy
    // desenfocado. Animable en el tiempo, igual que en cine real cuando
    // se "jala" el foco de un sujeto a otro.
    val focusBlur: Float = 0f,
    // Dolly zoom (efecto Vértigo): mueve la cámara virtual physically
    // más cerca/lejos mientras compensa el FOV para que ESTA capa
    // mantenga su tamaño exacto — el warp real ocurre en las demás capas
    // según su profundidad (derivada de su parallaxFactor). -1..1.
    val dollyZoom: Float = 0f,
    // Ver el comentario completo en CameraFrame.kt — mismo campo, mismo
    // default 1f (= sin estirar, comportamiento idéntico a antes).
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    // Solo se usan si easing == CUSTOM_BEZIER. Valores por defecto
    // equivalentes a un ease-in-out estándar (los mismos que usa CSS).
    val bezierX1: Float = 0.42f,
    val bezierY1: Float = 0f,
    val bezierX2: Float = 0.58f,
    val bezierY2: Float = 1f
)
