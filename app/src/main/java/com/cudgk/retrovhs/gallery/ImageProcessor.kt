package com.cudgk.retrovhs.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES30
import android.os.Build
import android.provider.MediaStore
import com.cudgk.retrovhs.camera.GlUtils
import com.cudgk.retrovhs.rust.NtscRs
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageProcessor {

    /** Loads, processes, and saves the image. Returns true on success. */
    fun process(
        context: Context,
        source: Uri,
        intensity: Float,
        displayName: String,
        useRust: Boolean = false,
    ): Boolean {
        if (useRust) return processWithRust(context, source, intensity, displayName)
        return processWithShader(context, source, intensity, displayName)
    }

    /**
     * Apply the VHS shader to an in-memory bitmap (used by the camera photo path
     * so the saved image matches the live preview look exactly).
     */
    fun processBitmapShader(
        source: Bitmap,
        intensity: Float,
        tintColor: FloatArray,
        tintSaturation: Float,
        variantMul: FloatArray,
    ): Bitmap? {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null
        val gl = OffscreenGl(width = w, height = h, recordable = false)
        val renderer = Sampler2DRenderer()
        var output: Bitmap? = null
        try {
            renderer.init()
            val texId = renderer.uploadBitmap(source)
            GLES30.glViewport(0, 0, w, h)
            // flipY=false: GL framebuffer ends up upside-down vs source, but glReadPixels
            // (reads from bottom-left first) + Bitmap.copyPixelsFromBuffer (writes to top-left
            // first) inverts again so the saved bitmap matches source orientation.
            renderer.draw(
                textureId = texId,
                intensity = intensity,
                timeSec = 0f,
                width = w,
                height = h,
                flipY = false,
                tintColor = tintColor,
                tintSaturation = tintSaturation,
                variantMul = variantMul,
            )
            val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4).order(java.nio.ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(buf)
            }
            GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
        } catch (t: Throwable) {
            output?.recycle()
            output = null
        } finally {
            renderer.release()
            gl.release()
        }
        return output
    }

    private fun processWithRust(context: Context, source: Uri, intensity: Float, displayName: String): Boolean {
        if (!NtscRs.isAvailable) return false
        val src = decodeBitmap(context, source, mutable = true) ?: return false
        return try {
            NtscRs.process(src, frameNum = 0, intensity = intensity)
            saveToMediaStore(context, src, displayName)
        } catch (t: Throwable) {
            false
        } finally {
            src.recycle()
        }
    }

    private fun processWithShader(context: Context, source: Uri, intensity: Float, displayName: String): Boolean {
        val srcBitmap = decodeBitmap(context, source) ?: return false
        val w = srcBitmap.width
        val h = srcBitmap.height

        val gl = OffscreenGl(width = w, height = h, recordable = false)
        val renderer = Sampler2DRenderer()
        var processed: Bitmap?
        try {
            renderer.init()
            val texId = renderer.uploadBitmap(srcBitmap)
            // bitmap data is uploaded; can recycle source
            srcBitmap.recycle()

            // Create FBO for capture (pbuffer is already current; we can render to default and read)
            GLES30.glViewport(0, 0, w, h)
            // flipY=false so glReadPixels + copyPixelsFromBuffer round-trip preserves orientation.
            renderer.draw(textureId = texId, intensity = intensity, timeSec = 0f, width = w, height = h, flipY = false)
            GlUtils.checkGlError("draw")

            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            buf.rewind()

            processed = bufferToBitmap(buf, w, h)
            GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
        } finally {
            renderer.release()
            gl.release()
        }

        val out = processed ?: return false
        return saveToMediaStore(context, out, displayName).also { out.recycle() }
    }

    private fun decodeBitmap(context: Context, uri: Uri, mutable: Boolean = false): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = mutable
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun bufferToBitmap(buf: ByteBuffer, w: Int, h: Int): Bitmap {
        // glReadPixels(GL_RGBA, GL_UNSIGNED_BYTE) gives bytes RGBA which matches
        // Bitmap.Config.ARGB_8888 in-memory layout, so copyPixelsFromBuffer is direct.
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.rewind()
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RetroVHS")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (t: Throwable) {
            false
        }
    }
}
