// V1.2.1 — Inputs plus compacts (labels courts + largeur 60.dp)

package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

// Lists
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed

// TextField options
import androidx.compose.foundation.text.KeyboardOptions

// Material3
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

// State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

// UI utils
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
    val active: Boolean = true
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
// Moteur
// -------------------------
private fun clamp2to6(x: Int) = x.coerceIn(2, 6)

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
    if (!p.active) return 0.0
    val attacks = max(p.models, 0) * max(p.attacks, 0)
    if (attacks == 0) return 0.0
    val ph = pHit(p.toHit, if (target.debuffHitEnabled) target.debuffHitValue else 0)
    val pw = pWound(p.toWound)
    val pu = pUnsaved(baseSave, p.rend)
    val ward = wardFactor(target.wardNeeded)
    return attacks * ph * pw * pu * p.damage * ward
}

private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double =
    units.filter { it.active }
        .flatMap { it.profiles }
        .sumOf { expectedDamageForProfile(it, target, baseSave) }

// NEW — EV pour une unité (somme sur ses profils actifs uniquement)
private fun expectedDamageForUnit(unit: UnitEntry, target: TargetConfig, baseSave: Int?): Double =
    unit.profiles.filter { it.active }.sumOf { expectedDamageForProfile(it, target, baseSave) }

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
                    name = "Unité 1",
                    profiles = listOf(
                        AttackProfile(
                            name = "Profil 1",
                            attackType = AttackType.MELEE,
                            models = 1,
                            attacks = 4,
                            toHit = 4,
                            toWound = 4,
                            rend = 1,
                            damage = 1
                        )
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
        Box(modifier = Modifier.padding(inner)) {
            when (selectedTab) {
                0 -> ProfilesTab(units = units, onUpdateUnits = { newUnits -> units = newUnits })
                1 -> TargetTab(target = target, onUpdate = { t -> target = t })
                2 -> SimulationTab(units = units, target = target)
            }
        }
    }
}

// -------------------------
// Onglet Profils
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
                var expanded by rememberSaveable(unitIndex) { mutableStateOf(true) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // LIGNE 1 : active + nom (largueur max) + chevron
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = unit.active,
                            onCheckedChange = { checked ->
                                onUpdateUnits(
                                    units.toMutableList().also { it[unitIndex] = unit.copy(active = checked) }
                                )
                            }
                        )
                        OutlinedTextField(
                            value = unit.name,
                            onValueChange = { newName ->
                                onUpdateUnits(
                                    units.toMutableList().also { it[unitIndex] = unit.copy(name = newName) }
                                )
                            },
                            label = { Text("Nom de l’unité") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "▼" else "▶")
                        }
                    }

                    // LIGNE 2 : actions (à droite)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onUpdateUnits(
                                    units.toMutableList().also { list ->
                                        val newProfiles =
                                            unit.profiles + AttackProfile(name = "Profil ${unit.profiles.size + 1}")
                                        list[unitIndex] = unit.copy(profiles = newProfiles)
                                    }
                                )
                            }
                        ) { Text("Ajouter profil") }

                        TextButton(
                            onClick = {
                                onUpdateUnits(units.toMutableList().also { it.removeAt(unitIndex) })
                            }
                        ) { Text("Supprimer unité") }
                    }

                    if (expanded) {
                        unit.profiles.forEachIndexed { pIndex, profile ->
                            ProfileEditor(
                                profile = profile,
                                onChange = { updated ->
                                    onUpdateUnits(
                                        units.toMutableList().also { list ->
                                            val newProfiles =
                                                unit.profiles.toMutableList().also { it[pIndex] = updated }
                                            list[unitIndex] = unit.copy(profiles = newProfiles)
                                        }
                                    )
                                },
                                onRemove = {
                                    onUpdateUnits(
                                        units.toMutableList().also { list ->
                                            val newProfiles = unit.profiles.toMutableList().also {
                                                if (it.size > 1) it.removeAt(pIndex)
                                            }
                                            list[unitIndex] = unit.copy(profiles = newProfiles)
                                        }
                                    )
                                }
                            )
                        }
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        var expanded by rememberSaveable(profile.hashCode()) { mutableStateOf(true) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LIGNE 1 : active + nom (max) + type + chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = profile.active,
                    onCheckedChange = { ok -> onChange(profile.copy(active = ok)) }
                )
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { newName -> onChange(profile.copy(name = newName)) },
                    label = { Text("Nom du profil") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val next = if (profile.attackType == AttackType.MELEE) AttackType.SHOOT else AttackType.MELEE
                        onChange(profile.copy(attackType = next))
                    }
                ) {
                    Text(text = if (profile.attackType == AttackType.MELEE) "Melee" else "Shoot")
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▼" else "▶")
                }
            }

            // LIGNE 2 : action (à droite)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRemove) { Text("Supprimer profil") }
            }

            if (expanded) {
                // Grille de champs (labels courts + largeur 60.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumberField(
                            label = "Models",
                            value = profile.models,
                            onValue = { v -> onChange(profile.copy(models = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(60.dp)
                        )
                        NumberField(
                            label = "Atk",
                            value = profile.attacks,
                            onValue = { v -> onChange(profile.copy(attacks = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(60.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GateField2to6(
                            label = "Hit",
                            value = profile.toHit,
                            onValue = { v -> onChange(profile.copy(toHit = v)) },
                            modifier = Modifier.width(60.dp)
                        )
                        GateField2to6(
                            label = "Wnd",
                            value = profile.toWound,
                            onValue = { v -> onChange(profile.copy(toWound = v)) },
                            modifier = Modifier.width(60.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumberField(
                            label = "Rend",
                            value = profile.rend,
                            onValue = { v -> onChange(profile.copy(rend = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(60.dp)
                        )
                        NumberField(
                            label = "Dmg",
                            value = profile.damage,
                            onValue = { v -> onChange(profile.copy(damage = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- Champs numériques ----------
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
            val digits = txt.filter { ch -> ch.isDigit() }
            onValue(if (digits.isEmpty()) 0 else digits.toInt())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun GateField2to6(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            val digits = newText.filter { ch -> ch.isDigit() }.take(1)
            text = digits
            val v = digits.toIntOrNull()
            if (v != null && v in 2..6) onValue(v)
        },
        label = { Text(label) },
        placeholder = { Text("2..6") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

// -------------------------
// Onglet Target
// -------------------------
@Composable
fun TargetTab(target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Buffs/Débuffs de la cible", style = MaterialTheme.typography.titleMedium)

        // Ward (0=off ou 2..6)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ward")
            var wardTxt by remember(target.wardNeeded) {
                mutableStateOf(if (target.wardNeeded in 2..6) target.wardNeeded.toString() else "")
            }
            OutlinedTextField(
                value = wardTxt,
                onValueChange = { newText ->
                    val digits = newText.filter { ch -> ch.isDigit() }.take(1)
                    wardTxt = digits
                    val v = digits.toIntOrNull()
                    onUpdate(target.copy(wardNeeded = if (v != null && v in 2..6) v else 0))
                },
                placeholder = { Text("off ou 2..6") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
            Text(text = if (target.wardNeeded in 2..6) "${target.wardNeeded}+" else "off")
        }

        // Debuff to hit (checkbox + valeur 0..3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = target.debuffHitEnabled,
                onCheckedChange = { enabled ->
                    onUpdate(target.copy(debuffHitEnabled = enabled, debuffHitValue = if (enabled) target.debuffHitValue else 0))
                }
            )
            Text("Debuff to hit")
            OutlinedTextField(
                value = target.debuffHitValue.toString(),
                onValueChange = { newText ->
                    val digits = newText.filter { ch -> ch.isDigit() }.take(1)
                    val v = digits.toIntOrNull() ?: 0
                    onUpdate(target.copy(debuffHitValue = v.coerceIn(0, 3)))
                },
                enabled = target.debuffHitEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(80.dp)
            )
            if (target.debuffHitEnabled) Text("−${target.debuffHitValue} à la touche")
        }

        Divider()

        Text(
            text = "Ward: " + (if (target.wardNeeded in 2..6) "${target.wardNeeded}+" else "off") +
                    " • Debuff hit: " + (if (target.debuffHitEnabled) "-${target.debuffHitValue}" else "off")
        )
    }
}

// -------------------------
// Onglet Simulations (par unité)
// -------------------------
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val activeUnits = units.filter { it.active }
    if (activeUnits.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Aucune unité active.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(activeUnits) { _, unit ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(unit.name, style = MaterialTheme.typography.titleMedium)

                    val saves = listOf(2, 3, 4, 5, 6, null)
                    saves.forEach { save ->
                        val label = if (save == null) "No Save" else "${save}+"
                        val dmg = expectedDamageForUnit(unit, target, save)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label)
                            Text(String.format("%.2f", dmg))
                        }
                        Divider()
                    }
                }
            }
        }
    }
}
