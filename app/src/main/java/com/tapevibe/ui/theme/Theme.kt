package com.tapevibe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Tv {
    val Background = Color(0xFF15130F)
    val Surface = Color(0xFF201D18)
    val SurfaceHigh = Color(0xFF2B2721)
    val Outline = Color(0xFF3A352D)
    val Text = Color(0xFFF2E9DC)
    val TextDim = Color(0xFFA89F91)
    val Accent = Color(0xFFFF8A3D)
    val Teal = Color(0xFF3DD6C3)
    val Record = Color(0xFFFF4F4F)
    val Yellow = Color(0xFFFFD25A)
    val GridDark = Color(0xFF1A1814)
    val GridLight = Color(0xFF24211C)

    val trackColors = listOf(
        Color(0xFFFF8A3D), Color(0xFF3DD6C3), Color(0xFFFFD25A), Color(0xFFFF6FA8),
        Color(0xFFA98BFF), Color(0xFF8BD66B), Color(0xFF5AB4FF), Color(0xFFFF6B5A),
    )

    fun trackColor(index: Int) = trackColors[((index % trackColors.size) + trackColors.size) % trackColors.size]

    val Mono = FontFamily.Monospace
}

private val scheme = darkColorScheme(
    primary = Tv.Accent,
    onPrimary = Color(0xFF1B1209),
    secondary = Tv.Teal,
    onSecondary = Color(0xFF06201D),
    background = Tv.Background,
    onBackground = Tv.Text,
    surface = Tv.Surface,
    onSurface = Tv.Text,
    surfaceVariant = Tv.SurfaceHigh,
    onSurfaceVariant = Tv.TextDim,
    surfaceContainer = Tv.Surface,
    surfaceContainerHigh = Tv.SurfaceHigh,
    surfaceContainerHighest = Tv.SurfaceHigh,
    surfaceContainerLow = Tv.Surface,
    outline = Tv.Outline,
    outlineVariant = Tv.Outline,
    error = Tv.Record,
)

private val typography = Typography().let { t ->
    t.copy(
        labelSmall = t.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.3.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoDisplay = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
fun TapevibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
