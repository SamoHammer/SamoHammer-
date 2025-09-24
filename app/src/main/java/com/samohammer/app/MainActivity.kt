// V2.1.1 — Commit-on-blur pour les champs texte (Unit name, Profile name)
// - Les noms s’éditent en local et NE sont persistés que quand le champ perd le focus.
// - Chiffres & checkboxes restent en écriture immédiate.
// - AoA (V2.1.0) toujours persisté + appliqué au moteur. UI inchangée visuellement.

package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.samohammer.app.ui.theme.SamoHammerTheme
import kotlin.math.max
import com.samohammer.app.ui.AppStateViewModel
import com.samohammer.app.ui.AppStateViewModelFactory

class MainActivity : ComponentActivity() {
    private val appStateVM: AppStateViewModel by viewModels {
        AppStateViewModelFactory(applicationContext)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamoHammerTheme {
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Profils", "Target", "Simulations")

                var units by remember { mutableStateOf(listOf(UnitEntry(name = "Unit 1"))) }
                var target by remember { mutableStateOf(TargetConfig()) }

                val persisted by appStateVM.state.collectAsState()
                LaunchedEffect(persisted.units) { units = persisted.units.map { it.toUi() } }
                LaunchedEffect(persisted.target) { target = persisted.target.toUi() }

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
                                val newUnits = units + UnitEntry(name = "Unit ${units.size + 1}")
                                units = newUnits
                                appStateVM.setUnits(newUnits.map { it.toDomain() })
                            }) { Text("+") }
                        }
                    }
                ) { inner ->
                    Box(Modifier.padding(inner)) {
                        when (selectedTab) {
                            0 -> ProfilesTab(units) { newUnits ->
                                units = newUnits
                                appStateVM.setUnits(newUnits.map { it.toDomain() })
                            }
                            1 -> TargetTab(target) { t ->
                                target = t
                                appStateVM.updateTarget(t.toDomain())
                            }
                            2 -> SimulationTab(units, target)
                        }
                    }
                }
            }
        }
    }
}

// ===== UI models (UI inchangée) =====
enum class AttackType { MELEE, SHOOT }

data class AttackProfile(
    val name: String = "Weapon Profile",
    val attackType: AttackType = AttackType.MELEE,
    val models: Int = 1,
    val attacks: Int = 1,
    val toHit: Int = 4,
    val toWound: Int = 4,
    val rend: Int = 0,
    val damage: Int = 1,
    val active: Boolean = true,
    val twoHits: Boolean = false,
    val autoW: Boolean = false,
    val mortal: Boolean = false,
    val aoa: Boolean = false // persisté via mapping
)

data class UnitEntry(
    val name: String = "Unit",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Weapon Profile"))
)

data class TargetConfig(
    val wardNeeded: Int = 0,
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 1
)

// ===== Engine (AoA inclus) =====
private fun clamp2to6(x: Int) = x.coerceIn(2, 6)
private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}
private fun effectiveHitThreshold(baseNeeded: Int, debuff: Int, aoa: Boolean): Int {
    val buff = if (aoa) -1 else 0
    return clamp2to6(baseNeeded + debuff + buff) // min 2+, max 6+
}
private fun pHitNonSix(effNeeded: Int): Double {
    val successes = (6 - effNeeded).coerceAtLeast(0)
    return successes / 6.0
}
private fun pWound(needed: Int): Double = pGate(needed)
private fun pUnsaved(baseSave: Int?, rend: Int): Double {
    if (baseSave == null) return 1.0
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
    val debuff = if (target.debuffHitEnabled) target.debuffHitValue else 0
    val effHit = effectiveHitThreshold(p.toHit, debuff, p.aoa)

    val p6 = 1.0 / 6.0
    val phNon6 = pHitNonSix(effHit)
    val pw = pWound(p.toWound)
    val pu = pUnsaved(baseSave, p.rend)
    val ward = wardFactor(target.wardNeeded)

    val evNon6 = phNon6 * pw * pu * p.damage
    val mult6 = if (p.twoHits) 2.0 else 1.0
    val pw6 = if (p.mortal || p.autoW) 1.0 else pw
    val pu6 = if (p.mortal) 1.0 else pu
    val ev6 = p6 * mult6 * pw6 * pu6 * p.damage
    return attacks * (evNon6 + ev6) * ward
}
private fun expectedDamageForUnit(u: UnitEntry, target: TargetConfig, baseSave: Int?): Double {
    if (!u.active) return 0.0
    return u.profiles.sumOf { expectedDamageForProfile(it, target, baseSave) }
}
private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double =
    units.filter { it.active }.sumOf { expectedDamageForUnit(it, target, baseSave) }

// ===== Profils (commit-on-blur pour les noms) =====
@Composable
fun ProfilesTab(units: List<UnitEntry>, onUpdateUnits: (List<UnitEntry>) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(units) { unitIndex, unit ->
            ElevatedCard {
                var expanded by rememberSaveable(unitIndex) { mutableStateOf(true) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ligne entête unité
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

                        // --- Unit name: commit on blur ---
                        var unitNameText by remember(unit.name) { mutableStateOf(unit.name) }
                        OutlinedTextField(
                            value = unitNameText,
                            onValueChange = { unitNameText = it }, // local-only
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { state ->
                                    if (!state.isFocused && unitNameText != unit.name) {
                                        onUpdateUnits(units.toMutableList().also {
                                            it[unitIndex] = unit.copy(name = unitNameText)
                                        })
                                    }
                                }
                        )

                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "▼" else "▶")
                        }
                    }

                    // Actions unité
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            onUpdateUnits(
                                units.toMutableList().also { list ->
                                    val newProfiles = unit.profiles + AttackProfile(name = "Weapon Profile")
                                    list[unitIndex] = unit.copy(profiles = newProfiles)
                                }
                            )
                        }) { Text("Add Profile") }

                        Spacer(Modifier.width(12.dp))

                        TextButton(onClick = {
                            if (units.size > 1) {
                                onUpdateUnits(units.toMutableList().also { it.removeAt(unitIndex) })
                            }
                        }) { Text("Delete Unit") }
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
                                },
                                // pour commit-on-blur du nom du profil
                                onCommitProfileName = { newName ->
                                    onUpdateUnits(units.toMutableList().also { list ->
                                        val newProfiles = unit.profiles.toMutableList()
                                        newProfiles[pIndex] = profile.copy(name = newName)
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
private fun TopLabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.9f)
        )
    }
}

@Composable
private fun ProfileEditor(
    profile: AttackProfile,
    onChange: (AttackProfile) -> Unit,
    onRemove: () -> Unit,
    onCommitProfileName: (String) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        var expanded by rememberSaveable(profile.hashCode()) { mutableStateOf(true) }
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header du profil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = profile.active,
                    onCheckedChange = { ok -> onChange(profile.copy(active = ok)) }
                )

                // --- Profile name: commit on blur ---
                var profileNameText by remember(profile.name) { mutableStateOf(profile.name) }
                OutlinedTextField(
                    value = profileNameText,
                    onValueChange = { profileNameText = it }, // local-only
                    label = { Text("Weapon Profile") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (!state.isFocused && profileNameText != profile.name) {
                                onCommitProfileName(profileNameText)
                            }
                        }
                )

                TextButton(
                    onClick = {
                        val next = if (profile.attackType == AttackType.MELEE) AttackType.SHOOT else AttackType.MELEE
                        onChange(profile.copy(attackType = next))
                    }
                ) {
                    Text(if (profile.attackType == AttackType.MELEE) "Melee" else "Shoot")
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▼" else "▶")
                }
            }

            // Bouton suppression
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onRemove) { Text("Delete Profile") }
            }

            if (expanded) {
                // SpaceBetween : pousse la colonne de droite au bord
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Colonne gauche : champs (écriture immédiate)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeled("Size") { NumberField(profile.models) { v -> onChange(profile.copy(models = v)) } }
                            TopLabeled("Atk")  { NumberField(profile.attacks) { v -> onChange(profile.copy(attacks = v)) } }
                            TopLabeled("Hit")  { GateField2to6(profile.toHit) { v -> onChange(profile.copy(toHit = v)) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeled("Wnd")  { GateField2to6(profile.toWound) { v -> onChange(profile.copy(toWound = v)) } }
                            TopLabeled("Rend") { NumberField(profile.rend) { v -> onChange(profile.copy(rend = v)) } }
                            TopLabeled("Dmg")  { NumberField(profile.damage) { v -> onChange(profile.copy(damage = v)) } }
                        }
                    }

                    // Colonne droite : checkboxes (écriture immédiate), collée à droite
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.width(110.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeledCheckbox(
                                label = "2Hits",
                                checked = profile.twoHits,
                                onCheckedChange = { checked -> onChange(profile.copy(twoHits = checked)) }
                            )
                            TopLabeledCheckbox(
                                label = "AutoW",
                                checked = profile.autoW,
                                onCheckedChange = { checked -> onChange(profile.copy(autoW = checked)) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeledCheckbox(
                                label = "Mortal",
                                checked = profile.mortal,
                                onCheckedChange = { checked -> onChange(profile.copy(mortal = checked)) }
                            )
                            TopLabeledCheckbox(
                                label = "AoA",
                                checked = profile.aoa,
                                onCheckedChange = { checked -> onChange(profile.copy(aoa = checked)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== Champs numériques (écriture immédiate) =====
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
        modifier = Modifier.size(width = 58.dp, height = 50.dp)
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
        modifier = Modifier.size(width = 58.dp, height = 50.dp)
    )
}

// ===== Target tab (inchangé) =====
@Composable
fun TargetTab(target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Target buffs/debuffs", style = MaterialTheme.typography.titleMedium)

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

// ===== Simulation tab (inchangé) =====
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val activeUnits = units.filter { it.active }.take(6)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Damage expectation by Save (per active unit, max 6)", style = MaterialTheme.typography.titleMedium)

        if (activeUnits.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No active unit.")
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save", modifier = Modifier.width(70.dp))
            activeUnits.forEach { u -> Text(u.name, modifier = Modifier.weight(1f), maxLines = 1) }
        }
        Divider()

        val saves = listOf<Int?>(2, 3, 4, 5, 6, null)
        saves.forEach { save ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

/* ===== Mapping UI <-> Domain (inclut aoa) ===== */
private fun TargetConfig.toDomain(): com.samohammer.app.model.TargetConfig =
    com.samohammer.app.model.TargetConfig(
        wardNeeded = this.wardNeeded,
        debuffHitEnabled = this.debuffHitEnabled,
        debuffHitValue = this.debuffHitValue
    )
private fun com.samohammer.app.model.TargetConfig.toUi(): TargetConfig =
    TargetConfig(
        wardNeeded = this.wardNeeded,
        debuffHitEnabled = this.debuffHitEnabled,
        debuffHitValue = this.debuffHitValue
    )

private fun com.samohammer.app.model.AttackProfile.toUi(): AttackProfile =
    AttackProfile(
        name = name,
        attackType = when (attackType) {
            com.samohammer.app.model.AttackType.MELEE -> AttackType.MELEE
            com.samohammer.app.model.AttackType.SHOOT -> AttackType.SHOOT
        },
        models = models,
        attacks = attacks,
        toHit = toHit,
        toWound = toWound,
        rend = rend,
        damage = damage,
        active = active,
        twoHits = twoHits,
        autoW = autoW,
        mortal = mortal,
        aoa = aoa
    )

private fun AttackProfile.toDomain(): com.samohammer.app.model.AttackProfile =
    com.samohammer.app.model.AttackProfile(
        id = com.samohammer.app.util.newUuid(),
        name = name,
        attackType = when (attackType) {
            AttackType.MELEE -> com.samohammer.app.model.AttackType.MELEE
            AttackType.SHOOT -> com.samohammer.app.model.AttackType.SHOOT
        },
        models = models,
        attacks = attacks,
        toHit = toHit,
        toWound = toWound,
        rend = rend,
        damage = damage,
        active = active,
        twoHits = twoHits,
        autoW = autoW,
        mortal = mortal,
        aoa = aoa
    )

private fun com.samohammer.app.model.UnitEntry.toUi(): UnitEntry =
    UnitEntry(name = name, active = active, profiles = profiles.map { it.toUi() })
private fun UnitEntry.toDomain(): com.samohammer.app.model.UnitEntry =
    com.samohammer.app.model.UnitEntry(
        id = com.samohammer.app.util.newUuid(),
        name = name,
        active = active,
        profiles = profiles.map { it.toDomain() }
    )
