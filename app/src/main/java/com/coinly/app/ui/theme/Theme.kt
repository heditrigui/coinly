package com.coinly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

object FinColors {
    val Income = Color(0xFF16A34A)
    val Expense = Color(0xFFEF4444)
    val Warn = Color(0xFFF59E0B)
    val HeroStart = Color(0xFF0F9D74)
    val HeroEnd = Color(0xFF0B5E8C)
}

private val Light = lightColorScheme(
    primary = Color(0xFF0F9D74),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F5E8),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF0B5E8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6ECF8),
    onSecondaryContainer = Color(0xFF002A40),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF111816),
    surface = Color.White,
    onSurface = Color(0xFF111816),
    surfaceVariant = Color(0xFFEAF0EE),
    onSurfaceVariant = Color(0xFF5B6763),
    outline = Color(0xFFB8C4C0),
    outlineVariant = Color(0xFFDDE5E2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF7FAF9),
    surfaceContainerHighest = Color(0xFFEAF0EE),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F5240),
    onPrimaryContainer = Color(0xFFC9F7E5),
    secondary = Color(0xFF7CC4F0),
    onSecondary = Color(0xFF002A40),
    secondaryContainer = Color(0xFF14445F),
    onSecondaryContainer = Color(0xFFD6ECF8),
    background = Color(0xFF0D1311),
    onBackground = Color(0xFFE3EAE7),
    surface = Color(0xFF161E1B),
    onSurface = Color(0xFFE3EAE7),
    surfaceVariant = Color(0xFF222C29),
    onSurfaceVariant = Color(0xFFA3B1AC),
    outline = Color(0xFF4A5853),
    outlineVariant = Color(0xFF2C3834),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF131A18),
    surfaceContainer = Color(0xFF161E1B),
    surfaceContainerHigh = Color(0xFF1D2623),
    surfaceContainerHighest = Color(0xFF242E2B),
    error = Color(0xFFF87171),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun CoinlyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
