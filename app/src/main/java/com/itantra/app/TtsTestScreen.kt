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
    val hindiSamples = listOf(
        "नमस्ते। कृपया सुरक्षित स्थान पर जाएँ।",
        "आपातकाल की स्थिति में तुरंत इस स्थान को छोड़ें और निकटतम सुरक्षित क्षेत्र में जाएँ।",
        "यह एक परीक्षण संदेश है। कृपया ध्यान से सुनें। आपकी सुरक्षा हमारी प्राथमिकता है।"
    )
    
    val englishSamples = listOf(
        "Hello. Please proceed to a safe location.",
        "Emergency situation detected. Please evacuate the area immediately and proceed to the nearest safety zone.",
        "This is a test message for the text-to-speech system. Please listen carefully and confirm that the audio is clear and intelligible."
    )
    
    var text by remember { 
        mutableStateOf(
            if (languages.firstOrNull()?.first == "en") englishSamples[0] else hindiSamples[0]
        ) 
    }
    var language by remember { mutableStateOf(languages.firstOrNull()?.first ?: "hi") }
    var isAlert by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TTS Test - Phase 4", style = MaterialTheme.typography.headlineMedium)
        
        Text("Test Conditions:", style = MaterialTheme.typography.titleMedium)
        Text("• Streaming TTS - first sentence should start before full synthesis completes")
        Text("• Alert path testing in various phone states")
        Text("• Volume restoration after alerts")
        
        OutlinedTextField(value = text, onValueChange = { text = it },
            label = { Text("Text to speak") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        
        Text("Sample Texts")
        val samples = if (language == "en") englishSamples else hindiSamples
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            samples.forEachIndexed { index, sample ->
                Button(
                    onClick = { text = sample },
                    modifier = Modifier.weight(1f)
                ) { 
                    Text("${index + 1}") 
                }
            }
        }
        
        Text("Language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (code, label) ->
                FilterChip(selected = language == code, onClick = { 
                    language = code
                    // Update text to match language
                    text = if (code == "en") englishSamples[0] else hindiSamples[0]
                }, label = { Text(label) })
            }
        }
        
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Normal")
            Switch(checked = isAlert, onCheckedChange = { isAlert = it })
            Text("Alert${if (isAlert) " (with attention tone + max alarm volume)" else ""}")
        }
        
        Button(onClick = { onPlay(text, language, isAlert) }, enabled = text.isNotBlank()) { 
            Text("▶ Play ${if (isAlert) "Alert" else "Normal"}") 
        }
        
        HorizontalDivider()
        
        Text("Performance Metrics:", style = MaterialTheme.typography.titleMedium)
        Text("Synthesis time: ${synthesisTimeMs?.let { "$it ms" } ?: "—"}")
        Text("RTF (Real-time factor): ${rtf?.let { "%.3f".format(it) } ?: "—"}")
        Text("Time to first audio: ${timeToFirstAudioMs?.let { "$it ms" } ?: "—"}")
        
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Test Instructions:", style = MaterialTheme.typography.titleSmall)
                Text("1. Test normal playback with sample texts")
                Text("2. Test ALERT mode in these conditions:")
                Text("   • Phone in silent mode")
                Text("   • Screen turned off")
                Text("   • Another app playing music")
                Text("   • Do Not Disturb enabled")
                Text("3. Confirm alarm volume is restored after alerts")
                Text("4. Verify streaming: first sentence starts before full text synthesis")
            }
        }
    }
}
