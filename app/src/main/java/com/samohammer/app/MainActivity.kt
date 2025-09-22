// V1.2.1

package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

// Lists
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed

// TextField
import androidx.compose.foundation.text.KeyboardOptions

// Material3
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue

// UI utils
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// Icônes (Material Icons)
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight

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
    val rend: Int = 0,      // >= 0 (rend positif = dégrade la save)
    val damage: Int = 1,
    // Crits
    val critTrigger: Int = 6,           // 2..6 (naturel)
    val critTwoHits: Boolean = false,   // Crit 2 hits
    val critAutoWound: Boolean = false, // Crit auto wound
    val critMortal: Boolean = false,    // Crit mortal
    // UI
    val active: Boolean = true,
    val expanded: Boolean = true
)

data class UnitEntry(
    val name: String = "Nouvelle unité",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Profil 1")),
    // UI
    val expanded: Boolean = true
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

private fun pHitEffective(toHit: Int, debuff: Int): Double {
    val eff = clamp2to6(toHit + debuff)
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

/**
 * EV pour un profil, avec Crits (priorité : Mortal > AutoWound > TwoHits).
 * Rappels :
 * - Déclencheur = résultat naturel (non modifié).
 * - Crits uniquement On-Hit.
 * - Un critique compte comme une touche réussie.
 */
private fun expectedDamageForProfile(
    p: AttackProfile,
    target: TargetConfig,
    baseSave: Int?
): Double {
    val attacks = max(p.models, 0) * max(p.attacks, 0)
    if (attacks == 0) return 0.0

    val debuff = if (target.debuffHitEnabled) target.debuffHitValue else 0
    val effToHit = clamp2to6(p.toHit + debuff)
    val pHit = pHitEffective(p.toHit, debuff)

    // proba que le jet naturel == trigger ET valide la touche (trigger >= seuil effectif)
    val pCrit = if (p.critTrigger in 2..6 && p.critTrigger >= effToHit) (1.0 / 6.0) else 0.0

    val pW = pWound(p.toWound)
    val pU = pUnsaved(baseSave, p.rend)
    val wF = wardFactor(target.wardNeeded)

    val useMortal = p.critMortal
    val useAuto = !useMortal && p.critAutoWound
    val useTwoHits = !useMortal && !useAuto && p.critTwoHits

    val expCrits = attacks * pCrit
    val expHits = attacks * pHit

    return when {
        useMortal -> {
            // Crit → mortels (ignore la save), ward seulement
            val normals = (expHits - expCrits).coerceAtLeast(0.0)
            val normalUnsaved = normals * pW * pU
            val mortalDamage = expCrits * p.damage * wF
            (normalUnsaved * p.damage + mortalDamage)
        }
        useAuto -> {
            // Crit → auto-wound (save s’applique)
            val normals = (expHits - expCrits).coerceAtLeast(0.0)
            val normalUnsaved = normals * pW * pU
            val autoUnsaved = expCrits * pU
            (normalUnsaved + autoUnsaved) * p.damage * wF
        }
        useTwoHits -> {
            // Crit → +1 jet de blessure
            val extraWoundRolls = expCrits
            val woundRolls = (expHits + extraWoundRolls) * pW
            val unsaved = woundRolls * pU
            unsaved * p.damage * wF
        }
        else -> {
            val unsaved = expHits * pW * pU
            unsaved * p.damage * wF
        }
    }
}

private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double =
    units.filter { it.active }
        .flatMap { it.profiles.filter { pr -> pr.active } }
        .sumOf { expectedDamageForProfile(it, target, baseSave) }

// NEW: EV par unité (pour SimulationTab par unité)
private fun expectedDamageForUnit(unit: UnitEntry, target: TargetConfig, baseSave: Int?): Double =
    if (!unit.active) 0.0
    else unit.profiles.filter { it.active }.sumOf { expectedDamageForProfile(it, target, baseSave) }

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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Header Unité (chevron à droite)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = unit.active,
                            onCheckedChange = { checked ->
                                onUpdateUnits(units.toMutableList().also { it[unitIndex] = unit.copy(active = checked) })
                            }
                        )

                        OutlinedTextField(
                            value = unit.name,
                            onValueChange = { newName ->
                                onUpdateUnits(units.toMutableList().also { it[unitIndex] = unit.copy(name = newName) })
                            },
                            label = { Text("Nom de l’unité") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                onUpdateUnits(
                                    units.toMutableList().also { list ->
                                        val newProfiles = unit.profiles + AttackProfile(name = "Profil ${unit.profiles.size + 1}")
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

                        IconButton(onClick = {
                            onUpdateUnits(units.toMutableList().also { it[unitIndex] = unit.copy(expanded = !unit.expanded) })
                        }) {
                            Icon(
                                imageVector = if (unit.expanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
                                contentDescription = "toggle unit"
                            )
                        }
                    }

                    if (unit.expanded) {
                        unit.profiles.forEachIndexed { pIndex, profile ->
                            ProfileEditor(
                                profile = profile,
                                onChange = { updated ->
                                    onUpdateUnits(
                                        units.toMutableList().also { list ->
                                            val newProfiles = unit.profiles.toMutableList().also { it[pIndex] = updated }
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Ligne titre + type + actions + chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = profile.active,
                    onCheckedChange = { checked -> onChange(profile.copy(active = checked)) }
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

                TextButton(onClick = onRemove) { Text("Supprimer profil") }

                IconButton(onClick = { onChange(profile.copy(expanded = !profile.expanded)) }) {
                    Icon(
                        imageVector = if (profile.expanded) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight,
                        contentDescription = "toggle profile"
                    )
                }
            }

            if (profile.expanded) {
                // Paramètres
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(
                            label = "Models",
                            value = profile.models,
                            onValue = { v -> onChange(profile.copy(models = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(120.dp)
                        )
                        NumberField(
                            label = "Attacks",
                            value = profile.attacks,
                            onValue = { v -> onChange(profile.copy(attacks = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        GateField2to6(
                            label = "Hit (2..6)",
                            value = profile.toHit,
                            onValue = { v -> onChange(profile.copy(toHit = v)) },
                            modifier = Modifier.width(120.dp)
                        )
                        GateField2to6(
                            label = "Wound (2..6)",
                            value = profile.toWound,
                            onValue = { v -> onChange(profile.copy(toWound = v)) },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(
                            label = "Rend (+)",
                            value = profile.rend,
                            onValue = { v -> onChange(profile.copy(rend = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(120.dp)
                        )
                        NumberField(
                            label = "Damage",
                            value = profile.damage,
                            onValue = { v -> onChange(profile.copy(damage = v.coerceAtLeast(0))) },
                            modifier = Modifier.width(120.dp)
                        )
                    }

                    Divider()

                    // CRITS
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GateField2to6(
                            label = "Crit trigger (2..6)",
                            value = profile.critTrigger,
                            onValue = { v -> onChange(profile.copy(critTrigger = v)) },
                            modifier = Modifier.width(140.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Checkbox(
                            checked = profile.critTwoHits,
                            onCheckedChange = { onChange(profile.copy(critTwoHits = it)) }
                        )
                        Text("Crit 2 hits")
                        Checkbox(
                            checked = profile.critAutoWound,
                            onCheckedChange = { onChange(profile.copy(critAutoWound = it)) }
                        )
                        Text("Crit Auto-Wound")
                        Checkbox(
                            checked = profile.critMortal,
                            onCheckedChange = { onChange(profile.copy(critMortal = it)) }
                        )
                        Text("Crit Mortal")
                    }

                    Text(
                        text = "Si plusieurs sont cochés : priorité Mortal > Auto-Wound > Two Hits.",
                        style = MaterialTheme.typography.bodySmall
                    )
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

// Gate 2..6 — texte local + mise à jour seulement si 2..6
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
// Onglet Simulations (par unité active)
// -------------------------
@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Espérance de dégâts (par unité active)", style = MaterialTheme.typography.titleMedium)

        units.filter { it.active }.forEach { unit ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(unit.name, style = MaterialTheme.typography.titleSmall)
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
