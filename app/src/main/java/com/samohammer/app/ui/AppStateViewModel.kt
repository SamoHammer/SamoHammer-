package com.samohammer.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samohammer.app.data.repo.AppStateRepository
import com.samohammer.app.model.AppStateDomain
import com.samohammer.app.model.TargetConfig
import com.samohammer.app.model.UnitEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel qui expose l'état persistant (DataStore Proto via Repository)
 * et relaye les actions de haut niveau vers le repository.
 */
class AppStateViewModel(
    private val repo: AppStateRepository
) : ViewModel() {

    /**
     * StateFlow utilisable directement dans Compose via collectAsState()/collectAsStateWithLifecycle().
     */
    val state: StateFlow<AppStateDomain> = repo.appStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppStateDomain(
            units = emptyList(),
            target = TargetConfig(
                wardNeeded = 0,          // 0 = pas de ward
                debuffHitEnabled = false,
                debuffHitValue = 0
            ),
            schemaVersion = 1
        )
    )

    // -------- Actions (délèguent au Repository) --------

    fun addUnit(unit: UnitEntry) {
        viewModelScope.launch { repo.addUnit(unit) }
    }

    fun removeUnit(unitId: String) {
        viewModelScope.launch { repo.removeUnit(unitId) }
    }

    fun updateUnit(updated: UnitEntry) {
        viewModelScope.launch { repo.updateUnit(updated) }
    }

    fun updateTarget(newTarget: TargetConfig) {
        viewModelScope.launch { repo.updateTarget(newTarget) }
    }

    fun reset() {
        viewModelScope.launch { repo.reset() }
    }

    /**
     * ⚠️ Méthode utilitaire pour les écrans qui manipulent une liste complète
     * (ex: onUpdateUnits(...) dans MainActivity). On persiste toute la liste.
     */
    fun setUnits(newUnits: List<UnitEntry>) {
        viewModelScope.launch {
            val current = state.value
            repo.save(current.copy(units = newUnits))
        }
    }
}
