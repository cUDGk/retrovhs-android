package com.cudgk.retrovhs.gallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cudgk.retrovhs.i18n.s
import com.cudgk.retrovhs.rust.NtscRs
import com.cudgk.retrovhs.settings.PresetRow
import com.cudgk.retrovhs.settings.Settings
import com.cudgk.retrovhs.settings.ShaderVariant
import com.cudgk.retrovhs.settings.Tint
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
    var preset by remember { mutableStateOf<String?>(null) }
    var tint by remember { mutableStateOf(Settings.DEFAULT_TINT) }
    var variant by remember { mutableStateOf(Settings.DEFAULT_VARIANT) }
    var processing by remember { mutableStateOf(false) }
    val savedLabel = s("gal_saved")
    val failedLabel = s("gal_failed")

    LaunchedEffect(Unit) {
        intensity = Settings.intensity(context).first()
        preset = Settings.preset(context).first()
        tint = Settings.tint(context).first()
        variant = Settings.variant(context).first()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        pickedUri = uri
        pickedMime = uri?.let { context.contentResolver.getType(it) }
    }

    val isVideo = pickedMime?.startsWith("video/") == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text(s("gal_back")) }
            Spacer(Modifier.width(12.dp))
            Text(s("gal_title"), style = MaterialTheme.typography.titleLarge)
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
        ) { Text(s("gal_pick")) }

        pickedUri?.let { uri ->
            if (isVideo) {
                ZoomablePreview(uri = uri)
            } else {
                GalleryShaderPreview(
                    uri = uri,
                    intensity = intensity,
                    tint = tint,
                    variant = variant,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            Text("${pickedMime ?: "?"}  (${s("gal_pinch_hint")})", style = MaterialTheme.typography.bodySmall)

            Text(s("gal_preset"), style = MaterialTheme.typography.titleSmall)
            PresetRow(
                selected = preset,
                enabled = !processing,
                onSelect = { p ->
                    preset = p.name
                    intensity = p.intensity
                    scope.launch { Settings.applyPreset(context, p) }
                },
                chipBg = MaterialTheme.colorScheme.surfaceVariant,
                chipFg = MaterialTheme.colorScheme.onSurfaceVariant,
                chipBgSelected = MaterialTheme.colorScheme.primary,
                chipFgSelected = MaterialTheme.colorScheme.onPrimary,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s("gal_intensity"), modifier = Modifier.width(48.dp))
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

            if (isVideo) {
                Text(
                    s("gal_video_only_shader"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!NtscRs.isAvailable) {
                Text(
                    s("gal_rust_unavailable"),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    s("gal_rust"),
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
                                    useRust = NtscRs.isAvailable,
                                )
                            }
                        }
                        processing = false
                        Toast.makeText(
                            context,
                            if (ok) savedLabel else failedLabel,
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

@Composable
private fun ColumnScope.ZoomablePreview(uri: Uri) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .pointerInput(uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 6f)
                    offset += pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}
