package com.samohammer.app.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.samohammer.app.proto.AppState
import com.samohammer.app.proto.AttackProfile
import com.samohammer.app.proto.AttackType
import com.samohammer.app.proto.TargetConfig
import com.samohammer.app.proto.UnitEntry
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Serializer Proto pour l'état global de l'app.
 * - defaultValue : seed UX-friendly (1 unité + 1 profil), schema_version = 1
 * - readFrom/writeTo : IO + corruption-safe
 */
object AppStateSerializer : Serializer<AppState> {

    override val defaultValue: AppState
        get() {
            val defaultProfile = AttackProfile.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setName("Weapon Profile")
                .setAttackType(AttackType.MELEE)
                .setModels(1)      // Size
                .setAttacks(4)     // Atk
                .setToHit(4)       // Hit  (2..6)
                .setToWound(4)     // Wnd  (2..6)
                .setRend(1)        // Rend (>=0)
                .setDamage(1)      // Dmg
                .setActive(true)
                // flags critiques off par défaut
                .setTwoHits(false)
                .setAutoW(false)
                .setMortal(false)
                .build()

            val defaultUnit = UnitEntry.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setName("Unit 1")
                .setActive(true)
                .addProfiles(defaultProfile)
                .build()

            val defaultTarget = TargetConfig.newBuilder()
                .setWardNeeded(0)          // off
                .setDebuffHitEnabled(false)
                .setDebuffHitValue(0)
                .build()

            return AppState.newBuilder()
                .addUnits(defaultUnit)
                .setTarget(defaultTarget)
                .setSchemaVersion(1)
                .build()
        }

    override suspend fun readFrom(input: InputStream): AppState {
        try {
            return AppState.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read AppState proto.", e)
        }
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        t.writeTo(output)
    }
}
