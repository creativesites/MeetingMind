package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Material's colour scheme, built from a palette so Material components follow the theme too. */
fun materialSchemeFor(c: MMColors) = if (c.isDark) darkColorScheme(
    primary = c.accent, onPrimary = c.onAccent,
    primaryContainer = c.accentWash, onPrimaryContainer = c.ink,
    secondary = c.inkSecondary, onSecondary = c.background,
    secondaryContainer = c.track, onSecondaryContainer = c.ink,
    tertiary = c.speaker3, onTertiary = c.background,
    tertiaryContainer = c.surfaceRaised, onTertiaryContainer = c.ink,
    background = c.background, onBackground = c.ink,
    surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.track, onSurfaceVariant = c.inkSecondary,
    surfaceContainerLowest = c.surfaceSunk, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised,
    outline = c.line, outlineVariant = c.lineSoft,
    error = c.danger, onError = c.background, errorContainer = c.dangerWash, onErrorContainer = c.danger,
    scrim = c.scrim
) else lightColorScheme(
    primary = c.accent, onPrimary = c.onAccent,
    primaryContainer = c.accentWash, onPrimaryContainer = c.ink,
    secondary = c.inkSecondary, onSecondary = c.surface,
    secondaryContainer = c.track, onSecondaryContainer = c.ink,
    tertiary = c.speaker3, onTertiary = c.surface,
    tertiaryContainer = c.surfaceSunk, onTertiaryContainer = c.ink,
    background = c.background, onBackground = c.ink,
    surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.track, onSurfaceVariant = c.inkSecondary,
    surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surfaceSunk,
    surfaceContainerHigh = c.surfaceSunk, surfaceContainerHighest = c.track,
    outline = c.line, outlineVariant = c.lineSoft,
    error = c.danger, onError = c.surface, errorContainer = c.dangerWash, onErrorContainer = c.danger,
    scrim = c.scrim
)

/**
 * The app's theme. Dark (Graphite) unless the person chose otherwise (docs/PRD_M0.md §5).
 * Screens read colours through the role tokens in Color.kt and Material's scheme, which both
 * follow [darkTheme].
 */
@Composable
fun MeetMindTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) GraphiteColors else PaperColors
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> materialSchemeFor(palette)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMMColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** Resolves a [ThemeMode] to dark or light, following the system when asked. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}
