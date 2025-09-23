package com.samohammer.app.data.repo

import android.content.Context
import com.samohammer.app.data.datastore.appStateDataStore
import com.samohammer.app.data.mapper.toDomain
import com.samohammer.app.data.mapper.toProto
import com.samohammer.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/**
 * Repository central pour l'état de l'application (units, target, etc.)
 * - Fournit un Flow<AppStateDomain> pour l'UI
 * - Offre des helpers pour modifier l'état
 */
class AppStateRepository(private val context: Context) {

    /** Flux d'état (Domain) toujours à jour. */
    val appStateFlow: Flow<AppStateDomain> =
        context.appStateDataStore.data.map { proto -> proto.toDomain() }

    /** Lecture snapshot (via Flow.first() en usage suspend). */
    suspend fun getSnapshot(): AppStateDomain {
        return context.appStateDataStore.data.map { it.toDomain() }.first()
    }

    /** Remplace tout l'état par un nouveau. */
    suspend fun save(state: AppStateDomain) {
        context.appStateDataStore.updateData { state.toProto() }
    }

    // -------------------------
    // Actions de haut niveau
    // -------------------------

    /** Ajoute une nouvelle unité. */
    suspend fun addUnit(unit: UnitEntry) {
        context.appStateDataStore.updateData { proto ->
            val domain = proto.toDomain()
            val newUnits = domain.units + unit
            domain.copy(units = newUnits).toProto()
        }
    }

    /** Supprime une unité par id. */
    suspend fun removeUnit(unitId: String) {
        context.appStateDataStore.updateData { proto ->
            val domain = proto.toDomain()
            val newUnits = domain.units.filterNot { it.id == unitId }
            domain.copy(units = newUnits).toProto()
        }
    }

    /** Met à jour une unité existante. */
    suspend fun updateUnit(updated: UnitEntry) {
        context.appStateDataStore.updateData { proto ->
            val domain = proto.toDomain()
            val newUnits = domain.units.map { if (it.id == updated.id) updated else it }
            domain.copy(units = newUnits).toProto()
        }
    }

    /** Met à jour la config cible (Target). */
    suspend fun updateTarget(newTarget: TargetConfig) {
        context.appStateDataStore.updateData { proto ->
            val domain = proto.toDomain()
            domain.copy(target = newTarget).toProto()
        }
    }

    /** Reset complet (valeur par défaut, compatible javalite). */
    suspend fun reset() {
        context.appStateDataStore.updateData {
            com.samohammer.app.proto.AppState.newBuilder()
                .setSchemaVersion(1)
                .build()
        }
    }
}
