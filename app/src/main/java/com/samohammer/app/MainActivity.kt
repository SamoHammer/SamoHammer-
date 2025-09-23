// V1.2.8 — 3 cases à cocher à côté des attributs (2Hits / AutoW / Mortal)
// - Conserve V1.2.7: labels au-dessus, champs 50dp, 2 lignes (3+3)
// - Ajoute dans ProfileEditor une colonne de 3 checkboxes à droite des champs
// - Moteur EV inchangé (les flags ne sont pas encore pris en compte dans le calcul)
package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Layout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions

// Material3
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val name: String = "Weapon Profile",
    val attackType: AttackType = AttackType.MELEE,
    val models: Int = 1,     // "Size"
    val attacks: Int = 1,    // "Atk"
    val toHit: Int = 4,      // "Hit"  2..6
    val toWound: Int = 4,    // "Wnd"  2..6
    val rend: Int = 0,       // "Rend" >= 0
    val damage: Int = 1,     // "Dmg"
    val active: Boolean = true,
    // Nouveaux flags UI (non utilisés par le moteur pour l'instant)
    val twoHits: Boolean = false,  // 2Hits
    val autoW: Boolean = false,    // AutoW
    val mortal: Boolean = false    // Mortal
)

data class UnitEntry(
    val name: String = "Unit",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Weapon Profile"))
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

private fun expectedDamageForUnit(u: UnitEntry, target: TargetConfig, baseSave: Int?): Double {
    if (!u.active) return 0.0
    return u.profiles.sumOf { expectedDamageForProfile(it, target, baseSave) }
}

private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double =
    units.filter { it.active }.sumOf { expectedDamageForUnit(it, target, baseSave) }

// -------------------------
// App à 3 onglets
// -------------------------
@Composable
fun SamoHammerApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Profils", "Target", "Simulations")

    var units by remember {
        mutableStateOf(listOf(UnitEntry(name = "Unit 1")))
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
                    units = units + UnitEntry(name = "Unit ${units.size + 1}")
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
                    // LIGNE 1 : active + nom + chevron
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
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "▼" else "▶")
                        }
                    }

                    if (expanded) {
                        unit.profiles.forEachIndexed { pIndex, profile ->
                            ProfileEditor(
                                profile = profile,
                                onChange = { updated ->
                                    onUpdateUnits(units.toMutableList().also { list ->
                                        val newProfiles = unit.profiles.toMutableList().also {
                                            it[pIndex] = updated
                                        }
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
}

@Composable
private fun TopLabeled(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        content()
    }
}

@Composable
private fun LabeledCheckbox(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text)
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
            // Ligne du header
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
                    label = { Text("Weapon Profile") },
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

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Colonne gauche: 2 lignes de 3 champs
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeled("Size") {
                                NumberField(profile.models) { v -> onChange(profile.copy(models = v)) }
                            }
                            TopLabeled("Atk") {
                                NumberField(profile.attacks) { v -> onChange(profile.copy(attacks = v)) }
                            }
                            TopLabeled("Hit") {
                                GateField2to6(profile.toHit) { v -> onChange(profile.copy(toHit = v)) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeled("Wnd") {
                                GateField2to6(profile.toWound) { v -> onChange(profile.copy(toWound = v)) }
                            }
                            TopLabeled("Rend") {
                                NumberField(profile.rend) { v -> onChange(profile.copy(rend = v)) }
                            }
                            TopLabeled("Dmg") {
                                NumberField(profile.damage) { v -> onChange(profile.copy(damage = v)) }
                            }
                        }
                    }
                    // Colonne droite: 3 cases cochables
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
                        LabeledCheckbox("2Hits", profile.twoHits) { onChange(profile.copy(twoHits = it)) }
                        LabeledCheckbox("AutoW", profile.autoW) { onChange(profile.copy(autoW = it)) }
                        LabeledCheckbox("Mortal", profile.mortal) { onChange(profile.copy(mortal = it)) }
                    }
                }
            }
        }
    }
}

// ---------- Champs numériques ----------
@Composable
private fun NumberField(value: Int, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { txt ->
            val digits = txt.filter { ch -> ch.isDigit() }
            onValue(if (digits.isEmpty()) 0 else digits.toInt())
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(57.dp).height(50.dp)
    )
}

@Composable
private fun GateField2to6(value: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            val digits = newText.filter { ch -> ch.isDigit() }.take(1)
            text = digits
            val v = digits.toIntOrNull()
            if (v != null && v in 2..6) onValue(v)
        },
        placeholder = { Text("2..6") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(57.dp).height(50.dp)
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
        Text("Target buffs/debuffs", style = MaterialTheme.typography.titleMedium)

        // Ward
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
                placeholder = { Text("off or 2..6") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(100.dp).height(50.dp)
            )
            Text(text = if (target.wardNeeded in 2..6) "${target.wardNeeded}+" else "off")
        }

        // Debuff to hit
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
                modifier = Modifier.width(80.dp).height(50.dp)
            )
            if (target.debuffHitEnabled) Text("−${target.debuffHitValue} to Hit")
        }

        Divider()
    }
}

// -------------------------
// Onglet Simulations
// -------------------------
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val activeUnits = units.filter { it.active }.take(6)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Damage expectation by Save (per active unit, max 6)", style = MaterialTheme.typography.titleMedium)

        if (activeUnits.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No active unit.")
            return@Column
        }

        // Header row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save", modifier = Modifier.width(70.dp))
            activeUnits.forEach { u -> Text(u.name, modifier = Modifier.weight(1f), maxLines = 1) }
        }
        Divider()

        val saves = listOf<Int?>(2, 3, 4, 5, 6, null)
        saves.forEach { save ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val label = if (save == null) "No Save" else "${save}+"
                Text(label, modifier = Modifier.width(70.dp))
                activeUnits.forEach { u ->
                    val dmg = expectedDamageForUnit(u, target, save)
                    Text(String.format("%.2f", dmg), modifier = Modifier.weight(1f))
                }
            }
            Divider()
        }
    }
}
