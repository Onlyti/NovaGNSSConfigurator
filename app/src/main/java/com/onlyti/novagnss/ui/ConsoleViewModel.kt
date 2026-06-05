package com.onlyti.novagnss.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.onlyti.novagnss.config.NovCommand
import com.onlyti.novagnss.novatel.BestPos
import com.onlyti.novagnss.novatel.InsPvax
import com.onlyti.novagnss.novatel.NovatelLog
import com.onlyti.novagnss.novatel.SatVis
import com.onlyti.novagnss.novatel.SpanCommands
import com.onlyti.novagnss.serial.SerialLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ConsoleStatus(
    val connected: Boolean = false,
    val deviceName: String = "",
    val portCount: Int = 0,
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val detail: String = "disconnected",
)

/**
 * Terminal session: owns the serial link, exposes a rolling console (TX echo + RX lines),
 * connection status, and the editable command palette. v0.1 manages serial in the VM
 * (no foreground service) since config is an interactive, foreground task.
 */
class ConsoleViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    private var serial: SerialLink? = null
    private val rxBuf = StringBuilder()
    private val lines = ArrayDeque<String>()

    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console

    private val _status = MutableStateFlow(ConsoleStatus())
    val status: StateFlow<ConsoleStatus> = _status

    private val _commands = MutableStateFlow(prefs.loadCommands())
    val commands: StateFlow<List<NovCommand>> = _commands

    // --- structured NovAtel state for the Status tab ---
    private val _insPvax = MutableStateFlow<InsPvax?>(null)
    val insPvax: StateFlow<InsPvax?> = _insPvax
    private val _bestPos = MutableStateFlow<BestPos?>(null)
    val bestPos: StateFlow<BestPos?> = _bestPos
    private val satsBySystem = LinkedHashMap<String, List<SatVis>>()
    private val _sats = MutableStateFlow<List<SatVis>>(emptyList())
    val sats: StateFlow<List<SatVis>> = _sats
    private val _calStatus = MutableStateFlow("")
    val calStatus: StateFlow<String> = _calStatus

    fun startStatusLogs() = SpanCommands.STATUS_LOGS.forEach { send(it, echo = false) }
    fun stopStatusLogs() = SpanCommands.STATUS_LOGS_STOP.forEach { send(it, echo = false) }
    fun startCalStatus() = send(SpanCommands.logInsCalStatus(), echo = false)

    val baud get() = prefs.baud
    private val _portIndex = MutableStateFlow(prefs.portIndex)   // -1 = auto-detect
    val portIndex: StateFlow<Int> = _portIndex
    fun setBaud(v: Int) { prefs.baud = v; refreshStatusDetail() }
    fun setPortIndex(v: Int) { prefs.portIndex = v; _portIndex.value = v; refreshStatusDetail() }

    private fun portLabel(p: Int) = if (p < 0) "auto" else "$p"

    private fun refreshStatusDetail() {
        if (!_status.value.connected) {
            _status.value = _status.value.copy(detail = "baud ${prefs.baud}, port ${portLabel(prefs.portIndex)}")
        }
    }

    fun connect() {
        if (serial != null) return
        appendLine("# connecting (baud ${prefs.baud}, port ${portLabel(prefs.portIndex)})...")
        serial = SerialLink(
            context = getApplication<Application>().applicationContext,
            baud = prefs.baud,
            portIndex = prefs.portIndex,
            onConnected = { name, ports, port ->
                _status.value = ConsoleStatus(true, name, ports, detail = "connected: $name (port $port/$ports)")
                appendLine("# connected: $name — using port $port of $ports")
            },
            onDisconnected = { reason ->
                _status.value = _status.value.copy(connected = false, detail = reason)
                appendLine("# disconnected: $reason")
                serial = null
            },
            onData = { bytes -> onData(bytes) },
        ).also { it.connect() }
    }

    fun disconnect() {
        serial?.close()
        serial = null
        _status.value = _status.value.copy(connected = false, detail = "disconnected")
        appendLine("# disconnected")
    }

    /** Send a command line (CRLF-terminated). [echo]=false for background log subscriptions. */
    fun send(cmd: String, echo: Boolean = true) {
        val c = cmd.trim()
        if (c.isEmpty()) return
        val s = serial
        if (s == null) { if (echo) appendLine("# not connected"); return }
        s.write((c + "\r\n").toByteArray(Charsets.US_ASCII))
        if (echo) appendLine("> $c")
        bumpCounters()
    }

    fun clearConsole() {
        synchronized(lines) { lines.clear() }
        _console.value = emptyList()
    }

    // --- command palette editing ---
    fun addCommand(label: String, cmd: String) {
        if (label.isBlank() || cmd.isBlank()) return
        val next = _commands.value + NovCommand(label.trim(), cmd.trim())
        _commands.value = next
        prefs.saveCommands(next)
    }

    fun removeCommand(c: NovCommand) {
        val next = _commands.value.filterNot { it == c }
        _commands.value = next
        prefs.saveCommands(next)
    }

    private fun onData(bytes: ByteArray) {
        synchronized(rxBuf) {
            rxBuf.append(String(bytes, Charsets.US_ASCII))
            if (rxBuf.length > RX_BUF_CAP) rxBuf.delete(0, rxBuf.length - RX_BUF_CAP)
            var nl = rxBuf.indexOf("\n")
            while (nl >= 0) {
                val line = rxBuf.substring(0, nl).trimEnd('\r')
                rxBuf.delete(0, nl + 1)
                if (line.isNotEmpty()) { appendLine(line); parseLog(line) }
                nl = rxBuf.indexOf("\n")
            }
        }
        bumpCounters()
    }

    /** Feed a complete line to the NovAtel parsers and update structured Status state. */
    private fun parseLog(line: String) {
        val (name, f) = NovatelLog.split(line) ?: return
        when (name) {
            "INSPVAX" -> NovatelLog.parseInspvax(f)?.let { _insPvax.value = it }
            "BESTPOS" -> NovatelLog.parseBestpos(f)?.let { _bestPos.value = it }
            "SATVIS2" -> {
                val sats = NovatelLog.parseSatvis2(f)
                if (sats.isNotEmpty()) {
                    satsBySystem[sats.first().system] = sats
                    _sats.value = satsBySystem.values.flatten()
                }
            }
            "INSCALSTATUS" -> _calStatus.value = f.joinToString(", ")
        }
    }

    private fun bumpCounters() {
        val s = serial ?: return
        _status.value = _status.value.copy(txBytes = s.txBytes.get(), rxBytes = s.rxBytes.get())
    }

    private fun appendLine(line: String) {
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            _console.value = lines.toList()
        }
    }

    override fun onCleared() {
        serial?.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_LINES = 500
        private const val RX_BUF_CAP = 8192
    }
}
