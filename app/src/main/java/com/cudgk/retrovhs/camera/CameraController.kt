package com.cudgk.retrovhs.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var targetFps: Int = 30

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

                val builder = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    previewSize,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                )
                            )
                            .build()
                    )
                Camera2Interop.Extender(builder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(targetFps, targetFps),
                )
                val p = builder.build()
                p.setSurfaceProvider { request ->
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { /* result */ }
                }
                preview = p

                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                cp.bindToLifecycle(owner, selector, p)
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        provider?.unbindAll()
    }
}
