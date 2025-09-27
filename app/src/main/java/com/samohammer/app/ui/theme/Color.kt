package com.samohammer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette SamoHammer — Vert contrasté (3 niveaux nets)
 * IMPORTANT: les noms restent identiques pour ne rien casser côté Theme.kt.
 *
 * Niveaux:
 * - SurfaceRootDark: fond global quasi noir vert
 * - SurfaceCardUnit: bloc "Unit" (le plus sombre)
 * - SurfaceCardProfile: bloc "Weapon Profile" (plus clair)
 * - SurfaceCardTarget: bloc "Target" (le plus clair des trois)
 */

// ---------- Accents / Brand ----------
val GreenPrimary        = Color(0xFF2E7D32) // vert foncé (barres, éléments persistants)
val GreenPrimaryLight   = Color(0xFF43A047) // accent plus vif (switch ON, FAB, boutons primaires)
val GreenSecondary      = Color(0xFF1B5E20)
val GreenSecondaryLight = Color(0xFF66BB6A)

// ---------- Surfaces (fonds et cartes) ----------
val SurfaceRootDark     = Color(0xFF0A0F0A) // fond global (écran)
val SurfaceCardUnit     = Color(0xFF122112) // Unit : sombre désaturé
val SurfaceCardProfile  = Color(0xFF1C331C) // Weapon Profile : plus clair et plus saturé
val SurfaceCardTarget   = Color(0xFF274427) // Target : le plus clair → se détache bien

// Compat avec Theme.kt (ne pas renommer)
val SurfaceVariant      = SurfaceCardProfile
val TertiaryContainer   = SurfaceCardTarget

// ---------- Texte / Icônes ----------
val OnSurfacePrimary    = Color(0xFFE6FFE6) // texte principal très lisible
val OnSurfaceSecondary  = Color(0xFFA0C0A0) // labels, hints
val OnSurfaceMuted      = Color(0xFF8BA798) // infos faibles

// Compat Theme.kt
val OnSurfaceVariant    = OnSurfaceSecondary
val OnTertiaryContainer = OnSurfacePrimary

// ---------- Bordures / Dividers ----------
val OutlineSoft         = Color(0xFF2E4A33) // bordures discrètes mais visibles

// ---------- Optionnels (si utilisés ailleurs) ----------
val ErrorRed            = Color(0xFFEF5350)
val WarningAmber        = Color(0xFFFFB300)
val InfoBlue            = Color(0xFF64B5F6)
val SuccessGreen        = GreenPrimaryLight

// Aliases texte (si utilisés côté UI)
val TextPrimary         = OnSurfacePrimary
val TextSecondary       = OnSurfaceSecondary
val TextMuted           = OnSurfaceMuted

// Anciennes démos (laissées pour compat potentielle, non utilisées par Theme.kt)
val Purple80            = Color(0xFFD0BCFF)
val PurpleGrey80        = Color(0xFFCCC2DC)
val Pink80              = Color(0xFFEFB8C8)
val Purple40            = Color(0xFF6650A4)
val PurpleGrey40        = Color(0xFF625B71)
val Pink40              = Color(0xFF7D5260)
