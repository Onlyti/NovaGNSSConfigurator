package com.onlyti.novagnss.novatel

/** Receiver's current config parsed from a `LOG RXCONFIGA ONCE` dump (best-effort). */
data class RxConfig(
    val serial: Map<String, Int> = emptyMap(),                 // port -> baud
    val iface: Map<String, Pair<String, String>> = emptyMap(), // port -> (rx, tx)
    val wifiMode: String = "",
    val wifiSsid: String = "",
    val ntripNcom: String = "",
    val ntripHost: String = "",
    val ntripMount: String = "",
    val ntripUser: String = "",
    val ntripV2: Boolean = true,
    val rtkSource: String = "",
    val rtkDynamics: String = "",
    val rtkTimeout: String = "",
    val ecutoff: String = "",
    val insProfile: String = "",
    val alignMode: String = "",
    val ant1: Triple<String, String, String>? = null,
    val ant2: Triple<String, String, String>? = null,
    val user: Triple<String, String, String>? = null,
)

/**
 * Extract known commands from an RXCONFIG dump. Each saved command appears verbatim in the
 * dump (e.g. "SERIALCONFIG COM1 115200 N 8 1 N OFF"); we find the keyword anywhere on a line
 * and read the args after it. Passwords are masked by the receiver so user/pass stay blank.
 */
fun parseRxConfig(text: String): RxConfig {
    val serial = LinkedHashMap<String, Int>()
    val iface = LinkedHashMap<String, Pair<String, String>>()
    var c = RxConfig()
    for (raw in text.split('\n')) {
        val toks = raw.trim().split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        fun argsAfter(kw: String): List<String>? {
            val i = toks.indexOf(kw)
            return if (i >= 0) toks.subList(i + 1, toks.size) else null
        }
        argsAfter("SERIALCONFIG")?.let { a -> if (a.size >= 2) a[1].toIntOrNull()?.let { serial[a[0]] = it } }
        argsAfter("INTERFACEMODE")?.let { a -> if (a.size >= 3) iface[a[0]] = a[1] to a[2] }
        argsAfter("NTRIPCONFIG")?.let { a ->
            if (a.size >= 4) c = c.copy(
                ntripNcom = a[0], ntripV2 = a.getOrNull(2) == "V2",
                ntripHost = a.getOrElse(3) { "" }, ntripMount = a.getOrElse(4) { "" },
                ntripUser = a.getOrElse(5) { "" },
            )
        }
        argsAfter("WIFIMODE")?.let { a -> if (a.isNotEmpty()) c = c.copy(wifiMode = a[0]) }
        argsAfter("WIFINETCONFIG")?.let { a -> if (a.size >= 3) c = c.copy(wifiSsid = a[2]) }
        argsAfter("RTKSOURCE")?.let { a -> if (a.isNotEmpty()) c = c.copy(rtkSource = a[0]) }
        argsAfter("RTKDYNAMICS")?.let { a -> if (a.isNotEmpty()) c = c.copy(rtkDynamics = a[0]) }
        argsAfter("RTKTIMEOUT")?.let { a -> if (a.isNotEmpty()) c = c.copy(rtkTimeout = a[0]) }
        argsAfter("ECUTOFF")?.let { a -> if (a.isNotEmpty()) c = c.copy(ecutoff = a[0]) }
        argsAfter("SETINSPROFILE")?.let { a -> if (a.isNotEmpty()) c = c.copy(insProfile = a[0]) }
        argsAfter("ALIGNMENTMODE")?.let { a -> if (a.isNotEmpty()) c = c.copy(alignMode = a[0]) }
        argsAfter("SETINSTRANSLATION")?.let { a ->
            if (a.size >= 4) {
                val tr = Triple(a[1], a[2], a[3])
                c = when (a[0]) {
                    "ANT1" -> c.copy(ant1 = tr)
                    "ANT2" -> c.copy(ant2 = tr)
                    "USER" -> c.copy(user = tr)
                    else -> c
                }
            }
        }
    }
    return c.copy(serial = serial, iface = iface)
}
