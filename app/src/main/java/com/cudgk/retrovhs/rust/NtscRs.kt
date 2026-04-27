package com.cudgk.retrovhs.rust

import android.graphics.Bitmap
import java.nio.ByteBuffer

object NtscRs {
    @Volatile private var loaded: Boolean = false
    @Volatile private var loadError: Throwable? = null

    val isAvailable: Boolean
        get() {
            ensureLoaded()
            return loaded
        }

    val errorMessage: String?
        get() = loadError?.message

    private fun ensureLoaded() {
        if (loaded || loadError != null) return
        synchronized(this) {
            if (loaded || loadError != null) return
            try {
                System.loadLibrary("ntscrs_jni")
                loaded = true
            } catch (t: Throwable) {
                loadError = t
            }
        }
    }

    @JvmStatic
    external fun processRgba(width: Int, height: Int, rgba: ByteArray, frameNum: Int, intensity: Float)

    /** Apply the ntsc-rs effect to an ARGB_8888 bitmap in place. */
    fun process(bitmap: Bitmap, frameNum: Int = 0, intensity: Float = 1.0f) {
        ensureLoaded()
        check(loaded) { "ntscrs_jni native library failed to load: ${loadError?.message}" }
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888" }
        val w = bitmap.width
        val h = bitmap.height
        val bytes = ByteArray(w * h * 4)
        val bb = ByteBuffer.wrap(bytes)
        bitmap.copyPixelsToBuffer(bb)
        processRgba(w, h, bytes, frameNum, intensity.coerceIn(0f, 1f))
        bb.rewind()
        bitmap.copyPixelsFromBuffer(bb)
    }
}
