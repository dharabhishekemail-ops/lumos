package com.lumos.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9D5FF),
    onPrimary = Color(0xFF1A1025),
    secondary = Color(0xFFBFD7FF),
    onSecondary = Color(0xFF0B1220),
    surface = Color(0xFF0E0E12),
    onSurface = Color(0xFFEAEAF2),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFEAEAF2),
    outline = Color(0xFF3A3A4A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    outline = Color(0xFFD1D5DB),
)

@Composable
fun LumosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.SansSerif),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.SansSerif),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        ),
        content = content
    )
}
