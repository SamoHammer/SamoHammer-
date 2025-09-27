package com.samohammer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Schémas Material3 stricts (aucun appel à une API non standard)
private val DarkColors = darkColorScheme(
    primary = SamoGreen,
    onPrimary = SamoOnGreen,
    primaryContainer = SamoGreenContainer,
    onPrimaryContainer = SamoOnGreenContainer,

    surface = SamoSurfaceDark,
    onSurface = SamoOnSurfaceDark,
    surfaceVariant = SamoSurfaceVariantDark,
    onSurfaceVariant = SamoOnSurfaceVariantDark,

    secondary = SamoSecondary,
    onSecondary = SamoOnSecondary,
)

private val LightColors = lightColorScheme(
    primary = SamoGreen,
    onPrimary = SamoOnGreen,
    primaryContainer = SamoGreenContainer,
    onPrimaryContainer = SamoOnGreenContainer,

    surface = SamoSurfaceLight,
    onSurface = SamoOnSurfaceLight,
    surfaceVariant = SamoSurfaceVariantLight,
    onSurfaceVariant = SamoOnSurfaceVariantLight,

    secondary = SamoSecondary,
    onSecondary = SamoOnSecondary,
)

@Composable
fun SamoHammerTheme(
    darkTheme: Boolean = true, // on reste en mode sombre par défaut
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        // Pas de Typography/Shapes custom → on laisse les défauts de Material3
        content = content
    )
}
