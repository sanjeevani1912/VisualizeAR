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
    background = Slate900,
    onBackground = Slate100,
    surface = Slate850,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate700
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
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate300
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