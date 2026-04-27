package com.cudgk.retrovhs.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
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

    // Recording state
    private val pendingStartRecording = AtomicReference<Recording?>(null)
    @Volatile private var pendingStopRecording: Boolean = false
    private var recording: Recording? = null
    private var encoderSurface: EGLSurface? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null

    private val quadVertices: FloatBuffer by lazy {
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
    fun startRecording(rec: Recording) { pendingStartRecording.set(rec) }
    fun stopRecording() { pendingStopRecording = true }
    val isRecording: Boolean get() = recording != null

    fun release() {
        // Note: encoder cleanup happens on GL thread via stopRecording. Best-effort here.
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

    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
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

        // Draw to display surface
        GLES30.glViewport(0, 0, viewportSize[0], viewportSize[1])
        drawQuad(viewportSize[0], viewportSize[1])

        // Cache EGL state
        if (eglDisplay == null) {
            eglDisplay = EGL14.eglGetCurrentDisplay()
            eglContext = EGL14.eglGetCurrentContext()
        }

        // Handle start of recording (set up encoder EGL surface)
        pendingStartRecording.getAndSet(null)?.let { rec ->
            try {
                val attribs = intArrayOf(EGL14.EGL_NONE)
                val cfg = chooseRecordableConfig()
                encoderSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, cfg, rec.inputSurface, attribs, 0,
                )
                recording = rec
            } catch (t: Throwable) {
                runCatching { rec.release() }
            }
        }

        // Render to encoder surface if active
        recording?.let { rec ->
            val displaySurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            val es = encoderSurface
            if (es != null) {
                EGL14.eglMakeCurrent(eglDisplay, es, es, eglContext)
                GLES30.glViewport(0, 0, rec.video.width, rec.video.height)
                drawQuad(rec.video.width, rec.video.height)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, es, st.timestamp)
                EGL14.eglSwapBuffers(eglDisplay, es)
                EGL14.eglMakeCurrent(eglDisplay, displaySurface, displaySurface, eglContext)
                GLES30.glViewport(0, 0, viewportSize[0], viewportSize[1])
            }
            rec.drainVideo(false)
        }

        // Handle stop of recording
        if (pendingStopRecording) {
            pendingStopRecording = false
            val rec = recording
            val es = encoderSurface
            if (rec != null) {
                if (es != null) {
                    EGL14.eglMakeCurrent(eglDisplay, es, es, eglContext)
                    rec.drainVideo(true)
                    EGL14.eglDestroySurface(eglDisplay, es)
                }
                rec.release()
            }
            recording = null
            encoderSurface = null
        }

        if (pendingPhoto) {
            pendingPhoto = false
            captureFrame()
        }
    }

    private fun chooseRecordableConfig(): EGLConfig {
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0)) {
            "eglChooseConfig (recordable) failed"
        }
        check(num[0] > 0) { "no recordable EGLConfig" }
        return configs[0]!!
    }

    private fun drawQuad(w: Int, h: Int) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES30.glUniform1i(uTexture, 0)
        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES30.glUniform1f(uIntensity, intensity)
        GLES30.glUniform1f(uTime, (SystemClock.uptimeMillis() - startMs) / 1000f)
        GLES30.glUniform2f(uResolution, w.toFloat(), h.toFloat())

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
            System.arraycopy(row, 0, pixels, (h - 1 - y) * w, w)
        }
        val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        onPhotoCaptured(bmp)
    }
}
