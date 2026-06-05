package com.onlyti.novagnss.novatel

/**
 * Builders for NovAtel OEM7 / PwrPak7 configuration commands, grouped by category.
 * Verified against docs.novatel.com (see docs/COMMANDS.md). Only confirmed commands here;
 * NovA is a CONFIG tool — it sets the receiver, it does not route corrections.
 */
object OemCommands {

    // --- Info: one-shot read-only logs (label -> command) ---
    val INFO_LOGS: List<Pair<String, String>> = listOf(
        "Version" to "LOG VERSIONA ONCE",
        "RX status" to "LOG RXSTATUSA ONCE",
        "RX config" to "LOG RXCONFIGA ONCE",
        "HW monitor" to "LOG HWMONITORA ONCE",
        "Port stats" to "LOG PORTSTATSA ONCE",
        "Best pos" to "LOG BESTPOSA ONCE",
        "Best vel" to "LOG BESTVELA ONCE",
        "Time" to "LOG TIMEA ONCE",
        "Track stat" to "LOG TRACKSTATA ONCE",
        "INS config" to "LOG INSCONFIGA ONCE",
    )

    // --- Ports ---
    val PORTS = listOf("COM1", "COM2", "COM3", "USB1", "USB2", "USB3", "NCOM1")
    val BAUDS = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800)
    val RXTYPES = listOf("NOVATEL", "RTCMV3", "RTCM", "RTCA", "CMR", "AUTO", "NONE")

    fun serialConfig(port: String, baud: Int): String = "SERIALCONFIG $port $baud N 8 1 N OFF"
    fun interfaceMode(port: String, rx: String, tx: String): String = "INTERFACEMODE $port $rx $tx ON"

    // --- Network / WiFi (PwrPak7) ---
    val WIFI_MODES = listOf("CLIENT", "AP", "ON", "OFF", "CONCURRENT")
    fun wifiMode(mode: String): String = "WIFIMODE $mode"
    fun wifiNetConfig(id: Int, ssid: String, passkey: String): String =
        "WIFINETCONFIG $id ENABLE $ssid $passkey"
    fun ipConfigDhcp(): String = "IPCONFIG ETHA DHCP"
    fun ipConfigStatic(ip: String, mask: String, gw: String): String = "IPCONFIG ETHA STATIC $ip $mask $gw"

    // --- NTRIP / endpoint (receiver's own client over WiFi/Ethernet) ---
    val NCOM_PORTS = listOf("NCOM1", "NCOM2", "NCOM3")
    fun ntripConfigClient(ncom: String, hostPort: String, mount: String, user: String, pass: String, v2: Boolean): String =
        "NTRIPCONFIG $ncom CLIENT ${if (v2) "V2" else "V1"} $hostPort $mount $user $pass"
    fun ntripSourcetable(hostPort: String): String = "NTRIPSOURCETABLE $hostPort"
    /** Feed a configured NCOM port's RTCM into the RTK engine. */
    fun useNcomForRtk(ncom: String): String = "INTERFACEMODE $ncom RTCMV3 NOVATEL ON"

    // --- RTK tuning ---
    val RTK_SOURCES = listOf("AUTO", "RTCMV3", "RTCM", "RTCA", "CMR", "NONE")
    val RTK_DYNAMICS = listOf("AUTO", "DYNAMIC", "STATIC")
    fun rtkSource(type: String, id: String = "ANY"): String = "RTKSOURCE $type ${id.ifBlank { "ANY" }}"
    fun rtkTimeout(sec: Int): String = "RTKTIMEOUT ${sec.coerceIn(5, 60)}"
    fun rtkDynamics(mode: String): String = "RTKDYNAMICS $mode"
    fun ecutoff(deg: Double): String = "ECUTOFF $deg"

    // --- GNSS constellations ---
    val CONSTELLATIONS = listOf("GPS", "GLONASS", "GALILEO", "BEIDOU", "QZSS")
    fun assignAll(system: String, enable: Boolean): String = "ASSIGNALL $system ${if (enable) "AUTO" else "IDLE"}"

    // --- SPAN extras (RBV/ANT/USER are in SpanCommands) ---
    val INS_PROFILES = listOf("DEFAULT", "LAND", "MARINE", "FIXEDWING", "FOOT", "VTOL", "RAIL", "AGRICULTURE")
    val ALIGN_MODES = listOf("AUTOMATIC", "AIDED_TRANSFER", "UNAIDED", "STATIC", "KINEMATIC")
    fun setInsProfile(p: String): String = "SETINSPROFILE $p"
    fun alignmentMode(m: String): String = "ALIGNMENTMODE $m"

    // --- System ---
    val FRESET_TARGETS = listOf("STANDARD", "COMMAND", "GPSALMANAC", "USERDATA", "ETHERNET")
    fun freset(target: String): String = "FRESET $target"
    fun unlogall(): String = "UNLOGALL"
    fun saveConfig(): String = "SAVECONFIG"
}
