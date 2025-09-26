package com.samohammer.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * SamoHammer Theme — FORCÉ en mode CLAIR.
 *
 * - S'aligne sur Color.kt (DarkBackground, UnitCard, WeaponCard, TargetCard, TextPrimary,
 *   TextSecondary, AccentGreen, DividerGreen, etc.)
 * - Aucune dépendance à Typography.kt : on utilise Typography() par défaut.
 * - Pas de bascule automatique avec le système : toujours clair.
 */

// ---------- Palette CLAIRE ----------
private val LightColors: ColorScheme = lightColorScheme(
    // Brand / Accents
    primary = AccentGreen,
    onPrimary = Color.White,
    secondary = AccentGreen,
    onSecondary = Color.White,
    tertiary = AccentGreen,

    // Background / Surface
    background = Color(0xFFFAFAFA),   // fond général clair
    onBackground = TextPrimary,
    surface = Color(0xFFFFFFFF),
    onSurface = TextPrimary,

    // Surfaces spécifiques
    surfaceVariant = WeaponCard,      // carte "Weapon Profile"
    onSurfaceVariant = TextSecondary,

    // Containers (Target)
    tertiaryContainer = TargetCard,   // bloc "Target" (Bonus)
    onTertiaryContainer = TextPrimary,

    // Outline / dividers
    outline = DividerGreen,

    // Fallbacks
    primaryContainer = UnitCard,          // carte "Unit"
    onPrimaryContainer = TextPrimary,
    secondaryContainer = WeaponCard,
    onSecondaryContainer = TextPrimary,
)

/**
 * Thème Composte Material 3 — forcé CLAIR.
 */
@Composable
fun SamoHammerTheme(
    content: @Composable () -> Unit
) {
    val colors = LightColors   // ← FORCÉ CLAIR (pas de isSystemInDarkTheme())

    // Optionnel : teinte des barres système pour cohérence visuelle
    val view = LocalView.current
    if (!view.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        (view.context as? Activity)?.window?.statusBarColor = colors.primary.toArgb()
        (view.context as? Activity)?.window?.navigationBarColor = colors.background.toArgb()
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
