package com.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.models.LanguageRegistry
import com.itantra.app.models.ModelFiles
import com.itantra.app.models.ModelStatus
import java.io.File

class LanguagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LanguagesScreen(onBack = { finish() }, filesDir = filesDir) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagesScreen(onBack: () -> Unit, filesDir: File) {
    var showExperimental by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val visibleLanguages = LanguageRegistry.languages.filter {
        showExperimental || it.status != ModelStatus.EXPERIMENTAL
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Languages · 10 languages") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Offline language packs", style = MaterialTheme.typography.headlineSmall)
            Text("VALIDATED requires measured STT WER/RTF and a human TTS listening check. Registry entries alone do not qualify.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Show experimental languages")
                Switch(checked = showExperimental, onCheckedChange = { showExperimental = it })
            }
            visibleLanguages.forEach { spec ->
                val packDir = File(filesDir, "models/${spec.code}")
                val bundleBytes = if (packDir.isDirectory) packDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
                val installed = runCatching { ModelFiles.installStatus(context, spec) }.getOrDefault(false)
                val statusText = when {
                    spec.status == ModelStatus.VALIDATED -> "VALIDATED"
                    spec.status == ModelStatus.EXPERIMENTAL -> "EXPERIMENTAL"
                    installed -> "INSTALLED · NOT VALIDATED"
                    else -> "NOT_INSTALLED"
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(statusText, style = MaterialTheme.typography.labelMedium,
                                color = if (spec.status == ModelStatus.VALIDATED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        }
                        Text("Code: ${spec.code} · STT: ${spec.sttArchitecture}", style = MaterialTheme.typography.bodySmall)
                        Text("Installed files: ${if (bundleBytes > 0) formatBytes(bundleBytes) else "None"}")
                        Text("Measured model size: ${spec.measuredBundleBytes?.let(::formatBytes) ?: "Not measured"}")
                        Text(spec.validationNote, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Model packs are not bundled in this build. Install only packs whose exact model, tokenizer, license and test results are documented in MODELS.md.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}
