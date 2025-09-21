package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.samohammer.app.ui.theme.SamoHammerTheme
import kotlin.math.max

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
    val rend: Int = 0,      // rend positif -> dégrade la sauvegarde
    val damage: Int = 1
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
    val debuffHitValue: Int = 1       // 1..3
)

// -------------------------
// Helpers
// -------------------------
private fun clampGate(x: Int) = x.coerceIn(2, 6)
private fun parseInt(raw: String, fallback: Int, gate2to6: Boolean = false): Int {
    val v = raw.toIntOrNull() ?: return fallback
    return if (gate2to6) clampGate(v) else v
}

// -------------------------
// Moteur proba (V0.3 compact)
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

private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double =
    units.filter { it.active }.flatMap { it.profiles }.sumOf { expectedDamageForProfile(it, target, baseSave) }

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
// App à 3 onglets
// -------------------------
@Composable
fun SamoHammerApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Profils", "Target", "Simulations")

    // État global (persistant plus tard)
    var units by remember {
        mutableStateOf(
            listOf(
                UnitEntry(
                    name = "Ratlings",
                    profiles = listOf(
                        AttackProfile("Ratling Gun", AttackType.SHOOT, models = 6, attacks = 3, toHit = 4, toWound = 5, rend = 1, damage = 1),
                        AttackProfile("Rusty Knives", AttackType.MELEE, models = 6, attacks = 2, toHit = 4, toWound = 5, rend = 0, damage = 1)
                    )
                )
            )
        )
    }
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
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = {
                    units = units + UnitEntry(name = "Unité ${units.size + 1}")
                }) { Text("+") }
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
// Onglet Profils (édition)
// -------------------------
@Composable
fun ProfilesTab(units: List<UnitEntry>, onUpdateUnits: (List<UnitEntry>) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        itemsIndexed(units) { idx, unit ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    // Ligne d'entête unité
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = unit.active,
                            onCheckedChange = { checked ->
                                onUpdateUnits(units.toMutableList().also { it[idx] = unit.copy(active = checked) })
                            }
                        )
                        OutlinedTextField(
                            value = unit.name,
                            onValueChange = { new ->
                                onUpdateUnits(units.toMutableList().also { it[idx] = unit.copy(name = new) })
                            },
                            label = { Text("Nom de l’unité") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val newProfiles = unit.profiles + AttackProfile(name = "Profil ${unit.profiles.size + 1}")
                            onUpdateUnits(units.toMutableList().also { it[idx] = unit.copy(profiles = newProfiles) })
                        }) { Text("Ajouter profil") }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Profils
                    unit.profiles.forEachIndexed { pIdx, p ->
                        AttackProfileEditor(
                            profile = p,
                            onChange = { edited ->
                                val newList = unit.profiles.toMutableList().also { it[pIdx] = edited }
                                onUpdateUnits(units.toMutableList().also { it[idx] = unit.copy(profiles = newList) })
                            },
                            onDelete = {
                                val newList = unit.profiles.toMutableList().also { it.removeAt(pIdx) }
                                onUpdateUnits(units.toMutableList().also { it[idx] = unit.copy(profiles = newList) })
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttackProfileEditor(
    profile: AttackProfile,
    onChange: (AttackProfile) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { onChange(profile.copy(name = it)) },
                    label = { Text("Nom du profil") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                // Toggle Melee/Shoot
                val isMelee = profile.attackType == AttackType.MELEE
                AssistChip(
                    onClick = {
                        onChange(profile.copy(attackType = if (isMelee) AttackType.SHOOT else AttackType.MELEE))
                    },
                    label = { Text(if (isMelee) "MELEE" else "SHOOT") }
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Supprimer") }
            }

            Spacer(Modifier.height(8.dp))

            // Champs numériques
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Models", profile.models) { onChange(profile.copy(models = parseInt(it, profile.models))) }
                NumField("Attacks", profile.attacks) { onChange(profile.copy(attacks = parseInt(it, profile.attacks))) }
                NumField("To Hit (2-6)", profile.toHit) { onChange(profile.copy(toHit = parseInt(it, profile.toHit, gate2to6 = true))) }
                NumField("To Wound (2-6)", profile.toWound) { onChange(profile.copy(toWound = parseInt(it, profile.toWound, gate2to6 = true))) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Rend (+)", profile.rend) { onChange(profile.copy(rend = parseInt(it, profile.rend))) }
                NumField("Damage", profile.damage) { onChange(profile.copy(damage = parseInt(it, profile.damage))) }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

// -------------------------
// Onglet Target
// -------------------------
@Composable
fun TargetTab(target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Buffs/débuffs de la cible", style = MaterialTheme.typography.titleMedium)

        // Ward
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ward")
            val wardText = remember(target.wardNeeded) { if (target.wardNeeded in 2..6) target.wardNeeded.toString() else "" }
            OutlinedTextField(
                value = wardText,
                onValueChange = { raw ->
                    val v = raw.toIntOrNull()
                    onUpdate(target.copy(wardNeeded = if (v != null && v in 2..6) v else 0))
                },
                placeholder = { Text("Off ou 2..6") },
                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
            Text(if (target.wardNeeded in 2..6) "${target.wardNeeded}+" else "off")
        }

        // Debuff to hit
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = target.debuffHitEnabled,
                onCheckedChange = { onUpdate(target.copy(debuffHitEnabled = it)) }
            )
            Text("Debuff to hit")
            OutlinedTextField(
                value = target.debuffHitValue.toString(),
                onValueChange = { raw ->
                    val v = raw.toIntOrNull()?.coerceIn(1, 3) ?: target.debuffHitValue
                    onUpdate(target.copy(debuffHitValue = v))
                },
                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(80.dp)
            )
            if (target.debuffHitEnabled) Text("−${target.debuffHitValue} à la touche")
        }
    }
}

// -------------------------
// Onglet Simulations
// -------------------------
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Espérance de dégâts (toutes unités actives)", style = MaterialTheme.typography.titleMedium)
        listOf(2,3,4,5,6,null).forEach { save ->
            val label = if (save == null) "No Save" else "${save}+"
            val dmg = expectedDamageAll(units, target, save)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label)
                Text(String.format("%.2f", dmg))
            }
            Divider()
        }
    }
}
