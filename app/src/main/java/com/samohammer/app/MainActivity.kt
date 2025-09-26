// V2.2.3 — Fix Toggle Bonus (merge UI-only vs persisté) + Cap ±1 + debuffs Target branchés
// - Corrige la perte des bonus: au lieu de remplacer l'état par le persisté, on MERGE en conservant les champs UI-only.
// - Pop-in Toggle Bonus: états temp réinitialisés à l’ouverture (remember(profile)).
// - Rappels moteur (inchangés depuis 2.2.2):
//   • Cap ±1 sur Hit/Wound par rapport au profil de base (somme des deltas puis clamp).
//   • Debuffs Target (-1 Hit/Wound/Rend/Atk/Dmg) branchés; Ward branchée.
//   • Bonus profil (+1 Hit/Wound/Atk/Rend/Dmg) branchés; AoA branché.
// - Persistance: on ne persiste que units/target "de base" (AoA inclus, ward, debuffHit).

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

                // ---- MERGE plutôt que REPLACE : on garde les bonus UI-only ----
                LaunchedEffect(persisted.units) {
                    val incoming = persisted.units.map { it.toUi() }
                    units = mergeUnitsKeepUiOnly(old = units, incoming = incoming)
                }
                LaunchedEffect(persisted.target) {
                    val inc = persisted.target.toUi()
                    target = target.copy(
                        wardNeeded = inc.wardNeeded,
                        debuffHitEnabled = inc.debuffHitEnabled,
                        debuffHitValue = inc.debuffHitValue
                        // on NE touche PAS aux debuffs UI-only : debuffWound/Rend/Atk/Dmg
                    )
                }

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
                                appStateVM.updateTarget(t.toDomain()) // seuls ward/debuffHit sont persistés
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
    // Bonus UI-only (non persistés)
    val bonusHit: Boolean = false,
    val bonusWound: Boolean = false,
    val bonusAtk: Int = 0,       // 0..2 (ajout d’attaques)
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
    val debuffHitValue: Int = 1,
    // Debuffs Target UI-only (non persistés) — BRANCHÉS moteur
    val debuffWoundEnabled: Boolean = false, // +1 to wound threshold (dégrade)
    val debuffRendEnabled: Boolean = false,  // -1 sur le rend effectif (affaiblit l’AP)
    val debuffAtkEnabled: Boolean = false,   // -1 attaque (min 0)
    val debuffDmgEnabled: Boolean = false    // -1 damage (min 0)
)

/* =========================
   MERGE helpers (conserver bonus UI-only)
   ========================= */
private fun mergeUnitsKeepUiOnly(
    old: List<UnitEntry>,
    incoming: List<UnitEntry>
): List<UnitEntry> {
    if (old.isEmpty()) return incoming
    val size = max(old.size, incoming.size)
    val out = mutableListOf<UnitEntry>()
    for (i in 0 until size) {
        val o = old.getOrNull(i)
        val n = incoming.getOrNull(i)
        when {
            n == null && o != null -> out += o // garde l'ancien si incoming plus court
            n != null && o == null -> out += n // nouveau élément
            n != null && o != null -> {
                val mergedProfiles = mergeProfilesKeepUiOnly(o.profiles, n.profiles)
                out += n.copy(profiles = mergedProfiles) // name/active depuis persisté, bonus depuis o.profiles
            }
        }
    }
    return out
}

private fun mergeProfilesKeepUiOnly(
    old: List<AttackProfile>,
    incoming: List<AttackProfile>
): List<AttackProfile> {
    val size = max(old.size, incoming.size)
    val out = mutableListOf<AttackProfile>()
    for (i in 0 until size) {
        val o = old.getOrNull(i)
        val n = incoming.getOrNull(i)
        when {
            n == null && o != null -> out += o
            n != null && o == null -> out += n
            n != null && o != null -> {
                // on prend les stats/base depuis 'n' (persisté) et on greffe les bonus UI-only depuis 'o'
                out += n.copy(
                    bonusHit = o.bonusHit,
                    bonusWound = o.bonusWound,
                    bonusAtk = o.bonusAtk,
                    bonusRend = o.bonusRend,
                    bonusDmg = o.bonusDmg
                )
            }
        }
    }
    return out
}

/* =========================
   Moteur (AoA + Bonus + Debuffs Target + Cap ±1)
   ========================= */
private fun clamp2to6(x: Int) = x.coerceIn(2, 6)

private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}

// Cap ±1 sur Hit (par rapport au profil de base)
private fun effectiveHitWithCap(baseToHit: Int, aoa: Boolean, bonusHit: Boolean, targetDebuffHit: Int): Int {
    val sum = (if (aoa) -1 else 0) + (if (bonusHit) -1 else 0) + targetDebuffHit
    val capped = sum.coerceIn(-1, 1)
    return clamp2to6(baseToHit + capped)
}

// Cap ±1 sur Wound (par rapport au profil de base)
private fun effectiveWoundWithCap(baseToWound: Int, bonusWound: Boolean, targetDebuffWound: Boolean): Int {
    val sum = (if (bonusWound) -1 else 0) + (if (targetDebuffWound) +1 else 0)
    val capped = sum.coerceIn(-1, 1)
    return clamp2to6(baseToWound + capped)
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

    // ---- Valeurs effectives (bonus profil + debuffs Target) ----
    val extraAttacks = p.bonusAtk + (if (target.debuffAtkEnabled) -1 else 0)
    val effAttacksStat = (p.attacks + extraAttacks).coerceAtLeast(0)
    val totalAttacks = max(p.models, 0) * effAttacksStat
    if (totalAttacks == 0) return 0.0

    val targetDebuffHit = if (target.debuffHitEnabled) target.debuffHitValue else 0
    val effHit = effectiveHitWithCap(
        baseToHit = p.toHit,
        aoa = p.aoa,
        bonusHit = p.bonusHit,
        targetDebuffHit = targetDebuffHit
    )

    val effWound = effectiveWoundWithCap(
        baseToWound = p.toWound,
        bonusWound = p.bonusWound,
        targetDebuffWound = target.debuffWoundEnabled
    )

    val effRend = p.rend +
        (if (p.bonusRend) 1 else 0) +
        (if (target.debuffRendEnabled) -1 else 0)

    val effDamage = (p.damage +
        (if (p.bonusDmg) 1 else 0) -
        (if (target.debuffDmgEnabled) 1 else 0)).coerceAtLeast(0)

    // ---- Probabilités ----
    val p6 = 1.0 / 6.0
    val phNon6 = pHitNonSix(effHit)
    val pw = pWound(effWound)
    val pu = pUnsaved(baseSave, effRend)
    val ward = wardFactor(target.wardNeeded)

    // ---- EV hors 6 (pas d'effet spécial) ----
    val evNon6 = phNon6 * pw * pu * effDamage

    // ---- EV sur 6 au jet pour toucher ----
    val mult6 = if (p.twoHits) 2.0 else 1.0
    val pw6 = if (p.mortal || p.autoW) 1.0 else pw
    val pu6 = if (p.mortal) 1.0 else pu
    val ev6 = p6 * mult6 * pw6 * pu6 * effDamage

    return totalAttacks * (evNon6 + ev6) * ward
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
   Onglet Bonus — read-only résumé + Target global (branché)
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

                    // Colonne Target (globale — branchée)
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
                            TopLabeledCheckbox(
                                label = "-1 Wound",
                                checked = target.debuffWoundEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdate(target.copy(debuffWoundEnabled = enabled))
                                }
                            )
                            TopLabeledCheckbox(
                                label = "-1 Rend",
                                checked = target.debuffRendEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdate(target.copy(debuffRendEnabled = enabled))
                                }
                            )
                            TopLabeledCheckbox(
                                label = "-1 Atk",
                                checked = target.debuffAtkEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdate(target.copy(debuffAtkEnabled = enabled))
                                }
                            )
                            TopLabeledCheckbox(
                                label = "-1 Dmg",
                                checked = target.debuffDmgEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdate(target.copy(debuffDmgEnabled = enabled))
                                }
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

                        var unitNameText by remember(unit.name) { mutableStateOf(unit.name) }
                        OutlinedTextField(
                            value = unitNameText,
                            onValueChange = { unitNameText = it },
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
                        TextButton(onClick = { /* type toggle is per profile */ }) { Text("Type") }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "▼" else "▶") }
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

                var profileNameText by remember(profile.name) { mutableStateOf(profile.name) }
                OutlinedTextField(
                    value = profileNameText,
                    onValueChange = { profileNameText = it },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
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
            // IMPORTANT: ré-init à l’ouverture en se basant sur le profil courant
            var tempHit by remember(profile) { mutableStateOf(profile.bonusHit) }
            var tempWound by remember(profile) { mutableStateOf(profile.bonusWound) }
            var tempAtk by remember(profile) { mutableStateOf(profile.bonusAtk.coerceIn(0, 2)) }
            var tempRend by remember(profile) { mutableStateOf(profile.bonusRend) }
            var tempDmg by remember(profile) { mutableStateOf(profile.bonusDmg) }

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
// Seuls ward/debuffHit sont persistés; le reste des toggles Target et les bonus profil sont UI-only.
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
