package com.mobile.enerjeep.ui.theme

import android.app.Activity
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
    primary = Color(0xFFA9C0F5),
    secondary = Color(0xFFB3C2E8),
    tertiary = Color(0xFFE8B4CF),
    background = Color(0xFF121418),
    surface = Color(0xFF1B1E24),
    error = Color(0xFFF2B8B5)
)

/*
 * Ang tunay na brand ng EnerJeep (hindi na ang template na purple).
 * Bawat default na Material 3 component (dialog, dropdown, atbp.) ay
 * gagamit ng kulay na ito kaya magkakatugma ang buong app.
 */
private val LightColorScheme = lightColorScheme(
    primary = EnerjeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = EnerjeepBlue,
    secondary = EnerjeepBlue,
    onSecondary = Color.White,
    background = LightGrayBackground,
    surface = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun EnerjeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /*
     * Hindi ginagamit ang dynamic color (Material You): ang app ay may
     * sariling brand color at hindi dapat sumusunod sa wallpaper ng
     * device ang mga default na component.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}