package com.itantra.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itantra.app.audio.WavReader
import com.itantra.app.models.LanguageManager
import com.itantra.app.models.LanguageRegistry
import com.itantra.app.models.LoadedLanguageState
import com.itantra.app.models.ModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.distinct().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionRequest.launch(wanted.toTypedArray())
        setContent { MaterialTheme { SmokeScreen(this) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmokeScreen(activity: ComponentActivity) {
    val context = activity
    val manager = remember { LanguageManager(context) }
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf("hi") }
    var status by remember { mutableStateOf("Select a language, check install status, then press Load.") }
    var result by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(LoadedLanguageState()) }
    var busy by remember { mutableStateOf(false) }
    // Tracks whether all required files are present for the selected language.
    var filesInstalled by remember { mutableStateOf<Boolean?>(null) } // null = checking
    DisposableEffect(Unit) { onDispose { manager.release(); AppState.languageManager = null } }

    val spec = LanguageRegistry.byCode(language)

    // Re-check install status whenever language changes or screen composes.
    LaunchedEffect(language) {
        filesInstalled = null
        filesInstalled = withContext(Dispatchers.IO) {
            runCatching { ModelFiles.installStatus(context, spec) }.getOrDefault(false)
        }
        if (filesInstalled == false)
            status = "Model files not installed for ${spec.displayName}. Run install_models.py (see README)."
        else if (filesInstalled == true && !loaded.isLoaded)
            status = "Files present for ${spec.displayName}. Press Load to initialise engines."
    }

    fun selectLanguage(code: String) {
        if (language == code || busy) return
        language = code
        result = ""
        busy = true
        status = "Releasing previous language engines…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { manager.release() }
                loaded = LoadedLanguageState()
                status = "${LanguageRegistry.byCode(code).displayName} selected."
            } catch (e: Exception) {
                loaded = LoadedLanguageState(error = e.message)
                status = "Language switch cleanup failed: ${e.message}"
            } finally { busy = false }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("iTantra · Model Smoke Test") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Offline model validation", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { activity.startActivity(Intent(activity, LanguagesActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Languages and model status · 10 languages") }
            OutlinedButton(onClick = { activity.startActivity(Intent(activity, BenchmarkActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Benchmark screen · metrics and export") }
            OutlinedButton(
                enabled = loaded.ttsLoaded,
                onClick = { activity.startActivity(Intent(activity, TtsTestActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("TTS test · synthesize + alert${if (!loaded.ttsLoaded) " (load a language first)" else ""}") }
            OutlinedButton(
                enabled = loaded.sttLoaded,
                onClick = {
                    val intent = Intent(activity, LiveSttActivity::class.java).apply {
                        putExtra(LiveSttActivity.EXTRA_LANGUAGE_CODE, language)
                    }
                    activity.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Live STT · microphone → transcript${if (!loaded.sttLoaded) " (load a language first)" else ""}") }
            Text("Only one language's STT + TTS engines are held in memory at a time.")

            // Language selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(enabled = !busy, selected = language == "hi", onClick = { selectLanguage("hi") }, label = { Text("Hindi") })
                FilterChip(enabled = !busy, selected = language == "en", onClick = { selectLanguage("en") }, label = { Text("English") })
            }

            // Install status indicator
            when (filesInstalled) {
                null -> Text("Checking installed files…", style = MaterialTheme.typography.bodySmall)
                false -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Files not installed for ${spec.displayName}", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Run optional_model_manager/install_models.py with a connected device, then return here.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Expected files:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        val sttPath = spec.sttModelRelativePath.substringBefore("/stt/") + "/stt/"
                        val ttsPath = spec.ttsModelRelativePath.substringBefore("/tts/") + "/tts/"
                        if (spec.sttArchitecture == com.itantra.app.models.SttArchitecture.TRANSDUCER) {
                            Text("  ${sttPath}encoder.int8.onnx, decoder.int8.onnx, joiner.int8.onnx, tokens.txt",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        } else {
                            Text("  ${sttPath}model.int8.onnx, tokens.txt",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Text("  ${ttsPath}model.onnx, tokens.txt" +
                            if (language == "en") ", espeak-ng-data/" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                true -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text("Files installed for ${spec.displayName} ✓", Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Text("Registry status: ${spec.status}")
            Text("STT: ${spec.sttModelRelativePath}\nTTS: ${spec.ttsModelRelativePath}", style = MaterialTheme.typography.bodySmall)

            Button(enabled = !busy && filesInstalled == true, onClick = {
                busy = true; result = ""
                scope.launch {
                    try {
                        loaded = withContext(Dispatchers.IO) { manager.loadLanguage(spec) }
                        AppState.languageManager = manager   // expose to LiveSttActivity
                        status = "Loaded ${spec.displayName}. STT ${loaded.sttLoadMillis} ms; TTS ${loaded.ttsLoadMillis} ms."
                    } catch (e: Exception) {
                        manager.release()
                        AppState.languageManager = null
                        loaded = LoadedLanguageState(error = e.message)
                        status = "Load failed: ${e.message}"
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Loading…" else "Load active language") }

            Button(enabled = !busy && loaded.sttLoaded, onClick = {
                busy = true
                scope.launch {
                    try {
                        val sample = File(context.filesDir, "samples/smoke_sample.wav")
                        require(sample.isFile) { "Place a 16 kHz PCM-16 mono WAV at:\n${sample.absolutePath}\n(adb push your_file.wav ${sample.absolutePath})" }
                        val decoded = withContext(Dispatchers.IO) {
                            val wav = WavReader.readPcm16(sample)
                            val start = System.nanoTime()
                            val text = manager.decode(wav.samples, wav.sampleRate)
                            val ms = (System.nanoTime() - start) / 1_000_000
                            Triple(text, ms, wav.samples.size.toDouble() / wav.sampleRate)
                        }
                        result = "STT text: ${decoded.first}\nDecode time: ${decoded.second} ms\nAudio duration: %.2f s".format(decoded.third)
                        status = "STT smoke test completed."
                    } catch (e: Exception) { status = "STT test failed: ${e.message}" }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Smoke test 1 · Decode sample WAV") }

            Button(enabled = !busy && loaded.ttsLoaded, onClick = {
                busy = true
                scope.launch {
                    try {
                        val sentence = if (language == "hi") "नमस्ते। कृपया मेरी बात सुनें।" else "Hello. Please listen to my message."
                        val generated = withContext(Dispatchers.IO) {
                            val start = System.nanoTime()
                            val audio = manager.synthesize(sentence)
                            val ms = (System.nanoTime() - start) / 1_000_000
                            Triple(audio.first, audio.second, ms)
                        }
                        result = "TTS sentence: $sentence\nSynthesis time: ${generated.third} ms\nSample rate: ${generated.second} Hz"
                        withContext(Dispatchers.IO) { playAudio(generated.first, generated.second) }
                        status = "TTS smoke test completed."
                    } catch (e: Exception) { status = "TTS test failed: ${e.message}" }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Smoke test 2 · Synthesize and play") }

            OutlinedButton(enabled = !busy, onClick = {
                manager.release(); AppState.languageManager = null
                loaded = LoadedLanguageState(); status = "All language engines released."
            }, modifier = Modifier.fillMaxWidth()) { Text("Unload all models") }

            Text("Engine state: STT=${loaded.sttLoaded}, TTS=${loaded.ttsLoaded}. VAD integration is not implemented in this smoke screen.")

            // Status line — red on failure, green on success
            val isError = status.contains("failed", ignoreCase = true) ||
                          status.contains("not installed", ignoreCase = true) ||
                          status.contains("error", ignoreCase = true)
            Text(status,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    status.contains("Loaded") || status.contains("completed") -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                })

            // Error detail card
            loaded.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Error detail", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(err, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (result.isNotBlank()) Card { Text(result, Modifier.padding(12.dp)) }
        }
    }
}

private fun playAudio(samples: FloatArray, sampleRate: Int) {
    val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(maxOf(min, pcm.size * 2))
        .setTransferMode(AudioTrack.MODE_STATIC).build()
    try { track.write(pcm, 0, pcm.size); track.play(); while (track.playState == AudioTrack.PLAYSTATE_PLAYING) Thread.sleep(30) }
    finally { track.stop(); track.release() }
}

