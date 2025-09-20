package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
    val tabs = listOf("Attaques", "Défenseur")

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
        Column(Modifier.padding(inner).fillMaxSize()) {
            when (selectedTab) {
                0 -> AttaquesScreen()
                1 -> DefenseurScreen()
            }
        }
    }
}

@Composable
fun AttaquesScreen() {
    var attaques by remember { mutableStateOf("") }
    var touche by remember { mutableStateOf("") }
    var blesse by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("⚔️ Attaques", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = attaques,
            onValueChange = { attaques = it },
            label = { Text("Nombre d’attaques") },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = touche,
            onValueChange = { touche = it },
            label = { Text("Jet pour toucher (ex: 3+)") },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = blesse,
            onValueChange = { blesse = it },
            label = { Text("Jet pour blesser (ex: 4+)") },
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = { /* TODO: brancher moteur */ },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Calculer")
        }
    }
}

@Composable
fun DefenseurScreen() {
    var sauvegarde by remember { mutableStateOf("") }
    var rend by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("🛡️ Défenseur", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = sauvegarde,
            onValueChange = { sauvegarde = it },
            label = { Text("Jet de sauvegarde (ex: 5+)") },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = rend,
            onValueChange = { rend = it },
            label = { Text("Rend (ex: -1)") },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHome() {
    MaterialTheme {
        SamoHomeScreen()
    }
}
