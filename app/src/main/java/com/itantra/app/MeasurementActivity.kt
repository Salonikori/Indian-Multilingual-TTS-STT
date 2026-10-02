package com.itantra.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.itantra.app.benchmark.BenchmarkStore
import com.itantra.app.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MeasurementActivity : ComponentActivity() {
    
    private lateinit var benchmarkStore: BenchmarkStore
    private var languageManager: LanguageManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        benchmarkStore = BenchmarkStore(this)
        benchmarkStore.setTestContext("Phase 8 Comprehensive Measurements - ${android.os.Build.MODEL}")
        
        setContent {
            MaterialTheme {
                MeasurementScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MeasurementScreen() {
        var isRunning by remember { mutableStateOf(false) }
        var currentTest by remember { mutableStateOf("") }
        var progress by remember { mutableStateOf(0f) }
        var results by remember { mutableStateOf(listOf<String>()) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Phase 8: Comprehensive Measurements", style = MaterialTheme.typography.headlineMedium)
                    Text("Device: ${android.os.Build.MODEL}")
                    Text("SoC: ${if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MODEL else android.os.Build.HARDWARE}")
                    val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memInfo)
                    Text("RAM: ${memInfo.totalMem / (1024 * 1024 * 1024)}GB")
                }
            }
            
            if (isRunning) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Running: $currentTest")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            runEfficiencyMeasurements { test, prog ->
                                currentTest = test
                                progress = prog
                            }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Efficiency")
                }
                
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            runLatencyMeasurements { test, prog ->
                                currentTest = test
                                progress = prog
                            }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Latency")
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            runAccuracyMeasurements { test, prog ->
                                currentTest = test
                                progress = prog
                            }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accuracy")
                }
                
                Button(
                    onClick = { generateBenchmarkReport() },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export")
                }
            }
            
            Button(
                onClick = {
                    lifecycleScope.launch {
                        isRunning = true
                        runAllMeasurements { test, prog ->
                            currentTest = test
                            progress = prog
                        }
                        isRunning = false
                        results = getBenchmarkResults()
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run All Measurements (20+ minutes)")
            }
            
            if (results.isNotEmpty()) {
                Card {
                    LazyColumn(Modifier.padding(8.dp)) {
                        item { Text("Recent Results:", style = MaterialTheme.typography.titleMedium) }
                        items(results) { result ->
                            Text(result, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun runAllMeasurements(onProgress: (String, Float) -> Unit) {
        onProgress("Starting comprehensive measurements...", 0f)
        
        // Device info
        recordDeviceInfo()
        onProgress("Device info recorded", 0.1f)
        
        // Efficiency measurements
        runEfficiencyMeasurements(onProgress)
        onProgress("Efficiency measurements complete", 0.4f)
        
        // Latency measurements  
        runLatencyMeasurements(onProgress)
        onProgress("Latency measurements complete", 0.7f)
        
        // Accuracy measurements
        runAccuracyMeasurements(onProgress)
        onProgress("Accuracy measurements complete", 0.9f)
        
        // Export results
        generateBenchmarkReport()
        onProgress("All measurements complete", 1.0f)
    }
    
    private fun recordDeviceInfo() {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        benchmarkStore.add("device_total_ram_gb", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0), "GB")
        
        // APK size
        val apkFile = java.io.File(applicationInfo.sourceDir)
        benchmarkStore.add("apk_size_mb", apkFile.length() / (1024.0 * 1024.0), "MB")
    }
    
    private suspend fun runEfficiencyMeasurements(onProgress: (String, Float) -> Unit) {
        onProgress("Measuring APK and model sizes...", 0.0f)
        
        // Record APK size
        val apkFile = java.io.File(applicationInfo.sourceDir)
        benchmarkStore.add("apk_size_mb", apkFile.length() / (1024.0 * 1024.0), "MB")
        
        // Model sizes
        val modelDir = java.io.File(filesDir, "models")
        if (modelDir.exists()) {
            var totalSize = 0L
            modelDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                    val relativePath = file.relativeTo(modelDir).path
                    benchmarkStore.add("model_file_size_mb_${relativePath.replace("/", "_")}", 
                        file.length() / (1024.0 * 1024.0), "MB")
                }
            }
            benchmarkStore.add("total_models_size_mb", totalSize / (1024.0 * 1024.0), "MB")
        }
        
        onProgress("Measuring idle CPU (5 minutes)...", 0.2f)
        
        // 5-minute idle CPU measurement - real system measurement
        val cpuMeasurements = mutableListOf<Double>()
        val startTime = SystemClock.elapsedRealtime()
        
        // Use /proc/stat for actual system CPU usage measurement
        var lastTotalTime = 0L
        var lastIdleTime = 0L
        
        try {
            val statFile = java.io.File("/proc/stat")
            if (statFile.exists()) {
                val firstLine = statFile.readLines().first()
                val values = firstLine.split("\\s+".toRegex()).drop(1).map { it.toLong() }
                lastTotalTime = values.sum()
                lastIdleTime = values[3] // idle time is 4th value
            }
        } catch (e: Exception) {
            benchmarkStore.add("cpu_measurement_error", 1.0, "proc_stat_unavailable")
        }
        
        repeat(30) { i -> // 30 samples over 5 minutes (10 second intervals)
            delay(10000) // 10 seconds
            
            try {
                val statFile = java.io.File("/proc/stat")
                if (statFile.exists()) {
                    val firstLine = statFile.readLines().first()
                    val values = firstLine.split("\\s+".toRegex()).drop(1).map { it.toLong() }
                    val totalTime = values.sum()
                    val idleTime = values[3]
                    
                    val totalDelta = totalTime - lastTotalTime
                    val idleDelta = idleTime - lastIdleTime
                    
                    val cpuPercent = if (totalDelta > 0) {
                        ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0
                    } else 0.0
                    
                    cpuMeasurements.add(cpuPercent.coerceIn(0.0, 100.0))
                    benchmarkStore.add("idle_cpu_percent_sample", cpuPercent, "percent")
                    
                    lastTotalTime = totalTime
                    lastIdleTime = idleTime
                } else {
                    // Fallback: report measurement unavailable
                    benchmarkStore.add("cpu_measurement_unavailable_sample", 1.0, "count")
                }
            } catch (e: Exception) {
                benchmarkStore.add("cpu_measurement_error_sample", 1.0, "count")
            }
            
            onProgress("Idle CPU measurement ${i+1}/30", 0.2f + (i * 0.6f / 30f))
        }
        
        if (cpuMeasurements.isNotEmpty()) {
            val avgCpu = cpuMeasurements.average()
            benchmarkStore.add("idle_cpu_percent_5min_avg", avgCpu, "percent")
            benchmarkStore.add("idle_cpu_measurement_count", cpuMeasurements.size.toDouble(), "samples")
        } else {
            benchmarkStore.add("idle_cpu_measurement_status", 0.0, "no_valid_samples")
        }
        
        onProgress("Measuring RAM with language loaded...", 0.8f)
        
        // RAM measurement with language loaded
        val memoryBefore = benchmarkStore.currentTotalPssKb()
        
        try {
            languageManager = LanguageManager(this)
            val spec = LanguageRegistry.byCode("hi")
            val loaded = languageManager!!.loadLanguage(spec)
            
            if (loaded.isLoaded) {
                delay(2000) // Let memory stabilize
                val memoryAfter = benchmarkStore.currentTotalPssKb()
                benchmarkStore.add("ram_with_language_loaded_mb", memoryAfter / 1024.0, "MB")
                benchmarkStore.add("ram_delta_language_load_mb", (memoryAfter - memoryBefore) / 1024.0, "MB")
            }
        } catch (e: Exception) {
            benchmarkStore.add("language_load_error", 1.0, "count")
        }
        
        onProgress("Efficiency measurements complete", 1.0f)
    }
    
    private suspend fun runLatencyMeasurements(onProgress: (String, Float) -> Unit) {
        onProgress("Latency measurements require real usage data", 0.0f)
        
        // STT latency measurements require real recorded audio corpus
        benchmarkStore.add("stt_latency_measurement_status", 0.0, "requires_real_audio_corpus")
        onProgress("STT latency: Use tools/prepare_real_speech_corpus.py for test data", 0.3f)
        
        // TTS latency can only be measured with real synthesis requests
        benchmarkStore.add("tts_latency_measurement_status", 0.0, "requires_real_synthesis_requests")
        onProgress("TTS latency: Measure during actual app usage", 0.6f)
        
        // End-to-end latency requires real Bluetooth message exchange
        benchmarkStore.add("end_to_end_latency_measurement_status", 0.0, "requires_real_bluetooth_exchange")
        onProgress("End-to-end latency: Measure during actual communication", 0.9f)
        
        // Note: Real latency measurements are implemented in AlertsAndMeasurements.kt
        // and are collected during normal app usage via CommunicationActivity
        benchmarkStore.add("real_latency_collection_location", 1.0, "AlertsAndMeasurements_class")
        
        onProgress("Latency measurements noted as requiring real usage", 1.0f)
    }
    
    private suspend fun runAccuracyMeasurements(onProgress: (String, Float) -> Unit) {
        onProgress("Accuracy measurements require manual testing", 0.0f)
        
        // WER measurements require actual recorded audio corpus with reference transcripts
        // These cannot be automatically measured without proper test data
        benchmarkStore.add("wer_measurement_status", 0.0, "requires_manual_corpus")
        
        onProgress("TTS listening scores require human evaluation panel", 0.5f)
        
        // TTS listening scores require actual human evaluation panel
        // These cannot be automatically measured without human listeners
        benchmarkStore.add("tts_listening_measurement_status", 0.0, "requires_human_panel")
        
        onProgress("Accuracy measurements noted as requiring manual testing", 1.0f)
    }
    
    private fun generateTestAudio(durationSeconds: Double): FloatArray {
        // REMOVED: No longer generate fake audio samples
        // Real measurements require actual recorded speech corpus
        // Use tools/prepare_real_speech_corpus.py to create proper test data
        throw UnsupportedOperationException("Use real audio corpus for STT measurements")
    }
    
    private fun getBenchmarkResults(): List<String> {
        val samples = benchmarkStore.samples()
        val results = mutableListOf<String>()
        
        for (i in 0 until samples.length()) {
            val sample = samples.optJSONObject(i)
            if (sample != null) {
                val metric = sample.optString("metric")
                val value = sample.optDouble("value")
                val unit = sample.optString("unit")
                results.add("$metric: ${"%.2f".format(value)} $unit")
            }
        }
        
        return results.takeLast(10) // Show last 10 results
    }
    
    private fun generateBenchmarkReport() {
        val snapshot = benchmarkStore.snapshot()
        val outputDir = java.io.File(filesDir, "benchmark_exports")
        outputDir.mkdirs()
        
        val timestamp = System.currentTimeMillis()
        val outputFile = java.io.File(outputDir, "benchmark_${timestamp}.json")
        outputFile.writeText(snapshot.toString(2))
        
        println("Benchmark exported to: ${outputFile.absolutePath}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        languageManager?.release()
    }
}