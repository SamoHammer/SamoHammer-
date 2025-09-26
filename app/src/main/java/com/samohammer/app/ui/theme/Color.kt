package com.samohammer.app.ui.theme

import androidx.compose.ui.graphics.Color

// === Palette verte (Material-ish) ===
val GreenPrimary       = Color(0xFF2E7D32)  // emerald 700
val GreenPrimaryDark   = Color(0xFF1B5E20)  // emerald 900
val GreenPrimaryLight  = Color(0xFF66BB6A)  // emerald 400

val GreenSecondary     = Color(0xFF00796B)  // teal 700
val GreenSecondaryLight= Color(0xFF26A69A)  // teal 400
val GreenSecondaryDark = Color(0xFF004D40)  // teal 900

// Surfaces / containers
val SurfaceVariant         = Color(0xFFE8F3EB) // léger vert/gris
val OnSurfaceVariant       = Color(0xFF274233)

val TertiaryContainer      = Color(0xFFDDF3E2) // pour carte Target
val OnTertiaryContainer    = Color(0xFF1F3A2C)

val OutlineSoft            = Color(0xFFB5C9BD)

// Garde tes tokens existants pour éviter les "unresolved reference" ailleurs
object SamoTokens {
    val ShootHeader = Color(0xFF2F6FED)
    val MeleeHeader = Color(0xFFD05050)
    val ShootTint   = Color(0xFFE7EFFF)
    val MeleeTint   = Color(0xFFF9E7E7)
}
