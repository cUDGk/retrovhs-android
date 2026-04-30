package com.cudgk.retrovhs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.cudgk.retrovhs.camera.CameraScreen
import com.cudgk.retrovhs.gallery.GalleryScreen
import com.cudgk.retrovhs.i18n.Language
import com.cudgk.retrovhs.i18n.LocalLanguage
import com.cudgk.retrovhs.i18n.s
import com.cudgk.retrovhs.settings.Settings
import com.cudgk.retrovhs.settings.SettingsScreen
import com.cudgk.retrovhs.theme.NeonChip
import com.cudgk.retrovhs.theme.RetroVhsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RetroVhsTheme {
                val ctx = LocalContext.current
                val language by Settings.language(ctx).collectAsState(initial = Settings.DEFAULT_LANGUAGE)
                CompositionLocalProvider(LocalLanguage provides language) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onCamera = { nav.navigate("camera") },
                onGallery = { nav.navigate("gallery") },
                onSettings = { nav.navigate("settings") },
                onLicense = { nav.navigate("license") },
            )
        }
        composable("camera") { CameraScreen(onBack = { nav.popBackStack() }) }
        composable("gallery") { GalleryScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("license") { LicenseScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun HomeScreen(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onLicense: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLang = LocalLanguage.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "RETRO//VHS",
            style = MaterialTheme.typography.displayLarge,
            color = com.cudgk.retrovhs.theme.NeonMagenta,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s("app_subtitle"),
            style = MaterialTheme.typography.labelMedium,
            color = com.cudgk.retrovhs.theme.NeonCyan,
        )
        Spacer(Modifier.height(48.dp))
        HomeButton(s("home_camera_title"), s("home_camera_sub"), com.cudgk.retrovhs.theme.NeonMagenta, onCamera)
        Spacer(Modifier.height(12.dp))
        HomeButton(s("home_gallery_title"), s("home_gallery_sub"), com.cudgk.retrovhs.theme.NeonCyan, onGallery)
        Spacer(Modifier.height(12.dp))
        HomeButton(s("home_settings_title"), s("home_settings_sub"), com.cudgk.retrovhs.theme.NeonYellow, onSettings)
        Spacer(Modifier.height(12.dp))
        HomeButton(
            title = "LANGUAGE",
            subtitle = currentLang.displayName,
            accent = com.cudgk.retrovhs.theme.NeonCyan,
            onClick = {
                val next = if (currentLang == Language.JA) Language.EN else Language.JA
                scope.launch { Settings.setLanguage(ctx, next) }
            },
        )
        Spacer(Modifier.height(12.dp))
        HomeButton(s("home_license_title"), s("home_license_sub"), com.cudgk.retrovhs.theme.InkTextDim, onLicense)
    }
}

@Composable
private fun HomeButton(
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(com.cudgk.retrovhs.theme.InkPanel)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = accent)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = com.cudgk.retrovhs.theme.InkText)
        }
    }
}
