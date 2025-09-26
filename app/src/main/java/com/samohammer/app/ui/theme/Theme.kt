package com.samohammer.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// === Light scheme (vert) ===
private val LightColors: ColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    secondary = GreenSecondary,
    onSecondary = Color.White,
    tertiary = GreenSecondaryLight,
    onTertiary = Color.White,

    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,

    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    outline = OutlineSoft,
    primaryContainer = GreenPrimaryLight,
    onPrimaryContainer = Color.Black,
    secondaryContainer = GreenSecondaryLight,
    onSecondaryContainer = Color.Black,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
)

// === Dark scheme (vert) ===
private val DarkColors: ColorScheme = darkColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = Color.Black,
    secondary = GreenSecondaryLight,
    onSecondary = Color.Black,
    tertiary = GreenSecondary,
    onTertiary = Color.White,

    background = Color(0xFF0F1512),
    onBackground = Color.White,
    surface = Color(0xFF0F1512),
    onSurface = Color.White,

    surfaceVariant = Color(0xFF1C2A22),
    onSurfaceVariant = Color(0xFFBFD7C8),

    outline = Color(0xFF3B5345),
    primaryContainer = GreenPrimary,
    onPrimaryContainer = Color.White,
    secondaryContainer = GreenSecondary,
    onSecondaryContainer = Color.White,
    tertiaryContainer = Color(0xFF254C3B),
    onTertiaryContainer = Color(0xFFCCF1DA),
)

/**
 * Thème SamoHammer — passe l’app en teintes de vert.
 * (Ne touche rien au moteur, uniquement couleurs.)
 */
@Composable
fun SamoHammerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Optionnel: teinter status/navigation bar pour cohérence visuelle
    val view = LocalView.current
    if (!view.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        (view.context as? Activity)?.window?.statusBarColor = colors.primary.toArgb()
        (view.context as? Activity)?.window?.navigationBarColor = colors.background.toArgb()
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
