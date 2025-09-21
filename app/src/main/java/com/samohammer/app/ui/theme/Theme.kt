package com.samohammer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tokens minimalistes pour éviter l'Unresolved reference
object SamoTokens {
    val ShootHeader = Color(0xFF2F6FED)
    val MeleeHeader = Color(0xFFD05050)
    val ShootTint   = Color(0xFFE7EFFF)
    val MeleeTint   = Color(0xFFF9E7E7)
}

// Thème Compose minimal (on garde Material3 par défaut)
@Composable
fun SamoHammerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}
