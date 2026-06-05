package com.onlyti.novagnss.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyti.novagnss.novatel.Direction
import com.onlyti.novagnss.novatel.NovatelLog
import com.onlyti.novagnss.novatel.SatVis
import com.onlyti.novagnss.novatel.SpanCommands
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val vm: ConsoleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppScreen(vm)
            }
        }
    }
}

@Composable
private fun AppScreen(vm: ConsoleViewModel) {
    val status by vm.status.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Status", "Terminal", "Config", "Calib")

    Column(modifier = Modifier.fillMaxSize()) {
        ConnectionBar(vm, status.connected, status.detail, status.txBytes, status.rxBytes)
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> StatusTab(vm, status.connected)
            1 -> TerminalTab(vm)
            2 -> ConfigTab(vm)
            else -> CalibrationTab(vm)
        }
    }
}

@Composable
private fun ConnectionBar(vm: ConsoleViewModel, connected: Boolean, detail: String, tx: Long, rx: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::connect, enabled = !connected) { Text("CONNECT") }
            Button(onClick = vm::disconnect, enabled = connected) { Text("DISCONNECT") }
        }
        Text(
            "${if (connected) "● " else "○ "}$detail  ·  tx $tx / rx $rx",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------- Status ----------------

@Composable
private fun StatusTab(vm: ConsoleViewModel, connected: Boolean) {
    val ins by vm.insPvax.collectAsState()
    val best by vm.bestPos.collectAsState()
    val sats by vm.sats.collectAsState()

    DisposableEffect(connected) {
        if (connected) vm.startStatusLogs()
        onDispose { if (connected) vm.stopStatusLogs() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("GNSS (BESTPOS)", style = MaterialTheme.typography.titleSmall)
                val b = best
                if (b == null) Text("—  (waiting)") else {
                    kv("Sol status", b.solStatus)
                    kv("RTK", NovatelLog.posTypeLabel(b.posType) + "  (${b.posType})")
                    kv("Sats used / tracked", "${b.svUsed} / ${b.svTracked}")
                    kv("Lat/Lon", if (b.lat.isNaN()) "-" else String.format("%.7f, %.7f", b.lat, b.lon))
                    kv("Height MSL", if (b.heightMsl.isNaN()) "-" else String.format("%.2f m", b.heightMsl))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("INS (INSPVAX)", style = MaterialTheme.typography.titleSmall)
                val n = ins
                if (n == null) Text("—  (no SPAN/INS log)") else {
                    kv("INS status", n.insStatus)
                    kv("Pose type", NovatelLog.posTypeLabel(n.posType) + "  (${n.posType})")
                    kv("Roll / Pitch", "${fmtDeg(n.roll)} / ${fmtDeg(n.pitch)}")
                    kv("Azimuth", fmtDeg(n.azimuth))
                }
            }
        }
        Text("Skyplot  (${sats.size} sats)", style = MaterialTheme.typography.titleSmall)
        Skyplot(sats)
        if (!connected) Text("connect to start status logs", color = MaterialTheme.colorScheme.outline)
    }
}

private fun systemColor(system: String): Color = when {
    system.startsWith("GPS") -> Color(0xFF2E7D32)
    system.startsWith("GLO") -> Color(0xFFC62828)
    system.startsWith("GAL") -> Color(0xFF1565C0)
    system.startsWith("BD") || system.startsWith("BEI") -> Color(0xFFEF6C00)
    system.startsWith("QZ") -> Color(0xFF6A1B9A)
    else -> Color.Gray
}

@Composable
private fun Skyplot(sats: List<SatVis>) {
    Card {
        Canvas(modifier = Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(cx, cy) * 0.92f
            val grid = Color.LightGray
            for (el in listOf(0, 30, 60)) {
                drawCircle(grid, radius = r * (90 - el) / 90f, center = Offset(cx, cy), style = Stroke(2f))
            }
            drawLine(grid, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 2f)
            drawLine(grid, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 2f)
            for (s in sats) {
                if (s.elevation < 0) continue
                val rr = r * ((90.0 - s.elevation) / 90.0).coerceIn(0.0, 1.0)
                val a = Math.toRadians(s.azimuth)
                val x = cx + (rr * sin(a)).toFloat()
                val y = cy - (rr * cos(a)).toFloat()
                drawCircle(systemColor(s.system), radius = 9f, center = Offset(x, y))
            }
        }
    }
    Text("center=zenith(90°), edge=horizon(0°), up=North", style = MaterialTheme.typography.bodySmall)
}

// ---------------- Terminal ----------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TerminalTab(vm: ConsoleViewModel) {
    val console by vm.console.collectAsState()
    val commands by vm.commands.collectAsState()
    var input by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }
    var newCmd by remember { mutableStateOf("") }
    var baudSel by remember { mutableStateOf(vm.baud) }
    val portSel by vm.portIndex.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Baud", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (b in listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800)) {
                FilterChip(baudSel == b, { baudSel = b; vm.setBaud(b) }, label = { Text("$b") })
            }
        }
        Text("USB port (auto = detect NovAtel CDC port)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(portSel == -1, { vm.setPortIndex(-1) }, label = { Text("auto") })
            for (p in 0..2) FilterChip(portSel == p, { vm.setPortIndex(p) }, label = { Text("$p") })
        }

        Text("Commands (tap=send, long-press=remove)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in commands) {
                AssistChip(onClick = {}, label = {
                    Text(c.label, modifier = Modifier.combinedClickable(
                        onClick = { vm.send(c.cmd) }, onLongClick = { vm.removeCommand(c) },
                    ))
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, label = { Text("command") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { vm.send(input); input = "" }) { Text("SEND") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newLabel, { newLabel = it }, label = { Text("label") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(newCmd, { newCmd = it }, label = { Text("cmd") }, singleLine = true, modifier = Modifier.weight(2f))
            Button(onClick = { vm.addCommand(newLabel, newCmd); newLabel = ""; newCmd = "" }) { Text("+") }
        }
        Row {
            Text("Console", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::clearConsole) { Text("clear") }
        }
        ConsoleView(console)
    }
}

@Composable
private fun ConsoleView(lines: List<String>) {
    val scroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.heightIn(min = 160.dp, max = 360.dp).verticalScroll(scroll).padding(8.dp)) {
            for (l in lines) {
                val color = when {
                    l.startsWith(">") -> MaterialTheme.colorScheme.primary
                    l.startsWith("#") -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color)
            }
        }
    }
}

// ---------------- Config (SPAN) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTab(vm: ConsoleViewModel) {
    var imuX by remember { mutableStateOf(Direction.FORWARD) }
    var imuY by remember { mutableStateOf(Direction.LEFT) }
    var imuZ by remember { mutableStateOf(Direction.UP) }
    var rbvCmd by remember { mutableStateOf("") }

    var ax by remember { mutableStateOf("") }
    var ay by remember { mutableStateOf("") }
    var az by remember { mutableStateOf("") }
    var ux by remember { mutableStateOf("0") }
    var uy by remember { mutableStateOf("0") }
    var uz by remember { mutableStateOf("0") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Axis alignment (RBV)", style = MaterialTheme.typography.titleSmall)
            Text("Vehicle frame: X=right, Y=forward, Z=up. Pick where each IMU axis points.",
                style = MaterialTheme.typography.bodySmall)
            DirDropdown("IMU X →", imuX) { imuX = it }
            DirDropdown("IMU Y →", imuY) { imuY = it }
            DirDropdown("IMU Z →", imuZ) { imuZ = it }
            Button(onClick = {
                rbvCmd = SpanCommands.rbvFromAxes(imuX, imuY, imuZ) ?: "ERR: axes not orthogonal/right-handed"
            }) { Text("COMPUTE RBV") }
            OutlinedTextField(rbvCmd, { rbvCmd = it }, label = { Text("SETINSROTATION command (review!)") },
                modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.send(rbvCmd) }, enabled = rbvCmd.startsWith("SETINSROTATION")) { Text("SEND RBV") }
        } }

        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("GNSS antenna lever arm (SETINSTRANSLATION ANT1)", style = MaterialTheme.typography.titleSmall)
            Text("IMU nav-centre → antenna phase centre, IMU body frame (m).",
                style = MaterialTheme.typography.bodySmall)
            Xyz(ax, ay, az, { ax = it }, { ay = it }, { az = it })
            Button(onClick = { vm.send(SpanCommands.setAnt1Translation(d(ax), d(ay), d(az))) }) { Text("SEND ANT1") }
        } }

        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Output position (SETINSTRANSLATION USER)", style = MaterialTheme.typography.titleSmall)
            Text("Where the INS solution is reported. Default = enclosure (0,0,0).",
                style = MaterialTheme.typography.bodySmall)
            Xyz(ux, uy, uz, { ux = it }, { uy = it }, { uz = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.send(SpanCommands.setUserTranslation(d(ux), d(uy), d(uz))) }) { Text("SEND USER") }
                OutlinedButton(onClick = { ux = "0"; uy = "0"; uz = "0" }) { Text("enclosure 0,0,0") }
            }
        } }

        Button(onClick = { vm.send(SpanCommands.saveConfig()) }) { Text("SAVECONFIG") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirDropdown(label: String, selected: Direction, onSelect: (Direction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (dir in Direction.entries) {
                DropdownMenuItem(text = { Text(dir.label) }, onClick = { onSelect(dir); expanded = false })
            }
        }
    }
}

@Composable
private fun Xyz(x: String, y: String, z: String, ox: (String) -> Unit, oy: (String) -> Unit, oz: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(x, ox, label = { Text("X") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(y, oy, label = { Text("Y") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(z, oz, label = { Text("Z") }, singleLine = true, modifier = Modifier.weight(1f))
    }
}

// ---------------- Calibration (RBV) ----------------

@Composable
private fun CalibrationTab(vm: ConsoleViewModel) {
    val cal by vm.calStatus.collectAsState()
    DisposableEffect(Unit) { vm.startCalStatus(); onDispose { } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("RBV kinematic calibration (INSCALIBRATE)", style = MaterialTheme.typography.titleSmall)
        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Prereq: seed RBV set, alignment to INS_SOLUTION_GOOD, accurate lever arm.",
                style = MaterialTheme.typography.bodySmall)
            Text("Drive > 5 m/s (18 km/h), straight & level. STOP at line end, ADD for next pass.",
                style = MaterialTheme.typography.bodySmall)
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.send(SpanCommands.calibrateRbvNew()) }) { Text("NEW") }
            Button(onClick = { vm.send(SpanCommands.calibrateRbvAdd()) }) { Text("ADD") }
            Button(onClick = { vm.send(SpanCommands.calibrateRbvStop()) }) { Text("STOP") }
            OutlinedButton(onClick = { vm.send(SpanCommands.calibrateRbvReset()) }) { Text("RESET") }
        }
        Text("INSCALSTATUS", style = MaterialTheme.typography.labelLarge)
        Card { Text(cal.ifBlank { "— (waiting; status log on)" },
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(10.dp)) }
        Button(onClick = { vm.send(SpanCommands.saveConfig()) }) { Text("SAVECONFIG") }
    }
}

// ---------------- helpers ----------------

@Composable
private fun kv(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodyMedium)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fmtDeg(v: Double): String = if (v.isNaN()) "-" else String.format("%.2f°", v)
private fun d(s: String): Double = s.trim().toDoubleOrNull() ?: 0.0
