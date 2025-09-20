package com.samohammer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SamoHammerApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamoHammerApp() {
    var tabIndex by remember { mutableStateOf(0) }
    var units by remember { mutableStateOf(listOf<UnitEntry>()) }
    var target by remember { mutableStateOf(TargetConfig()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("SamoHammer") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    label = { Text("Profils") },
                    icon = { }
                )
                NavigationBarItem(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    label = { Text("Target") },
                    icon = { }
                )
                NavigationBarItem(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    label = { Text("Simulation") },
                    icon = { }
                )
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (tabIndex) {
                0 -> ProfilesTab(units) { units = it }
                1 -> TargetTab(target) { target = it }
                2 -> SimulationTab(units, target)
            }
        }
    }
}

@Composable
fun ProfilesTab(units: List<UnitEntry>, onChange: (List<UnitEntry>) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(units.size) { idx ->
            UnitCard(units[idx],
                onChange = { updated ->
                    onChange(units.toMutableList().also { it[idx] = updated })
                },
                onRemove = {
                    onChange(units.toMutableList().also { it.removeAt(idx) })
                }
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            Button(onClick = {
                onChange(units + UnitEntry("Nouvelle unité"))
            }) { Text("Ajouter une unité") }
        }
    }
}

@Composable
fun UnitCard(unit: UnitEntry, onChange: (UnitEntry) -> Unit, onRemove: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(unit.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(checked = unit.enabled, onCheckedChange = { onChange(unit.copy(enabled = it)) })
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRemove) { Text("Supprimer") }
            }
            Spacer(Modifier.height(8.dp))

            unit.profiles.forEachIndexed { idx, profile ->
                ProfileCard(
                    profile,
                    onChange = { p -> onChange(unit.copy(profiles = unit.profiles.toMutableList().also { it[idx] = p })) },
                    onRemove = { onChange(unit.copy(profiles = unit.profiles.toMutableList().also { it.removeAt(idx) })) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = {
                onChange(unit.copy(profiles = unit.profiles + AttackProfile(name = "Nouveau profil")))
            }) { Text("Ajouter un profil") }
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
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = { onChange(profile.copy(name = it)) },
                        label = { Text("Nom du profil (ex: Hallebarde)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Type : ")
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = profile.attackType == AttackType.MELEE,
                            onClick = { onChange(profile.copy(attackType = AttackType.MELEE)) },
                            label = { Text("MELEE") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = profile.attackType == AttackType.SHOOT,
                            onClick = { onChange(profile.copy(attackType = AttackType.SHOOT)) },
                            label = { Text("SHOOT") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile.models.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onChange(profile.copy(models = it)) } },
                        label = { Text("Models") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile.attacks.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onChange(profile.copy(attacks = it)) } },
                        label = { Text("Attacks") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile.toHit.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onChange(profile.copy(toHit = it)) } },
                        label = { Text("To Hit (ex: 3 pour 3+)") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile.toWound.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onChange(profile.copy(toWound = it)) } },
                        label = { Text("To Wound (ex: 4 pour 4+)") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile.rend.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onChange(profile.copy(rend = it)) } },
                        label = { Text("Rend") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile.damage.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { onChange(profile.copy(damage = it)) } },
                        label = { Text("Damage") }
                    )

                    // --- Critiques ---
                    // (À brancher plus tard quand on ajoute les champs au data model)
                    // Exemple futur : CritField("Crit 2 hits", ...) etc.
                    // Pour l’instant on laisse un espace visuel.
                    Spacer(Modifier.height(4.dp))
                    Text("Critiques : non configurés dans ce build", color = Color.Gray)
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

@Composable
fun TargetTab(target: TargetConfig, onChange: (TargetConfig) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cible (défenseur)", style = MaterialTheme.typography.titleLarge)

        // Ward
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val wardChecked = target.wardNeeded in 2..6
            Checkbox(
                checked = wardChecked,
                onCheckedChange = { checked ->
                    onChange(target.copy(wardNeeded = if (checked) 5 else 0))
                }
            )
            Text("Ward")
            if (wardChecked) {
                OutlinedTextField(
                    value = target.wardNeeded.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { onChange(target.copy(wardNeeded = it.coerceIn(2, 6))) } },
                    label = { Text("Seuil (2..6)") },
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        // Debuff Hit
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(
                checked = target.debuffHitEnabled,
                onCheckedChange = { onChange(target.copy(debuffHitEnabled = it)) }
            )
            Text("Debuff Hit (-Y)")
            if (target.debuffHitEnabled) {
                OutlinedTextField(
                    value = target.debuffHitValue.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { onChange(target.copy(debuffHitValue = it.coerceIn(0, 3))) }
                    },
                    label = { Text("Malus (0..3)") },
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        Divider()
        Text(
            "Ces réglages s’appliquent à l’onglet Simulations.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun SimulationTab(units: List<UnitEntry>, target: TargetConfig) {
    val rows = remember(units, target) {
        val saves = listOf(2, 3, 4, 5, 6)
        val data = mutableListOf<Pair<String, Double>>()
        for (s in saves) data += "${s}+" to expectedDamageAll(units, target, baseSave = s)
        data += "—" to expectedDamageAll(units, target, baseSave = null) // no save
        data
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Espérance de dégâts (toutes unités actives)", style = MaterialTheme.typography.titleLarge)
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

// =========================
// Moteur simplifié (sans Crit pour ce build)
// =========================

private fun pGate(needed: Int): Double = when {
    needed <= 1 -> 1.0
    needed >= 7 -> 0.0
    else -> (7 - needed) / 6.0
}

private fun pHit(needed: Int, debuff: Int): Double {
    val eff = (needed + if (debuff > 0) debuff else 0).coerceIn(2, 7)
    return pGate(eff)
}

private fun pWound(needed: Int): Double = pGate(needed)

private fun pUnsaved(baseSave: Int?, rend: Int): Double {
    if (baseSave == null) return 1.0
    val eff = baseSave + rend
    if (eff >= 7) return 1.0
    val pSave = pGate(eff)
    return 1.0 - pSave
}

private fun wardFactor(wardNeeded: Int): Double {
    if (wardNeeded !in 2..6) return 1.0
    val pWard = pGate(wardNeeded)
    return 1.0 - pWard
}

private fun expectedDamageForProfile(profile: AttackProfile, target: TargetConfig, baseSave: Int?): Double {
    val attacks = (profile.models.coerceAtLeast(0)) * (profile.attacks.coerceAtLeast(0))
    if (attacks == 0) return 0.0

    val ph = pHit(profile.toHit, if (target.debuffHitEnabled) target.debuffHitValue else 0)
    val pw = pWound(profile.toWound)
    val pu = pUnsaved(baseSave, profile.rend)
    val ward = wardFactor(target.wardNeeded)

    return attacks * ph * pw * pu * profile.damage * ward
}

private fun expectedDamageAll(units: List<UnitEntry>, target: TargetConfig, baseSave: Int?): Double {
    var sum = 0.0
    for (u in units) {
        if (!u.enabled) continue
        for (p in u.profiles) {
            sum += expectedDamageForProfile(p, target, baseSave)
        }
    }
    return sum
}
                    
