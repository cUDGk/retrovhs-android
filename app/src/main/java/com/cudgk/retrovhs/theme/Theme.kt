package com.cudgk.retrovhs.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.cudgk.retrovhs.R

val NeonMagenta = Color(0xFFFF2DB8)
val NeonCyan = Color(0xFF39E5FF)
val NeonYellow = Color(0xFFFFD84D)
val NeonRed = Color(0xFFFF3D3D)

val InkBlack = Color(0xFF08080F)
val InkSlate = Color(0xFF14141F)
val InkPanel = Color(0xFF1C1C2A)
val InkBorder = Color(0xFF2C2C40)
val InkText = Color(0xFFE8E8F0)
val InkTextDim = Color(0xFFA0A0B5)

private val RetroColorScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = InkBlack,
    primaryContainer = NeonMagenta.copy(alpha = 0.18f),
    onPrimaryContainer = NeonMagenta,
    secondary = NeonCyan,
    onSecondary = InkBlack,
    secondaryContainer = NeonCyan.copy(alpha = 0.18f),
    onSecondaryContainer = NeonCyan,
    tertiary = NeonYellow,
    onTertiary = InkBlack,
    background = InkBlack,
    onBackground = InkText,
    surface = InkSlate,
    onSurface = InkText,
    surfaceVariant = InkPanel,
    onSurfaceVariant = InkTextDim,
    outline = InkBorder,
    outlineVariant = InkBorder,
    error = NeonRed,
    onError = InkBlack,
)

private val RetroShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

val VcrFontFamily = FontFamily(Font(R.font.vcr_osd_mono, FontWeight.Normal))

private fun retroTypography(): Typography {
    val mono = VcrFontFamily
    val sans = FontFamily.Default

    val display = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 1.sp)
    val title = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.5.sp)
    val titleS = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
    val body = TextStyle(fontFamily = sans, fontSize = 15.sp)
    val bodyS = TextStyle(fontFamily = sans, fontSize = 13.sp)
    val label = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)

    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = title, headlineMedium = title, headlineSmall = title,
        titleLarge = title, titleMedium = titleS, titleSmall = titleS,
        bodyLarge = body, bodyMedium = body, bodySmall = bodyS,
        labelLarge = label, labelMedium = label, labelSmall = label,
    )
}

@Composable
fun RetroVhsTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = InkBlack.toArgb()
            window.navigationBarColor = InkBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = RetroColorScheme,
        typography = retroTypography(),
        shapes = RetroShapes,
        content = content,
    )
}
