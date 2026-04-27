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
            renderer.draw(textureId = texId, intensity = intensity, timeSec = 0f, width = w, height = h, flipY = true)
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

        val out = processed
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
        val pixels = IntArray(w * h)
        for (i in 0 until w * h) {
            val r = buf.get(i * 4).toInt() and 0xff
            val g = buf.get(i * 4 + 1).toInt() and 0xff
            val b = buf.get(i * 4 + 2).toInt() and 0xff
            pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
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
