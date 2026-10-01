package com.example.dhruvar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrecisionBlueLight,
    onPrimary = Slate950,
    primaryContainer = Slate800,
    onPrimaryContainer = PrecisionBlueLight,
    secondary = Slate400,
    onSecondary = Slate950,
    secondaryContainer = Slate850,
    onSecondaryContainer = Slate200,
    tertiary = TacticalGreen,
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate850,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    outlineVariant = Slate800,
    error = Color(0xFFCF6679),
    onError = Slate950
)

private val LightColorScheme = lightColorScheme(
    primary = PrecisionBlue,
    onPrimary = Color.White,
    primaryContainer = Slate100,
    onPrimaryContainer = PrecisionBlueDark,
    secondary = Slate600,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = TacticalGreen,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun DhruvARTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
