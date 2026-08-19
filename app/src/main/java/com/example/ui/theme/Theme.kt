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

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = VioletSecondary,
    onSecondary = VioletOnSecondary,
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = CyanTertiary,
    onTertiary = CyanOnTertiary,
    tertiaryContainer = Color(0xFF164E63),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRed
)

// CleanMyMac Ultra-Clean Light Color Scheme
private val CleanMacLightColorScheme = lightColorScheme(
    primary = CleanMacIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF3730A3),
    secondary = CleanMacPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFAF5FF),
    onSecondaryContainer = Color(0xFF6B21A8),
    tertiary = CleanMacCyan,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECFEFF),
    onTertiaryContainer = Color(0xFF155E75),
    background = LightCanvasBackground,
    onBackground = LightTextPrimary,
    surface = LightCardSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightPillSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorderColor,
    outlineVariant = LightBorderSubtle,
    error = ErrorRed
)

@Composable
fun MeetMindTheme(
    darkTheme: Boolean = false, // Clean & modern light CleanMyMac theme by default
    dynamicColor: Boolean = false, // Keep signature vibrant branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> CleanMacLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
