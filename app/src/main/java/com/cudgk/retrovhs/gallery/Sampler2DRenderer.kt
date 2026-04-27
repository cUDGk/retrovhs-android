package com.cudgk.retrovhs.gallery

import android.opengl.GLES30
import android.opengl.GLUtils
import android.graphics.Bitmap
import com.cudgk.retrovhs.camera.GlUtils
import com.cudgk.retrovhs.camera.VhsShader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders a sampler2D-backed source (bitmap or video decoder texture, when bound externally)
 * with the VHS shader. Caller manages EGL and the source texture lifecycle.
 */
class Sampler2DRenderer {
    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    private var program: Int = 0
    private var aPosition: Int = 0
    private var aTexCoord: Int = 0
    private var uTexture: Int = 0
    private var uTexMatrix: Int = 0
    private var uIntensity: Int = 0
    private var uTime: Int = 0
    private var uResolution: Int = 0

    private val quad: FloatBuffer = run {
        val data = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f,
        )
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    fun init() {
        program = GlUtils.buildProgram(VhsShader.VERTEX, VhsShader.FRAGMENT_2D)
        aPosition = GLES30.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES30.glGetAttribLocation(program, "aTexCoord")
        uTexture = GLES30.glGetUniformLocation(program, "uTexture")
        uTexMatrix = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uIntensity = GLES30.glGetUniformLocation(program, "uIntensity")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
    }

    fun uploadBitmap(bmp: Bitmap): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        return tex[0]
    }

    /**
     * Draws the bound 2D texture with VHS shader to the currently-bound framebuffer.
     * Caller sets viewport before calling.
     */
    fun draw(textureId: Int, intensity: Float, timeSec: Float, width: Int, height: Int, flipY: Boolean = false) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTexture, 0)

        val matrix = if (flipY) {
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, -1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 1f, 0f, 1f,
            )
        } else identity
        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, matrix, 0)
        GLES30.glUniform1f(uIntensity, intensity)
        GLES30.glUniform1f(uTime, timeSec)
        GLES30.glUniform2f(uResolution, width.toFloat(), height.toFloat())

        quad.position(0)
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glEnableVertexAttribArray(aPosition)
        quad.position(2)
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glEnableVertexAttribArray(aTexCoord)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aTexCoord)
    }
}
