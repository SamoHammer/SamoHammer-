package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SamoHomeScreen()
            }
        }
    }
}

@Composable
fun SamoHomeScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Screen 1", "Screen 2")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SamoHammer") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label) },
                        icon = {}
                    )
                }
            }
        }
    ) { inner ->
        when (selectedTab) {
            0 -> ScreenOne()
            1 -> ScreenTwo()
        }
    }
}

@Composable
fun ScreenOne() {
    Text("📊 Écran 1 - Probabilités")
}

@Composable
fun ScreenTwo() {
    Text("⚙️ Écran 2 - Paramètres")
}

@Preview(showBackground = true)
@Composable
fun PreviewHome() {
    MaterialTheme {
        SamoHomeScreen()
    }
}
