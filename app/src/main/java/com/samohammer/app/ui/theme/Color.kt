package com.samohammer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette claire — contraste renforcé
 * - Fond général clair
 * - Cartes hiérarchisées en nuances de gris (Unit un peu plus foncé que Weapon)
 * - Target légèrement teinté vert très pâle pour ressortir sans crier
 * - Actions (boutons, switches ON, FAB) conservent le vert
 *
 * NOTE: Les noms sont inchangés pour rester 100% compatibles avec ton Theme.kt actuel.
 */

// 🌑 (utilisé seulement en mode sombre par Theme.kt)
val DarkBackground = Color(0xFF0A0F0A)

// 🟩 Actions / Accents (conservés)
val AccentGreen   = Color(0xFF43A047) // boutons primaires, switch ON, FAB
val DividerGreen  = Color(0xFFE0E0E0) // lignes/outline (gris clair en thème clair)

// ✍️ Texte (thème clair)
val TextPrimary   = Color(0xFF111111) // texte principal
val TextSecondary = Color(0xFF616161) // labels/hints

// 🧱 Surfaces (thème clair)
val UnitCard      = Color(0xFFE9ECEA) // bloc Unit : gris très clair, un cran plus foncé
val WeaponCard    = Color(0xFFF4F6F5) // bloc Weapon Profile : gris encore plus clair
val TargetCard    = Color(0xFFEEF7F0) // bloc Target : gris/vert très pâle, lisible et distinct

// ---------- (anciens alias non utilisés dans Theme.kt actuel, laissés pour compat éventuelle) ----------
val Purple80      = Color(0xFFD0BCFF)
val PurpleGrey80  = Color(0xFFCCC2DC)
val Pink80        = Color(0xFFEFB8C8)
val Purple40      = Color(0xFF6650A4)
val PurpleGrey40  = Color(0xFF625B71)
val Pink40        = Color(0xFF7D5260)
