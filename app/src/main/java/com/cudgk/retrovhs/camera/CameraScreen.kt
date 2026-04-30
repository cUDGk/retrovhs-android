package com.cudgk.retrovhs.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.cudgk.retrovhs.gallery.ImageProcessor
import com.cudgk.retrovhs.i18n.s
import com.cudgk.retrovhs.settings.AspectRatio
import com.cudgk.retrovhs.settings.Engine
import com.cudgk.retrovhs.settings.PresetRow
import com.cudgk.retrovhs.settings.Quality
import com.cudgk.retrovhs.settings.Settings
import com.cudgk.retrovhs.settings.ShaderVariant
import com.cudgk.retrovhs.settings.StampPosition
import com.cudgk.retrovhs.settings.StampRotation
import com.cudgk.retrovhs.settings.Tint
import com.cudgk.retrovhs.theme.ControlRow
import com.cudgk.retrovhs.theme.InkBlack
import com.cudgk.retrovhs.theme.InkBorder
import com.cudgk.retrovhs.theme.InkPanel
import com.cudgk.retrovhs.theme.InkText
import com.cudgk.retrovhs.theme.InkTextDim
import com.cudgk.retrovhs.theme.NeonChip
import com.cudgk.retrovhs.theme.NeonCyan
import com.cudgk.retrovhs.theme.NeonMagenta
import com.cudgk.retrovhs.theme.NeonRed
import com.cudgk.retrovhs.theme.NeonYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FPS_OPTIONS = listOf(15, 24, 30, 60)

private enum class CaptureMode { PHOTO, VIDEO }

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermission = result[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("カメラ権限が必要です", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            }) { Text("権限をリクエスト") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("戻る") }
        }
        return
    }

    CameraContent(onBack = onBack)
}

@Composable
private fun CameraContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var intensity by remember { mutableFloatStateOf(Settings.DEFAULT_INTENSITY) }
    var fps by remember { mutableIntStateOf(Settings.DEFAULT_FPS) }
    var lens by remember { mutableIntStateOf(Settings.DEFAULT_LENS) }
    var preset by remember { mutableStateOf<String?>(null) }
    var tint by remember { mutableStateOf(Settings.DEFAULT_TINT) }
    var quality by remember { mutableStateOf(Settings.DEFAULT_QUALITY) }
    var aspect by remember { mutableStateOf(Settings.DEFAULT_ASPECT) }
    var dateStamp by remember { mutableStateOf(false) }
    var stampPosition by remember { mutableStateOf(Settings.DEFAULT_STAMP_POSITION) }
    var stampRotation by remember { mutableStateOf(Settings.DEFAULT_STAMP_ROTATION) }
    var variant by remember { mutableStateOf(Settings.DEFAULT_VARIANT) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var settingsExpanded by remember { mutableStateOf(false) }
    val cameraErrorLabel = s("cam_camera_error")
    val captureFailedLabel = s("cam_capture_failed")
    val saveFailedLabel = s("cam_save_failed")
    val shaderFailedLabel = s("cam_shader_failed")
    val savedLabel = s("cam_saved_with")
    val videoSavedLabel = s("cam_video_saved")
    val recordStartFailedLabel = s("cam_record_start_failed")
    val captureBtnLabel = s("cam_capture_btn")
    val videoStartLabel = s("cam_video_start")
    val videoStopLabel = s("cam_video_stop")

    BackHandler(enabled = true) {
        if (settingsExpanded) settingsExpanded = false else onBack()
    }

    LaunchedEffect(Unit) {
        intensity = Settings.intensity(context).first()
        fps = Settings.fps(context).first()
        lens = Settings.lens(context).first()
        preset = Settings.preset(context).first()
        tint = Settings.tint(context).first()
        quality = Settings.quality(context).first()
        dateStamp = Settings.dateStamp(context).first()
        stampPosition = Settings.stampPosition(context).first()
        stampRotation = Settings.stampRotation(context).first()
        variant = Settings.variant(context).first()
        aspect = Settings.aspect(context).first()
    }

    val controller = remember { CameraController(context) }
    var renderer by remember { mutableStateOf<VhsRenderer?>(null) }
    var cameraSurface by remember { mutableStateOf<Surface?>(null) }

    // Track the actual preview buffer size and pass it to the shader so chroma blur
    // texel stepping uses the correct source resolution.
    DisposableEffect(controller, renderer) {
        controller.onPreviewSizeChanged = { size ->
            // Buffer size must match the sensor resolution that CameraX chose,
            // otherwise frames are stretched into a wrong-sized buffer.
            renderer?.setSourceBufferSize(size.width, size.height)
        }
        onDispose { controller.onPreviewSizeChanged = null }
    }

    LaunchedEffect(intensity, renderer) {
        renderer?.intensity = intensity
    }
    LaunchedEffect(tint, renderer) {
        renderer?.tintColor = tint.rgbArray
        renderer?.tintSaturation = tint.saturation
    }
    LaunchedEffect(dateStamp, renderer) {
        renderer?.dateStampEnabled = dateStamp
    }
    LaunchedEffect(stampPosition, renderer) {
        renderer?.stampIsLeft = stampPosition.isLeft
        renderer?.stampIsTop = stampPosition.isTop
    }
    LaunchedEffect(stampRotation, renderer) {
        renderer?.stampRotation = stampRotation.degrees
    }
    LaunchedEffect(variant, renderer) {
        renderer?.variantMul = floatArrayOf(
            variant.chromaAb, variant.scanline, variant.grain,
            variant.vignette, variant.jitter, variant.chromaBlur,
        )
    }

    LaunchedEffect(lens, fps, quality, aspect, cameraSurface) {
        val s = cameraSurface ?: return@LaunchedEffect
        controller.lensFacing = lens
        controller.targetFps = fps
        controller.aspect = aspect
        // Resolve preview size that respects aspect (avoid CameraX fallback to small 4:3 buffer
        // when 16:9 size is requested with 4:3 aspect strategy).
        val targetH = quality.height // shorter sensor side
        val targetW = (targetH.toFloat() * aspect.w / aspect.h).toInt() and 0xFFFFFFFE.toInt()
        controller.bind(
            owner = owner,
            surface = s,
            previewSize = Size(targetW, targetH),
            onError = { t ->
                Toast.makeText(context, "${cameraErrorLabel}: ${t.message}", Toast.LENGTH_LONG).show()
            },
        )
    }

    LaunchedEffect(recording) {
        if (recording) {
            recordSeconds = 0
            while (recording) {
                delay(1000)
                recordSeconds += 1
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recording) renderer?.stopRecording()
            controller.unbind()
            renderer?.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.portraitRatio),
            factory = { ctx ->
                val view = GLSurfaceView(ctx)
                view.setEGLContextClientVersion(3)
                view.setEGLConfigChooser(RecordableConfigChooser())
                val r = VhsRenderer(
                    context = ctx,
                    onSurfaceTextureReady = { _, surface ->
                        view.post { cameraSurface = surface }
                    },
                    onPhotoCaptured = { bmp ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                PhotoSaver.save(ctx, bmp, "RetroVHS_${System.currentTimeMillis()}")
                            }
                            Toast.makeText(
                                ctx,
                                if (ok) "保存しました" else "保存失敗",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                r.intensity = intensity
                r.tintColor = tint.rgbArray
                r.tintSaturation = tint.saturation
                r.dateStampEnabled = dateStamp
                r.stampIsLeft = stampPosition.isLeft
                r.stampIsTop = stampPosition.isTop
                r.stampRotation = stampRotation.degrees
                r.variantMul = floatArrayOf(
                    variant.chromaAb, variant.scanline, variant.grain,
                    variant.vignette, variant.jitter, variant.chromaBlur,
                )
                renderer = r
                view.setRenderer(r)
                view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                view
            },
        )

        if (recording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red),
                )
                Spacer(Modifier.width(8.dp))
                Text("REC %02d:%02d".format(recordSeconds / 60, recordSeconds % 60), color = Color.White)
            }
        }

        // Bottom: collapsible settings + always-visible shutter row
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(visible = settingsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("プリセット", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    PresetRow(
                        selected = preset,
                        enabled = !recording,
                        onSelect = { p ->
                            preset = p.name
                            intensity = p.intensity
                            scope.launch { Settings.applyPreset(context, p) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onlyEngine = Engine.SHADER,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("強度", color = Color.White, modifier = Modifier.width(48.dp))
                        Slider(
                            value = intensity,
                            onValueChange = {
                                intensity = it
                                preset = null
                            },
                            onValueChangeFinished = {
                                scope.launch {
                                    Settings.setIntensity(context, intensity)
                                    Settings.setPreset(context, null)
                                }
                            },
                            valueRange = 0f..Settings.MAX_INTENSITY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Section header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s("cam_settings_title"), color = NeonMagenta, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { settingsExpanded = false }) {
                            Icon(Icons.Filled.Close, s("cam_close"), tint = NeonMagenta)
                        }
                    }

                    ControlRow(s("cam_mode"), accent = NeonMagenta) {
                        listOf(s("cam_mode_photo") to CaptureMode.PHOTO, s("cam_mode_video") to CaptureMode.VIDEO).forEach { (label, m) ->
                            NeonChip(label, selected = mode == m, enabled = !recording,
                                onClick = { mode = m }, accent = NeonMagenta)
                        }
                    }

                    ControlRow(s("cam_preset"), accent = NeonCyan) {
                        Text(preset ?: s("cam_preset_custom"), color = InkText, style = MaterialTheme.typography.bodySmall)
                    }
                    PresetRow(
                        selected = preset,
                        enabled = !recording,
                        onSelect = { p ->
                            preset = p.name
                            intensity = p.intensity
                            renderer?.intensity = p.intensity
                            scope.launch { Settings.applyPreset(context, p) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onlyEngine = com.cudgk.retrovhs.settings.Engine.SHADER,
                    )

                    ControlRow(s("cam_shader"), accent = NeonCyan) {
                        ShaderVariant.values().forEach { v ->
                            NeonChip(v.displayName, selected = variant == v,
                                onClick = {
                                    variant = v
                                    scope.launch { Settings.setVariant(context, v) }
                                }, accent = NeonCyan)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s("cam_intensity"),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            modifier = Modifier.width(80.dp))
                        Slider(
                            value = intensity,
                            onValueChange = {
                                intensity = it
                                preset = null
                            },
                            onValueChangeFinished = {
                                scope.launch {
                                    Settings.setIntensity(context, intensity)
                                    Settings.setPreset(context, null)
                                }
                            },
                            valueRange = 0f..Settings.MAX_INTENSITY,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = NeonMagenta,
                                activeTrackColor = NeonMagenta,
                                inactiveTrackColor = InkBorder,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    ControlRow(s("cam_tint"), accent = NeonYellow) {
                        Tint.values().forEach { t ->
                            NeonChip(t.displayName, selected = tint == t,
                                onClick = {
                                    tint = t
                                    scope.launch { Settings.setTint(context, t) }
                                }, accent = NeonYellow)
                        }
                    }

                    ControlRow(s("cam_res"), accent = NeonCyan) {
                        Quality.values().forEach { q ->
                            NeonChip(q.displayLabel, selected = quality == q, enabled = !recording,
                                onClick = {
                                    quality = q
                                    scope.launch { Settings.setQuality(context, q) }
                                }, accent = NeonCyan)
                        }
                    }
                    ControlRow(s("cam_aspect"), accent = NeonCyan) {
                        AspectRatio.values().forEach { a ->
                            NeonChip(a.displayName, selected = aspect == a, enabled = !recording,
                                onClick = {
                                    aspect = a
                                    scope.launch { Settings.setAspect(context, a) }
                                }, accent = NeonCyan)
                        }
                    }
                    ControlRow(s("cam_fps"), accent = NeonCyan) {
                        FPS_OPTIONS.forEach { v ->
                            NeonChip("$v", selected = fps == v, enabled = !recording,
                                onClick = {
                                    fps = v
                                    scope.launch { Settings.setFps(context, v) }
                                }, accent = NeonCyan)
                        }
                    }

                    ControlRow(s("cam_date"), accent = NeonYellow) {
                        NeonChip(
                            label = if (dateStamp) s("cam_on") else s("cam_off"),
                            selected = dateStamp,
                            onClick = {
                                dateStamp = !dateStamp
                                scope.launch { Settings.setDateStamp(context, dateStamp) }
                            },
                            accent = NeonYellow,
                        )
                    }
                    if (dateStamp) {
                        ControlRow(s("cam_pos"), accent = NeonYellow) {
                            StampPosition.values().forEach { p ->
                                NeonChip(p.displayName, selected = stampPosition == p,
                                    onClick = {
                                        stampPosition = p
                                        scope.launch { Settings.setStampPosition(context, p) }
                                    }, accent = NeonYellow)
                            }
                        }
                        ControlRow(s("cam_rot"), accent = NeonYellow) {
                            StampRotation.values().forEach { r ->
                                NeonChip(r.displayName, selected = stampRotation == r,
                                    onClick = {
                                        stampRotation = r
                                        scope.launch { Settings.setStampRotation(context, r) }
                                    }, accent = NeonYellow)
                            }
                        }
                    }
                }
            }

            // Always-visible shutter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InkBlack.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeonIconButton(
                        icon = if (settingsExpanded) Icons.Filled.ExpandMore else Icons.Filled.Tune,
                        contentDescription = s("cam_settings_btn"),
                        accent = NeonMagenta,
                        onClick = { settingsExpanded = !settingsExpanded },
                    )
                    NeonChip(
                        label = if (settingsExpanded) s("cam_close") else s("cam_back"),
                        selected = false,
                        onClick = {
                            if (settingsExpanded) settingsExpanded = false else onBack()
                        },
                        enabled = !recording,
                        accent = if (settingsExpanded) NeonYellow else InkText,
                    )
                }

                ShutterButton(
                    mode = mode,
                    recording = recording,
                    onClick = {
                        when (mode) {
                            CaptureMode.PHOTO -> {
                                val capture = controller.imageCapture
                                if (capture != null) {
                                    capture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val rot = image.imageInfo.rotationDegrees
                                                android.util.Log.d("CameraScreen", "ImageCapture: ${image.width}x${image.height} rot=$rot")
                                                val raw = image.toBitmapRgba()
                                                image.close()
                                                if (raw == null) {
                                                    Toast.makeText(context, captureFailedLabel, Toast.LENGTH_SHORT).show()
                                                    return
                                                }
                                                val capturedIntensity = intensity
                                                val capturedTint = tint
                                                val capturedVariant = variant
                                                val capturedDateStamp = dateStamp
                                                val capturedStampPos = stampPosition
                                                val capturedStampRot = stampRotation
                                                scope.launch {
                                                    val processed = withContext(Dispatchers.Default) {
                                                        ImageProcessor.processBitmapShader(
                                                            source = raw,
                                                            intensity = capturedIntensity,
                                                            tintColor = capturedTint.rgbArray,
                                                            tintSaturation = capturedTint.saturation,
                                                            variantMul = floatArrayOf(
                                                                capturedVariant.chromaAb, capturedVariant.scanline,
                                                                capturedVariant.grain, capturedVariant.vignette,
                                                                capturedVariant.jitter, capturedVariant.chromaBlur,
                                                            ),
                                                        ).also { raw.recycle() }
                                                    }
                                                    if (processed == null) {
                                                        Toast.makeText(context, shaderFailedLabel, Toast.LENGTH_SHORT).show()
                                                        return@launch
                                                    }
                                                    if (capturedDateStamp) {
                                                        DateStampCanvas.draw(processed, context, capturedStampPos, capturedStampRot)
                                                    }
                                                    val ok = withContext(Dispatchers.IO) {
                                                        PhotoSaver.save(context, processed, "RetroVHS_${System.currentTimeMillis()}")
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        if (ok) "$savedLabel ${processed.width}x${processed.height}" else saveFailedLabel,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                    processed.recycle()
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Toast.makeText(context, "$captureFailedLabel: ${exception.message}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                    )
                                } else {
                                    // Fallback to shader-rendered glReadPixels if ImageCapture not bound yet.
                                    renderer?.requestPhoto()
                                }
                            }
                            CaptureMode.VIDEO -> {
                                val r = renderer ?: return@ShutterButton
                                if (recording) {
                                    r.stopRecording()
                                    recording = false
                                    Toast.makeText(context, videoSavedLabel, Toast.LENGTH_SHORT).show()
                                } else {
                                    val audioOk = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    try {
                                        val rec = Recording(
                                            context = context,
                                            width = quality.width,
                                            height = quality.height,
                                            fps = fps,
                                            displayName = "RetroVHS_${System.currentTimeMillis()}",
                                            audioEnabled = audioOk,
                                        )
                                        rec.start()
                                        r.startRecording(rec)
                                        recording = true
                                    } catch (t: Throwable) {
                                        Toast.makeText(context, "$recordStartFailedLabel: ${t.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                )

                NeonIconButton(
                    icon = Icons.Filled.Cameraswitch,
                    contentDescription = s("cam_lens_flip"),
                    accent = NeonCyan,
                    enabled = !recording,
                    onClick = {
                        lens = if (lens == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                        scope.launch { Settings.setLens(context, lens) }
                    },
                )
            }
        }
    }
}

@Composable
private fun NeonIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(InkPanel.copy(alpha = alpha))
            .border(1.dp, accent.copy(alpha = 0.7f * alpha), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = accent.copy(alpha = alpha))
    }
}

@Composable
private fun ShutterButton(
    mode: CaptureMode,
    recording: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        recording -> NeonRed
        mode == CaptureMode.VIDEO -> NeonRed
        else -> NeonMagenta
    }
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(InkBlack)
            .border(3.dp, accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (recording) 1f else 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (mode == CaptureMode.VIDEO) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                contentDescription = "",
                tint = InkBlack,
            )
        }
    }
}

@Composable
private fun ShutterButtonOld(
    mode: CaptureMode,
    recording: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (recording) Color.Red else Color.White
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(ringColor),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (mode == CaptureMode.VIDEO) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                contentDescription = if (mode == CaptureMode.VIDEO) {
                    if (recording) "停止" else "録画開始"
                } else "撮影",
                tint = Color.Black,
            )
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
