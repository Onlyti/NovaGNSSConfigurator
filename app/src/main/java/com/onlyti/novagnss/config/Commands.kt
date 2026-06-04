package com.onlyti.novagnss.config

import kotlinx.serialization.Serializable

/** A labelled NovAtel command for the palette. */
@Serializable
data class NovCommand(val label: String, val cmd: String)

/**
 * Default frequently-used NovAtel OEM7 commands. ASCII-log (...A) variants are used so
 * responses are human-readable in the console. Users can add/remove favorites.
 */
object Commands {
    val DEFAULTS: List<NovCommand> = listOf(
        NovCommand("Version", "LOG VERSIONA ONCE"),
        NovCommand("RX status", "LOG RXSTATUSA ONCE"),
        NovCommand("Best pos", "LOG BESTPOSA ONCE"),
        NovCommand("Best vel", "LOG BESTVELA ONCE"),
        NovCommand("Sat vis", "LOG SATVIS2A ONCE"),
        NovCommand("Port config", "LOG PORTSTATSA ONCE"),
        NovCommand("Interface modes", "LOG INTERFACEMODEA ONCE"),
        NovCommand("GGA on COM1 1Hz", "LOG COM1 GPGGA ONTIME 1"),
        NovCommand("RTCM in COM2", "INTERFACEMODE COM2 RTCMV3 NOVATEL ON"),
        NovCommand("COM2 115200", "SERIALCONFIG COM2 115200 N 8 1 N OFF"),
        NovCommand("Unlog all", "UNLOGALL"),
        NovCommand("Save config", "SAVECONFIG"),
        NovCommand("Reset", "RESET"),
        NovCommand("Factory reset", "FRESET"),
    )
}
