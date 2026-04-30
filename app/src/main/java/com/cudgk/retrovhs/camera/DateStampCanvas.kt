package com.cudgk.retrovhs.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.cudgk.retrovhs.settings.StampPosition
import com.cudgk.retrovhs.settings.StampRotation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the VCR-style date stamp directly onto a Bitmap (used for ImageCapture
 * photo path where the GL pipeline isn't applied to the saved image).
 */
object DateStampCanvas {

    private val typefaceCache = mutableMapOf<Int, Typeface>()

    private fun getTypeface(context: Context): Typeface {
        return typefaceCache.getOrPut(0) {
            runCatching { Typeface.createFromAsset(context.assets, "fonts/vcr_osd_mono.ttf") }
                .getOrNull() ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    fun draw(
        target: Bitmap,
        context: Context,
        position: StampPosition,
        rotation: StampRotation,
        timeMs: Long = System.currentTimeMillis(),
    ) {
        val typeface = getTypeface(context)

        val side = minOf(target.width, target.height)
        val textSize = (side / 28f).coerceIn(20f, 96f)

        val paint = Paint().apply {
            color = Color.rgb(255, 240, 80)
            this.textSize = textSize
            this.typeface = typeface
            isAntiAlias = false
            letterSpacing = -0.05f
        }
        val shadowPaint = Paint(paint).apply { color = Color.BLACK }

        val format = SimpleDateFormat("MMM.dd.yyyy HH:mm:ss", Locale.US)
        val text = format.format(Date(timeMs)).uppercase(Locale.US)

        val padding = textSize * 0.4f
        val textW = paint.measureText(text)
        val textH = paint.fontMetrics.bottom - paint.fontMetrics.top
        val stampW = textW + padding * 2
        val stampH = textH + padding * 2

        val rotated = rotation.degrees == 90 || rotation.degrees == 270
        val effectiveW = if (rotated) stampH else stampW
        val effectiveH = if (rotated) stampW else stampH

        val marginX = target.width * 0.04f
        val marginY = target.height * 0.04f
        val baseX = if (position.isLeft) marginX else target.width - effectiveW - marginX
        val baseY = if (position.isTop) marginY else target.height - effectiveH - marginY

        val centerX = baseX + effectiveW / 2f
        val centerY = baseY + effectiveH / 2f

        val canvas = Canvas(target)
        canvas.save()
        if (rotation.degrees != 0) {
            canvas.rotate(rotation.degrees.toFloat(), centerX, centerY)
        }

        // Draw text such that the un-rotated stamp box is centered at (centerX, centerY).
        val textTopLeftX = centerX - stampW / 2f
        val textTopLeftY = centerY - stampH / 2f
        val drawX = textTopLeftX + padding
        val drawY = textTopLeftY + padding - paint.fontMetrics.top

        for (dx in -2..2 step 2) {
            for (dy in -2..2 step 2) {
                if (dx == 0 && dy == 0) continue
                canvas.drawText(text, drawX + dx, drawY + dy, shadowPaint)
            }
        }
        canvas.drawText(text, drawX, drawY, paint)
        canvas.restore()
    }
}
