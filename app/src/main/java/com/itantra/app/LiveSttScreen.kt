package com.itantra.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.audio.*

@Composable
fun LiveSttScreen(controller: LiveSttController, onStart: () -> Unit, onStop: () -> Unit) {
    val state by controller.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Live STT", style = MaterialTheme.typography.headlineMedium)
        Text("State: ${state.phase}")
        Text(state.message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart) { Text("Arm microphone") }
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
        state.latest?.let { v -> Card { Column(Modifier.padding(12.dp)) {
            Text("Final transcript", style = MaterialTheme.typography.titleMedium)
            Text(v.text)
            Text("End of speech → text ready: ${v.endToTextMillis} ms")
            Text("Decode: ${v.decodeMillis} ms")
            Text("Audio length: ${v.audioLengthMillis} ms")
            Text("RTF: %.3f".format(v.rtf))
        } } }
        state.idleCpuPercent?.let { Text("Recent idle CPU sample: %.2f%%".format(it)) }
        Text("CPU value is a rough thread-level sample, not a sustained benchmark.", style = MaterialTheme.typography.bodySmall)
    }
}
