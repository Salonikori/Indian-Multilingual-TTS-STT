package com.itantra.app.benchmark

import android.content.Context
import android.os.Debug
import android.app.ActivityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Stores raw samples, not fabricated summaries. Values persist in app-private SharedPreferences. */
class BenchmarkStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("benchmark_samples_v1", Context.MODE_PRIVATE)
    fun add(metric: String, value: Double, unit: String = "ms", method: String = "manual measurement", timestampEpochMs: Long = System.currentTimeMillis()) {
        require(metric.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
        require(value.isFinite() && value >= 0.0)
        val a = JSONArray(prefs.getString("samples", "[]"))
        a.put(JSONObject().put("metric", metric).put("value", value).put("unit", unit)
            .put("method", method).put("timestampEpochMs", timestampEpochMs))
        prefs.edit().putString("samples", a.toString()).apply()
    }
    fun samples(): JSONArray = JSONArray(prefs.getString("samples", "[]"))
    fun setTestContext(value: String) { prefs.edit().putString("test_context", value.take(2000)).apply() }
    fun clear() { prefs.edit().remove("samples").apply() }

    fun snapshot(): JSONObject {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val apk = runCatching { File(context.applicationInfo.sourceDir).length() }.getOrNull()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val systemMemory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val socModel = if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MODEL else android.os.Build.HARDWARE
        val modelRoot = File(context.filesDir, "models")
        val modelFiles = JSONArray()
        if (modelRoot.isDirectory) modelRoot.walkTopDown().filter { it.isFile }.forEach { f ->
            modelFiles.put(JSONObject().put("path", f.relativeTo(context.filesDir).path).put("bytes", f.length()))
        }
        val rawSamples = samples()
        val notMeasured = mutableListOf("WER requires per-language recorded WAV/reference corpus", "TTS listening scores require human panel submissions", "end-to-end latency requires synchronized/recorded two-phone audio", "STT/TTS pipeline event timings are not automatically instrumented in this build")
        val hasPssSeries = (0 until rawSamples.length()).any { rawSamples.optJSONObject(it)?.optString("metric") == "process_total_pss_kb" }
        val hasCpuSeries = (0 until rawSamples.length()).any { rawSamples.optJSONObject(it)?.optString("metric") == "silent_listening_cpu_percent_one_core" }
        if (!hasPssSeries) notMeasured.add("peak RAM requires repeated samples during model load/inference")
        if (!hasCpuSeries) notMeasured.add("5-minute silent-listening CPU requires the explicit CPU sampling run")
        return JSONObject()
            .put("schemaVersion", 1)
            .put("capturedAtEpochMs", System.currentTimeMillis())
            .put("testContext", prefs.getString("test_context", "not recorded"))
            .put("device", JSONObject().put("manufacturer", android.os.Build.MANUFACTURER)
                .put("model", android.os.Build.MODEL).put("device", android.os.Build.DEVICE)
                .put("hardware", android.os.Build.HARDWARE).put("socModel", socModel)
                .put("totalRamBytes", systemMemory.totalMem).put("sdkInt", android.os.Build.VERSION.SDK_INT)
                .put("supportedAbis", JSONArray(android.os.Build.SUPPORTED_ABIS.toList())))
            .put("app", JSONObject().put("packageName", context.packageName)
                .put("versionName", runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull())
                .put("installedApkBytes", apk ?: JSONObject.NULL))
            .put("memory", JSONObject().put("dalvikPssKb", info.dalvikPss).put("nativePssKb", info.nativePss)
                .put("otherPssKb", info.otherPss).put("totalPssKb", info.totalPss)
                .put("method", "Debug.getMemoryInfo; snapshot only, not peak unless captured during peak"))
            .put("models", modelFiles)
            .put("samples", rawSamples)
            .put("notMeasured", JSONArray(notMeasured))
    }

    fun currentTotalPssKb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss
    }

    fun cpuTicksPerSecond(): Long? = runCatching {
        RandomAccessFile("/proc/self/stat", "r").use { raf ->
            val line = raf.readLine(); val end = line.lastIndexOf(')')
            val f = line.substring(end + 2).trim().split(Regex("\\s+"))
            f[11].toLong() + f[12].toLong()
        }
    }.getOrNull()
}
