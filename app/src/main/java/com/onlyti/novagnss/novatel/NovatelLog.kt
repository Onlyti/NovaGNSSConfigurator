package com.onlyti.novagnss.novatel

/**
 * Parsers for NovAtel OEM7 abbreviated-ASCII logs. Format:
 *   #LOGNAMEA,port,seq,idle,timeStatus,week,sec,...header... ; field1,field2,... *CRC
 * Header is between '#'/'%' and ';'. Body is between ';' and '*'.
 * Field orders verified against docs.novatel.com (INSPVAX / BESTPOS / SATVIS2).
 */
object NovatelLog {

    /** Returns (logName, bodyFields) or null if not a recognizable ASCII log line. */
    fun split(line: String): Pair<String, List<String>>? {
        val s = line.trim()
        if (s.isEmpty() || (s[0] != '#' && s[0] != '%')) return null
        val semi = s.indexOf(';')
        if (semi < 0) return null
        val header = s.substring(1, semi)
        val name = header.substringBefore(',').removeSuffix("A").removeSuffix("_1")
        val body = s.substring(semi + 1).substringBefore('*')
        if (body.isBlank()) return null
        return name to body.split(',')
    }

    // INSPVAX: [0]=INS status, [1]=pos type, [2]lat [3]lon [4]height [5]undulation
    //          [6]vN [7]vE [8]vU [9]roll [10]pitch [11]azimuth ...
    fun parseInspvax(f: List<String>): InsPvax? {
        if (f.size < 12) return null
        return InsPvax(
            insStatus = f[0],
            posType = f[1],
            lat = f[2].toDoubleOrNull() ?: Double.NaN,
            lon = f[3].toDoubleOrNull() ?: Double.NaN,
            heightMsl = f[4].toDoubleOrNull() ?: Double.NaN,
            roll = f[9].toDoubleOrNull() ?: Double.NaN,
            pitch = f[10].toDoubleOrNull() ?: Double.NaN,
            azimuth = f[11].toDoubleOrNull() ?: Double.NaN,
        )
    }

    // BESTPOS: [0]solStat [1]posType [2]lat [3]lon [4]hgt [5]undulation [6]datum
    //          [7]latSD [8]lonSD [9]hgtSD [10]stnId [11]diffAge [12]solAge
    //          [13]#SVtracked [14]#SVused ...
    fun parseBestpos(f: List<String>): BestPos? {
        if (f.size < 15) return null
        return BestPos(
            solStatus = f[0],
            posType = f[1],
            lat = f[2].toDoubleOrNull() ?: Double.NaN,
            lon = f[3].toDoubleOrNull() ?: Double.NaN,
            heightMsl = f[4].toDoubleOrNull() ?: Double.NaN,
            svTracked = f[13].toIntOrNull() ?: 0,
            svUsed = f[14].toIntOrNull() ?: 0,
        )
    }

    // SATVIS2: [0]system [1]satVis [2]almanac [3]#sat, then 6 fields/sat:
    //          [id, health, elev, az, trueDopp, appDopp]
    fun parseSatvis2(f: List<String>): List<SatVis> {
        if (f.size < 4) return emptyList()
        val system = f[0]
        val count = f[3].toIntOrNull() ?: return emptyList()
        val out = ArrayList<SatVis>(count)
        var i = 4
        repeat(count) {
            if (i + 3 < f.size) {
                val id = f[i]
                val elev = f[i + 2].toDoubleOrNull()
                val az = f[i + 3].toDoubleOrNull()
                if (elev != null && az != null) out.add(SatVis(system, id, elev, az))
            }
            i += 6
        }
        return out
    }

    /** RTK / fix label from a NovAtel position-type token (BESTPOS or INS-blended). */
    fun posTypeLabel(t: String): String = when (t) {
        "NARROW_INT", "INS_RTKFIXED" -> "RTK FIXED"
        "NARROW_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> "RTK float"
        "PSRDIFF", "INS_PSRDIFF" -> "DGPS"
        "SINGLE", "INS_PSRSP" -> "single"
        "WAAS", "INS_SBAS" -> "SBAS"
        "PPP", "PPP_CONVERGING", "INS_PPP", "INS_PPP_CONVERGING" -> "PPP"
        "NONE", "" -> "no fix"
        else -> t
    }
}

data class InsPvax(
    val insStatus: String,
    val posType: String,
    val lat: Double,
    val lon: Double,
    val heightMsl: Double,
    val roll: Double,
    val pitch: Double,
    val azimuth: Double,
)

data class BestPos(
    val solStatus: String,
    val posType: String,
    val lat: Double,
    val lon: Double,
    val heightMsl: Double,
    val svTracked: Int,
    val svUsed: Int,
)

/** One satellite for the skyplot. elev/az in degrees. system e.g. GPS/GLONASS/GALILEO/BEIDOU/QZSS. */
data class SatVis(val system: String, val id: String, val elevation: Double, val azimuth: Double)
