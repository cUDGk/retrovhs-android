package com.cudgk.retrovhs.gallery

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView renderer that displays a single 2D bitmap with the VHS shader applied.
 * Used for live gallery preview so the intensity slider reflects in real time.
 */
class GalleryPreviewRenderer : GLSurfaceView.Renderer {
    @Volatile var intensity: Float = 0.7f
    @Volatile var tintColor: FloatArray = floatArrayOf(1f, 1f, 1f)
    @Volatile var tintSaturation: Float = 1f
    @Volatile var variantMul: FloatArray = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f)
    @Volatile var pendingBitmap: Bitmap? = null

    private val sampler = Sampler2DRenderer()
    private var texId: Int = 0
    private val viewport = IntArray(2)

    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        sampler.init()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        viewport[0] = w
        viewport[1] = h
        GLES30.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val newBmp = pendingBitmap
        if (newBmp != null) {
            pendingBitmap = null
            uploadBitmap(newBmp)
        }
        if (texId == 0) return
        sampler.draw(
            textureId = texId,
            intensity = intensity,
            timeSec = 0f,
            width = viewport[0],
            height = viewport[1],
            flipY = false,
            tintColor = tintColor,
            tintSaturation = tintSaturation,
            variantMul = variantMul,
        )
    }

    private fun uploadBitmap(bmp: Bitmap) {
        if (texId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            texId = ids[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle() // we copied to GPU, no further need for the CPU-side bitmap
    }

    fun release() {
        if (texId != 0) GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
        texId = 0
        sampler.release()
    }
}
