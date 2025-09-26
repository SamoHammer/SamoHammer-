package com.samohammer.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * Theme.kt aligné sur le Color.kt "nouveaux noms"
 * (DarkBackground, UnitCard, WeaponCard, TargetCard, TextPrimary, TextSecondary,
 *  AccentGreen, DividerGreen, etc.).
 *
 * Aucune référence aux anciens identifiants (GreenPrimary, SurfaceVariant, etc.).
 * Pas de Typography.kt requis (on passe Typography()).
 */

// ---------- Color mapping vers Material3 ----------
private val LightColors: ColorScheme = lightColorScheme(
    // Brand / Accents
    primary = AccentGreen,
    onPrimary = Color.White,
    secondary = AccentGreen,        // on garde le même accent pour cohérence
    onSecondary = Color.White,
    tertiary = AccentGreen,         // peu utilisé (fallback)

    // Background / Surface
    background = Color.White,       // mode clair (si activé par l’OS)
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,

    // Surfaces spécifiques (nous les consommons via MaterialTheme.colorScheme.*)
    surfaceVariant = WeaponCard,    // carte "Weapon Profile"
    onSurfaceVariant = TextSecondary,

    // Containers (Target)
    tertiaryContainer = TargetCard, // bloc "Target" (Bonus)
    onTertiaryContainer = TextPrimary,

    // Outline / dividers
    outline = DividerGreen,
    primaryContainer = AccentGreen,         // fallback
    onPrimaryContainer = Color.White,
    secondaryContainer = AccentGreen,       // fallback
    onSecondaryContainer = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    // Brand / Accents
    primary = AccentGreen,
    onPrimary = Color.Black,
    secondary = AccentGreen,
    onSecondary = Color.Black,
    tertiary = AccentGreen,

    // Background / Surface
    background = DarkBackground,    // fond global sombre (ton vert très foncé)
    onBackground = TextPrimary,
    surface = DarkBackground,
    onSurface = TextPrimary,

    // Surfaces spécifiques
    surfaceVariant = WeaponCard,    // carte "Weapon Profile"
    onSurfaceVariant = TextSecondary,

    // Containers (Target)
    tertiaryContainer = TargetCard, // bloc "Target" (Bonus)
    onTertiaryContainer = TextPrimary,

    // Outline
    outline = DividerGreen,

    // Containers complémentaires (fallbacks)
    primaryContainer = UnitCard,        // carte "Unit" (le plus sombre)
    onPrimaryContainer = TextPrimary,
    secondaryContainer = WeaponCard,
    onSecondaryContainer = TextPrimary,
)

/**
 * Thème SamoHammer (Material 3) aligné sur Color.kt (nouveaux noms).
 */
@Composable
fun SamoHammerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // (Optionnel) teinter status/navigation bar pour coller au thème
    val view = LocalView.current
    if (!view.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        (view.context as? Activity)?.window?.statusBarColor = colors.primary.toArgb()
        (view.context as? Activity)?.window?.navigationBarColor = colors.background.toArgb()
    }

    MaterialTheme(
        colorScheme = colors,
        // Pas de fichier Typography.kt requis : instance M3 par défaut
        typography = Typography(),
        content = content
    )
}
