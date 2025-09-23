package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samohammer.app.model.*
import com.samohammer.app.ui.AppStateViewModel
import com.samohammer.app.ui.AppStateViewModelFactory
import com.samohammer.app.ui.theme.SamoHammerTheme
import com.samohammer.app.util.newUuid

class MainActivity : ComponentActivity() {

    private val appStateVM: AppStateViewModel by viewModels {
        AppStateViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamoHammerTheme {
                val uiState by appStateVM.state.collectAsStateWithLifecycle()

                var units by remember { mutableStateOf(uiState.units) }
                var target by remember { mutableStateOf(uiState.target) }
                var selectedTab by remember { mutableStateOf(0) }

                // Sync VM → UI locale
                LaunchedEffect(uiState.units) { units = uiState.units }
                LaunchedEffect(uiState.target) { target = uiState.target }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("SamoHammer") }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Profiles") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Target") }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Simulation") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when (selectedTab) {
                            0 -> {
                                UnitListScreen(
                                    units = units,
                                    onUpdateUnits = { newUnits ->
                                        units = newUnits
                                        appStateVM.setUnits(newUnits) // persiste
                                    },
                                    onAddUnit = {
                                        val newUnit = UnitEntry(
                                            id = newUuid(),
                                            name = "New Unit",
                                            active = true,
                                            profiles = emptyList()
                                        )
                                        units = units + newUnit
                                        appStateVM.addUnit(newUnit) // persiste
                                    },
                                    onDeleteUnit = { unitId ->
                                        units = units.filterNot { it.id == unitId }
                                        appStateVM.removeUnit(unitId) // persiste
                                    }
                                )
                            }
                            1 -> {
                                TargetConfigEditor(
                                    target = target,
                                    onUpdate = { newTarget ->
                                        target = newTarget
                                        appStateVM.updateTarget(newTarget) // persiste
                                    }
                                )
                            }
                            2 -> {
                                SimulationScreen(
                                    units = units,
                                    target = target
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
