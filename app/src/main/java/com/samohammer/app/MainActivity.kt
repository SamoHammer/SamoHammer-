package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // V0 : on branchera SamoHammerTheme (V0.2.6) plus tard
            MaterialTheme {
                SamoHomeScreen()
            }
        }
    }
}

@Composable
fun SamoHomeScreen() {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("SamoHammer") }) }
    ) { inner ->
        Text("Hello SamoHammer 👋", modifier = Modifier.padding(inner))
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHome() {
    MaterialTheme { SamoHomeScreen() }
}
