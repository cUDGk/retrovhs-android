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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FPS_OPTIONS = listOf(15, 24, 30, 60)
private val PREVIEW_SIZE = Size(1280, 720)

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("カメラ権限が必要です", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
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

    DisposableEffect(Unit) {
        onDispose {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onBack) { Text("戻る") }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { renderer?.requestPhoto() }) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = "撮影",
                            tint = Color.Black,
                        )
                    }
                }

                IconButton(
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

private fun hasCameraPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
