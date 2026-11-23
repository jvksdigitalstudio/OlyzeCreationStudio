package com.yeivikas.olyzecs.engine.effects.gpu.gl

import android.opengl.GLES20
import com.yeivikas.olyzecs.debug.AppLogger

/** Compila y enlaza programas de efectos, registrando en el «Registro de errores» cualquier fallo con el motivo del controlador. */
internal object GlShaderCompiler {

    /** Devuelve el identificador del programa, o 0 si no compiló o no enlazó (ya registrado). */
    fun buildProgram(tag: String, label: String, vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(tag, label, GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertex == 0) return 0
        val fragment = compile(tag, label, GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragment == 0) {
            GLES20.glDeleteShader(vertex)
            return 0
        }
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            AppLogger.e(tag, "glCreateProgram devolvió 0 ($label)")
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            return 0
        }
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        // Una vez enlazado, los shaders sueltos ya no hacen falta.
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            AppLogger.e(tag, "No enlazó el programa de efectos ($label): ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compile(tag: String, label: String, type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            AppLogger.e(tag, "glCreateShader devolvió 0 ($label, tipo $type)")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            AppLogger.e(tag, "No compiló el shader de efectos ($label, tipo $type): ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
