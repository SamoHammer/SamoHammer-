@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SamoHammerApp() } }
    }
}

// -------------------------
// Modèles de données
// -------------------------
enum class AttackType { MELEE, SHOOT }
data class ToGate(val needed: Int = 4) // [2..6]

data class AttackProfile(
    val name: String = "Profil",
    val attackType: AttackType = AttackType.MELEE,
    val models: Int = 1,
    val attacks: String = "1",      // ex: "6", "3D6", "D3+3"
    val toHit: ToGate = ToGate(4),
    val toWound: ToGate = ToGate(4),
    val rend: Int = 0,              // rend positif -> dégrade la save
    val damage: String = "1",       // ex: "2", "D3", "D6"
    // Critiques (seuil naturel configurable, null = off)
    val crit2On: Int? = null,         // Crit 2 hits sur X+
    val critAutoWoundOn: Int? = null, // Crit auto wound sur X+
    val critMortalOn: Int? = null,    // Crit mortal sur X+
    val expanded: Boolean = true
)

data class UnitEntry(
    val name: String = "Nouvelle unité",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Profil 1", attackType = AttackType.MELEE)),
    val expanded: Boolean = true
)

data class TargetConfig(
    val wardEnabled: Boolean = false,
    val wardNeeded: Int = 5,           // [2..6]
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 1        // 0..3
)

// -------------------------
// Helpers UI (contrôles)
// -------------------------
private fun clampGate(x: Int) = x.coerceIn(2, 6)
private fun isValidPositiveIntOrZero(s: String) = s.toIntOrNull()?.let { it >= 0 } == true
private fun asIntOr(old: Int, s: String, gate: Boolean = false): Int {
    val v = s.toIntOrNull() ?: return old
    return if (gate) clampGate(v) else v
}
private fun isValidDiceExprOrIntForDisplay(s: String): Boolean {
    val t = s.trim().uppercase()
    if (t.isEmpty()) return false
    if (t.contains("..")) return false
    return true
}

// -------------------------
// Moteur — parsing & proba
// -------------------------
private fun expectedFromDice(expr: String): Double {
    val t = expr.trim().uppercase()
    t.toIntOrNull()?.let { return it.toDouble() }
    val regex = Regex("""^\s*(\d+)?D(\d+)\s*([+\-]\s*\d+)?\s*$""")
    val m = regex.matchEntire(t) ?: return 0.0
    val n = (m.groups[1]?.value?.replace(" ", "")?.toIntOrNull() ?: 1).coerceAtLeast(1)
    val faces = m.groups[2]!!.value.toInt().coerceAtLeast(2)
    val shift = m.groups[3]?.value?.replace(" ", "")?.toIntOrNull() ?: 0
    val evOneDie = (1.0 + faces) / 2.0
    return n * evOneDie + shift
}
private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}
private fun pCritHit(critOn: Int?, needed: Int): Double {
    if (critOn == null) return 0.0
    val gate = max(critOn, needed)
    return pGate(gate)
}
private fun pHit(needed: Int): Double = pGate(needed)
private fun pWound(needed: Int): Double = pGate(needed)
private fun pUnsaved(baseSave: Int?, rend: Int): Double {
    if (baseSave == null) return 1.0
    val eff = baseSave + rend
    if (eff >= 7) return 1.0
    val pSave = pGate(eff)
    return 1.0 - pSave
}
private fun wardFactor(target: TargetConfig): Double {
    if (!target.wardEnabled) return 1.0
    val pWard = pGate(target.wardNeeded)
    return 1.0 - pWard
}
private fun expectedDamageForProfile(
    profile: AttackProfile,
    target: TargetConfig,
    baseSave: Int?
): Double {
    val attacksEV = expectedFromDice(profile.attacks) * max(profile.models, 0)
    val dmgEV = expectedFromDice(profile.damage)
    val wardMul = wardFactor(target)

    val effHit = clampGate(if (target.debuffHitEnabled) profile.toHit.needed + target.debuffHitValue else profile.toHit.needed)
    val phit = pHit(effHit)

    val pMortalMain = pCritHit(profile.critMortalOn, effHit)
    val pAutoMainRaw = pCritHit(profile.critAutoWoundOn, effHit)
    val pAutoMain = max(0.0, min(phit - pMortalMain, pAutoMainRaw - pMortalMain))
    val pNonCritMain = max(0.0, phit - pMortalMain - pAutoMain)

    val pExtraHit = pCritHit(profile.crit2On, effHit)

    val pwound = pWound(profile.toWound.needed)
    val punsaved = pUnsaved(baseSave, profile.rend)

    val mortalDamage = attacksEV * pMortalMain       * dmgEV * wardMul
    val autoWoundDamage = attacksEV * pAutoMain      * punsaved * dmgEV * wardMul
    val normalDamage = attacksEV * pNonCritMain * pwound * punsaved * dmgEV * wardMul
    val extraDamage  = attacksEV * pExtraHit    * pwound * punsaved * dmgEV * wardMul

    return mortalDamage + autoWoundDamage + normalDamage + extraDamage
}
private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double {
    var sum = 0.0
    for (u in units) if (u.active) for (p in u.profiles) sum += expectedDamageForProfile(p, target, baseSave)
    return sum
}

// -------------------------
// App — 3 onglets
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
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { units = units + UnitEntry(name = "Unité ${units.size + 1}") },
                    text = { Text("Ajouter une unité") }
                )
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
// Onglet 1 — Profils
// -------------------------
@Composable
fun ProfilesTab(units: List<UnitEntry>, onUpdateUnits: (List<UnitEntry>) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(units) { idx, unit ->
            UnitCard(
                unit = unit,
                onChange = { changed -> onUpdateUnits(units.toMutableList().also { it[idx] = changed }) },
                onRemove = { onUpdateUnits(units.toMutableList().also { it.removeAt(idx) }) }
            )
        }
    }
}

@Composable
fun UnitCard(unit: UnitEntry, onChange: (UnitEntry) -> Unit, onRemove: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    unit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                FilterChip(
                    selected = unit.active,
                    onClick = { onChange(unit.copy(active = !unit.active)) },
                    label = { Text(if (unit.active) "Activée" else "Inactive") }
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onChange(unit.copy(expanded = !unit.expanded)) }) { Text(if (unit.expanded) "Réduire" else "Déplier") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRemove) { Text("Supprimer") }
            }

            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (unit.expanded) {
                    OutlinedTextField(
                        value = unit.name,
                        onValueChange = { onChange(unit.copy(name = it)) },
                        label = { Text("Nom de l’unité") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    unit.profiles.forEachIndexed { pIdx, p ->
                        ProfileCard(
                            profile = p,
                            onChange = { np ->
                                val updated = unit.profiles.toMutableList().also { it[pIdx] = np }
                                onChange(unit.copy(profiles = updated))
                            },
                            onRemove = {
                                val updated = unit.profiles.toMutableList().also { it.removeAt(pIdx) }
                                onChange(unit.copy(profiles = updated.ifEmpty { listOf(AttackProfile(name = "Profil 1")) }))
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedButton(onClick = {
                        val updated = unit.profiles + AttackProfile(
                            name = "Profil ${unit.profiles.size + 1}",
                            attackType = AttackType.MELEE
                        )
                        onChange(unit.copy(profiles = updated))
                    }) { Text("Ajouter un profil") }
                }
            }
        }
    }
}

@Composable
fun ProfileCard(profile: AttackProfile, onChange: (AttackProfile) -> Unit, onRemove: () -> Unit) {
    val headerColor = if (profile.attackType == AttackType.MELEE) Color(0xFFD05050) else Color(0xFF2F6FED)
    val bodyTint = if (profile.attackType == AttackType.MELEE) Color(0xFFF9E7E7) else Color(0xFFE7EFFF)

    ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(headerColor).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${profile.attackType}]  ${profile.name}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                    onClick = { onChange(profile.copy(expanded = !profile.expanded)) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text(if (profile.expanded) "Réduire" else "Déplier") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Supprimer") }
            }

            Column(Modifier.fillMaxWidth().background(bodyTint).padding(12.dp)) {
                if (profile.expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profile.name,
                            onValueChange = { onChange(profile.copy(name = it)) },
                            label = { Text("Nom du profil (ex: Hallebarde)") },
                            modifier = Modifier.weight(1f)
                        )
                        var ddOpen by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = ddOpen, onExpandedChange = { ddOpen = !ddOpen }) {
                            OutlinedTextField(
                                value = if (profile.attackType == AttackType.MELEE) "MELEE" else "SHOOT",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Type") },
                                modifier = Modifier.menuAnchor().width(140.dp)
                            )
                            ExposedDropdownMenu(expanded = ddOpen, onDismissRequest = { ddOpen = false }) {
                                DropdownMenuItem(text = { Text("MELEE") }, onClick = {
                                    onChange(profile.copy(attackType = AttackType.MELEE)); ddOpen = false
                                })
                                DropdownMenuItem(text = { Text("SHOOT") }, onClick = {
                                    onChange(profile.copy(attackType = AttackType.SHOOT)); ddOpen = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = profile.models.toString(),
                        onValueChange = { if (isValidPositiveIntOrZero(it)) onChange(profile.copy(models = it.toInt())) },
                        label = { Text("Models (nb figurines)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(180.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profile.attacks,
                            onValueChange = { s -> if (isValidDiceExprOrIntForDisplay(s)) onChange(profile.copy(attacks = s)) },
                            label = { Text("Attacks (ex: 6, 3D6)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = profile.rend.toString(),
                            onValueChange = { s -> onChange(profile.copy(rend = asIntOr(profile.rend, s))) },
                            label = { Text("Rend (+)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profile.toHit.needed.toString(),
                            onValueChange = { s -> onChange(profile.copy(toHit = profile.toHit.copy(needed = clampGate(asIntOr(profile.toHit.needed, s, true))))) },
                            label = { Text("To Hit (2..6)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = profile.toWound.needed.toString(),
                            onValueChange = { s -> onChange(profile.copy(toWound = profile.toWound.copy(needed = clampGate(asIntOr(profile.toWound.needed, s, true))))) },
                            label = { Text("To Wound (2..6)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = profile.damage,
                        onValueChange = { s -> if (isValidDiceExprOrIntForDisplay(s)) onChange(profile.copy(damage = s)) },
                        label = { Text("Damage (ex: 1, 2, D3, D3+3)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CritField("Crit 2 hits", profile.crit2On) { onChange(profile.copy(crit2On = it)) }
                        CritField("Crit auto wound", profile.critAutoWoundOn) { onChange(profile.copy(critAutoWoundOn = it)) }
                        CritField("Crit mortal", profile.critMortalOn) { onChange(profile.copy(critMortalOn = it)) }
                    }
                } else {
                    Text("A:${profile.attacks}  ${profile.toHit.needed}+/${profile.toWound.needed}+  R${profile.rend}  D${profile.damage}")
                }
            }
        }
    }
}

@Composable
private fun CritField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var enabled by remember { mutableStateOf(value != null) }
    val gate = value ?: 6
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = {
                enabled = it; onChange(if (it) 6 else null)
            })
            Text(label)
        }
        if (enabled) {
            OutlinedTextField(
                value = gate.toString(),
                onValueChange = { s -> onChange(clampGate(asIntOr(gate, s, true))) },
                label = { Text("Seuil (2..6)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

// -------------------------
// Onglet 2 — Target
// -------------------------
@Composable
fun TargetTab(target: TargetConfig, onUpdate: (TargetConfig) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cible (défenseur)", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = target.wardEnabled, onCheckedChange = { onUpdate(target.copy(wardEnabled = it)) })
            Text("Ward")
            if (target.wardEnabled) {
                OutlinedTextField(
                    value = target.wardNeeded.toString(),
                    onValueChange = { s -> onUpdate(target.copy(wardNeeded = clampGate(asIntOr(target.wardNeeded, s, true)))) },
                    label = { Text("Ward (2..6)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = target.debuffHitEnabled, onCheckedChange = { onUpdate(target.copy(debuffHitEnabled = it)) })
            Text("Debuff Hit (-Y)")
            if (target.debuffHitEnabled) {
                OutlinedTextField(
                    value = target.debuffHitValue.toString(),
                    onValueChange = { s ->
                        val v = s.toIntOrNull()?.coerceIn(0, 3) ?: target.debuffHitValue
                        onUpdate(target.copy(debuffHitValue = v))
                    },
                    label = { Text("Malus (0..3)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        Divider()
        Text("Ces réglages s’appliquent à la simulation (onglet 3).", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

// -------------------------
// Onglet 3 — Simulations
// -------------------------
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val rows = remember(units, target) {
        val saves = listOf(2, 3, 4, 5, 6)
        val data = mutableListOf<Pair<String, Double>>()
        for (s in saves) data += "${s}+" to expectedDamageAll(units, target, s)
        data += "—" to expectedDamageAll(units, target, null) // no save
        data
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Espérance de dégâts", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        SimulationTable(rows)
    }
}

@Composable
private fun SimulationTable(rows: List<Pair<String, Double>>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Save", style = MaterialTheme.typography.titleMedium)
                Text("Dégâts moyens", style = MaterialTheme.typography.titleMedium)
            }
            Divider(Modifier.padding(vertical = 8.dp))
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text(String.format("%.2f", value))
                }
            }
        }
    }
}
