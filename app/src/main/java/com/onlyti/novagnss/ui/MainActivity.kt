package com.onlyti.novagnss.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: ConsoleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ConsoleScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ConsoleScreen(vm: ConsoleViewModel) {
    val status by vm.status.collectAsState()
    val console by vm.console.collectAsState()
    val commands by vm.commands.collectAsState()
    var input by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }
    var newCmd by remember { mutableStateOf("") }
    var baudSel by remember { mutableStateOf(vm.baud) }
    var portSel by remember { mutableStateOf(vm.portIndex) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("NovAtel Companion", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = vm::connect, enabled = !status.connected) { Text("CONNECT") }
            Button(onClick = vm::disconnect, enabled = status.connected) { Text("DISCONNECT") }
        }
        Text(
            "${if (status.connected) "● " else "○ "}${status.detail}  ·  tx ${status.txBytes} / rx ${status.rxBytes}",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Baud", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (b in listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800)) {
                FilterChip(
                    selected = baudSel == b,
                    onClick = { baudSel = b; vm.setBaud(b) },
                    label = { Text("$b") },
                )
            }
        }
        Text("USB port", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in 0..2) {
                FilterChip(
                    selected = portSel == p,
                    onClick = { portSel = p; vm.setPortIndex(p) },
                    label = { Text("$p") },
                )
            }
        }

        Text("Commands (tap=send, long-press=remove)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in commands) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            c.label,
                            modifier = Modifier.combinedClickable(
                                onClick = { vm.send(c.cmd) },
                                onLongClick = { vm.removeCommand(c) },
                            ),
                        )
                    },
                )
            }
        }

        // Manual command line.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("command") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.send(input); input = "" }) { Text("SEND") }
        }

        // Add to palette.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newLabel, onValueChange = { newLabel = it },
                label = { Text("label") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = newCmd, onValueChange = { newCmd = it },
                label = { Text("cmd") }, singleLine = true,
                modifier = Modifier.weight(2f),
            )
            Button(onClick = { vm.addCommand(newLabel, newCmd); newLabel = ""; newCmd = "" }) { Text("+") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Console", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::clearConsole) { Text("clear") }
        }
        ConsoleView(console)
    }
}

@Composable
private fun ConsoleView(lines: List<String>) {
    val scroll = rememberScrollState()
    // Auto-scroll to the newest line.
    androidx.compose.runtime.LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.heightIn(min = 200.dp, max = 420.dp).verticalScroll(scroll).padding(8.dp),
        ) {
            for (l in lines) {
                val color = when {
                    l.startsWith(">") -> MaterialTheme.colorScheme.primary
                    l.startsWith("#") -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = color)
            }
        }
    }
}
