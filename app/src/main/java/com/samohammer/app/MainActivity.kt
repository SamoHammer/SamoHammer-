package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

// -------------------------
// Activity
// -------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SamoHammerApp() } }
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
    val damage: Int = 1,
    val expanded: Boolean = true
)

data class UnitEntry(
    val name: String = "Nouvelle unité",
    val active: Boolean = true,
    val profiles: List<AttackProfile> = listOf(AttackProfile(name = "Profil 1")),
    val expanded: Boolean = true
)

data class TargetConfig(
    val wardNeeded: Int = 0,          // 0 = off, sinon 2..6
    val debuffHitEnabled: Boolean = false,
    val debuffHitValue: Int = 1       // 0..3
)

// -------------------------
// Helpers UI
// -------------------------
private fun clampGate(x: Int) = x.coerceIn(2, 6)
private fun isValidPositiveIntOrZero(s: String) = s.toIntOrNull()?.let { it >= 0 } == true
private fun asIntOr(old: Int, s: String, gate: Boolean = false): Int {
    val v = s.toIntOrNull() ?: return old
    return if (gate) clampGate(v) else v
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
    val eff = clampGate(needed + debuff)
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
private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double {
    var sum = 0.0
    for (u in units) {
        if (!u.active) continue
        for (p in u.profiles) sum += expectedDamageForProfile(p, target, baseSave)
    }
    return sum
}

// -------------------------
// App à 3 onglets
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
                onChange = { changed ->
                    onUpdateUnits(units.toMutableList().also { it[idx] = changed })
                },
                onRemove = {
                    onUpdateUnits(units.toMutableList().also { it.removeAt(idx) })
                }
            )
        }
        item {
            Button(onClick = { onUpdateUnits(units + UnitEntry(name = "Unité ${units.size + 1}")) }) {
                Text("Ajouter une unité")
            }
        }
    }
}

@Composable
fun UnitCard(unit: UnitEntry, onChange: (UnitEntry) -> Unit, onRemove: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp)) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    unit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(onClick = { onChange(unit.copy(active = !unit.active)) }) {
                    Text(if (unit.active) "Désactiver" else "Activer")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onChange(unit.copy(expanded = !unit.expanded)) }) {
                    Text(if (unit.expanded) "Réduire" else "Déplier")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRemove) { Text("Supprimer") }
            }

            // Body
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
                                onChange(
                                    unit.copy(
                                        profiles = updated.ifEmpty { listOf(AttackProfile(name = "Profil 1")) }
                                    )
                                )
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
fun ProfileCard(
    profile: AttackProfile,
    onChange: (AttackProfile) -> Unit,
    onRemove: () -> Unit
) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = { onChange(profile.copy(name = it)) },
                        label = { Text("Nom du profil (ex: Hallebarde)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    // Switch MELEE/SHOOT
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onChange(profile.copy(attackType = AttackType.MELEE)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (profile.attackType == AttackType.MELEE) Color(0xFFD05050) else MaterialTheme.colorScheme.secondary
                            )
                        ) { Text("MELEE") }
                        Button(
                            onClick = { onChange(profile.copy(attackType = AttackType.SHOOT)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (profile.attackType == AttackType.SHOOT) Color(0xFF2F6FED) else MaterialTheme.colorScheme.secondary
                            )
                        ) { Text("SHOOT") }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = profile.models.toString(),
                        onValueChange = { if (isValidPositiveIntOrZero(it)) onChange(profile.copy(models = it.toInt())) },
                        label = { Text("Models (nb figurines)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(200.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profile.attacks.toString(),
                            onValueChange = { s -> s.toIntOrNull()?.let { onChange(profile.copy(attacks = it)) } },
                            label = { Text("Attacks") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = profile.rend.toString(),
                            onValueChange = { s -> s.toIntOrNull()?.let { onChange(profile.copy(rend = it)) } },
                            label = { Text("Rend (+)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profile.toHit.toString(),
                            onValueChange = { s -> s.toIntOrNull()?.let { onChange(profile.copy(toHit = clampGate(it))) } },
                            label = { Text("To Hit (2..6)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = profile.toWound.toString(),
                            onValueChange = { s -> s.toIntOrNull()?.let { onChange(profile.copy(toWound = clampGate(it))) } },
                            label = { Text("To Wound (2..6)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = profile.damage.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { onChange(profile.copy(damage = it)) } },
                        label = { Text("Damage") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "A:${profile.attacks}  ${profile.toHit}+/${profile.toWound}+  R${profile.rend}  D${profile.damage}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
            val wardChecked = target.wardNeeded in 2..6
            Checkbox(
                checked = wardChecked,
                onCheckedChange = { checked ->
                    onUpdate(target.copy(wardNeeded = if (checked) 5 else 0))
                }
            )
            Text("Ward")
            if (wardChecked) {
                OutlinedTextField(
                    value = target.wardNeeded.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { onUpdate(target.copy(wardNeeded = it.coerceIn(2, 6))) } },
                    label = { Text("Seuil (2..6)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(
                checked = target.debuffHitEnabled,
                onCheckedChange = { onUpdate(target.copy(debuffHitEnabled = it)) }
            )
            Text("Debuff Hit (-Y)")
            if (target.debuffHitEnabled) {
                OutlinedTextField(
                    value = target.debuffHitValue.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { onUpdate(target.copy(debuffHitValue = it.coerceIn(0, 3))) }
                    },
                    label = { Text("Malus (0..3)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        Divider()
        Text("Ces réglages s’appliquent à la simulation (onglet 3).", color = Color.Gray)
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
        Text("Espérance de dégâts (unités actives)", style = MaterialTheme.typography.titleLarge)
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
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label)
                    Text(String.format("%.2f", value))
                }
            }
        }
    }
}
