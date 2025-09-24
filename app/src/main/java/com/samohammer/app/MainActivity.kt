// V2.0.2 — UI: cases à droite en grille 2×2 avec label au-dessus (petite police)
// - 4 cases: 2Hits, AutoW, Mortal, AoA (UI-only, non persistées, non branchées moteur)
// - Colonne gauche (attributs) inchangée, moteur/persistance identiques

package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

// Compose
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import com.samohammer.app.ui.theme.SamoHammerTheme
import kotlin.math.max

// VM + Factory
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

                // lecture état persistant
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
                    Box(modifier = Modifier.padding(inner)) {
                        when (selectedTab) {
                            0 -> ProfilesTab(
                                units = units,
                                onUpdateUnits = { newUnits ->
                                    units = newUnits
                                    appStateVM.setUnits(newUnits.map { it.toDomain() })
                                }
                            )
                            1 -> TargetTab(
                                target = target,
                                onUpdate = { t ->
                                    target = t
                                    appStateVM.updateTarget(t.toDomain())
                                }
                            )
                            2 -> SimulationTab(units = units, target = target)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------
// Modèles UI
// -------------------------
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
    // UI-only
    val aoa: Boolean = false
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

// -------------------------
// Moteur
// -------------------------
private fun clamp2to6(x: Int) = x.coerceIn(2, 6)
private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}
private fun effectiveHitThreshold(baseNeeded: Int, debuff: Int): Int =
    clamp2to6(baseNeeded + debuff)
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
    val effHit = effectiveHitThreshold(p.toHit, debuff)
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
                    // Ligne 1
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

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onUpdateUnits(
                                    units.toMutableList().also { list ->
                                        val newProfiles = unit.profiles + AttackProfile(name = "Weapon Profile")
                                        list[unitIndex] = unit.copy(profiles = newProfiles)
                                    }
                                )
                            }
                        ) { Text("Add Profile") }

                        Spacer(Modifier.width(12.dp))

                        TextButton(
                            onClick = {
                                if (units.size > 1) {
                                    onUpdateUnits(units.toMutableList().also { it.removeAt(unitIndex) })
                                }
                            }
                        ) { Text("Delete Unit") }
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
private fun TopLabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        // petite police pour le label
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        // case légèrement compacte pour gagner de la place
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.9f))
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

            // Bouton suppression
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onRemove) { Text("Delete Profile") }
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Gauche : attributs (50%)
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

                    // Droite : grille 2×2, labels au-dessus, petite police
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TopLabeledCheckbox("2Hits", profile.twoHits) { onChange(profile.copy(twoHits = it)) }
                            TopLabeledCheckbox("AutoW", profile.autoW) { onChange(profile.copy(autoW = it)) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TopLabeledCheckbox("Mortal", profile.mortal) { onChange(profile.copy(mortal = it)) }
                            TopLabeledCheckbox("AoA", profile.aoa) { onChange(profile.copy(aoa = it)) }
                        }
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
// Target Tab
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
// Simulation Tab
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

        // Header
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

/* -------------------------
   Mapping Target UI <-> Domain
   ------------------------- */
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

/* -------------------------
   Mapping Units/Profiles UI <-> Domain
   (UI n’a pas d’id → on en génère un à l’aller)
   ------------------------- */
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
        aoa = false // UI-only par défaut
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
        mortal = mortal
        // aoa non mappé (UI-only)
    )

private fun com.samohammer.app.model.UnitEntry.toUi(): UnitEntry =
    UnitEntry(
        name = name,
        active = active,
        profiles = profiles.map { it.toUi() }
    )

private fun UnitEntry.toDomain(): com.samohammer.app.model.UnitEntry =
    com.samohammer.app.model.UnitEntry(
        id = com.samohammer.app.util.newUuid(),
        name = name,
        active = active,
        profiles = profiles.map { it.toDomain() }
    )
