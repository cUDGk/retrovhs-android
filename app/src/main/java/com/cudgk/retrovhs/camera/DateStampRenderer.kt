package com.cudgk.retrovhs.camera

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Tiny shader that draws a textured quad with alpha blending — used to overlay
 * the date/time stamp on top of the VHS-processed frame.
 */
class DateStampRenderer {
    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTex = 0
    private var uRect = 0

    private val quad: FloatBuffer = run {
        // Pos in [0,1] (multiplied by uRect.zw + offset uRect.xy in vertex shader),
        // UV [0,1] across the texture.
        val data = floatArrayOf(
            0f, 0f, 0f, 1f,
            1f, 0f, 1f, 1f,
            0f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    fun init() {
        program = GlUtils.buildProgram(VERTEX, FRAGMENT)
        aPosition = GLES30.glGetAttribLocation(program, "aPos")
        aTexCoord = GLES30.glGetAttribLocation(program, "aUv")
        uTex = GLES30.glGetUniformLocation(program, "uTex")
        uRect = GLES30.glGetUniformLocation(program, "uRect")
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
    }

    /** Draws the texture inside an NDC rectangle (origin lower-left, span size). */
    fun draw(textureId: Int, ndcX: Float, ndcY: Float, ndcW: Float, ndcH: Float) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform4f(uRect, ndcX, ndcY, ndcW, ndcH)

        quad.position(0)
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glEnableVertexAttribArray(aPosition)
        quad.position(2)
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glEnableVertexAttribArray(aTexCoord)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aTexCoord)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    companion object {
        private const val VERTEX = """#version 300 es
in vec2 aPos;
in vec2 aUv;
uniform vec4 uRect; // ndcX, ndcY, ndcW, ndcH (origin lower-left)
out vec2 vUv;
void main() {
    vec2 ndc = uRect.xy + aPos * uRect.zw;
    gl_Position = vec4(ndc, 0.0, 1.0);
    vUv = aUv;
}
"""
        private const val FRAGMENT = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
in vec2 vUv;
out vec4 fragColor;
void main() {
    fragColor = texture(uTex, vUv);
}
"""
    }
}
