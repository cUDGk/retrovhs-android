package com.cudgk.retrovhs.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio as CxAspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.cudgk.retrovhs.settings.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    var imageCapture: ImageCapture? = null
        private set
    var chosenPreviewSize: android.util.Size? = null
        private set

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var targetFps: Int = 30
    var aspect: AspectRatio = AspectRatio.RATIO_4_3
    var onPreviewSizeChanged: ((android.util.Size) -> Unit)? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun bind(
        owner: LifecycleOwner,
        surface: Surface,
        previewSize: Size,
        onError: (Throwable) -> Unit = {},
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cp = future.get()
                provider = cp
                cp.unbindAll()

                val aspectStrategy = when (aspect) {
                    AspectRatio.RATIO_4_3 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    AspectRatio.RATIO_16_9 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    AspectRatio.RATIO_1_1 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                }
                // Strict aspect ratio filter to override CameraX's "AUTO fallback" behaviour,
                // which otherwise prefers 16:9 sizes when ResolutionStrategy is also set.
                val expectedAspectW = aspect.w
                val expectedAspectH = aspect.h
                val aspectFilter = androidx.camera.core.resolutionselector.ResolutionFilter { sizes, _ ->
                    val matched = sizes.filter { s ->
                        // Allow up to 1% tolerance.
                        val ratio = s.width.toDouble() / s.height.toDouble()
                        val target = expectedAspectW.toDouble() / expectedAspectH.toDouble()
                        kotlin.math.abs(ratio - target) / target < 0.01
                    }
                    if (matched.isNotEmpty()) matched else sizes
                }
                val previewSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectStrategy)
                    .setResolutionFilter(aspectFilter)
                    .build()
                val builder = Preview.Builder()
                    .setResolutionSelector(previewSelector)
                // Tight FPS range so AE doesn't extend exposure (which causes motion blur).
                Camera2Interop.Extender(builder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(targetFps, targetFps),
                )
                val p = builder.build()
                p.setSurfaceProvider { request ->
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { /* result */ }
                }
                preview = p

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(aspectStrategy)
                            .setResolutionFilter(aspectFilter)
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .build()
                    )
                    .build()
                imageCapture = capture
                android.util.Log.d("CameraCtrl", "ImageCapture configured with HIGHEST_AVAILABLE_STRATEGY")

                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                // ViewPort makes Preview and ImageCapture share the same FOV crop.
                val cxAspect = when (aspect) {
                    AspectRatio.RATIO_4_3 -> CxAspectRatio.RATIO_4_3
                    AspectRatio.RATIO_16_9 -> CxAspectRatio.RATIO_16_9
                    AspectRatio.RATIO_1_1 -> CxAspectRatio.RATIO_4_3 // 1:1 not supported by ViewPort; clip via shader
                }
                val displayRotation = (context as? android.app.Activity)?.windowManager?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
                val viewPort = ViewPort.Builder(android.util.Rational(aspect.w, aspect.h), displayRotation)
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build()
                val group = UseCaseGroup.Builder()
                    .addUseCase(p)
                    .addUseCase(capture)
                    .setViewPort(viewPort)
                    .build()
                val cam = cp.bindToLifecycle(owner, selector, group)
                // Log actual resolutions chosen (helps diagnose CameraX fallback behaviour).
                p.resolutionInfo?.let {
                    val s = it.resolution
                    chosenPreviewSize = s
                    onPreviewSizeChanged?.invoke(s)
                    android.util.Log.d("CameraCtrl", "Preview chosen: ${s.width}x${s.height}")
                }
                capture.resolutionInfo?.let {
                    android.util.Log.d("CameraCtrl", "ImageCapture chosen: ${it.resolution.width}x${it.resolution.height}")
                }
                @Suppress("UNUSED_VARIABLE")
                val unused = cam
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        provider?.unbindAll()
    }
}
