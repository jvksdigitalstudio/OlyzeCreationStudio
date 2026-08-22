package com.yeivikas.olyzecs.engine.render

import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger

private const val TAG = "ShaderProgram"

/**
 * Shader de vértices: aplica la matriz MVP (proyección de perspectiva real
 * + vista + modelo) que codifica pan/zoom/rotate/tilt3D/dolly de la
 * cámara virtual para esta capa.
 */
private const val VERTEX_SHADER = """
    uniform mat4 uMVPMatrix;
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTexCoord = aTexCoord;
    }
"""

/**
 * Shader de fragmentos: pipeline completo de "cámara + lente + film" en
 * un solo pase. Orden de operaciones (igual que un pipeline de color real):
 * 1. Distorsión de lente (barril/cojín) sobre las coordenadas UV
 * 2. Desenfoque de profundidad (rack focus, anamórfico opcional)
 * 3. Motion blur direccional (según la velocidad de movimiento de cámara)
 * 4. Aberración cromática
 * 5. Grading de color (exposición, saturación, contraste, temperatura, tinte, sombras/luces, split-tone)
 * 6. Glow + lens flare anamórfico
 * 7. Viñeta
 * 8. Grano de película
 */
private const val FRAGMENT_SHADER = """
    precision mediump float;
    uniform sampler2D uTexture;
    uniform float uAlpha;
    uniform float uSaturation;
    uniform float uContrast;
    uniform float uWarmth;
    uniform float uExposure;
    uniform float uTint;
    uniform float uShadowsLift;
    uniform float uHighlightsRolloff;
    uniform float uSplitToneIntensity;
    uniform float uVignetteIntensity;
    uniform float uGrainIntensity;
    uniform float uGlowIntensity;
    uniform float uGlowThreshold;
    uniform float uTimeSeconds;
    uniform float uFocusBlur;
    uniform float uAnamorphicBokeh;
    uniform float uLensDistortion;
    uniform float uChromaticAberration;
    uniform float uLensFlareIntensity;
    uniform vec2 uMotionVector;
    uniform vec2 uTexelSize;
    varying vec2 vTexCoord;

    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }

    // --- Distorsión de lente: barril (gran angular) o cojín, según el signo ---
    vec2 applyLensDistortion(vec2 uv, float amount) {
        vec2 centered = uv - vec2(0.5);
        float r2 = dot(centered, centered);
        vec2 warped = centered * (1.0 + amount * r2);
        return warped + vec2(0.5);
    }

    // --- Desenfoque de profundidad: 9 muestras, estirable horizontalmente (bokeh anamórfico) ---
    vec4 sampleFocus(vec2 uv, float amount, float anamorphic) {
        float radiusX = amount * 3.5 * (1.0 + anamorphic * 1.5);
        float radiusY = amount * 3.5;
        vec4 sum = vec4(0.0);
        sum += texture2D(uTexture, uv + uTexelSize * vec2(-1.0, -1.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv + uTexelSize * vec2( 0.0, -1.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv + uTexelSize * vec2( 1.0, -1.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv + uTexelSize * vec2(-1.0,  0.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv);
        sum += texture2D(uTexture, uv + uTexelSize * vec2( 1.0,  0.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv + uTexelSize * vec2(-1.0,  1.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv + uTexelSize * vec2( 0.0,  1.0) * vec2(radiusX, radiusY));
        sum += texture2D(uTexture, uv + uTexelSize * vec2( 1.0,  1.0) * vec2(radiusX, radiusY));
        return sum / 9.0;
    }

    // --- Motion blur direccional: 7 muestras a lo largo del vector de movimiento ---
    vec4 sampleMotionBlur(vec2 uv, vec2 motionVec) {
        vec4 sum = vec4(0.0);
        const int TAPS = 7;
        for (int i = 0; i < TAPS; i++) {
            float t = (float(i) / float(TAPS - 1)) - 0.5;
            sum += texture2D(uTexture, uv + motionVec * t);
        }
        return sum / float(TAPS);
    }

    void main() {
        vec2 uv = uLensDistortion != 0.0 ? applyLensDistortion(vTexCoord, uLensDistortion * 0.35) : vTexCoord;

        vec4 texColor;
        bool hasFocus = uFocusBlur > 0.003;
        bool hasMotion = length(uMotionVector) > 0.0008;
        if (hasFocus && hasMotion) {
            vec4 focusSample = sampleFocus(uv, uFocusBlur, uAnamorphicBokeh);
            vec4 motionSample = sampleMotionBlur(uv, uMotionVector);
            texColor = mix(focusSample, motionSample, 0.5);
        } else if (hasFocus) {
            texColor = sampleFocus(uv, uFocusBlur, uAnamorphicBokeh);
        } else if (hasMotion) {
            texColor = sampleMotionBlur(uv, uMotionVector);
        } else {
            texColor = texture2D(uTexture, uv);
        }

        // --- Aberración cromática: cada canal de color muestreado con un offset distinto ---
        vec3 color;
        if (uChromaticAberration > 0.001) {
            vec2 dir = normalize(uv - vec2(0.5) + vec2(0.0001));
            float amt = uChromaticAberration * 0.012;
            float rChannel = texture2D(uTexture, uv + dir * amt).r;
            float gChannel = texColor.g;
            float bChannel = texture2D(uTexture, uv - dir * amt).b;
            color = vec3(rChannel, gChannel, bChannel);
        } else {
            color = texColor.rgb;
        }

        // --- Exposición (compensación multiplicativa, como en cámara real) ---
        color *= pow(2.0, uExposure);

        // --- Saturación ---
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(vec3(luminance), color, uSaturation);

        // --- Contraste ---
        color = (color - 0.5) * uContrast + 0.5;

        // --- Balance de blancos / temperatura (azul-naranja) ---
        color.r += uWarmth * 0.12;
        color.b -= uWarmth * 0.12;

        // --- Tinte (verde-magenta), complementa la temperatura ---
        color.g += uTint * 0.1;
        color.r -= uTint * 0.05;
        color.b -= uTint * 0.05;

        // --- Sombras: levantar el negro (look "película desteñida") ---
        float lum2 = dot(color, vec3(0.299, 0.587, 0.114));
        color += uShadowsLift * (1.0 - smoothstep(0.0, 0.5, lum2));

        // --- Luces altas: comprimir/suavizar para evitar quemados ---
        float highlightMask = smoothstep(0.6, 1.0, lum2);
        color = mix(color, vec3(lum2), uHighlightsRolloff * highlightMask);

        // --- Split-tone cinematográfico: sombras frías (teal), luces cálidas (naranja) ---
        vec3 shadowTone = vec3(0.0, 0.9, 1.0);
        vec3 highlightTone = vec3(1.0, 0.65, 0.2);
        float shadowMask = smoothstep(0.55, 0.0, lum2);
        float highMask = smoothstep(0.45, 1.0, lum2);
        color = mix(color, color * shadowTone, shadowMask * uSplitToneIntensity * 0.5);
        color = mix(color, color * highlightTone, highMask * uSplitToneIntensity * 0.5);

        // --- Glow en zonas brillantes (aproximación de bloom en un solo pase) ---
        float brightness = dot(color, vec3(0.299, 0.587, 0.114));
        float glowAmount = max(brightness - uGlowThreshold, 0.0) * uGlowIntensity;
        color += vec3(glowAmount);

        // --- Lens flare anamórfico: destello horizontal en zonas muy brillantes ---
        if (uLensFlareIntensity > 0.001) {
            float flareSample = 0.0;
            const int FLARE_TAPS = 5;
            for (int i = 0; i < FLARE_TAPS; i++) {
                float offset = (float(i) - 2.0) * 0.03;
                vec3 s = texture2D(uTexture, uv + vec2(offset, 0.0)).rgb;
                float b = dot(s, vec3(0.299, 0.587, 0.114));
                flareSample += max(b - uGlowThreshold, 0.0);
            }
            flareSample = flareSample / float(FLARE_TAPS) * uLensFlareIntensity * 2.0;
            color += vec3(0.5, 0.75, 1.0) * flareSample;
        }

        // --- Viñeta ---
        vec2 centered = uv - vec2(0.5);
        float dist = length(centered);
        float vignette = smoothstep(0.85, 0.35, dist * (1.0 + uVignetteIntensity));
        color *= mix(1.0, vignette, uVignetteIntensity);

        // --- Grano de película ---
        float noise = rand(uv * uTimeSeconds * 60.0) - 0.5;
        color += noise * uGrainIntensity * 0.12;

        color = clamp(color, 0.0, 1.0);
        // `color` ya sale premultiplicado por texColor.a (el sample de
        // textura arranca en texColor.rgb, que es como llega la textura
        // desde uploadTexture — ver comentario largo en LayerDrawer al
        // fijar GL_ONE/GL_ONE_MINUS_SRC_ALPHA). Para que el resultado
        // completo quede premultiplicado por el alfa FINAL de esta capa
        // (texColor.a * uAlpha, que incluye el fundido de
        // entrada/salida por keyframe — ver Keyframe.alpha/CameraFrame.
        // alpha), falta multiplicar tambien el color por uAlpha acá. Sin
        // este multiplicador, una capa a mitad de un fade-out (uAlpha
        // bajando de 1 a 0) mantenía el color a full brillo mientras
        // solo el alfa bajaba — con el blend premultiplicado eso se veía
        // cada vez MÁS quemada/brillante en vez de desvanecerse, en vez
        // de apagarse suave hasta transparente como corresponde.
        gl_FragColor = vec4(color * uAlpha, texColor.a * uAlpha);
    }
"""

class ShaderProgram {
    var programId: Int = 0
        private set

    fun build() {
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        programId = GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                AppLogger.e(TAG, "Error al linkear el programa: ${GLES20.glGetProgramInfoLog(program)}")
                GLES20.glDeleteProgram(program)
            }
        }
    }

    private fun compile(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                AppLogger.e(TAG, "Error al compilar shader: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
            }
        }
    }
}
