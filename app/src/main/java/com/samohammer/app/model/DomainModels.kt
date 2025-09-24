package com.samohammer.app.model

/**
 * Modèles "domain" utilisés par l'app + l'UI.
 * On garde la même structure fonctionnelle que MainActivity,
 * mais on ajoute des IDs stables pour lier la persistance.
 */

enum class AttackType {
    MELEE, SHOOT
}

data class AttackProfile(
    val id: String,
    val name: String = "Weapon Profile",
    val attackType: AttackType = AttackType.MELEE,
    val models: Int = 1,     // Size
    val attacks: Int = 1,    // Atk
    val toHit: Int = 4,      // Hit  (2..6)
    val toWound: Int = 4,    // Wnd  (2..6)
    val rend: Int = 0,       // Rend (>=0)
    val damage: Int = 1,     // Dmg
    val active: Boolean = true,

    // Flags critiques (déclenchés sur 6 naturel)
    val twoHits: Boolean = false,
    val autoW: Boolean = false,
    val mortal: Boolean = false,
    val aoa: Boolean = false   // NEW: All-out Attack (+1 to Hit)
)

data class UnitEntry(
    val id: String,
    val name: String = "Unit",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf()
)

data class TargetConfig(
    val wardNeeded: Int = 0,          // 0 = off, sinon 2..6
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 0       // 0..3
)

/** État global persistant. */
data class AppStateDomain(
    val units: List<UnitEntry> = emptyList(),
    val target: TargetConfig = TargetConfig(),
    val schemaVersion: Int = 1
)
