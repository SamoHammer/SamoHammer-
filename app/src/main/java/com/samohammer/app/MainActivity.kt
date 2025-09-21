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
    val rend: Int = 0,      // rend positif -> dégrade la sauvegarde
    val damage: Int = 1
)

data class UnitEntry(
    val name: String = "Nouvelle unité",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Profil 1"))
)

data class TargetConfig(
    val wardNeeded: Int = 0,          // 0 = off, sinon 2..6
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 1       // 0..3
)

// -------------------------
// Helpers
// -------------------------
private fun clamp2to6(x: Int) = x.coerceIn(2, 6)
private fun parseInt(raw: String, fallback: Int, clamp2to6: Boolean = false): Int {
    val v = raw.toIntOrNull() ?: return fallback
    return if (clamp2to6) clamp2to6(v) else v
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
    val eff = clamp2to6(needed + debuff)
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
// App à 3 onglets
// -------------------------
@Composable
fun SamoHammerApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Profils", "Target", "Simulations")

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
// Onglet Profils (édition complète)
// -------------------------
@Composable
fun ProfilesTab(units: List<UnitEntry>, onUpdateUnits: (List<UnitEntry>) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        itemsIndexed(units) { idx, unit ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {

                    // Ligne d’en-tête (actif + nom + ajout profil)
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
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth(0.0001f) // évite weight → on garde fillMaxWidth ailleurs
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val newProfiles = unit.profiles + AttackProfile(name = "Profil ${unit.profiles.size + 1}")
                            onUpdateUnits(units.toMutableList().also { it[idx] = unit.copy(profiles = newProfiles) })
                        }) { Text("Ajouter profil") }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Profils de l’unité
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
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // Nom + type
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { onChange(profile.copy(name = it)) },
                    label = { Text("Nom du profil") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Ligne 1 : models / attacks / to hit / to wound
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IntField("Models", profile.models) { onChange(profile.copy(models = parseInt(it, profile.models))) }
                IntField("Attacks", profile.attacks) { onChange(profile.copy(attacks = parseInt(it, profile.attacks))) }
                IntField("To Hit (2-6)", profile.toHit) { onChange(profile.copy(toHit = parseInt(it, profile.toHit, clamp2to6 = true))) }
                IntField("To Wound (2-6)", profile.toWound) { onChange(profile.copy(toWound = parseInt(it, profile.toWound, clamp2to6 = true))) }
            }

            Spacer(Modifier.height(8.dp))

            // Ligne 2 : rend / damage + supprimer
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IntField("Rend (+)", profile.rend) { onChange(profile.copy(rend = parseInt(it, profile.rend))) }
                IntField("Damage", profile.damage) { onChange(profile.copy(damage = parseInt(it, profile.damage))) }
                Spacer(Modifier.weight(1f, fill = true))
                TextButton(onClick = onDelete) { Text("Supprimer") }
            }
        }
    }
}

@Composable
private fun IntField(label: String, value: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.widthIn(min = 96.dp).fillMaxWidth()
    )
}

// -------------------------
// Onglet Target (éditable)
// -------------------------
@Composable
fun TargetTab(target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Buffs/Débuffs de la cible", style = MaterialTheme.typography.titleMedium)

        // Ward
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ward")
            var wardTxt by remember { mutableStateOf(if (target.wardNeeded in 2..6) target.wardNeeded.toString() else "") }
            OutlinedTextField(
                value = wardTxt,
                onValueChange = {
                    wardTxt = it
                    val v = it.toIntOrNull()
                    onUpdate(target.copy(wardNeeded = if (v != null && v in 2..6) v else 0))
                },
                placeholder = { Text("Off ou 2..6") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                onValueChange = {
                    val v = it.toIntOrNull()?.coerceIn(0, 3) ?: target.debuffHitValue
                    onUpdate(target.copy(debuffHitValue = v))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
