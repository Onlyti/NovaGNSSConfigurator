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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyti.novagnss.novatel.Direction
import com.onlyti.novagnss.novatel.NovatelLog
import com.onlyti.novagnss.novatel.OemCommands
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigTab(vm: ConsoleViewModel) {
    var cat by remember { mutableStateOf("Info") }
    val cats = listOf("Info", "Ports", "Network", "NTRIP", "RTK", "GNSS", "SPAN", "System")
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { FilterChip(cat == it, { cat = it }, label = { Text(it) }) }
        }
        Text("config 명령은 SAVECONFIG 해야 영구 저장. 응답은 Terminal 콘솔에 표시.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        when (cat) {
            "Info" -> InfoSection(vm)
            "Ports" -> PortsSection(vm)
            "Network" -> NetworkSection(vm)
            "NTRIP" -> NtripSection(vm)
            "RTK" -> RtkSection(vm)
            "GNSS" -> GnssSection(vm)
            "SPAN" -> SpanSection(vm)
            else -> SystemSection(vm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSection(vm: ConsoleViewModel) = Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Info (LOG ... ONCE → Terminal 콘솔)", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, cmd) in OemCommands.INFO_LOGS) {
            OutlinedButton(onClick = { vm.send(cmd) }) { Text(label) }
        }
    }
} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortsSection(vm: ConsoleViewModel) {
    var scPort by remember { mutableStateOf("COM1") }
    var scBaud by remember { mutableStateOf(115200) }
    var imPort by remember { mutableStateOf("COM1") }
    var rx by remember { mutableStateOf("RTCMV3") }
    var tx by remember { mutableStateOf("NOVATEL") }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("SERIALCONFIG (baud)", style = MaterialTheme.typography.titleSmall)
        Text("같은 포트엔 SERIALCONFIG 를 INTERFACEMODE 보다 먼저!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        StrDropdown("port", OemCommands.PORTS, scPort) { scPort = it }
        StrDropdown("baud", OemCommands.BAUDS.map { "$it" }, "$scBaud") { scBaud = it.toInt() }
        Button(onClick = { vm.send(OemCommands.serialConfig(scPort, scBaud)) }) { Text("SEND SERIALCONFIG") }
    } }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("INTERFACEMODE (rx/tx type)", style = MaterialTheme.typography.titleSmall)
        StrDropdown("port", OemCommands.PORTS, imPort) { imPort = it }
        StrDropdown("rx (입력 해석)", OemCommands.RXTYPES, rx) { rx = it }
        StrDropdown("tx (출력)", OemCommands.RXTYPES, tx) { tx = it }
        Button(onClick = { vm.send(OemCommands.interfaceMode(imPort, rx, tx)) }) { Text("SEND INTERFACEMODE") }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkSection(vm: ConsoleViewModel) {
    var mode by remember { mutableStateOf("CLIENT") }
    var ssid by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("WiFi (PwrPak7)", style = MaterialTheme.typography.titleSmall)
        StrDropdown("WIFIMODE", OemCommands.WIFI_MODES, mode) { mode = it }
        Button(onClick = { vm.send(OemCommands.wifiMode(mode)) }) { Text("SEND WIFIMODE") }
        OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pw, { pw = it }, label = { Text("passkey") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.send(OemCommands.wifiNetConfig(1, ssid.trim(), pw.trim())) }, enabled = ssid.isNotBlank()) { Text("SEND WIFINETCONFIG") }
        Button(onClick = { vm.send(OemCommands.ipConfigDhcp()) }) { Text("IPCONFIG ETHA DHCP") }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NtripSection(vm: ConsoleViewModel) {
    var ncom by remember { mutableStateOf("NCOM1") }
    var host by remember { mutableStateOf("") }
    var mount by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf(true) }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("수신기 NTRIP 클라이언트 (자가 RTK 설정)", style = MaterialTheme.typography.titleSmall)
        Text("수신기가 직접 caster 접속. 폰 라우팅 아님.", style = MaterialTheme.typography.bodySmall)
        StrDropdown("NCOM port", OemCommands.NCOM_PORTS, ncom) { ncom = it }
        OutlinedTextField(host, { host = it }, label = { Text("host:port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mount, { mount = it }, label = { Text("mountpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(user, { user = it }, label = { Text("user") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(pass, { pass = it }, label = { Text("pass") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("NTRIP v2", modifier = Modifier.weight(1f)); Switch(checked = v2, onCheckedChange = { v2 = it })
        }
        Button(onClick = {
            vm.send(OemCommands.ntripConfigClient(ncom, host.trim(), mount.trim(), user.trim(), pass.trim(), v2))
            vm.send(OemCommands.useNcomForRtk(ncom))
        }, enabled = host.isNotBlank()) { Text("APPLY (NTRIPCONFIG + RTK 입력)") }
        OutlinedButton(onClick = { vm.send(OemCommands.ntripSourcetable(host.trim())) }, enabled = host.isNotBlank()) { Text("SOURCETABLE 조회") }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RtkSection(vm: ConsoleViewModel) {
    var src by remember { mutableStateOf("AUTO") }
    var dyn by remember { mutableStateOf("AUTO") }
    var timeout by remember { mutableStateOf("60") }
    var mask by remember { mutableStateOf("5") }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("RTK tuning", style = MaterialTheme.typography.titleSmall)
        StrDropdown("RTKSOURCE", OemCommands.RTK_SOURCES, src) { src = it }
        Button(onClick = { vm.send(OemCommands.rtkSource(src)) }) { Text("SEND RTKSOURCE") }
        StrDropdown("RTKDYNAMICS", OemCommands.RTK_DYNAMICS, dyn) { dyn = it }
        Button(onClick = { vm.send(OemCommands.rtkDynamics(dyn)) }) { Text("SEND RTKDYNAMICS") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(timeout, { timeout = it }, label = { Text("RTKTIMEOUT 5-60s") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { vm.send(OemCommands.rtkTimeout(timeout.toIntOrNull() ?: 60)) }) { Text("SEND") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(mask, { mask = it }, label = { Text("ECUTOFF deg") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { vm.send(OemCommands.ecutoff(mask.toDoubleOrNull() ?: 5.0)) }) { Text("SEND") }
        }
    } }
}

@Composable
private fun GnssSection(vm: ConsoleViewModel) = Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("GNSS constellations (ASSIGNALL)", style = MaterialTheme.typography.titleSmall)
    for (sys in OemCommands.CONSTELLATIONS) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sys, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.send(OemCommands.assignAll(sys, true)) }) { Text("on") }
            OutlinedButton(onClick = { vm.send(OemCommands.assignAll(sys, false)) }) { Text("off") }
        }
    }
} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpanSection(vm: ConsoleViewModel) {
    var imuX by remember { mutableStateOf(Direction.FORWARD) }
    var imuY by remember { mutableStateOf(Direction.LEFT) }
    val imuZ = SpanCommands.thirdAxis(imuX, imuY)
    var rbvCmd by remember { mutableStateOf("") }
    var ax by remember { mutableStateOf("") }; var ay by remember { mutableStateOf("") }; var az by remember { mutableStateOf("") }
    var bx by remember { mutableStateOf("") }; var by_ by remember { mutableStateOf("") }; var bz by remember { mutableStateOf("") }
    var dualAnt by remember { mutableStateOf(false) }
    var ux by remember { mutableStateOf("0") }; var uy by remember { mutableStateOf("0") }; var uz by remember { mutableStateOf("0") }
    var profile by remember { mutableStateOf("LAND") }
    var align by remember { mutableStateOf("AUTOMATIC") }

    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Axis alignment (RBV)", style = MaterialTheme.typography.titleSmall)
        Text("Vehicle frame X=right/Y=forward/Z=up. Pick IMU X & Y; Z auto.", style = MaterialTheme.typography.bodySmall)
        DirDropdown("IMU X →", imuX) { imuX = it }
        DirDropdown("IMU Y →", imuY) { imuY = it }
        Text("IMU Z →  ${imuZ?.label ?: "invalid (X⊥Y required)"}",
            color = if (imuZ == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Button(onClick = { rbvCmd = imuZ?.let { SpanCommands.rbvFromAxes(imuX, imuY, it) } ?: "ERR" }, enabled = imuZ != null) { Text("COMPUTE RBV") }
        OutlinedTextField(rbvCmd, { rbvCmd = it }, label = { Text("SETINSROTATION (review!)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.send(rbvCmd) }, enabled = rbvCmd.startsWith("SETINSROTATION")) { Text("SEND RBV") }
    } }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Antenna lever arm ANT1", style = MaterialTheme.typography.titleSmall)
        Xyz(ax, ay, az, { ax = it }, { ay = it }, { az = it })
        Button(onClick = { vm.send(SpanCommands.setAnt1Translation(d(ax), d(ay), d(az))) }) { Text("SEND ANT1") }
    } }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("ANT2 (dual-antenna)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = dualAnt, onCheckedChange = { dualAnt = it })
        }
        if (dualAnt) {
            Xyz(bx, by_, bz, { bx = it }, { by_ = it }, { bz = it })
            Button(onClick = { vm.send(SpanCommands.setAnt2Translation(d(bx), d(by_), d(bz))) }) { Text("SEND ANT2") }
        }
    } }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Output point USER (기본 enclosure 0,0,0)", style = MaterialTheme.typography.titleSmall)
        Xyz(ux, uy, uz, { ux = it }, { uy = it }, { uz = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.send(SpanCommands.setUserTranslation(d(ux), d(uy), d(uz))) }) { Text("SEND USER") }
            OutlinedButton(onClick = { ux = "0"; uy = "0"; uz = "0" }) { Text("0,0,0") }
        }
    } }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Profile / Alignment", style = MaterialTheme.typography.titleSmall)
        StrDropdown("SETINSPROFILE", OemCommands.INS_PROFILES, profile) { profile = it }
        Button(onClick = { vm.send(OemCommands.setInsProfile(profile)) }) { Text("SEND PROFILE") }
        StrDropdown("ALIGNMENTMODE", OemCommands.ALIGN_MODES, align) { align = it }
        Button(onClick = { vm.send(OemCommands.alignmentMode(align)) }) { Text("SEND ALIGNMENT") }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemSection(vm: ConsoleViewModel) {
    var fresetTarget by remember { mutableStateOf("STANDARD") }
    var confirmFreset by remember { mutableStateOf(false) }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("System", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { vm.send(OemCommands.saveConfig()) }, modifier = Modifier.fillMaxWidth()) { Text("SAVECONFIG (영구 저장)") }
        OutlinedButton(onClick = { vm.send(OemCommands.unlogall()) }, modifier = Modifier.fillMaxWidth()) { Text("UNLOGALL") }
        StrDropdown("FRESET target", OemCommands.FRESET_TARGETS, fresetTarget) { fresetTarget = it }
        OutlinedButton(onClick = { confirmFreset = true }, modifier = Modifier.fillMaxWidth()) { Text("FRESET (공장초기화)") }
    } }
    if (confirmFreset) {
        AlertDialog(
            onDismissRequest = { confirmFreset = false },
            title = { Text("FRESET $fresetTarget?") },
            text = { Text("수신기 설정이 초기화됩니다. 계속?") },
            confirmButton = { TextButton(onClick = { vm.send(OemCommands.freset(fresetTarget)); confirmFreset = false }) { Text("FRESET") } },
            dismissButton = { TextButton(onClick = { confirmFreset = false }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (o in options) DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); expanded = false })
        }
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
