package com.andoni.tubocheck

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanContract
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class Observation(
    val id: String = UUID.randomUUID().toString(),
    var fullCode: String,
    var ok: Boolean,
    var note: String,
    var date: String
)

private class ObservationStore(context: Context) {
    private val prefs = context.getSharedPreferences("tubocheck", Context.MODE_PRIVATE)
    private val key = "observations"

    fun load(): MutableList<Observation> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)

        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)

            // Compatible con los registros antiguos
            val oldCode = o.optString("code")
            val oldLetter = o.optString("letter")
            val oldTube = o.optString("tube")

            val completeCode = o.optString("fullCode").ifBlank {
                listOf(oldCode, oldLetter, oldTube)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }

            Observation(
                id = o.optString("id", UUID.randomUUID().toString()),
                fullCode = completeCode,
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
                put("id", o.id)
                put("fullCode", o.fullCode)
                put("ok", o.ok)
                put("note", o.note)
                put("date", o.date)
            })
        }

        prefs.edit().putString(key, arr.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {

    private var qrCallback: ((String) -> Unit)? = null

    private val qrScanner =
        registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                qrCallback?.invoke(result.contents.trim())
            }
            qrCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ObservationStore(this)

        setContent {
            TuboCheckApp(
                store = store,
                onScanQr = { callback ->
                    qrCallback = callback

                    val options = ScanOptions().apply {
    setPrompt("Apunta al código del tubo")
    setBeepEnabled(true)
    setCameraId(0)
    setOrientationLocked(true)
    setDesiredBarcodeFormats(
    ScanOptions.DATA_MATRIX
)
}
                    

                    qrScanner.launch(options)
                }
            )
        }
    }
}

@Composable
private fun TuboCheckApp(
    store: ObservationStore,
    onScanQr: ((String) -> Unit) -> Unit
) {
    var screen by remember { mutableStateOf("home") }
    var observations by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Observation?>(null) }

    fun persist() {
        store.save(observations)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF0B8DFF)
        )
    ) {
        when (screen) {

            "home" -> HomeScreen(
                onNew = {
                    editing = null
                    screen = "form"
                },
                onSearch = {
                    screen = "search"
                }
            )

            "form" -> ObservationFormScreen(
                initial = editing,
                onBack = {
                    screen = "home"
                },
                onScanQr = onScanQr,
                onSave = { item ->

                    val index =
                        observations.indexOfFirst { it.id == item.id }

                    observations =
                        observations.toMutableList().apply {
                            if (index >= 0) {
                                this[index] = item
                            } else {
                                add(0, item)
                            }
                        }

                    persist()
                    screen = "search"
                }
            )

            "search" -> SearchScreen(
                observations = observations,
                onBack = {
                    screen = "home"
                },
                onEdit = { item ->
                    editing = item.copy()
                    screen = "form"
                },
                onDelete = { item ->
                    observations =
                        observations
                            .filterNot { it.id == item.id }
                            .toMutableList()

                    persist()
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onNew: () -> Unit,
    onSearch: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07131E))
    ) {

        Image(
            painter = painterResource(R.drawable.tubocheck_cover),
            contentDescription = "TuboCheck",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.weight(1f))

            Text(
                "TUBOCHECK",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                "CONTROL DE CALIDAD · ANDONI",
                fontSize = 15.sp,
                color = Color(0xFF55B7FF),
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.AddCircleOutline, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "NUEVO REGISTRO",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "BUSCAR TUBO",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "v1.0 · Datos guardados en este móvil",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObservationFormScreen(
    initial: Observation?,
    onBack: () -> Unit,
    onScanQr: ((String) -> Unit) -> Unit,
    onSave: (Observation) -> Unit
) {
    var fullCode by remember {
        mutableStateOf(initial?.fullCode ?: "")
    }

    var note by remember {
        mutableStateOf(initial?.note ?: "")
    }

    var ok by remember {
        mutableStateOf(initial?.ok ?: true)
    }

    val canSave = fullCode.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial == null)
                            "Nuevo registro"
                        else
                            "Editar registro"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->

        Column(
            Modifier
                .padding(pad)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                "IDENTIFICACIÓN DEL TUBO",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = fullCode,
                onValueChange = {
                    fullCode = it
                },
                label = {
                    Text("Código del tubo")
                },
                placeholder = {
                    Text("Ejemplo: 39145 A 1-1")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onScanQr { scannedCode ->
                        fullCode = scannedCode
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "ESCANEAR QR",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "ESTADO",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                FilterChip(
                    selected = ok,
                    onClick = {
                        ok = true
                    },
                    label = {
                        Text("✓  OK")
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = !ok,
                    onClick = {
                        ok = false
                    },
                    label = {
                        Text("✕  NO OK")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                },
                label = {
                    Text("Observación")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                minLines = 5
            )

            Spacer(Modifier.weight(1f))

            Button(
                enabled = canSave,
                onClick = {

                    val date =
                        initial?.date
                            ?: SimpleDateFormat(
                                "dd/MM/yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date())

                    onSave(
                        Observation(
                            id = initial?.id
                                ?: UUID.randomUUID().toString(),
                            fullCode = fullCode.trim(),
                            ok = ok,
                            note = note.trim(),
                            date = date
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    "GUARDAR",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    observations: List<Observation>,
    onBack: () -> Unit,
    onEdit: (Observation) -> Unit,
    onDelete: (Observation) -> Unit
) {
    var query by remember {
        mutableStateOf("")
    }

    val q = query.trim()
        .uppercase(Locale.getDefault())

    val results =
        if (q.isBlank()) {
            observations
        } else {
            observations.filter { o ->
                o.fullCode
                    .uppercase(Locale.getDefault())
                    .contains(q)
            }
        }

    val exactId =
        observations.firstOrNull {
            it.fullCode
                .uppercase(Locale.getDefault()) == q
        }?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Buscar tubos")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->

        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, null)
                },
                label = {
                    Text("Código del tubo")
                },
                placeholder = {
                    Text("Ejemplo: 39145 A 1-1")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Text(
                "${results.size} registro(s)",
                modifier = Modifier.padding(
                    horizontal = 16.dp
                ),
                color = Color.Gray
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                items(
                    results,
                    key = { it.id }
                ) { item ->

                    ObservationCard(
                        item = item,
                        highlighted = exactId == item.id,
                        onEdit = {
                            onEdit(item)
                        },
                        onDelete = {
                            onDelete(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(
    item: Observation,
    highlighted: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor =
        if (item.ok)
            Color(0xFF16A34A)
        else
            Color(0xFFDC2626)

    val border =
        if (highlighted)
            Color(0xFF168BFF)
        else
            Color.Transparent

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            if (highlighted) 3.dp else 0.dp,
            border
        ),
        colors = CardDefaults.cardColors(
            containerColor =
                if (highlighted)
                    Color(0xFF0D2B45)
                else
                    Color(0xFFF4F6F8)
        )
    ) {

        Column(
            Modifier.padding(14.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    item.fullCode,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    color =
                        if (highlighted)
                            Color(0xFF4DB2FF)
                        else
                            Color(0xFF17212B)
                )

                Spacer(Modifier.weight(1f))

                Text(
                    if (item.ok) "OK" else "NO OK",
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(7.dp))

            Text(
                if (item.note.isBlank())
                    "Sin observación"
                else
                    item.note,
                color =
                    if (highlighted)
                        Color.White
                    else
                        Color(0xFF334155)
            )

            Spacer(Modifier.height(7.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    item.date,
                    fontSize = 12.sp,
                    color =
                        if (highlighted)
                            Color(0xFFB6C8D8)
                        else
                            Color.Gray
                )

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        "Editar"
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        "Eliminar"
                    )
                }
            }
        }
    }
}
