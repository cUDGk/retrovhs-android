package com.cudgk.retrovhs.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders date/time text into a 2D OpenGL texture using the bundled VCR OSD Mono font.
 * Supports 0/90/180/270 degree rotation; the bitmap dimensions and canvas transform
 * are updated whenever the rotation or text changes.
 */
class DateStamp(private val context: Context) {
    private val typeface: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/vcr_osd_mono.ttf")
    }.getOrNull() ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private val paint = Paint().apply {
        color = Color.rgb(255, 240, 80)
        textSize = 56f
        this.typeface = this@DateStamp.typeface
        isAntiAlias = false
        letterSpacing = -0.05f
    }
    private val shadowPaint = Paint(paint).apply { color = Color.BLACK }

    private val format = SimpleDateFormat("MMM.dd.yyyy HH:mm:ss", Locale.US)

    var textureId: Int = 0
        private set
    var widthPx: Int = 0
        private set
    var heightPx: Int = 0
        private set

    private var bitmap: Bitmap? = null
    private var lastKey: String = ""

    fun init() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    fun ensureCurrent(nowMs: Long, rotationDeg: Int = 0): Boolean {
        val text = format.format(Date(nowMs)).uppercase(Locale.US)
        val key = "$text@$rotationDeg"
        if (key == lastKey && bitmap != null) return false
        lastKey = key

        val textW = (paint.measureText(text) + 12).toInt()
        val textH = (paint.fontMetrics.bottom - paint.fontMetrics.top + 8).toInt()

        // For 90/270, swap dimensions so the rotated text fits.
        val (bmpW, bmpH) = if (rotationDeg == 90 || rotationDeg == 270) textH to textW else textW to textH

        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.save()
        when (rotationDeg) {
            90 -> {
                canvas.translate(bmpW.toFloat(), 0f)
                canvas.rotate(90f)
            }
            180 -> {
                canvas.translate(bmpW.toFloat(), bmpH.toFloat())
                canvas.rotate(180f)
            }
            270 -> {
                canvas.translate(0f, bmpH.toFloat())
                canvas.rotate(270f)
            }
        }

        val baseY = -paint.fontMetrics.top + 4f
        for (dx in -2..2 step 2) {
            for (dy in -2..2 step 2) {
                if (dx == 0 && dy == 0) continue
                canvas.drawText(text, 6f + dx, baseY + dy, shadowPaint)
            }
        }
        canvas.drawText(text, 6f, baseY, paint)
        canvas.restore()

        widthPx = bmpW
        heightPx = bmpH

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)

        bitmap?.recycle()
        bitmap = bmp
        return true
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }
}
