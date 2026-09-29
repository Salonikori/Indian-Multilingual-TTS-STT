package com.itantra.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TtsTestScreen(
    languages: List<Pair<String, String>>,
    onPlay: (text: String, languageCode: String, isAlert: Boolean) -> Unit,
    synthesisTimeMs: Long?,
    rtf: Double?,
    timeToFirstAudioMs: Long?
) {
    var text by remember { mutableStateOf("नमस्ते। कृपया सुरक्षित स्थान पर जाएँ।") }
    var language by remember { mutableStateOf(languages.firstOrNull()?.first ?: "hi") }
    var isAlert by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TTS test", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = text, onValueChange = { text = it },
            label = { Text("Text to speak") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Text("Language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (code, label) ->
                FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Normal")
            Switch(checked = isAlert, onCheckedChange = { isAlert = it })
            Text("Alert")
        }
        Button(onClick = { onPlay(text, language, isAlert) }, enabled = text.isNotBlank()) { Text("Play") }
        HorizontalDivider()
        Text("Synthesis time: ${synthesisTimeMs?.let { "$it ms" } ?: "—"}")
        Text("RTF: ${rtf?.let { "%.3f".format(it) } ?: "—"}")
        Text("Time to first audio: ${timeToFirstAudioMs?.let { "$it ms" } ?: "—"}")
        Text("Metrics are populated by the host controller after real synthesis/playback.")
    }
}
