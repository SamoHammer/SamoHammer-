package com.samohammer.app.data.mapper

import com.samohammer.app.model.*
import com.samohammer.app.proto.*
import com.samohammer.app.util.newUuid

/**
 * Mappers Proto ↔ Domain.
 * - Tolérants si des IDs sont absents (créent un UUID).
 * - Enum mapping explicite.
 */

// -----------------------
// Enums
// -----------------------
private fun AttackTypeProto.toDomain(): AttackType = when (this) {
    AttackTypeProto.MELEE -> AttackType.MELEE
    AttackTypeProto.SHOOT -> AttackType.SHOOT
    else -> AttackType.MELEE
}

private fun AttackType.toProto(): AttackTypeProto = when (this) {
    AttackType.MELEE -> AttackTypeProto.MELEE
    AttackType.SHOOT -> AttackTypeProto.SHOOT
}

// -----------------------
// Profiles
// -----------------------
private fun AttackProfileProto.toDomain(): AttackProfile =
    AttackProfile(
        id = if (this.id.isNullOrBlank()) newUuid() else this.id,
        name = this.name,
        attackType = this.attack_type.toDomain(),
        models = this.models,
        attacks = this.attacks,
        toHit = this.to_hit,
        toWound = this.to_wound,
        rend = this.rend,
        damage = this.damage,
        active = this.active,
        twoHits = this.two_hits,
        autoW = this.auto_w,
        mortal = this.mortal
    )

private fun AttackProfile.toProto(): AttackProfileProto =
    AttackProfileProto.newBuilder()
        .setId(if (id.isBlank()) newUuid() else id)
        .setName(name)
        .setAttackType(attackType.toProto())
        .setModels(models)
        .setAttacks(attacks)
        .setToHit(toHit)
        .setToWound(toWound)
        .setRend(rend)
        .setDamage(damage)
        .setActive(active)
        .setTwoHits(twoHits)
        .setAutoW(autoW)
        .setMortal(mortal)
        .build()

// -----------------------
// Units
// -----------------------
private fun UnitEntryProto.toDomain(): UnitEntry =
    UnitEntry(
        id = if (this.id.isNullOrBlank()) newUuid() else this.id,
        name = this.name,
        active = this.active,
        profiles = this.profilesList.map { it.toDomain() }
    )

private fun UnitEntry.toProto(): UnitEntryProto =
    UnitEntryProto.newBuilder()
        .setId(if (id.isBlank()) newUuid() else id)
        .setName(name)
        .setActive(active)
        .addAllProfiles(profiles.map { it.toProto() })
        .build()

// -----------------------
// Target
// -----------------------
private fun TargetConfigProto.toDomain(): TargetConfig =
    TargetConfig(
        wardNeeded = this.ward_needed,
        debuffHitEnabled = this.debuff_hit_enabled,
        debuffHitValue = this.debuff_hit_value
    )

private fun TargetConfig.toProto(): TargetConfigProto =
    TargetConfigProto.newBuilder()
        .setWardNeeded(wardNeeded)
        .setDebuffHitEnabled(debuffHitEnabled)
        .setDebuffHitValue(debuffHitValue)
        .build()

// -----------------------
// AppState
// -----------------------
fun AppState.toDomain(): AppStateDomain =
    AppStateDomain(
        units = this.unitsList.map { it.toDomain() },
        target = if (this.hasTarget()) this.target.toDomain() else TargetConfig(),
        schemaVersion = if (this.schemaVersion == 0) 1 else this.schemaVersion
    )

fun AppStateDomain.toProto(): AppState =
    AppState.newBuilder()
        .addAllUnits(units.map { it.toProto() })
        .setTarget(target.toProto())
        .setSchemaVersion(if (schemaVersion <= 0) 1 else schemaVersion)
        .build()
