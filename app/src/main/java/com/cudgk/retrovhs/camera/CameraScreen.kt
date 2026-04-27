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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FPS_OPTIONS = listOf(15, 24, 30, 60)
private val PREVIEW_SIZE = Size(1280, 720)
private const val VIDEO_W = 1280
private const val VIDEO_H = 720

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
            }) {
                Text("権限をリクエスト")
            }
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

    var intensity by remember { mutableFloatStateOf(0.7f) }
    var fps by remember { mutableIntStateOf(30) }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }

    val controller = remember { CameraController(context) }
    var renderer by remember { mutableStateOf<VhsRenderer?>(null) }
    var cameraSurface by remember { mutableStateOf<Surface?>(null) }

    LaunchedEffect(lens, fps, cameraSurface) {
        val s = cameraSurface ?: return@LaunchedEffect
        controller.lensFacing = lens
        controller.targetFps = fps
        controller.bind(
            owner = owner,
            surface = s,
            previewSize = PREVIEW_SIZE,
            onError = { t ->
                Toast.makeText(context, "Camera error: ${t.message}", Toast.LENGTH_LONG).show()
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = GLSurfaceView(ctx)
                view.setEGLContextClientVersion(3)
                view.setEGLConfigChooser(RecordableConfigChooser())
                val r = VhsRenderer(
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("強度", color = Color.White, modifier = Modifier.width(48.dp))
                Slider(
                    value = intensity,
                    onValueChange = {
                        intensity = it
                        renderer?.intensity = it
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FPS", color = Color.White, modifier = Modifier.width(48.dp))
                FPS_OPTIONS.forEach { v ->
                    AssistChip(
                        enabled = !recording,
                        onClick = { fps = v },
                        label = { Text("$v") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (fps == v) Color.White else Color.DarkGray,
                            labelColor = if (fps == v) Color.Black else Color.White,
                        ),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("モード", color = Color.White, modifier = Modifier.width(48.dp))
                listOf(CaptureMode.PHOTO to "写真", CaptureMode.VIDEO to "動画").forEach { (m, label) ->
                    AssistChip(
                        enabled = !recording,
                        onClick = { mode = m },
                        label = { Text(label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (mode == m) Color.White else Color.DarkGray,
                            labelColor = if (mode == m) Color.Black else Color.White,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onBack, enabled = !recording) { Text("戻る") }

                ShutterButton(
                    mode = mode,
                    recording = recording,
                    onClick = {
                        when (mode) {
                            CaptureMode.PHOTO -> renderer?.requestPhoto()
                            CaptureMode.VIDEO -> {
                                val r = renderer ?: return@ShutterButton
                                if (recording) {
                                    r.stopRecording()
                                    recording = false
                                    Toast.makeText(context, "動画を保存しました", Toast.LENGTH_SHORT).show()
                                } else {
                                    val audioOk = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    try {
                                        val rec = Recording(
                                            context = context,
                                            width = VIDEO_W,
                                            height = VIDEO_H,
                                            fps = fps,
                                            displayName = "RetroVHS_${System.currentTimeMillis()}",
                                            audioEnabled = audioOk,
                                        )
                                        rec.start()
                                        r.startRecording(rec)
                                        recording = true
                                    } catch (t: Throwable) {
                                        Toast.makeText(context, "録画開始失敗: ${t.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                )

                IconButton(
                    enabled = !recording,
                    onClick = {
                        lens = if (lens == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Cameraswitch,
                        contentDescription = "前後切替",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
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
