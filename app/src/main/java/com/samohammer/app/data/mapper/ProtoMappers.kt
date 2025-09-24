package com.samohammer.app.data.mapper

import com.samohammer.app.model.*
import com.samohammer.app.proto.AppState as AppStateProto
import com.samohammer.app.proto.AttackProfile as AttackProfileProto
import com.samohammer.app.proto.UnitEntry as UnitEntryProto
import com.samohammer.app.proto.TargetConfig as TargetConfigProto
import com.samohammer.app.proto.AttackType as AttackTypeProto
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
        attackType = this.attackType.toDomain(),
        models = this.models,
        attacks = this.attacks,
        toHit = this.toHit,
        toWound = this.toWound,
        rend = this.rend,
        damage = this.damage,
        active = this.active,
        twoHits = this.twoHits,
        autoW = this.autoW,
        mortal = this.mortal,
        aoa = if (this.hasAoa()) this.aoa else false // NEW: compat ancienne version
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
        .setAoa(aoa) // NEW
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
        wardNeeded = this.wardNeeded,
        debuffHitEnabled = this.debuffHitEnabled,
        debuffHitValue = this.debuffHitValue
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
fun AppStateProto.toDomain(): AppStateDomain =
    AppStateDomain(
        units = this.unitsList.map { it.toDomain() },
        target = if (this.hasTarget()) this.target.toDomain() else TargetConfig(),
        schemaVersion = if (this.schemaVersion == 0) 1 else this.schemaVersion
    )

fun AppStateDomain.toProto(): AppStateProto =
    AppStateProto.newBuilder()
        .addAllUnits(units.map { it.toProto() })
        .setTarget(target.toProto())
        .setSchemaVersion(if (schemaVersion <= 0) 1 else schemaVersion)
        .build()
