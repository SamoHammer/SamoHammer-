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
     * StateFlow utilisable directement dans Compose via collectAsStateWithLifecycle().
     * On démarre avec une valeur par défaut cohérente, le repo émettra la vraie valeur dès qu'elle est lue.
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
}
