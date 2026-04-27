package com.cudgk.retrovhs.camera

import android.opengl.GLES20
import android.opengl.GLES30

object GlUtils {

    fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        check(shader != 0) { "glCreateShader failed" }
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Shader compile error: $log")
        }
        return shader
    }

    fun linkProgram(vs: Int, fs: Int): Int {
        val prog = GLES30.glCreateProgram()
        check(prog != 0) { "glCreateProgram failed" }
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog)
            GLES30.glDeleteProgram(prog)
            error("Program link error: $log")
        }
        return prog
    }

    fun buildProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSrc)
        val prog = linkProgram(vs, fs)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    fun checkGlError(tag: String) {
        var err = GLES20.glGetError()
        while (err != GLES20.GL_NO_ERROR) {
            error("$tag: glError 0x${err.toString(16)}")
        }
    }
}
