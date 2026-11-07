package com.yeivikas.olyzecs.engine.render

import android.graphics.Color
import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Pinta el fondo ([PreviewBackdrop]) de la vista previa: limpia el framebuffer con el color de fondo y, si es un
 * tablero, dibuja encima un cuadrado de pantalla completa con un shader procedural.
 *
 * ## Responsabilidad única
 * Solo dibuja el fondo. No sabe de capas, cámara, cuadrícula ni exportación. Vive aparte de [LayerDrawer] a
 * propósito: `LayerDrawer` también lo usan `VideoExporter` y `ThumbnailRenderer`, que deben seguir con el fondo de
 * croma de siempre; así este cambio no puede alterarles el comportamiento.
 *
 * ## Contrato con el hilo GL
 * Todos los métodos se llaman desde el hilo de GL de `GLRenderer`, con el contexto EGL actual. Si el contexto se
 * recrea (`onSurfaceCreated`), hay que llamar [forceReinitialize]: los ids de programa del contexto anterior ya no
 * existen y usarlos corrompería el frame (mismo razonamiento que `LayerDrawer.forceReinitialize`).
 *
 * ## Tolerancia a fallos
 * Si el shader no compila o no enlaza en algún dispositivo, se registra el error UNA vez y el tablero se degrada a
 * un fondo liso del color claro. La vista previa nunca queda sin fondo ni lanza una excepción en el hilo de GL.
 */
internal class BackdropDrawer {

    private var program = 0
    private var positionHandle = -1
    private var lightHandle = -1
    private var darkHandle = -1
    private var cellHandle = -1

    /** true tras un fallo de compilación en este contexto: no se reintenta cada frame. */
    private var programUnavailable = false

    /** Cuadrado de pantalla completa en coordenadas de recorte, como tira de triángulos. */
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    /** Olvida el programa del contexto EGL anterior (que ya no existe) para recompilarlo en el nuevo. */
    fun forceReinitialize() {
        program = 0
        positionHandle = -1
        lightHandle = -1
        darkHandle = -1
        cellHandle = -1
        programUnavailable = false
    }

    /** Limpia el framebuffer con [backdrop] y, si es un tablero, lo dibuja. Reemplaza al `clear()` del frame. */
    fun draw(backdrop: PreviewBackdrop) {
        applyClearColor(backdrop.clearColorArgb())
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (backdrop is PreviewBackdrop.Checkerboard) drawCheckerboard(backdrop)
    }

    private fun applyClearColor(argb: Int) {
        GLES20.glClearColor(
            Color.red(argb) / CHANNEL_MAX,
            Color.green(argb) / CHANNEL_MAX,
            Color.blue(argb) / CHANNEL_MAX,
            1f
        )
    }

    private fun drawCheckerboard(backdrop: PreviewBackdrop.Checkerboard) {
        if (!ensureProgram()) return

        GLES20.glUseProgram(program)
        GLES20.glUniform3f(
            lightHandle,
            Color.red(backdrop.lightArgb) / CHANNEL_MAX,
            Color.green(backdrop.lightArgb) / CHANNEL_MAX,
            Color.blue(backdrop.lightArgb) / CHANNEL_MAX
        )
        GLES20.glUniform3f(
            darkHandle,
            Color.red(backdrop.darkArgb) / CHANNEL_MAX,
            Color.green(backdrop.darkArgb) / CHANNEL_MAX,
            Color.blue(backdrop.darkArgb) / CHANNEL_MAX
        )
        GLES20.glUniform1f(cellHandle, backdrop.cellPx.toFloat())

        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTEX_COUNT)
        // Mismo hábito que LayerDrawer.drawLayer: cada dibujante habilita y deshabilita SUS atributos, así no
        // dependen del estado que haya dejado el anterior.
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    /** true si hay un programa listo para usar; compila y enlaza la primera vez en cada contexto. */
    private fun ensureProgram(): Boolean {
        if (program != 0) return true
        if (programUnavailable) return false

        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SOURCE)
        val fragment = if (vertex != 0) compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE) else 0
        val linked = if (vertex != 0 && fragment != 0) link(vertex, fragment) else 0

        // Los shaders ya enlazados (o los que fallaron) no hacen falta: el programa conserva su copia.
        if (vertex != 0) GLES20.glDeleteShader(vertex)
        if (fragment != 0) GLES20.glDeleteShader(fragment)

        if (linked == 0) {
            programUnavailable = true
            return false
        }

        program = linked
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        lightHandle = GLES20.glGetUniformLocation(program, "uLight")
        darkHandle = GLES20.glGetUniformLocation(program, "uDark")
        cellHandle = GLES20.glGetUniformLocation(program, "uCellPx")

        if (positionHandle < 0 || lightHandle < 0 || darkHandle < 0 || cellHandle < 0) {
            AppLogger.e(TAG, "El shader del tablero no expone todos sus parámetros (posición/colores/casilla)")
            GLES20.glDeleteProgram(program)
            program = 0
            programUnavailable = true
            return false
        }
        return true
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            AppLogger.e(TAG, "glCreateShader devolvió 0 (tipo $type)")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            AppLogger.e(TAG, "No compiló el shader del tablero (tipo $type): ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun link(vertex: Int, fragment: Int): Int {
        val linkedProgram = GLES20.glCreateProgram()
        if (linkedProgram == 0) {
            AppLogger.e(TAG, "glCreateProgram devolvió 0")
            return 0
        }
        GLES20.glAttachShader(linkedProgram, vertex)
        GLES20.glAttachShader(linkedProgram, fragment)
        GLES20.glLinkProgram(linkedProgram)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linkedProgram, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            AppLogger.e(TAG, "No enlazó el programa del tablero: ${GLES20.glGetProgramInfoLog(linkedProgram)}")
            GLES20.glDeleteProgram(linkedProgram)
            return 0
        }
        return linkedProgram
    }

    private companion object {
        const val TAG = "BackdropDrawer"
        const val CHANNEL_MAX = 255f
        const val QUAD_VERTEX_COUNT = 4

        val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        const val VERTEX_SOURCE = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        // La paridad de la casilla sale de gl_FragCoord (píxeles de la superficie), así el tablero no depende de
        // ninguna geometría de capa ni de la cámara. highp cuando el dispositivo lo ofrece: con mediump (16 bits) las
        // coordenadas por encima de ~1024 px pierden precisión y las casillas bailarían en tablets.
        const val FRAGMENT_SOURCE = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec3 uLight;
            uniform vec3 uDark;
            uniform float uCellPx;
            void main() {
                vec2 cell = floor(gl_FragCoord.xy / uCellPx);
                float dark = mod(cell.x + cell.y, 2.0);
                gl_FragColor = vec4(mix(uLight, uDark, dark), 1.0);
            }
        """
    }
}
