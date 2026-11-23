package com.yeivikas.olyzecs.engine.effects.gpu.gl

import com.yeivikas.olyzecs.engine.effects.gpu.EffectPassKind

/**
 * Fuentes GLSL (OpenGL ES 2.0) de los pases de efectos. Un fragment shader por tipo de pase.
 *
 * Todos los fragment shaders usan `highp` cuando el dispositivo lo ofrece: con `mediump` (16 bits) las coordenadas de
 * textura de imágenes grandes pierden precisión y aparecen bandas y bordes ajustados a píxel, que es justo la pérdida de
 * calidad que esta fase quiere evitar. Si el dispositivo no tiene `highp` en fragmento, la decisión
 * (`EffectGpuAvailability`) manda el efecto al camino CPU en vez de mostrar una versión degradada.
 */
internal object EffectShaders {

    /** Un cuadrado de recorte completo: cada pase dibuja toda la textura de destino. */
    const val VERTEX = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """

    private const val FRAGMENT_PRELUDE = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
    """

    private const val IDENTITY_BODY = """
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """

    /**
     * El fragment shader de [kind], o `null` si ese pase aún no tiene shader. El `when` es EXHAUSTIVO: añadir un tipo de
     * pase sin decidir su shader no compila.
     */
    fun fragmentFor(kind: EffectPassKind): String? = when (kind) {
        EffectPassKind.IDENTITY -> FRAGMENT_PRELUDE + IDENTITY_BODY
        EffectPassKind.COLOR_BASIC -> null // fase 5.1
    }
}
