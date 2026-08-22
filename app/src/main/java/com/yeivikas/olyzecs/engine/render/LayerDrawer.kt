package com.yeivikas.olyzecs.engine.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sin
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.scene.Layer

/**
 * Encapsula el shader, el quad y la lógica de transformación/dibujo de una
 * capa individual. Es usado tanto por [GLRenderer] (preview en vivo dentro
 * de un GLSurfaceView) como por el exportador de video offline, para que
 * el resultado final exportado sea pixel-idéntico al preview.
 *
 * Importante: cada instancia de LayerDrawer vive dentro de UN contexto EGL.
 * No se comparten texturas entre el contexto del preview y el del export;
 * por eso el export re-sube sus propias texturas a partir de los bitmaps
 * originales (ver VideoExporter).
 *
 * ## Cámara de perspectiva real, con profundidad y dolly zoom
 * Cada capa vive en un plano Z distinto, derivado de su [Layer.parallaxFactor]
 * (fondo = parallax bajo = más atrás en Z; sujeto = parallax 1.0 = z=0,
 * el "plano de foco" de la cámara). La proyección está calibrada
 * matemáticamente para que una capa en z=0 con dollyZoom=0 se vea
 * IDÉNTICA al render 2D original — cero regresión. Cuando `dollyZoom`
 * se anima, la cámara virtual se acerca/aleja de verdad y el FOV se
 * autocompensa para que el plano z=0 no cambie de tamaño — el warp real
 * ocurre en las capas de fondo, exactamente como el efecto Vértigo real.
 */
class LayerDrawer {

    private var shaderProgram: ShaderProgram? = null

    // Distancia base de la cámara virtual al plano de foco (z=0). Números
    // más chicos = perspectiva más exagerada por grado de tilt (lente
    // gran angular); más grandes = más sutil (teleobjetivo).
    private val baseEyeZ = 2.5f
    // Cuánto se mueve físicamente la cámara con dollyZoom=±1.
    private val dollyRange = 1.6f

    private val quadVertices = floatArrayOf(
        -0.5f,  0.5f, 0f,
        -0.5f, -0.5f, 0f,
         0.5f,  0.5f, 0f,
         0.5f, -0.5f, 0f
    )
    private val quadTexCoords = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(quadVertices); position(0) }

    private val texCoordBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadTexCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(quadTexCoords); position(0) }

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    /** Debe llamarse una vez que el contexto EGL actual esté activo (current). */
    fun ensureInitialized() {
        if (shaderProgram == null) {
            // Verde chroma-key (0,177,64 / #00B140) como fondo por defecto
            // del lienzo: cualquier zona del preview sin una capa encima
            // queda lista para chroma key, en vez de negro. El usuario
            // puede taparlo con su propio fondo vía "Importar fondo".
            GLES20.glClearColor(0f, 177f / 255f, 64f / 255f, 1f)
            GLES20.glEnable(GLES20.GL_BLEND)
            // GL_ONE (no GL_SRC_ALPHA) para el factor de origen: TODO
            // bitmap que sube [uploadTexture] vía GLUtils.texImage2D —ya
            // sea decodificado con BitmapFactory desde una foto/PNG, ya
            // sea dibujado a mano con android.graphics.Canvas (caso de la
            // cuadrícula de composición, ver rasterizeGridBitmap)— llega
            // SIEMPRE con el color premultiplicado por su propio alpha:
            // es como Android guarda un ARGB_8888 en cualquiera de esos
            // dos caminos, no hay forma de pedirle lo contrario (un
            // Canvas ni siquiera te deja dibujar en un bitmap marcado
            // como no-premultiplicado, tira RuntimeException).
            // GL_SRC_ALPHA multiplica ESE color por el alpha una SEGUNDA
            // vez al mezclar — con capas opacas (alpha=1 en todos lados)
            // no se nota, multiplicar por 1 no cambia nada, por eso pasó
            // desapercibido en fotos/logos normales. Pero con cualquier
            // color semitransparente de verdad (las líneas de guía de la
            // cuadrícula, alpha 0.4) la doble atenuación oscurece y lava
            // el color elegido casi hasta el punto de no distinguirse
            // del fondo, sin importar qué matiz o forma se use — el bug
            // reportado. GL_ONE toma el color YA premultiplicado tal
            // cual, sin volver a escalarlo, que es la fórmula correcta
            // de compositing "over" para texturas premultiplicadas.
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            shaderProgram = ShaderProgram().apply { build() }
        }
    }

    /** Sube un bitmap como textura GL en el contexto actual. Devuelve el textureId. */
    fun uploadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // BUG REAL corregido: quien llama a uploadTexture() recicla el
        // `bitmap` inmediatamente después de que esta función retorna (ver
        // uploadTextureIfNeeded/updateGridTextureIfNeeded en GLRenderer —
        // "se libera apenas se sube a GL"). El problema es que
        // GLUtils.texImage2D no garantiza en todos los drivers que la copia
        // de píxeles a la GPU ya haya terminado cuando la llamada retorna
        // en el hilo de CPU — algunos drivers (Adreno/Mali en ciertas
        // versiones) la dejan pendiente/asíncrona por rendimiento. Si el
        // bitmap se recicla (su memoria se libera/reutiliza) antes de que
        // esa copia termine, la GPU puede terminar leyendo memoria ya
        // sobrescrita: la textura sale con bandas y bloques de color
        // mezclados, como si la imagen se hubiera "corrompido" — el bug
        // reportado. glFinish() bloquea hasta que TODOS los comandos GL
        // encolados (incluida esta subida de textura) terminaron de
        // ejecutarse de verdad en la GPU, así que cuando esta función
        // retorna es seguro reciclar el bitmap del lado de CPU.
        GLES20.glFinish()

        return textureId
    }

    fun deleteTexture(textureId: Int) {
        if (textureId >= 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }

    fun clear() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    /**
     * Dibuja una capa ya con textura subida, aplicando el encuadre de
     * cámara [frame] ya interpolado (tilt 3D, dolly zoom, desenfoque de
     * profundidad), respetando el aspect ratio original de la imagen
     * dentro del viewport dado.
     *
     * @param previousFrame encuadre de esta misma capa un instante antes
     * (p.ej. 33ms atrás). Si se provee, se usa para calcular un vector de
     * movimiento y aplicar motion blur direccional automático — cuanto
     * más rápido se mueve la cámara entre un frame y otro, más borroneo.
     */
    fun drawLayer(
        textureId: Int,
        imageWidthPx: Int,
        imageHeightPx: Int,
        frame: CameraFrame,
        parallaxFactor: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        look: LookSettings = LookSettings(),
        timeSeconds: Float = 0f,
        previousFrame: CameraFrame? = null
    ) {
        val program = shaderProgram ?: return
        if (textureId < 0 || viewportHeight == 0 || imageHeightPx == 0) return
        ensureInitialized()

        val imageAspect = imageWidthPx.toFloat() / imageHeightPx.toFloat()
        val viewportAspect = viewportWidth.toFloat() / viewportHeight.toFloat()

        // --- Cámara dinámica: dolly zoom real ---
        // fovy elegido para que tan(fovy/2) = 1/eyeZ: así, en el plano
        // z=0 (donde vive el "sujeto", parallax=1.0), el resultado es
        // SIEMPRE idéntico sin importar cuánto se mueva la cámara con
        // dollyZoom — el warp real ocurre en las capas de fondo (z<0).
        val eyeZ = baseEyeZ + frame.dollyZoom * dollyRange
        val fovyDeg = Math.toDegrees(2.0 * kotlin.math.atan(1.0 / eyeZ.toDouble())).toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, fovyDeg, 1f, 0.1f, eyeZ + 20f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Profundidad real de la capa: fondo (parallax bajo) se coloca
        // más atrás en Z; el sujeto (parallax=1.0) queda en el plano de
        // foco z=0. Esto es lo que permite que el dolly zoom y el tilt 3D
        // generen paralaje de verdad entre capas, no solo una simulación
        // por desplazamiento.
        val depthZ = (1f - parallaxFactor.coerceIn(0f, 1f)) * -1.8f

        // --- Vibración de cámara (handheld) ---
        var shakeX = 0f
        var shakeY = 0f
        var shakeRot = 0f
        if (look.cameraShakeIntensity > 0.001f) {
            val amp = look.cameraShakeIntensity
            shakeX = (sin(timeSeconds * 5.3) * 0.6 + sin(timeSeconds * 9.1) * 0.4).toFloat() * amp * 0.03f
            shakeY = (sin(timeSeconds * 6.7 + 1.3) * 0.6 + sin(timeSeconds * 11.4) * 0.4).toFloat() * amp * 0.03f
            shakeRot = (sin(timeSeconds * 4.1 + 2.1)).toFloat() * amp * 1.2f
        }

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix, 0,
            frame.translateX * parallaxFactor + shakeX,
            frame.translateY * parallaxFactor + shakeY,
            depthZ
        )
        Matrix.rotateM(modelMatrix, 0, frame.rotationDeg + shakeRot, 0f, 0f, 1f)
        Matrix.rotateM(modelMatrix, 0, frame.tiltXDeg, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, frame.tiltYDeg, 0f, 1f, 0f)

        val fitScaleX: Float
        val fitScaleY: Float
        if (imageAspect > viewportAspect) {
            fitScaleX = 2f
            fitScaleY = 2f * viewportAspect / imageAspect
        } else {
            fitScaleY = 2f
            fitScaleX = 2f * imageAspect / viewportAspect
        }
        Matrix.scaleM(modelMatrix, 0, fitScaleX * frame.scale * frame.scaleX, fitScaleY * frame.scale * frame.scaleY, 1f)

        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0)

        // --- Motion blur direccional: vector de movimiento en espacio UV ---
        var motionX = 0f
        var motionY = 0f
        if (previousFrame != null && look.motionBlurIntensity > 0.001f) {
            val dx = (frame.translateX - previousFrame.translateX) * parallaxFactor
            val dy = (frame.translateY - previousFrame.translateY) * parallaxFactor
            motionX = dx * 0.5f * look.motionBlurIntensity * 3f
            motionY = -dy * 0.5f * look.motionBlurIntensity * 3f
        }

        GLES20.glUseProgram(program.programId)

        val positionHandle = GLES20.glGetAttribLocation(program.programId, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program.programId, "aTexCoord")
        val mvpHandle = GLES20.glGetUniformLocation(program.programId, "uMVPMatrix")
        val alphaHandle = GLES20.glGetUniformLocation(program.programId, "uAlpha")
        val textureHandle = GLES20.glGetUniformLocation(program.programId, "uTexture")
        val saturationHandle = GLES20.glGetUniformLocation(program.programId, "uSaturation")
        val contrastHandle = GLES20.glGetUniformLocation(program.programId, "uContrast")
        val warmthHandle = GLES20.glGetUniformLocation(program.programId, "uWarmth")
        val exposureHandle = GLES20.glGetUniformLocation(program.programId, "uExposure")
        val tintHandle = GLES20.glGetUniformLocation(program.programId, "uTint")
        val shadowsLiftHandle = GLES20.glGetUniformLocation(program.programId, "uShadowsLift")
        val highlightsRolloffHandle = GLES20.glGetUniformLocation(program.programId, "uHighlightsRolloff")
        val splitToneHandle = GLES20.glGetUniformLocation(program.programId, "uSplitToneIntensity")
        val vignetteHandle = GLES20.glGetUniformLocation(program.programId, "uVignetteIntensity")
        val grainHandle = GLES20.glGetUniformLocation(program.programId, "uGrainIntensity")
        val glowIntensityHandle = GLES20.glGetUniformLocation(program.programId, "uGlowIntensity")
        val glowThresholdHandle = GLES20.glGetUniformLocation(program.programId, "uGlowThreshold")
        val timeHandle = GLES20.glGetUniformLocation(program.programId, "uTimeSeconds")
        val focusBlurHandle = GLES20.glGetUniformLocation(program.programId, "uFocusBlur")
        val anamorphicHandle = GLES20.glGetUniformLocation(program.programId, "uAnamorphicBokeh")
        val lensDistortionHandle = GLES20.glGetUniformLocation(program.programId, "uLensDistortion")
        val chromaticAberrationHandle = GLES20.glGetUniformLocation(program.programId, "uChromaticAberration")
        val lensFlareHandle = GLES20.glGetUniformLocation(program.programId, "uLensFlareIntensity")
        val motionVectorHandle = GLES20.glGetUniformLocation(program.programId, "uMotionVector")
        val texelSizeHandle = GLES20.glGetUniformLocation(program.programId, "uTexelSize")

        GLES20.glEnableVertexAttribArray(positionHandle)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(alphaHandle, frame.alpha)
        GLES20.glUniform1f(saturationHandle, look.saturation)
        GLES20.glUniform1f(contrastHandle, look.contrast)
        GLES20.glUniform1f(warmthHandle, look.warmth)
        GLES20.glUniform1f(exposureHandle, look.exposure)
        GLES20.glUniform1f(tintHandle, look.tint)
        GLES20.glUniform1f(shadowsLiftHandle, look.shadowsLift)
        GLES20.glUniform1f(highlightsRolloffHandle, look.highlightsRolloff)
        GLES20.glUniform1f(splitToneHandle, look.splitToneIntensity)
        GLES20.glUniform1f(vignetteHandle, look.vignetteIntensity)
        GLES20.glUniform1f(grainHandle, look.grainIntensity)
        GLES20.glUniform1f(glowIntensityHandle, look.glowIntensity)
        GLES20.glUniform1f(glowThresholdHandle, look.glowThreshold)
        GLES20.glUniform1f(timeHandle, timeSeconds)
        GLES20.glUniform1f(focusBlurHandle, frame.focusBlur)
        GLES20.glUniform1f(anamorphicHandle, look.anamorphicBokeh)
        GLES20.glUniform1f(lensDistortionHandle, look.lensDistortion)
        GLES20.glUniform1f(chromaticAberrationHandle, look.chromaticAberration)
        GLES20.glUniform1f(lensFlareHandle, look.lensFlareIntensity)
        GLES20.glUniform2f(motionVectorHandle, motionX, motionY)
        GLES20.glUniform2f(texelSizeHandle, 1f / imageWidthPx.toFloat(), 1f / imageHeightPx.toFloat())

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}
