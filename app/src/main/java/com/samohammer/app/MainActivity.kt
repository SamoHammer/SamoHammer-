// V2.4.0 — Simulations: histogramme + couleurs par unité (tableau + barres coordonnées)
// - Ajoute un histogramme sous le tableau de l’onglet Simulations (Canvas, sans lib externe).
// - Une couleur par unité active (réutilisée pour le nom de colonne du tableau + légende + barres).
// - Conserve tout l’existant (AoA persisté + moteur, numeric UX with clear-on-focus & blur default,
//   commit-on-blur pour les noms, delete à droite, Bonus tab avec Target en haut, etc.).
// - Thème côté UI supposé CLAIR (Theme.kt force le mode clair).
//
// Path: app/src/main/java/com/samohammer/app/MainActivity.kt

package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.samohammer.app.ui.theme.SamoHammerTheme
import com.samohammer.app.ui.AppStateViewModel
import com.samohammer.app.ui.AppStateViewModelFactory
import kotlin.math.max

// ======================
// UI models (local)
// ======================

enum class AttackType { MELEE, SHOOT }

data class AttackProfile(
    val name: String = "",
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
    val aoa: Boolean = false,
    // Bonus “toggle” (persisté côté Domain via mapping)
    val bonusPlusHit: Boolean = false,
    val bonusPlusWound: Boolean = false,
    val bonusPlusAtkTimes: Int = 0, // 0..2
    val bonusPlusRend: Boolean = false,
    val bonusPlusDmg: Boolean = false
)

data class UnitEntry(
    val name: String = "",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = ""))
)

data class TargetConfig(
    val wardNeeded: Int = 0,
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 1,
    // debuffs additionnels (bonus tab)
    val debuffWoundEnabled: Boolean = false,
    val debuffRendEnabled: Boolean = false,
    val debuffAtkEnabled: Boolean = false,
    val debuffDmgEnabled: Boolean = false
)

// ======================
// Probabilities & engine
// ======================

private fun clamp2to6(x: Int) = x.coerceIn(2, 6)
private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}

private fun capHitWoundDelta(base: Int, delta: Int): Int {
    // Un profil ne peut pas bénéficier d’un delta > +1 ou < -1 au final (cap après cumul)
    val capped = delta.coerceIn(-1, +1)
    return clamp2to6(base - capped) // delta positif améliore (ex: +1 => 3+ si base 4+)
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

    // Attaques au moins 1
    val attacksBase = max(p.models, 0) * max(p.attacks, 0)
    val bonusAtk = if (target.debuffAtkEnabled) -1 else 0
    val extraAtk = (p.bonusPlusAtkTimes).coerceIn(0, 2) // +0..+2
    val attacks = max(attacksBase + extraAtk + bonusAtk, 1)

    // Hit delta cumulé: AoA + toggle + debuffHit
    var deltaHit = 0
    if (p.aoa) deltaHit += 1
    if (p.bonusPlusHit) deltaHit += 1
    if (target.debuffHitEnabled) deltaHit -= target.debuffHitValue
    val effHitNeeded = capHitWoundDelta(p.toHit, deltaHit)

    // Wound delta cumulé: +1 wound (toggle) - debuff Wound
    var deltaW = 0
    if (p.bonusPlusWound) deltaW += 1
    if (target.debuffWoundEnabled) deltaW -= 1
    val effWoundNeeded = capHitWoundDelta(p.toWound, deltaW)

    // Rend / Damage ajustés
    val effRend = p.rend + (if (p.bonusPlusRend) 1 else 0) + (if (target.debuffRendEnabled) -1 else 0)
    val effDmg = max(1, p.damage + (if (p.bonusPlusDmg) 1 else 0) + (if (target.debuffDmgEnabled) -1 else 0))

    val p6 = 1.0 / 6.0
    val phNon6 = pHitNonSix(effHitNeeded)
    val pw = pWound(effWoundNeeded)
    val pu = pUnsaved(baseSave, effRend)
    val ward = wardFactor(target.wardNeeded)

    val evNon6 = phNon6 * pw * pu * effDmg
    val mult6 = if (p.twoHits) 2.0 else 1.0
    val pw6 = if (p.mortal || p.autoW) 1.0 else pw
    val pu6 = if (p.mortal) 1.0 else pu
    val ev6 = p6 * mult6 * pw6 * pu6 * effDmg
    return attacks * (evNon6 + ev6) * ward
}

private fun expectedDamageForUnit(u: UnitEntry, target: TargetConfig, baseSave: Int?): Double {
    if (!u.active) return 0.0
    return u.profiles.sumOf { expectedDamageForProfile(it, target, baseSave) }
}

private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double =
    units.filter { it.active }.sumOf { expectedDamageForUnit(it, target, baseSave) }

// ======================
// App
// ======================

class MainActivity : ComponentActivity() {
    private val appStateVM: AppStateViewModel by viewModels {
        AppStateViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamoHammerTheme {
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Profils", "Bonus", "Simulations")

                var units by remember { mutableStateOf(listOf(UnitEntry())) }
                var target by remember { mutableStateOf(TargetConfig()) }

                val persisted by appStateVM.state.collectAsState()
                LaunchedEffect(persisted.units) {
                    units = persisted.units.map { it.toUi() }.ifEmpty { listOf(UnitEntry()) }
                }
                LaunchedEffect(persisted.target) { target = persisted.target.toUi() }

                Scaffold(
                    topBar = {
                        TabRow(selectedTabIndex = selectedTab) {
                            tabs.forEachIndexed { i, title ->
                                Tab(
                                    selected = (selectedTab == i),
                                    onClick = { selectedTab = i },
                                    text = { Text(title) }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (selectedTab == 0) {
                            FloatingActionButton(onClick = {
                                val newUnits = units + UnitEntry()
                                units = newUnits
                                appStateVM.setUnits(newUnits.map { it.toDomain() })
                            }) { Text("+") }
                        }
                    }
                ) { inner ->
                    Box(Modifier.padding(inner)) {
                        when (selectedTab) {
                            0 -> ProfilesTab(
                                units = units,
                                onUpdateUnits = { newUnits ->
                                    units = newUnits
                                    appStateVM.setUnits(newUnits.map { it.toDomain() })
                                }
                            )
                            1 -> BonusTab(
                                units = units,
                                target = target,
                                onUpdate = { t ->
                                    target = t
                                    appStateVM.updateTarget(t.toDomain())
                                }
                            )
                            2 -> SimulationTab(units, target)
                        }
                    }
                }
            }
        }
    }
}

// ======================
// Helpers UI
// ======================

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

// ======================
// Profils
// ======================

@Composable
fun ProfilesTab(units: List<UnitEntry>, onUpdateUnits: (List<UnitEntry>) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(units) { unitIndex, unit ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                var expanded by rememberSaveable(unitIndex) { mutableStateOf(true) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header unité
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

                        // Unit name — commit-on-blur, initialement vide si nouvel item
                        var unitNameText by remember(unit.name) { mutableStateOf(unit.name) }
                        OutlinedTextField(
                            value = unitNameText,
                            onValueChange = { unitNameText = it },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { st ->
                                    if (!st.isFocused && unitNameText != unit.name) {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = {
                                onUpdateUnits(
                                    units.toMutableList().also { list ->
                                        val newProfiles = unit.profiles + AttackProfile(name = "")
                                        list[unitIndex] = unit.copy(profiles = newProfiles)
                                    }
                                )
                            }) { Text("Add Profile") }
                        }
                        Row {
                            TextButton(
                                onClick = {
                                    if (units.size > 1) {
                                        onUpdateUnits(units.toMutableList().also { it.removeAt(unitIndex) })
                                    }
                                }
                            ) { Text("Delete Unit") }
                        }
                    }

                    if (expanded) {
                        unit.profiles.forEachIndexed { pIndex, profile ->
                            ProfileEditor(
                                unitName = unit.name.ifBlank { "Unit" },
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
private fun ProfileEditor(
    unitName: String,
    profile: AttackProfile,
    onChange: (AttackProfile) -> Unit,
    onRemove: () -> Unit,
    onCommitProfileName: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        var expanded by rememberSaveable(profile.hashCode()) { mutableStateOf(true) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header profil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = profile.active,
                    onCheckedChange = { ok -> onChange(profile.copy(active = ok)) }
                )

                // Profile name — commit-on-blur
                var profileNameText by remember(profile.name) { mutableStateOf(profile.name) }
                OutlinedTextField(
                    value = profileNameText,
                    onValueChange = { profileNameText = it },
                    label = { Text("Weapon Profile") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { st ->
                            if (!st.isFocused && profileNameText != profile.name) {
                                onCommitProfileName(profileNameText)
                            }
                        }
                )

                TextButton(onClick = {
                    val next = if (profile.attackType == AttackType.MELEE) AttackType.SHOOT else AttackType.MELEE
                    onChange(profile.copy(attackType = next))
                }) { Text(if (profile.attackType == AttackType.MELEE) "Melee" else "Shoot") }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▼" else "▶")
                }
            }

            // Ligne actions profil : Toggle Bonus + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showBonus by remember { mutableStateOf(false) }
                TextButton(onClick = { showBonus = true }) {
                    val hasAny = profile.bonusPlusHit || profile.bonusPlusWound ||
                            profile.bonusPlusAtkTimes > 0 || profile.bonusPlusRend || profile.bonusPlusDmg
                    Text(if (hasAny) "Toggle Bonus ✓" else "Toggle Bonus")
                }
                TextButton(onClick = onRemove) { Text("Delete Profile") }

                if (showBonus) {
                    BonusDialog(
                        unitName = unitName,
                        profileName = profile.name.ifBlank { "Weapon Profile" },
                        current = profile,
                        onDismiss = { showBonus = false },
                        onValidate = { np ->
                            showBonus = false
                            onChange(np)
                        }
                    )
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            NumberField("Size", profile.models, 0) { v -> onChange(profile.copy(models = v)) }
                            NumberField("Atk", profile.attacks, 0) { v -> onChange(profile.copy(attacks = v)) }
                            GateField2to6("Hit", profile.toHit, 4) { v -> onChange(profile.copy(toHit = v)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            GateField2to6("Wnd", profile.toWound, 4) { v -> onChange(profile.copy(toWound = v)) }
                            NumberField("Rend", profile.rend, 0) { v -> onChange(profile.copy(rend = v)) }
                            NumberField("Dmg", profile.damage, 1) { v -> onChange(profile.copy(damage = v)) }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.width(120.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeledCheckbox("2Hits", profile.twoHits) { onChange(profile.copy(twoHits = it)) }
                            TopLabeledCheckbox("AutoW", profile.autoW) { onChange(profile.copy(autoW = it)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TopLabeledCheckbox("Mortal", profile.mortal) { onChange(profile.copy(mortal = it)) }
                            TopLabeledCheckbox("AoA", profile.aoa) { onChange(profile.copy(aoa = it)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BonusDialog(
    unitName: String,
    profileName: String,
    current: AttackProfile,
    onDismiss: () -> Unit,
    onValidate: (AttackProfile) -> Unit
) {
    var plusHit by remember { mutableStateOf(current.bonusPlusHit) }
    var plusWound by remember { mutableStateOf(current.bonusPlusWound) }
    var plusAtk by remember { mutableStateOf(current.bonusPlusAtkTimes.coerceIn(0, 2)) }
    var plusRend by remember { mutableStateOf(current.bonusPlusRend) }
    var plusDmg by remember { mutableStateOf(current.bonusPlusDmg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$unitName — ${profileName.ifBlank { "Weapon Profile" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+1 Hit")
                    Switch(checked = plusHit, onCheckedChange = { plusHit = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+1 Wound")
                    Switch(checked = plusWound, onCheckedChange = { plusWound = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+1 Atk (x2)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(enabled = plusAtk > 0, onClick = { plusAtk = (plusAtk - 1).coerceAtLeast(0) }) { Text("-") }
                        Text("$plusAtk")
                        FilledTonalButton(enabled = plusAtk < 2, onClick = { plusAtk = (plusAtk + 1).coerceAtMost(2) }) { Text("+") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+1 Rend")
                    Switch(checked = plusRend, onCheckedChange = { plusRend = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+1 Dmg")
                    Switch(checked = plusDmg, onCheckedChange = { plusDmg = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValidate(
                    current.copy(
                        bonusPlusHit = plusHit,
                        bonusPlusWound = plusWound,
                        bonusPlusAtkTimes = plusAtk,
                        bonusPlusRend = plusRend,
                        bonusPlusDmg = plusDmg
                    )
                )
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ======================
// Champs numériques UX
// ======================

@Composable
private fun NumberField(
    label: String,
    value: Int,
    defaultOnBlur: Int,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hadFocus by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                val digits = newText.filter { it.isDigit() }
                if (digits.isEmpty()) {
                    text = ""
                } else {
                    text = digits
                    val v = digits.toIntOrNull()
                    if (v != null) onValue(v)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .size(width = 58.dp, height = 50.dp)
                .onFocusChanged { st ->
                    if (st.isFocused && !hadFocus) {
                        hadFocus = true
                        text = ""
                    } else if (!st.isFocused) {
                        hadFocus = false
                        if (text.isEmpty()) {
                            text = defaultOnBlur.toString()
                            onValue(defaultOnBlur)
                        }
                    }
                }
        )
    }
}

@Composable
private fun GateField2to6(
    label: String,
    value: Int,
    defaultOnBlur: Int,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hadFocus by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                val digits = newText.filter { it.isDigit() }
                if (digits.isEmpty()) {
                    text = ""
                } else {
                    val d1 = digits.take(1)
                    text = d1
                    val v = d1.toIntOrNull()
                    if (v != null && v in 2..6) onValue(v)
                }
            },
            placeholder = { Text("2..6") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .size(width = 58.dp, height = 50.dp)
                .onFocusChanged { st ->
                    if (st.isFocused && !hadFocus) {
                        hadFocus = true
                        text = ""
                    } else if (!st.isFocused) {
                        hadFocus = false
                        if (text.isEmpty()) {
                            text = defaultOnBlur.toString()
                            onValue(defaultOnBlur)
                        } else {
                            val v = text.toIntOrNull()
                            if (v == null || v !in 2..6) {
                                text = defaultOnBlur.toString()
                                onValue(defaultOnBlur)
                            }
                        }
                    }
                }
        )
    }
}

// ======================
// Onglet Bonus
// ======================

@Composable
fun BonusTab(units: List<UnitEntry>, target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    val activeUnits = units.filter { it.active }.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bloc Target (global)
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Target", style = MaterialTheme.typography.titleMedium)

                    // Ligne 1: Ward, -1 Hit, -1 Wnd
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        TopLabeled("Ward") {
                            var wardTxt by remember(target.wardNeeded) {
                                mutableStateOf(
                                    when {
                                        target.wardNeeded == 0 -> "0"
                                        target.wardNeeded in 2..6 -> target.wardNeeded.toString()
                                        else -> ""
                                    }
                                )
                            }
                            OutlinedTextField(
                                value = wardTxt,
                                onValueChange = { newText ->
                                    val digits = newText.filter { it.isDigit() }.take(1)
                                    wardTxt = if (digits.isEmpty()) "" else digits
                                    val v = digits.toIntOrNull()
                                    onUpdate(target.copy(wardNeeded = v ?: 0))
                                },
                                placeholder = { Text("0 or 2..6") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp).height(50.dp)
                            )
                        }
                        TopLabeledCheckbox(
                            label = "-1 Hit",
                            checked = target.debuffHitEnabled,
                            onCheckedChange = { enabled ->
                                onUpdate(
                                    target.copy(
                                        debuffHitEnabled = enabled,
                                        debuffHitValue = if (enabled) target.debuffHitValue else 0
                                    )
                                )
                            }
                        )
                        TopLabeledCheckbox(
                            label = "-1 Wnd",
                            checked = target.debuffWoundEnabled,
                            onCheckedChange = { onUpdate(target.copy(debuffWoundEnabled = it)) }
                        )
                    }

                    // Ligne 2: -1 Rend / -1 Atk / -1 Dmg
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        TopLabeledCheckbox(
                            label = "-1 Rend",
                            checked = target.debuffRendEnabled,
                            onCheckedChange = { onUpdate(target.copy(debuffRendEnabled = it)) }
                        )
                        TopLabeledCheckbox(
                            label = "-1 Atk",
                            checked = target.debuffAtkEnabled,
                            onCheckedChange = { onUpdate(target.copy(debuffAtkEnabled = it)) }
                        )
                        TopLabeledCheckbox(
                            label = "-1 Dmg",
                            checked = target.debuffDmgEnabled,
                            onCheckedChange = { onUpdate(target.copy(debuffDmgEnabled = it)) }
                        )
                    }
                }
            }
        }

        // Récap par unité / profils (read-only)
        itemsIndexed(activeUnits) { _, unit ->
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Conditionnal Bonus — ${unit.name.ifBlank { "Unit" }}", style = MaterialTheme.typography.titleMedium)
                    unit.profiles.forEach { p ->
                        val list = buildList<String> {
                            if (p.bonusPlusHit) add("+1 Hit")
                            if (p.bonusPlusWound) add("+1 Wound")
                            if (p.bonusPlusAtkTimes > 0) add("+1 Atk x${p.bonusPlusAtkTimes}")
                            if (p.bonusPlusRend) add("+1 Rend")
                            if (p.bonusPlusDmg) add("+1 Dmg")
                        }
                        if (list.isNotEmpty()) {
                            Text(p.name.ifBlank { "Weapon Profile" }, style = MaterialTheme.typography.labelLarge)
                            Text(list.joinToString(", "))
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

// ======================
// Onglet Simulations (tableau + histogramme)
// ======================

@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val activeUnits = units.filter { it.active }.take(6)
    if (activeUnits.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No active unit.")
        }
        return
    }

    // Palette de couleurs pour les unités
    val colors = remember(activeUnits.size) { unitColors(activeUnits.size) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Damage expectation by Save (per active unit, max 6)", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save", modifier = Modifier.width(70.dp))
            activeUnits.forEachIndexed { i, u ->
                Text(u.name.ifBlank { "Unit" }, modifier = Modifier.weight(1f), maxLines = 1, color = colors[i])
            }
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

        // Histogramme
        SimulationBarChart(units = activeUnits, target = target, barColors = colors)
    }
}

@Composable
private fun SimulationBarChart(
    units: List<UnitEntry>,
    target: TargetConfig,
    barColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val saves = listOf<Int?>(2, 3, 4, 5, 6, null)
    val saveLabels = listOf("2+", "3+", "4+", "5+", "6+", "No Save")

    val matrix = saves.map { s -> units.map { u -> expectedDamageForUnit(u, target, s) } }
    val maxY = (matrix.flatten().maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
    val yTicks = 4
    val yStep = maxY / yTicks

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val groups = saves.size
            val unitsCount = units.size

            val leftPad = 40.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val topPad = 8.dp.toPx()
            val rightPad = 16.dp.toPx()

            val plotWidth = size.width - leftPad - rightPad
            val plotHeight = size.height - topPad - bottomPad

            val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
            // Axes
            drawLine(axisColor, Offset(leftPad, size.height - bottomPad), Offset(size.width - rightPad, size.height - bottomPad), 1.dp.toPx())
            drawLine(axisColor, Offset(leftPad, size.height - bottomPad), Offset(leftPad, topPad), 1.dp.toPx())

            // Grille horizontale
            for (t in 1..yTicks) {
                val yVal = (yStep * t).toFloat()
                val y = size.height - bottomPad - (yVal / maxY.toFloat()) * plotHeight
                drawLine(axisColor.copy(alpha = 0.5f), Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            }

            // Barres
            val groupWidth = plotWidth / groups
            val barWidth = groupWidth / (unitsCount + 1)

            matrix.forEachIndexed { g, valuesForSave ->
                valuesForSave.forEachIndexed { i, ev ->
                    val barHeight = (ev.toFloat() / maxY.toFloat()) * plotHeight
                    val x = leftPad + g * groupWidth + i * barWidth + (groupWidth - unitsCount * barWidth) / 2f
                    val y = size.height - bottomPad - barHeight
                    drawRect(
                        color = barColors[i],
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
                    )
                }
            }
        }

        // Labels X
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            saveLabels.forEach { lab -> Text(lab, style = MaterialTheme.typography.labelSmall) }
        }

        // Légende
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            units.forEachIndexed { i, u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(barColors[i], shape = RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(u.name.ifBlank { "Unit" }, color = barColors[i], maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun unitColors(n: Int): List<Color> {
    val base = listOf(
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFFB8C00), // Orange
        Color(0xFF8E24AA), // Purple
        Color(0xFFE53935), // Red
        Color(0xFF00897B)  // Teal
    )
    return base.take(maxOf(1, minOf(6, n)))
}

// ======================
// Mapping UI <-> Domain
// ======================

private fun TargetConfig.toDomain(): com.samohammer.app.model.TargetConfig =
    com.samohammer.app.model.TargetConfig(
        wardNeeded = this.wardNeeded,
        debuffHitEnabled = this.debuffHitEnabled,
        debuffHitValue = this.debuffHitValue,
        debuffWoundEnabled = this.debuffWoundEnabled,
        debuffRendEnabled = this.debuffRendEnabled,
        debuffAtkEnabled = this.debuffAtkEnabled,
        debuffDmgEnabled = this.debuffDmgEnabled
    )

private fun com.samohammer.app.model.TargetConfig.toUi(): TargetConfig =
    TargetConfig(
        wardNeeded = this.wardNeeded,
        debuffHitEnabled = this.debuffHitEnabled,
        debuffHitValue = this.debuffHitValue,
        debuffWoundEnabled = this.debuffWoundEnabled,
        debuffRendEnabled = this.debuffRendEnabled,
        debuffAtkEnabled = this.debuffAtkEnabled,
        debuffDmgEnabled = this.debuffDmgEnabled
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
        aoa = aoa,
        bonusPlusHit = bonusPlusHit,
        bonusPlusWound = bonusPlusWound,
        bonusPlusAtkTimes = bonusPlusAtkTimes,
        bonusPlusRend = bonusPlusRend,
        bonusPlusDmg = bonusPlusDmg
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
        aoa = aoa,
        bonusPlusHit = bonusPlusHit,
        bonusPlusWound = bonusPlusWound,
        bonusPlusAtkTimes = bonusPlusAtkTimes,
        bonusPlusRend = bonusPlusRend,
        bonusPlusDmg = bonusPlusDmg
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
