package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.samohammer.app.ui.theme.SamoHammerTheme
import kotlin.math.max

// -------------------------
// Activity
// -------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamoHammerTheme {
                SamoHammerApp()
            }
        }
    }
}

// -------------------------
// Modèles
// -------------------------
enum class AttackType { MELEE, SHOOT }

data class AttackProfile(
    val name: String = "Profil",
    val attackType: AttackType = AttackType.MELEE,
    val models: Int = 1,
    val attacks: Int = 1,
    val toHit: Int = 4,     // 2..6
    val toWound: Int = 4,   // 2..6
    val rend: Int = 0,      // >= 0
    val damage: Int = 1,
    val expanded: Boolean = true
)

data class UnitEntry(
    val name: String = "Nouvelle unité",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Profil 1")),
    val expanded: Boolean = true
)

data class TargetConfig(
    val wardNeeded: Int = 0,          // 0 = off, sinon 2..6
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 1       // 0..3
)

// -------------------------
// Helpers
// -------------------------
private fun clampGate(x: Int) = x.coerceIn(2, 6)
private fun asIntOr(old: Int, s: String, gate: Boolean = false): Int {
    val v = s.toIntOrNull() ?: return old
    return if (gate) clampGate(v) else v
}

// -------------------------
// Moteur
// -------------------------
private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}

private fun pHit(needed: Int, debuff: Int): Double {
    val eff = clampGate(needed + debuff)
    return pGate(eff)
}
private fun pWound(needed: Int): Double = pGate(needed)
private fun pUnsaved(baseSave: Int?, rend: Int): Double {
    if (baseSave == null) return 1.0 // no save
    val eff = baseSave + rend
    if (eff >= 7) return 1.0
    return 1.0 - pGate(eff)
}
private fun wardFactor(wardNeeded: Int): Double {
    if (wardNeeded !in 2..6) return 1.0
    return 1.0 - pGate(wardNeeded)
}
private fun expectedDamageForProfile(p: AttackProfile, target: TargetConfig, baseSave: Int?): Double {
    val attacks = max(p.models, 0) * max(p.attacks, 0)
    if (attacks == 0) return 0.0

    val ph = pHit(p.toHit, if (target.debuffHitEnabled) target.debuffHitValue else 0)
    val pw = pWound(p.toWound)
    val pu = pUnsaved(baseSave, p.rend)
    val ward = wardFactor(target.wardNeeded)

    return attacks * ph * pw * pu * p.damage * ward
}
private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double {
    var sum = 0.0
    for (u in units) {
        if (!u.active) continue
        for (p in u.profiles) sum += expectedDamageForProfile(p, target, baseSave)
    }
    return sum
}

// -------------------------
// App à 3 onglets
// -------------------------
@Composable
fun SamoHammerApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Profils", "Target", "Simulations")

    var units by remember { mutableStateOf(listOf(UnitEntry(name = "Unité 1"))) }
    var target by remember { mutableStateOf(TargetConfig()) }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (selectedTab) {
                0 -> ProfilesTab(units = units, onUpdateUnits = { units = it })
                1 -> TargetTab(target = target, onUpdate = { target = it })
                2 -> SimulationTab(units = units, target = target)
            }
        }
    }
}

// -------------------------
// Onglet Profils (nouvelle version)
// -------------------------
@Composable
fun ProfilesTab(units: List<UnitEntry>, onUpdateUnits: (List<UnitEntry>) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(units) { unitIndex, unit ->
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // En-tête unité
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = unit.active,
                            onCheckedChange = { checked ->
                                onUpdateUnits(units.toMutableList().also {
                                    it[unitIndex] = unit.copy(active = checked)
                                })
                            }
                        )
                        OutlinedTextField(
                            value = unit.name,
                            onValueChange = { newName ->
                                onUpdateUnits(units.toMutableList().also {
                                    it[unitIndex] = unit.copy(name = newName)
                                })
                            },
                            label = { Text("Nom de l’unité") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                onUpdateUnits(units.toMutableList().also { list ->
                                    val newProfiles = unit.profiles + AttackProfile(name = "Profil ${unit.profiles.size + 1}")
                                    list[unitIndex] = unit.copy(profiles = newProfiles)
                                })
                            }
                        ) { Text("Ajouter profil") }
                    }

                    // Profils
                    unit.profiles.forEachIndexed { pIndex, profile ->
                        ProfileEditor(
                            profile = profile,
                            onChange = { updated ->
                                onUpdateUnits(units.toMutableList().also { list ->
                                    val newProfiles = unit.profiles.toMutableList().also { it[pIndex] = updated }
                                    list[unitIndex] = unit.copy(profiles = newProfiles)
                                })
                            },
                            onRemove = {
                                onUpdateUnits(units.toMutableList().also { list ->
                                    val newProfiles = unit.profiles.toMutableList().also {
                                        if (it.size > 1) it.removeAt(pIndex)
                                    }
                                    list[unitIndex] = unit.copy(profiles = newProfiles)
                                })
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: AttackProfile,
    onChange: (AttackProfile) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Ligne titre + type + supprimer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { onChange(profile.copy(name = it)) },
                    label = { Text("Nom du profil") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = {
                        val next = if (profile.attackType == AttackType.MELEE) AttackType.SHOOT else AttackType.MELEE
                        onChange(profile.copy(attackType = next))
                    },
                    label = { Text(if (profile.attackType == AttackType.MELEE) "Melee" else "Shoot") }
                )

                TextButton(onClick = onRemove) { Text("Supprimer") }
            }

            // Grille 2 colonnes
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberField("Models", profile.models, { onChange(profile.copy(models = it.coerceAtLeast(0))) }, Modifier.width(120.dp))
                    NumberField("Attacks", profile.attacks, { onChange(profile.copy(attacks = it.coerceAtLeast(0))) }, Modifier.width(120.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberField("Hit (2..6)", profile.toHit, { onChange(profile.copy(toHit = it.coerceIn(2, 6))) }, Modifier.width(120.dp))
                    NumberField("Wound (2..6)", profile.toWound, { onChange(profile.copy(toWound = it.coerceIn(2, 6))) }, Modifier.width(120.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberField("Rend (+)", profile.rend, { onChange(profile.copy(rend = it.coerceAtLeast(0))) }, Modifier.width(120.dp))
                    NumberField("Damage", profile.damage, { onChange(profile.copy(damage = it.coerceAtLeast(0))) }, Modifier.width(120.dp))
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { txt ->
            val v = txt.filter { it.isDigit() }
            onValue(if (v.isEmpty()) 0 else v.toInt())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

// -------------------------
// Onglet Target (inchangé sauf petite démo d’input)
// -------------------------
@Composable
fun TargetTab(target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ward : ${if (target.wardNeeded in 2..6) target.wardNeeded.toString() + "+" else "off"}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Debuff Hit : ", modifier = Modifier.padding(end = 8.dp))
            OutlinedTextField(
                value = if (target.debuffHitEnabled) "-${target.debuffHitValue}" else "off",
                onValueChange = { /* wiring plus tard */ },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

// -------------------------
// Onglet Simulations (inchangé)
// -------------------------
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Espérance de dégâts (par sauvegarde) :")
        for (save in listOf(2,3,4,5,6,null)) {
            val label = if (save == null) "No Save" else "${save}+"
            val dmg = expectedDamageAll(units, target, save)
            Text("$label → ${"%.2f".format(dmg)}")
        }
    }
}
