package com.cudgk.retrovhs.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cudgk.retrovhs.settings.ShaderVariant
import com.cudgk.retrovhs.settings.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PREVIEW_DIM = 1280

@Composable
fun GalleryShaderPreview(
    uri: Uri,
    intensity: Float,
    tint: Tint,
    variant: ShaderVariant,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rendererRef = remember { mutableStateOf<GalleryPreviewRenderer?>(null) }
    val viewRef = remember { mutableStateOf<GLSurfaceView?>(null) }

    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { decodeScaled(context, uri) }
        if (bmp != null) {
            val r = rendererRef.value
            if (r != null) {
                // Recycle any previously queued (but un-uploaded) bitmap.
                r.pendingBitmap?.recycle()
                r.pendingBitmap = bmp
            } else {
                bmp.recycle()
            }
            viewRef.value?.requestRender()
        }
    }

    LaunchedEffect(intensity, tint, variant, rendererRef.value) {
        val r = rendererRef.value ?: return@LaunchedEffect
        r.intensity = intensity
        r.tintColor = tint.rgbArray
        r.tintSaturation = tint.saturation
        r.variantMul = floatArrayOf(
            variant.chromaAb, variant.scanline, variant.grain,
            variant.vignette, variant.jitter, variant.chromaBlur,
        )
        viewRef.value?.requestRender()
    }

    DisposableEffect(Unit) {
        onDispose {
            val view = viewRef.value
            val r = rendererRef.value
            if (view != null && r != null) {
                view.queueEvent { r.release() }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .pointerInput(uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 6f)
                    offset += pan
                }
            },
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            factory = { ctx ->
                val view = GLSurfaceView(ctx)
                view.setEGLContextClientVersion(3)
                val r = GalleryPreviewRenderer().apply {
                    this.intensity = intensity
                    this.tintColor = tint.rgbArray
                    this.tintSaturation = tint.saturation
                    this.variantMul = floatArrayOf(
                        variant.chromaAb, variant.scanline, variant.grain,
                        variant.vignette, variant.jitter, variant.chromaBlur,
                    )
                }
                rendererRef.value = r
                view.setRenderer(r)
                view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                viewRef.value = view
                view
            },
        )
    }
}

private fun decodeScaled(ctx: android.content.Context, uri: Uri): Bitmap? {
    return try {
        // Read bounds first
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = computeSample(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Throwable) {
        null
    }
}

private fun computeSample(w: Int, h: Int): Int {
    if (w <= 0 || h <= 0) return 1
    var s = 1
    var max = maxOf(w, h)
    while (max / s > MAX_PREVIEW_DIM) s *= 2
    return s
}
