package com.samohammer.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.samohammer.app.proto.AppState

/**
 * Extension property sur Context pour fournir un DataStore<AppState> unique (Proto).
 * Utiliser applicationContext.appStateDataStore pour éviter toute fuite d'Activity.
 */
val Context.appStateDataStore: DataStore<AppState> by dataStore(
    fileName = "app_state.pb",
    serializer = AppStateSerializer
)
