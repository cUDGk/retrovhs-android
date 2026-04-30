package com.cudgk.retrovhs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cudgk.retrovhs.i18n.s

private data class OssEntry(
    val name: String,
    val url: String,
    val license: String,
    val notice: String,
)

private val OSS_ENTRIES = listOf(
    OssEntry(
        name = "ntsc-rs",
        url = "https://github.com/ntsc-rs/ntsc-rs",
        license = "MIT / Apache-2.0 / ISC",
        notice = "本アプリのVHS/NTSCエフェクトのコアロジック。Copyright (c) ntsc-rs contributors.",
    ),
    OssEntry(
        name = "AndroidX / Jetpack Compose",
        url = "https://developer.android.com/jetpack/androidx",
        license = "Apache-2.0",
        notice = "Copyright The Android Open Source Project",
    ),
    OssEntry(
        name = "CameraX",
        url = "https://developer.android.com/training/camerax",
        license = "Apache-2.0",
        notice = "Copyright The Android Open Source Project",
    ),
    OssEntry(
        name = "Kotlin",
        url = "https://kotlinlang.org/",
        license = "Apache-2.0",
        notice = "Copyright JetBrains s.r.o. and Kotlin contributors",
    ),
    OssEntry(
        name = "Coil",
        url = "https://github.com/coil-kt/coil",
        license = "Apache-2.0",
        notice = "Copyright Coil contributors",
    ),
    OssEntry(
        name = "DataStore",
        url = "https://developer.android.com/jetpack/androidx/releases/datastore",
        license = "Apache-2.0",
        notice = "Copyright The Android Open Source Project",
    ),
    OssEntry(
        name = "jni-rs",
        url = "https://github.com/jni-rs/jni-rs",
        license = "MIT / Apache-2.0",
        notice = "Copyright (c) 2016 Yifan Lyu et al.",
    ),
    OssEntry(
        name = "image-rs",
        url = "https://github.com/image-rs/image",
        license = "MIT / Apache-2.0",
        notice = "Copyright (c) image-rs contributors",
    ),
    OssEntry(
        name = "VCR OSD Mono (font)",
        url = "https://www.dafont.com/vcr-osd-mono.font",
        license = "Free for personal/commercial use",
        notice = "Designed by Riciery Leal",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s("lic_title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s("lic_back"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                s("lic_intro"),
                style = MaterialTheme.typography.bodyMedium,
            )
            OSS_ENTRIES.forEach { entry -> OssCard(entry) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OssCard(entry: OssEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(entry.name, style = MaterialTheme.typography.titleMedium)
        Text("${s("lic_license_label")}${entry.license}", style = MaterialTheme.typography.bodySmall)
        Text(entry.url, style = MaterialTheme.typography.bodySmall)
        Text(entry.notice, style = MaterialTheme.typography.bodySmall)
    }
}
