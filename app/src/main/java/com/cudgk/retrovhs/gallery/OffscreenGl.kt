package com.cudgk.retrovhs.gallery

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Standalone EGL context for offscreen processing (image) or
 * window surfaces backed by MediaCodec input (video).
 */
class OffscreenGl(
    private val width: Int,
    private val height: Int,
    private val recordable: Boolean = false,
) {
    val display: EGLDisplay
    val context: EGLContext
    val config: EGLConfig
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display !== EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val ver = IntArray(2)
        check(EGL14.eglInitialize(display, ver, 0, ver, 1)) { "eglInitialize failed" }

        val attribs = mutableListOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
        )
        if (recordable) {
            attribs += EGLExt.EGL_RECORDABLE_ANDROID
            attribs += 1
        }
        attribs += EGL14.EGL_NONE
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, attribs.toIntArray(), 0, configs, 0, 1, num, 0) && num[0] > 0
        ) { "eglChooseConfig failed" }
        config = configs[0]!!

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(context !== EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val pbAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, pbAttribs, 0)
        check(pbuffer !== EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        check(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) { "eglMakeCurrent failed" }
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val win = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        check(win !== EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        return win
    }

    fun makeCurrent(surface: EGLSurface = pbuffer) {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
    }

    fun setPresentationTime(surface: EGLSurface, nsec: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nsec)
    }

    fun swapBuffers(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun destroyWindowSurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(display, surface)
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (pbuffer !== EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }
}
