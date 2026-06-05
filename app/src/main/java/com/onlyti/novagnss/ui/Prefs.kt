package com.onlyti.novagnss.ui

import android.content.Context
import com.onlyti.novagnss.config.Commands
import com.onlyti.novagnss.config.NovCommand
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Persists terminal settings: baud, port index, and the editable command palette. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nov_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var baud: Int
        get() = sp.getInt(KEY_BAUD, 115200)
        set(v) { sp.edit().putInt(KEY_BAUD, v).apply() }

    var portIndex: Int
        get() = sp.getInt(KEY_PORT, -1)   // -1 = auto-detect (NovAtel multi-port)
        set(v) { sp.edit().putInt(KEY_PORT, v).apply() }

    fun loadCommands(): List<NovCommand> {
        val s = sp.getString(KEY_CMDS, null) ?: return Commands.DEFAULTS
        return try {
            json.decodeFromString(ListSerializer(NovCommand.serializer()), s)
        } catch (_: Throwable) {
            Commands.DEFAULTS
        }
    }

    fun saveCommands(list: List<NovCommand>) {
        sp.edit().putString(KEY_CMDS, json.encodeToString(ListSerializer(NovCommand.serializer()), list)).apply()
    }

    companion object {
        private const val KEY_BAUD = "baud"
        private const val KEY_PORT = "port"
        private const val KEY_CMDS = "commands"
    }
}
