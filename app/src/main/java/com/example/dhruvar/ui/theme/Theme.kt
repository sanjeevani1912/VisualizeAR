package com.example.dhruvar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Field Kit scheme — the only scheme. There is deliberately no dark counterpart: DhruvAR is a
 * day-ops tool and a night theme would make the planning surfaces unreadable in glare, so system
 * dark mode is ignored rather than honoured.
 */
private val FieldKitColorScheme = lightColorScheme(
    primary = Moss,
    onPrimary = Mist,
    primaryContainer = Meadow,
    onPrimaryContainer = Coal,
    secondary = Cacao,
    onSecondary = Mist,
    secondaryContainer = GoldenHour,
    onSecondaryContainer = Coal,
    tertiary = Meadow,
    onTertiary = Coal,
    tertiaryContainer = Meadow,
    onTertiaryContainer = Coal,
    background = Mist,
    onBackground = Coal,
    surface = GoldenHour,
    onSurface = Coal,
    surfaceVariant = Mist,
    onSurfaceVariant = Cacao,
    surfaceTint = Moss,
    inverseSurface = Coal,
    inverseOnSurface = Mist,
    outline = Cacao,
    outlineVariant = Meadow,
    error = Rust,
    onError = Mist,
    errorContainer = GoldenHour,
    onErrorContainer = Cacao,
    scrim = Coal
)

@Composable
fun DhruvARTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FieldKitColorScheme,
        typography = Typography,
        content = content
    )
}
