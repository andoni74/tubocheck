package com.andoni.tubocheck

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class Observation(
    val id: String = UUID.randomUUID().toString(),
    var code: String,
    var letter: String,
    var tube: String,
    var ok: Boolean,
    var note: String,
    var date: String
) {
    fun fullCode() = listOf(code, letter, tube).filter { it.isNotBlank() }.joinToString(" ")
}

private class ObservationStore(context: Context) {
    private val prefs = context.getSharedPreferences("tubocheck", Context.MODE_PRIVATE)
    private val key = "observations"

    fun load(): MutableList<Observation> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Observation(
                id = o.optString("id", UUID.randomUUID().toString()),
                code = o.optString("code"),
                letter = o.optString("letter"),
                tube = o.optString("tube"),
                ok = o.optBoolean("ok", true),
                note = o.optString("note"),
                date = o.optString("date")
            )
        }
    }

    fun save(list: List<Observation>) {
        val arr = JSONArray()
        list.forEach { o ->
            arr.put(JSONObject().apply {
                put("id", o.id); put("code", o.code); put("letter", o.letter)
                put("tube", o.tube); put("ok", o.ok); put("note", o.note); put("date", o.date)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ObservationStore(this)
        setContent { TuboCheckApp(store) }
    }
}

@Composable
private fun TuboCheckApp(store: ObservationStore) {
    var screen by remember { mutableStateOf("home") }
    var observations by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Observation?>(null) }

    fun persist() { store.save(observations) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF0B8DFF))) {
        when (screen) {
            "home" -> HomeScreen(
                onNew = { editing = null; screen = "form" },
                onSearch = { screen = "search" }
            )
            "form" -> ObservationFormScreen(
                initial = editing,
                onBack = { screen = "home" },
                onSave = { item ->
                    val index = observations.indexOfFirst { it.id == item.id }
                    observations = observations.toMutableList().apply {
                        if (index >= 0) this[index] = item else add(0, item)
                    }
                    persist(); screen = "search"
                }
            )
            "search" -> SearchScreen(
                observations = observations,
                onBack = { screen = "home" },
                onEdit = { item -> editing = item.copy(); screen = "form" },
                onDelete = { item -> observations = observations.filterNot { it.id == item.id }.toMutableList(); persist() }
            )
        }
    }
}

@Composable
private fun HomeScreen(onNew: () -> Unit, onSearch: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF07131E))) {
        Image(
            painter = painterResource(R.drawable.tubocheck_cover),
            contentDescription = "TuboCheck",
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Text("TUBOCHECK", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("CONTROL DE CALIDAD · ANDONI", fontSize = 15.sp, color = Color(0xFF55B7FF), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.AddCircleOutline, null); Spacer(Modifier.width(10.dp)); Text("NUEVA OBSERVACIÓN", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Icon(Icons.Default.Search, null); Spacer(Modifier.width(10.dp)); Text("BUSCAR TUBO", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("v1.0 · Datos guardados en este móvil", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObservationFormScreen(initial: Observation?, onBack: () -> Unit, onSave: (Observation) -> Unit) {
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var letter by remember { mutableStateOf(initial?.letter ?: "") }
    var tube by remember { mutableStateOf(initial?.tube ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var ok by remember { mutableStateOf(initial?.ok ?: true) }
    val canSave = code.isNotBlank() && letter.isNotBlank() && tube.isNotBlank()

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (initial == null) "Nueva observación" else "Editar observación") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("IDENTIFICACIÓN DEL TUBO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(code, { code = it }, label = { Text("Código") }, singleLine = true, modifier = Modifier.weight(1.5f))
                OutlinedTextField(letter, { letter = it.uppercase(Locale.getDefault()) }, label = { Text("Letra") }, singleLine = true, modifier = Modifier.weight(0.7f))
                OutlinedTextField(tube, { tube = it }, label = { Text("Tubo") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text("ESTADO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = ok, onClick = { ok = true }, label = { Text("✓  OK") }, modifier = Modifier.weight(1f))
                FilterChip(selected = !ok, onClick = { ok = false }, label = { Text("✕  NO OK") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(note, { note = it }, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth().height(150.dp), minLines = 5)
            Spacer(Modifier.weight(1f))
            Button(enabled = canSave, onClick = {
                val date = initial?.date ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                onSave(Observation(initial?.id ?: UUID.randomUUID().toString(), code.trim(), letter.trim(), tube.trim(), ok, note.trim(), date))
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("GUARDAR", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(observations: List<Observation>, onBack: () -> Unit, onEdit: (Observation) -> Unit, onDelete: (Observation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().uppercase(Locale.getDefault())
    val exact = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val results = if (q.isBlank()) observations else observations.filter { o ->
        val full = o.fullCode().uppercase(Locale.getDefault())
        full.contains(q) || o.code.uppercase(Locale.getDefault()).contains(q) || (exact.size >= 2 && full.contains(exact.joinToString(" ")))
    }
    val exactId = observations.firstOrNull { it.fullCode().uppercase(Locale.getDefault()) == q }?.id

    Scaffold(topBar = {
        TopAppBar(title = { Text("Buscar tubos") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Código, letra o tubo") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
            Text("${results.size} registro(s)", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.id }) { item ->
                    ObservationCard(item, highlighted = exactId == item.id, onEdit = { onEdit(item) }, onDelete = { onDelete(item) })
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(item: Observation, highlighted: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val statusColor = if (item.ok) Color(0xFF16A34A) else Color(0xFFDC2626)
    val border = if (highlighted) Color(0xFF168BFF) else Color.Transparent
    Card(modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(if (highlighted) 3.dp else 0.dp, border), colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFF0D2B45) else Color(0xFFF4F6F8))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.code, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = if (highlighted) Color(0xFF4DB2FF) else Color(0xFF17212B))
                Spacer(Modifier.width(8.dp)); Text(item.letter, fontWeight = FontWeight.Bold, color = if (highlighted) Color.White else Color.DarkGray)
                Spacer(Modifier.width(8.dp)); Text(item.tube, fontWeight = FontWeight.ExtraBold, color = statusColor, fontSize = 18.sp)
                Spacer(Modifier.weight(1f)); Text(if (item.ok) "OK" else "NO OK", color = statusColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Text(if (item.note.isBlank()) "Sin observación" else item.note, color = if (highlighted) Color.White else Color(0xFF334155))
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.date, fontSize = 12.sp, color = if (highlighted) Color(0xFFB6C8D8) else Color.Gray)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Eliminar") }
            }
        }
    }
}
