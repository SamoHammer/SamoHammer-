package com.samohammer.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.samohammer.app.data.repo.AppStateRepository

/**
 * Factory simple pour fournir AppStateViewModel avec un AppStateRepository
 * basé sur applicationContext (évite les fuites d'Activity).
 */
class AppStateViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppStateViewModel::class.java)) {
            val repo = AppStateRepository(appContext.applicationContext)
            return AppStateViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
