package com.samohammer.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

// --- ColorScheme DARK: fond noir + surfaces gris, texte blanc.
// Primary/Secondary en vert pour les accents (FAB, checkboxes, boutons).
private val SamoDarkColorScheme: ColorScheme = darkColorScheme(
    // Accents
    primary = Green500,
    onPrimary = OnPrimaryDark,
    secondary = Green400,
    onSecondary = OnPrimaryDark,
    tertiary = Green600,
    onTertiary = OnPrimaryDark,

    // Fond & surfaces
    background = DarkBg,
    onBackground = OnDark,
    surface = DarkSurface,          // par défaut: cartes "Unit"
    onSurface = OnDark,

    // Surfaces alternatives (utilisées via surfaceVariant / onSurfaceVariant)
    surfaceVariant = DarkSurfaceAlt, // cartes "Weapon Profile"
    onSurfaceVariant = OnDark,

    // Autres (dialog, menus, etc.)
    outline = DividerGray,
    outlineVariant = DividerGray,

    // Erreur (garde par défaut M3)
    error = androidx.compose.material3.ColorScheme.defaults.error,
    onError = androidx.compose.material3.ColorScheme.defaults.onError
)

// Garder la typo & shapes M3 par défaut (déjà nickel pour notre use).
private val SamoTypography = Typography()
private val SamoShapes = Shapes()

@Composable
fun SamoHammerTheme(
    content: @Composable () -> Unit
) {
    // On force le thème dark (pas de dynamic color pour garder un rendu stable).
    MaterialTheme(
        colorScheme = SamoDarkColorScheme,
        typography = SamoTypography,
        shapes = SamoShapes,
        content = content
    )
}
