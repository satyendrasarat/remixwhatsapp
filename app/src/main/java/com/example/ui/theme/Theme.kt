package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = DeepForest,
    secondary = TealInfo,
    onSecondary = Color.White,
    secondaryContainer = TealSoftLight,
    onSecondaryContainer = DeepForest,
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoftLight,
    onTertiaryContainer = Color(0xFF451A03),
    error = CoralDanger,
    onError = Color.White,
    errorContainer = CoralSoftLight,
    onErrorContainer = Color(0xFF7F1D1D),
    background = PorcelainBg,
    onBackground = InkPrimary,
    surface = CrispSurface,
    onSurface = InkPrimary,
    surfaceVariant = SoftSageSurface,
    onSurfaceVariant = InkSecondary,
    outline = SubtleOutline,
    outlineVariant = Color(0xFFE2ECE6)
)

private val DarkColorScheme = darkColorScheme(
    primary = MintAccent,
    onPrimary = DeepForest,
    primaryContainer = Color(0xFF0A4733),
    onPrimaryContainer = MintSoft,
    secondary = Color(0xFF5EEAD4),
    onSecondary = DeepForest,
    secondaryContainer = TealSoftDark,
    onSecondaryContainer = TealSoftLight,
    tertiary = AmberOnDark,
    onTertiary = Color(0xFF3F2807),
    tertiaryContainer = AmberSoftDark,
    onTertiaryContainer = AmberSoftLight,
    error = CoralOnDark,
    onError = Color(0xFF450A0A),
    errorContainer = CoralSoftDark,
    onErrorContainer = CoralSoftLight,
    background = ObsidianBg,
    onBackground = DarkInkPrimary,
    surface = ObsidianSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = DarkInkSecondary,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1B2E27)
)

val ReplyFlowShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ReplyFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ReplyFlowTypography,
        shapes = ReplyFlowShapes,
        content = content
    )
}
