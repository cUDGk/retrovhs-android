package com.cudgk.retrovhs.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VhsRenderer(
    private val onSurfaceTextureReady: (SurfaceTexture, Surface) -> Unit,
    private val onPhotoCaptured: (Bitmap) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var intensity: Float = 0.7f
    @Volatile private var pendingPhoto: Boolean = false

    private var program: Int = 0
    private var aPosition: Int = 0
    private var aTexCoord: Int = 0
    private var uTexture: Int = 0
    private var uTexMatrix: Int = 0
    private var uIntensity: Int = 0
    private var uTime: Int = 0
    private var uResolution: Int = 0

    private var oesTexId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val texMatrix = FloatArray(16)

    private val viewportSize = IntArray(2)
    private val startMs = SystemClock.uptimeMillis()
    private val frameAvailable = AtomicReference(false)

    private val quadVertices: FloatBuffer by lazy {
        // pos.xy, uv.xy
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

    fun requestPhoto() { pendingPhoto = true }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        if (oesTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(oesTexId), 0)
            oesTexId = 0
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GlUtils.buildProgram(VhsShader.VERTEX, VhsShader.FRAGMENT)
        aPosition = GLES30.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES30.glGetAttribLocation(program, "aTexCoord")
        uTexture = GLES30.glGetUniformLocation(program, "uTexture")
        uTexMatrix = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uIntensity = GLES30.glGetUniformLocation(program, "uIntensity")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")

        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        oesTexId = tex[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTexId)
        st.setOnFrameAvailableListener { frameAvailable.set(true) }
        surfaceTexture = st
        onSurfaceTextureReady(st, Surface(st))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportSize[0] = width
        viewportSize[1] = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        if (frameAvailable.compareAndSet(true, false)) {
            st.updateTexImage()
        }
        st.getTransformMatrix(texMatrix)

        drawQuad()

        if (pendingPhoto) {
            pendingPhoto = false
            captureFrame()
        }
    }

    private fun drawQuad() {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES30.glUniform1i(uTexture, 0)
        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES30.glUniform1f(uIntensity, intensity)
        GLES30.glUniform1f(uTime, (SystemClock.uptimeMillis() - startMs) / 1000f)
        GLES30.glUniform2f(uResolution, viewportSize[0].toFloat(), viewportSize[1].toFloat())

        quadVertices.position(0)
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
        GLES30.glEnableVertexAttribArray(aPosition)
        quadVertices.position(2)
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
        GLES30.glEnableVertexAttribArray(aTexCoord)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aTexCoord)
    }

    private fun captureFrame() {
        val w = viewportSize[0]
        val h = viewportSize[1]
        if (w <= 0 || h <= 0) return
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        buf.rewind()
        // GL origin is bottom-left; flip vertically into bitmap.
        val pixels = IntArray(w * h)
        val row = IntArray(w)
        for (y in 0 until h) {
            val srcOffset = y * w * 4
            for (x in 0 until w) {
                val i = srcOffset + x * 4
                val r = buf.get(i).toInt() and 0xff
                val g = buf.get(i + 1).toInt() and 0xff
                val b = buf.get(i + 2).toInt() and 0xff
                row[x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
            // place into flipped row
            System.arraycopy(row, 0, pixels, (h - 1 - y) * w, w)
        }
        val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        onPhotoCaptured(bmp)
    }
}
