// V2.2.0 — Toggle Bonus (UI-only) via pop-in + Bonus tab en lecture seule
// - Profils: supprime la case "Bonus" introduite en 2.1.8. Ajoute un bouton "Toggle Bonus" par profil.
//   • Clic → ouvre une AlertDialog "<UnitName> – <ProfileName>" avec cases : +1 Hit, +1 Wound,
//     +1 Atk (cochable 2 fois), +1 Rend, +1 Dmg. Boutons Annuler / Valider.
//   • Si au moins un bonus est sélectionné, le bouton devient "Toggle Bonus ✓" (état visuel).
// - Bonus: lecture seule. Affiche TOUTES les unités actives (max 6) et TOUS leurs profils actifs.
//   • Sous "Conditionnal Bonus — <UnitName>" (avec "Target" à droite), on liste chaque profil et
//     on résume les bonus cochés (ex: "+1 Hit, +1 Atk x2"). S'il n'y a rien: "—".
//   • La colonne Target (Ward, -1 Hit, -1 Wound) reste fonctionnelle et GLOBALE (moteur inchangé).
// - Conserve 2.1.7: polish UI, labels au-dessus, champs numériques centrés, Ward compact, AoA persisté.

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
                val tabs = listOf("Profils", "Bonus", "Simulations")

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
                            1 -> BonusTab(units, target) { t ->
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

/* =========================
   UI models (UI layer only)
   ========================= */
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
    val aoa: Boolean = false, // persisté via mapping
    // --- NEW: bonus UI-only (non persisté) ---
    val bonusHit: Boolean = false,
    val bonusWound: Boolean = false,
    val bonusAtk: Int = 0,       // 0..2
    val bonusRend: Boolean = false,
    val bonusDmg: Boolean = false
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

/* =========================
   Moteur (AoA inclus)
   ========================= */
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

/* =========================
   Helpers UI
   ========================= */
private fun AttackProfile.hasAnyBonus(): Boolean =
    bonusHit || bonusWound || bonusAtk > 0 || bonusRend || bonusDmg

private fun formatBonusSummary(p: AttackProfile): String {
    val parts = mutableListOf<String>()
    if (p.bonusHit) parts += "+1 Hit"
    if (p.bonusWound) parts += "+1 Wound"
    if (p.bonusAtk > 0) parts += "+1 Atk x${p.bonusAtk}"
    if (p.bonusRend) parts += "+1 Rend"
    if (p.bonusDmg) parts += "+1 Dmg"
    return if (parts.isEmpty()) "—" else parts.joinToString(", ")
}

/* =========================
   Composants partagés (labels au-dessus)
   ========================= */
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

/* =========================
   Onglet Bonus — lecture seule (résumé par profil) + Target global
   ========================= */
@Composable
fun BonusTab(units: List<UnitEntry>, target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    val activeUnits = units.filter { it.active }.take(6)
    if (activeUnits.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) { Text("No active unit.") }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(activeUnits) { _, unit ->
            val activeProfiles = unit.profiles.filter { it.active }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Titre (unité) + "Target" à droite
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Conditionnal Bonus — ${unit.name}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Target", style = MaterialTheme.typography.titleMedium)
                    }

                    // Résumé par profil (read-only)
                    activeProfiles.forEach { profile ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("[Profile: ${profile.name}]", style = MaterialTheme.typography.titleSmall)
                            Text(formatBonusSummary(profile))
                        }
                        Divider()
                    }

                    // Colonne Target (contrôles globaux — fonctionnels)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.width(IntrinsicSize.Min)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ward", style = MaterialTheme.typography.labelSmall)
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
                                        onUpdate(target.copy(wardNeeded = digits.toIntOrNull() ?: 0))
                                    },
                                    placeholder = { Text("0 or 2..6") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(56.dp).height(50.dp)
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

                            var debuffWound by remember { mutableStateOf(false) } // UI-only (pas moteur)
                            TopLabeledCheckbox(
                                label = "-1 Wound",
                                checked = debuffWound,
                                onCheckedChange = { debuffWound = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* =========================
   Profils (Toggle Bonus + pop-in)
   ========================= */
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

                        // Unit name: commit-on-blur
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

                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { /* le toggle type est par profil */ }) { Text("Type") }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "▼" else "▶") }
                    }

                    // Actions unité: Add à gauche, Delete totalement à droite
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = {
                                onUpdateUnits(
                                    units.toMutableList().also { list ->
                                        val newProfiles = unit.profiles + AttackProfile(name = "Weapon Profile")
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
                                unitName = unit.name,
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
    ElevatedCard(Modifier.fillMaxWidth()) {
        var expanded by rememberSaveable(profile.hashCode()) { mutableStateOf(true) }
        // Dialog state for "Toggle Bonus"
        var showBonusDialog by remember(profile.hashCode()) { mutableStateOf(false) }

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

                // Profile name: commit-on-blur — élargi visuellement
                var profileNameText by remember(profile.name) { mutableStateOf(profile.name) }
                OutlinedTextField(
                    value = profileNameText,
                    onValueChange = { profileNameText = it }, // local-only
                    label = { Text("Weapon Profile") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.2f)
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

            // Ligne d’actions: Toggle Bonus (à gauche) + Delete Profile (à droite)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val toggleLabel = if (profile.hasAnyBonus()) "Toggle Bonus ✓" else "Toggle Bonus"
                TextButton(onClick = { showBonusDialog = true }) { Text(toggleLabel) }

                TextButton(onClick = onRemove) { Text("Delete Profile") }
            }

            if (expanded) {
                // SpaceBetween : pousse la colonne de droite au bord
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Colonne gauche : champs numériques (labels au-dessus, case centrée visuellement)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            NumberFieldCentered(
                                label = "Size",
                                value = profile.models,
                                defaultOnBlur = 0,
                                onValue = { v -> onChange(profile.copy(models = v)) }
                            )
                            NumberFieldCentered(
                                label = "Atk",
                                value = profile.attacks,
                                defaultOnBlur = 0,
                                onValue = { v -> onChange(profile.copy(attacks = v)) }
                            )
                            GateField2to6Centered(
                                label = "Hit",
                                value = profile.toHit,
                                defaultOnBlur = 4,
                                onValue = { v -> onChange(profile.copy(toHit = v)) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            GateField2to6Centered(
                                label = "Wnd",
                                value = profile.toWound,
                                defaultOnBlur = 4,
                                onValue = { v -> onChange(profile.copy(toWound = v)) }
                            )
                            NumberFieldCentered(
                                label = "Rend",
                                value = profile.rend,
                                defaultOnBlur = 0,
                                onValue = { v -> onChange(profile.copy(rend = v)) }
                            )
                            NumberFieldCentered(
                                label = "Dmg",
                                value = profile.damage,
                                defaultOnBlur = 0,
                                onValue = { v -> onChange(profile.copy(damage = v)) }
                            )
                        }
                    }

                    // Colonne droite : flags evergreen
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.width(130.dp)
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

        // --- AlertDialog: Toggle Bonus ---
        if (showBonusDialog) {
            var tempHit by remember { mutableStateOf(profile.bonusHit) }
            var tempWound by remember { mutableStateOf(profile.bonusWound) }
            var tempAtk by remember { mutableStateOf(profile.bonusAtk.coerceIn(0, 2)) }
            var tempRend by remember { mutableStateOf(profile.bonusRend) }
            var tempDmg by remember { mutableStateOf(profile.bonusDmg) }

            AlertDialog(
                onDismissRequest = { showBonusDialog = false },
                title = { Text("$unitName – ${profile.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("+1 Hit"); Switch(checked = tempHit, onCheckedChange = { tempHit = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("+1 Wound"); Switch(checked = tempWound, onCheckedChange = { tempWound = it })
                        }
                        // +1 Atk (0..2) — deux crans
                        Column {
                            Text("+1 Atk (x${tempAtk})")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { tempAtk = (tempAtk - 1).coerceAtLeast(0) }, enabled = tempAtk > 0) { Text("-") }
                                Button(onClick = { tempAtk = (tempAtk + 1).coerceAtMost(2) }, enabled = tempAtk < 2) { Text("+") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("+1 Rend"); Switch(checked = tempRend, onCheckedChange = { tempRend = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("+1 Dmg"); Switch(checked = tempDmg, onCheckedChange = { tempDmg = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onChange(
                            profile.copy(
                                bonusHit = tempHit,
                                bonusWound = tempWound,
                                bonusAtk = tempAtk,
                                bonusRend = tempRend,
                                bonusDmg = tempDmg
                            )
                        )
                        showBonusDialog = false
                    }) { Text("Valider") }
                },
                dismissButton = {
                    TextButton(onClick = { showBonusDialog = false }) { Text("Annuler") }
                }
            )
        }
    }
}

/* =========================
   Composants numériques (labels au-dessus, case centrée)
   ========================= */
@Composable
private fun NumberFieldCentered(
    label: String,
    value: Int,
    defaultOnBlur: Int,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hadFocus by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                val digits = newText.filter { it.isDigit() }
                if (digits.isEmpty()) {
                    text = ""
                } else {
                    text = digits
                    digits.toIntOrNull()?.let { onValue(it) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(58.dp)
                .height(50.dp)
                .onFocusChanged { st ->
                    if (st.isFocused && !hadFocus) {
                        hadFocus = true
                        text = "" // clear on focus
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
private fun GateField2to6Centered(
    label: String,
    value: Int,
    defaultOnBlur: Int,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hadFocus by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                .width(58.dp)
                .height(50.dp)
                .onFocusChanged { st ->
                    if (st.isFocused && !hadFocus) {
                        hadFocus = true
                        text = "" // clear on focus
                    } else if (!st.isFocused) {
                        hadFocus = false
                        val v = text.toIntOrNull()
                        if (text.isEmpty() || v == null || v !in 2..6) {
                            text = defaultOnBlur.toString()
                            onValue(defaultOnBlur)
                        }
                    }
                }
        )
    }
}

/* =========================
   Simulation
   ========================= */
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val activeUnits = units.filter { it.active }.take(6)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Damage expectation by Save (per active unit, max 6)", style = MaterialTheme.typography.titleMedium)
        if (activeUnits.isEmpty()) {
            Spacer(Modifier.height(8.dp)); Text("No active unit."); return@Column
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

/* =========================
   Mapping UI <-> Domain
   ========================= */
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
        // bonus* non mappés (UI-only)
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
        // bonus* non persistés
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
