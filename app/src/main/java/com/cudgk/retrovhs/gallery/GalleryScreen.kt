package com.cudgk.retrovhs.gallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cudgk.retrovhs.rust.NtscRs
import com.cudgk.retrovhs.settings.Engine
import com.cudgk.retrovhs.settings.PresetRow
import com.cudgk.retrovhs.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedMime by remember { mutableStateOf<String?>(null) }
    var intensity by remember { mutableFloatStateOf(Settings.DEFAULT_INTENSITY) }
    var engine by remember { mutableStateOf(Settings.DEFAULT_ENGINE) }
    var preset by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        intensity = Settings.intensity(context).first()
        engine = Settings.engine(context).first()
        preset = Settings.preset(context).first()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        pickedUri = uri
        pickedMime = uri?.let { context.contentResolver.getType(it) }
    }

    val isVideo = pickedMime?.startsWith("video/") == true
    val effectiveEngine = if (isVideo) Engine.SHADER else engine

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("戻る") }
            Spacer(Modifier.width(12.dp))
            Text("ギャラリー", style = MaterialTheme.typography.titleLarge)
        }

        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("画像/動画を選択") }

        pickedUri?.let { uri ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text("MIME: ${pickedMime ?: "?"}", style = MaterialTheme.typography.bodySmall)

            Text("プリセット", style = MaterialTheme.typography.titleSmall)
            PresetRow(
                selected = preset,
                enabled = !processing,
                onSelect = { p ->
                    preset = p.name
                    intensity = p.intensity
                    engine = p.engine
                    scope.launch { Settings.applyPreset(context, p) }
                },
                chipBg = MaterialTheme.colorScheme.surfaceVariant,
                chipFg = MaterialTheme.colorScheme.onSurfaceVariant,
                chipBgSelected = MaterialTheme.colorScheme.primary,
                chipFgSelected = MaterialTheme.colorScheme.onPrimary,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("強度", modifier = Modifier.width(48.dp))
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
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("エンジン", modifier = Modifier.width(64.dp))
                listOf(Engine.SHADER to "シェーダ (高速)", Engine.RUST to "NTSC-rs (高画質)").forEach { (e, label) ->
                    val isSel = effectiveEngine == e
                    val rustUnavail = e == Engine.RUST && !NtscRs.isAvailable
                    AssistChip(
                        enabled = !processing && !isVideo && !rustUnavail,
                        onClick = {
                            engine = e
                            scope.launch { Settings.setEngine(context, e) }
                        },
                        label = { Text(if (rustUnavail) "$label ✕" else label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
            if (isVideo) {
                Text(
                    "動画はシェーダのみ対応",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !processing,
                onClick = {
                    processing = true
                    scope.launch {
                        val displayName = "RetroVHS_gallery_${System.currentTimeMillis()}"
                        val ok = withContext(Dispatchers.IO) {
                            if (isVideo) {
                                VideoProcessor.process(context, uri, intensity, displayName)
                            } else {
                                ImageProcessor.process(
                                    context = context,
                                    source = uri,
                                    intensity = intensity,
                                    displayName = displayName,
                                    useRust = effectiveEngine == Engine.RUST,
                                )
                            }
                        }
                        processing = false
                        Toast.makeText(
                            context,
                            if (ok) "保存しました" else "処理に失敗",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) { Text(if (processing) "処理中…" else "VHS加工して保存") }

            if (processing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("処理中…")
                }
            }
        }
    }
}
