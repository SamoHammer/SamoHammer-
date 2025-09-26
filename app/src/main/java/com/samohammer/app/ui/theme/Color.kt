package com.samohammer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette SamoHammer — Vert contrasté
 *
 * Règles :
 * - Texte principal en gris clair sur fonds sombres -> lisibilité ++
 * - Cartes Unité / Profil / Target : nuances de vert différentes pour la hiérarchie
 * - Vert accent uniquement pour les actions (switch ON, FAB, boutons primaires)
 *
 * NB: Les noms ci-dessous sont ceux attendus par Theme.kt — ne pas renommer.
 */

// ---------- Accents / Brand ----------
val GreenPrimary       = Color(0xFF2E7D32) // vert principal (boutons, app bar dark)
val GreenPrimaryLight  = Color(0xFF66BB6A) // variante claire (switch ON, accents)
val GreenSecondary     = Color(0xFF1B5E20) // secondaire (hover/états, tags)
val GreenSecondaryLight= Color(0xFF43A047) // secondaire clair (icônes actives)

// ---------- Surfaces (fonds et cartes) ----------
val SurfaceRootDark    = Color(0xFF0F1512) // fond global très sombre (écran)
val SurfaceCardUnit    = Color(0xFF14211B) // carte Unité (plus sombre)
val SurfaceCardProfile = Color(0xFF1A2B22) // carte Weapon Profile (un cran plus clair)
val SurfaceCardTarget  = Color(0xFF20352A) // carte Target (encore un cran plus clair pour la mettre en avant)

val SurfaceVariant     = SurfaceCardProfile // utilisé par Theme.kt (profils)
val TertiaryContainer  = SurfaceCardTarget  // utilisé par Theme.kt (target, containers)

// ---------- Texte / Icônes ----------
val OnSurfacePrimary   = Color(0xFFE6EAE7) // texte principal sur fonds sombres
val OnSurfaceSecondary = Color(0xFFBFD7C8) // texte secondaire (labels, hints)
val OnSurfaceMuted     = Color(0xFF8BA798) // informations faibles

val OnSurfaceVariant   = OnSurfaceSecondary // utilisé par Theme.kt
val OnTertiaryContainer= OnSurfacePrimary   // texte sur TertiaryContainer

// ---------- Bordures / Dividers ----------
val OutlineSoft        = Color(0xFF325043) // bordures discrètes sur cartes

// ---------- Optionnels (si besoin plus tard) ----------
val ErrorRed           = Color(0xFFEF5350)
val WarningAmber       = Color(0xFFFFB300)
val InfoBlue           = Color(0xFF64B5F6)
val SuccessGreen       = GreenPrimaryLight

/**
 * Helpers (si tu veux accéder facilement à des couleurs de texte cohérentes).
 * Non utilisés par Theme.kt directement, mais utiles côté UI si besoin.
 */
val TextPrimary   = OnSurfacePrimary
val TextSecondary = OnSurfaceSecondary
val TextMuted     = OnSurfaceMuted

/**
 * Reco d’usage (non bloquant) :
 * - Fond écran: SurfaceRootDark
 * - Carte Unité: SurfaceCardUnit
 * - Carte Profil: SurfaceCardProfile
 * - Carte Target: SurfaceCardTarget
 * - Bouton primaire / FAB / switch ON: GreenPrimaryLight
 * - Bordures cartes: OutlineSoft
 * - Textes principaux: TextPrimary, secondaires: TextSecondary
 */
