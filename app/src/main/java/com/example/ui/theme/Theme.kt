package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.model.ChartAppearance

val LocalChartAppearance = staticCompositionLocalOf { ChartAppearance() }

@Composable
fun DccChartingTheme(
    appearance: ChartAppearance = ChartAppearance(),
    content: @Composable () -> Unit
) {
    val background = appearance.backgroundArgb.toComposeColor()
    val surface = appearance.surfaceArgb.toComposeColor()
    val surfaceElevated = appearance.surfaceElevatedArgb.toComposeColor()
    val border = appearance.borderArgb.toComposeColor()
    val primary = appearance.primaryAccentArgb.toComposeColor()
    val textPrimary = appearance.textPrimaryArgb.toComposeColor()
    val textSecondary = appearance.textSecondaryArgb.toComposeColor()
    val bullish = appearance.bullishCandleArgb.toComposeColor()
    val bearish = appearance.bearishCandleArgb.toComposeColor()

    val colorScheme = if (appearance.isLightTheme) {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = primary,
            secondary = appearance.dccUpperArgb.toComposeColor(),
            onSecondary = Color.White,
            tertiary = bullish,
            onTertiary = Color.White,
            error = bearish,
            onError = Color.White,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            outline = border,
            outlineVariant = border.copy(alpha = 0.6f)
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF081018),
            primaryContainer = primary.copy(alpha = 0.18f),
            onPrimaryContainer = primary,
            secondary = appearance.dccUpperArgb.toComposeColor(),
            onSecondary = Color(0xFF0A0F1A),
            tertiary = bullish,
            onTertiary = Color(0xFF061510),
            error = bearish,
            onError = Color(0xFF1F090A),
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            outline = border,
            outlineVariant = border.copy(alpha = 0.65f)
        )
    }

    CompositionLocalProvider(LocalChartAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
