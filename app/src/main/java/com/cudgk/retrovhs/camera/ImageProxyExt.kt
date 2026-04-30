package com.cudgk.retrovhs.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Decodes a JPEG-format ImageProxy from CameraX ImageCapture into a mutable
 * ARGB_8888 Bitmap with the rotation degrees applied.
 */
fun ImageProxy.toBitmapRgba(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rot = imageInfo.rotationDegrees
    val source = if (raw.config != Bitmap.Config.ARGB_8888) {
        raw.copy(Bitmap.Config.ARGB_8888, true).also { raw.recycle() }
    } else if (!raw.isMutable) {
        raw.copy(Bitmap.Config.ARGB_8888, true).also { raw.recycle() }
    } else raw

    return if (rot == 0) source
    else {
        val matrix = Matrix().apply { postRotate(rot.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        if (!rotated.isMutable) {
            rotated.copy(Bitmap.Config.ARGB_8888, true).also { rotated.recycle() }
        } else rotated
    }
}
