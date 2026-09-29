package com.itantra.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.benchmark.BenchmarkStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BenchmarkScreen(this) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BenchmarkScreen(activity: BenchmarkActivity) {
    val store = remember { BenchmarkStore(activity) }
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var testContext by remember { mutableStateOf("language=not specified; model_revision=not specified; scenario=not specified") }
    var status by remember { mutableStateOf("Load exactly one language before measuring. Snapshot reports current PSS, installed APK size, device SoC/RAM, and on-device model files. Capture repeated samples while the model is loaded to estimate a sampled high-water mark.") }
    var cpuRunning by remember { mutableStateOf(false) }
    var cpuSeconds by remember { mutableIntStateOf(0) }
    var latest by remember { mutableStateOf(store.snapshot().put("samples", JSONArray()).toString(2)) }
    val metricOptions = listOf("speech_end_to_stt_ready_ms", "text_received_to_first_audio_ms", "tts_rtf", "phone_a_speech_to_phone_b_audio_ms")

    fun refresh() { latest = store.snapshot().put("samples", JSONArray()).toString(2) }
    Scaffold(topBar = { TopAppBar(title = { Text("iTantra Â· Benchmarks") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Device snapshot", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = testContext, onValueChange = { testContext = it }, label = { Text("Test context: language, model revision, scenario") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { store.setTestContext(testContext); refresh(); status = "Test context saved and will be included in exports." }, modifier = Modifier.fillMaxWidth()) { Text("Save test context") }
            Text("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} Â· Android ${android.os.Build.VERSION.RELEASE}")
            Button(onClick = { refresh(); status = "Snapshot refreshed. Export JSON to retain raw values." }, modifier = Modifier.fillMaxWidth()) { Text("Capture memory / APK / model-size snapshot") }
            Text("Silent-listening CPU sampler", style = MaterialTheme.typography.titleMedium)
            Text("Start this while the app is armed but silent. Keep it running for at least 300 seconds. CPU% uses Process.getElapsedCpuTime() / elapsed wall time and is expressed as a percentage of one core; multi-thread usage may exceed 100%.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = !cpuRunning, onClick = {
                cpuRunning = true; cpuSeconds = 0
                scope.launch {
                    val startTicks = store.cpuTicksPerSecond()
                    val startNs = System.nanoTime()
                    val tickSamples = mutableListOf<Triple<Double, String, Long>>()
                    val pssSamples = mutableListOf<Pair<Double, Long>>()
                    val cpuSamples = mutableListOf<Pair<Double, Long>>()
                    var previousTicks = startTicks
                    var previousCpuMs = android.os.Process.getElapsedCpuTime()
                    var previousWallNs = System.nanoTime()
                    repeat(300) {
                        delay(1000)
                        cpuSeconds++
                        val ticks = store.cpuTicksPerSecond()
                        val sampleEpoch = System.currentTimeMillis()
                        if (ticks != null && previousTicks != null) tickSamples.add(Triple((ticks - previousTicks!!).toDouble(), "sampled once per second from /proc/self/stat; second=$cpuSeconds", sampleEpoch))
                        pssSamples.add(store.currentTotalPssKb().toDouble() to sampleEpoch)
                        val cpuMs = android.os.Process.getElapsedCpuTime()
                        val wallNs = System.nanoTime()
                        val wallMs = (wallNs - previousWallNs) / 1_000_000.0
                        if (wallMs > 0) cpuSamples.add((((cpuMs - previousCpuMs).coerceAtLeast(0) / wallMs) * 100.0) to sampleEpoch)
                        previousCpuMs = cpuMs; previousWallNs = wallNs
                        previousTicks = ticks
                    }
                    tickSamples.forEach { store.add("silent_listening_cpu_delta_ticks", it.first, "ticks", it.second, it.third) }
                    pssSamples.forEach { store.add("process_total_pss_kb", it.first, "KiB", "Debug.getMemoryInfo sample during silent-listening run", it.second) }
                    cpuSamples.forEach { store.add("silent_listening_cpu_percent_one_core", it.first, "% one core", "Process.getElapsedCpuTime delta / wall-clock delta; multi-thread usage can exceed 100%", it.second) }
                    val elapsed = (System.nanoTime() - startNs) / 1_000_000_000.0
                    val endTicks = store.cpuTicksPerSecond()
                    if (startTicks != null && endTicks != null && elapsed > 0) store.add("silent_listening_cpu_ticks_per_second", (endTicks - startTicks).toDouble() / elapsed, "ticks/s", "300-second process CPU delta; clock tick rate is device-dependent")
                    store.add("silent_listening_cpu_sample_duration_seconds", elapsed, "s", "process stat sampling; target 300 seconds")
                    cpuRunning = false; refresh(); status = "CPU sampling completed (${"%.1f".format(elapsed)} s). Interpret raw tick values with device USER_HZ; use adb top cross-check per README."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (cpuRunning) "Samplingâ€¦ $cpuSeconds / 300 s" else "Start 5-minute silent CPU sample") }
            Text("Latency / RTF sample entry", style = MaterialTheme.typography.titleMedium)
            Text("Enter values from instrumented runs or synchronized external recordings. This form does not automatically measure the live pipeline. Use N â‰¥ 20 per metric and label timing method.", style = MaterialTheme.typography.bodySmall)
            var selected by remember { mutableStateOf(metricOptions.first()) }
            var timingMethod by remember { mutableStateOf("manual / external recording; specify in notes") }
            var sampleUnit by remember { mutableStateOf("ms") }
            OutlinedTextField(value = selected, onValueChange = { selected = it }, label = { Text("Metric key (editable)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Measured value") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = sampleUnit, onValueChange = { sampleUnit = it }, label = { Text("Unit (ms or rtf)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = timingMethod, onValueChange = { timingMethod = it }, label = { Text("Measurement method / run ID") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val n = value.toDoubleOrNull()
                if (n == null || !n.isFinite() || n < 0) status = "Enter a finite non-negative numeric measurement."
                else { store.add(selected, n, sampleUnit, timingMethod); value = ""; refresh(); status = "Raw sample saved. Repeat at least 20 times for latency metrics." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save measured sample") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { store.setTestContext(testContext); shareExport(activity, store.snapshot(), "json"); status = "JSON share sheet opened." }, modifier = Modifier.weight(1f)) { Text("Export JSON") }
                Button(onClick = { store.setTestContext(testContext); shareExport(activity, store.snapshot(), "csv"); status = "CSV share sheet opened." }, modifier = Modifier.weight(1f)) { Text("Export CSV") }
            }
            OutlinedButton(onClick = { store.clear(); refresh(); status = "Raw samples cleared." }, modifier = Modifier.fillMaxWidth()) { Text("Clear raw samples") }
            Text(status, color = MaterialTheme.colorScheme.primary)
            Text("Snapshot preview", style = MaterialTheme.typography.titleMedium)
            Text(latest, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun shareExport(activity: BenchmarkActivity, json: JSONObject, extension: String) {
    val dir = File(activity.cacheDir, "benchmark-export").apply { mkdirs() }
    val file = File(dir, "itantra-benchmarks-${System.currentTimeMillis()}.$extension")
    val content = if (extension == "json") json.toString(2) else toCsv(json)
    file.writeText(content, Charsets.UTF_8)
    // Export is shared as a file attachment through the app-private FileProvider cache path.
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).setType(if (extension == "json") "application/json" else "text/csv")
        .putExtra(Intent.EXTRA_SUBJECT, "iTantra benchmark export")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    activity.startActivity(Intent.createChooser(send, "Export benchmark $extension"))
}

private fun toCsv(root: JSONObject): String {
    val rows = mutableListOf("record_type,key,value,unit,method,device_model,device_soc_model,timestamp_epoch_ms")
    val device = root.optJSONObject("device") ?: JSONObject()
    val samples = root.optJSONArray("samples") ?: JSONArray()
    fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
    for (i in 0 until samples.length()) {
        val s = samples.getJSONObject(i)
        rows += listOf("sample", s.optString("metric"), s.opt("value")?.toString() ?: "", s.optString("unit"), s.optString("method"), device.optString("model"), device.optString("socModel"), s.opt("timestampEpochMs")?.toString() ?: "").joinToString(",", transform = ::esc)
    }
    val app = root.optJSONObject("app") ?: JSONObject()
    val mem = root.optJSONObject("memory") ?: JSONObject()
    rows += listOf("context", "test_context", root.optString("testContext", "not recorded"), "text", "user-supplied run label; not independently verified", device.optString("model"), device.optString("socModel"), root.opt("capturedAtEpochMs")?.toString() ?: "").joinToString(",", transform = ::esc)
    rows += listOf("snapshot", "installed_apk_bytes", app.opt("installedApkBytes")?.toString() ?: "", "bytes", "PackageManager sourceDir length", device.optString("model"), device.optString("socModel"), root.opt("capturedAtEpochMs")?.toString() ?: "").joinToString(",", transform = ::esc)
    rows += listOf("snapshot", "current_total_pss_kb", mem.opt("totalPssKb")?.toString() ?: "", "KiB", mem.optString("method"), device.optString("model"), device.optString("socModel"), root.opt("capturedAtEpochMs")?.toString() ?: "").joinToString(",", transform = ::esc)
    rows += listOf("device", "total_ram_bytes", device.opt("totalRamBytes")?.toString() ?: "", "bytes", "ActivityManager.MemoryInfo.totalMem", device.optString("model"), device.optString("socModel"), root.opt("capturedAtEpochMs")?.toString() ?: "").joinToString(",", transform = ::esc)
    val models = root.optJSONArray("models") ?: JSONArray()
    for (i in 0 until models.length()) { val m = models.getJSONObject(i); rows += listOf("model_file", m.optString("path"), m.opt("bytes")?.toString() ?: "", "bytes", "filesDir/models file length", device.optString("model"), device.optString("socModel"), root.opt("capturedAtEpochMs")?.toString() ?: "").joinToString(",", transform = ::esc) }
    return rows.joinToString("\n")
}
