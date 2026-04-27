package com.cudgk.retrovhs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cudgk.retrovhs.rust.NtscRs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var intensity by remember { mutableFloatStateOf(Settings.DEFAULT_INTENSITY) }
    var preset by remember { mutableStateOf<String?>(null) }
    var engine by remember { mutableStateOf(Settings.DEFAULT_ENGINE) }

    LaunchedEffect(Unit) {
        intensity = Settings.intensity(context).first()
        preset = Settings.preset(context).first()
        engine = Settings.engine(context).first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            Button(onClick = onBack) { Text("戻る") }
            Spacer(Modifier.width(12.dp))
            Text("設定 / プリセット", style = MaterialTheme.typography.titleLarge)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("現在", style = MaterialTheme.typography.titleMedium)
                Text("プリセット: ${preset ?: "未選択 (カスタム)"}")
                Text("強度: ${"%.2f".format(intensity)}")
                Text("エンジン: ${engineLabel(engine)}")
                if (engine == Engine.RUST && !NtscRs.isAvailable) {
                    Text(
                        "⚠ NTSC-rs ネイティブライブラリ読み込み失敗: ${NtscRs.errorMessage ?: "不明"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text("プリセットを選択", style = MaterialTheme.typography.titleMedium)
        Presets.ALL.forEach { p ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        Settings.applyPreset(context, p)
                        intensity = p.intensity
                        preset = p.name
                        engine = p.engine
                    }
                },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(p.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "強度 ${"%.2f".format(p.intensity)} / ${engineLabel(p.engine)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    Settings.setIntensity(context, Settings.DEFAULT_INTENSITY)
                    Settings.setFps(context, Settings.DEFAULT_FPS)
                    Settings.setLens(context, Settings.DEFAULT_LENS)
                    Settings.setEngine(context, Settings.DEFAULT_ENGINE)
                    Settings.setPreset(context, null)
                    intensity = Settings.DEFAULT_INTENSITY
                    preset = null
                    engine = Settings.DEFAULT_ENGINE
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("デフォルトに戻す") }
    }
}

private fun engineLabel(e: Engine): String = when (e) {
    Engine.SHADER -> "シェーダ (高速)"
    Engine.RUST -> "NTSC-rs (高画質)"
}
