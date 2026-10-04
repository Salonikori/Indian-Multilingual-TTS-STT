package com.itantra.app.models

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Installs models from assets/ to app-private storage on first run.
 * Tracks installation progress and provides status updates.
 */
class AssetModelInstaller(private val context: Context) {
    
    // lazy: must not touch the Context during Activity construction
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("model_installer", Context.MODE_PRIVATE)
    }
    private val tag = "AssetModelInstaller"
    
    data class InstallProgress(
        val currentFile: String,
        val filesComplete: Int,
        val totalFiles: Int,
        val bytesComplete: Long,
        val totalBytes: Long
    ) {
        val progressPercent: Int get() = if (totalBytes > 0) ((bytesComplete * 100) / totalBytes).toInt() else 0
    }
    
    data class InstallResult(
        val success: Boolean,
        val installedFiles: Int,
        val totalSizeBytes: Long,
        val durationMs: Long,
        val error: String? = null
    )
    
    /**
     * Check if models are already installed for all supported languages.
     */
    suspend fun areModelsInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val supportedLanguages = LanguageRegistry.languages
            supportedLanguages.all { spec ->
                ModelFiles.installStatusInFilesDir(context, spec)
            } && ModelFiles.resolve(context, VAD_PATH).isFile
        } catch (e: Exception) {
            Log.w(tag, "Error checking install status", e)
            false
        }
    }
    
    /**
     * Install models from assets to app storage with progress callbacks.
     */
    suspend fun installModels(
        onProgress: ((InstallProgress) -> Unit)? = null
    ): InstallResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Check if already installed via preferences (faster than file check)
            val installVersion = prefs.getInt("install_version", 0)
            val currentVersion = 2 // Increment when model files change
            
            if (installVersion >= currentVersion && areModelsInstalled()) {
                Log.i(tag, "Models already installed (version $installVersion)")
                return@withContext InstallResult(
                    success = true,
                    installedFiles = 0,
                    totalSizeBytes = 0,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
            
            Log.i(tag, "Starting model installation from assets")
            
            val filesToInstall = collectFilesToInstall()
            val totalBytes = calculateTotalSize(filesToInstall)
            
            Log.i(tag, "Installing ${filesToInstall.size} files, ~${totalBytes / 1024 / 1024}MB")
            
            var filesComplete = 0
            var bytesComplete = 0L
            
            for (assetPath in filesToInstall) {
                val targetFile = ModelFiles.resolve(context, assetPath)
                
                onProgress?.invoke(InstallProgress(
                    currentFile = assetPath,
                    filesComplete = filesComplete,
                    totalFiles = filesToInstall.size,
                    bytesComplete = bytesComplete,
                    totalBytes = totalBytes
                ))
                
                val fileSize = copyAssetToFile(assetPath, targetFile)
                bytesComplete += fileSize
                filesComplete++
                
                Log.d(tag, "Installed $assetPath (${fileSize / 1024}KB)")
            }
            
            // Mark installation complete
            prefs.edit()
                .putInt("install_version", currentVersion)
                .putLong("install_time", System.currentTimeMillis())
                .putLong("install_size", totalBytes)
                .apply()
            
            val result = InstallResult(
                success = true,
                installedFiles = filesComplete,
                totalSizeBytes = bytesComplete,
                durationMs = System.currentTimeMillis() - startTime
            )
            
            Log.i(tag, "Model installation complete: ${result.installedFiles} files, " +
                    "${result.totalSizeBytes / 1024 / 1024}MB in ${result.durationMs}ms")
            
            result
            
        } catch (e: Exception) {
            Log.e(tag, "Model installation failed", e)
            InstallResult(
                success = false,
                installedFiles = 0,
                totalSizeBytes = 0,
                durationMs = System.currentTimeMillis() - startTime,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Get installation info from preferences.
     */
    fun getInstallInfo(): Map<String, Any> {
        return mapOf(
            "version" to prefs.getInt("install_version", 0),
            "installTime" to prefs.getLong("install_time", 0),
            "installSize" to prefs.getLong("install_size", 0)
        )
    }
    
    /**
     * Force reinstall by clearing installation markers.
     */
    fun clearInstallation() {
        prefs.edit().clear().apply()
        Log.i(tag, "Installation markers cleared - will reinstall on next run")
    }
    
    /**
     * Debug method to test installation with current placeholder files.
     */
    suspend fun testInstallation(): InstallResult = withContext(Dispatchers.IO) {
        Log.i(tag, "Running test installation...")
        clearInstallation()
        installModels { progress ->
            Log.d(tag, "Test install progress: ${progress.progressPercent}% " +
                    "(${progress.filesComplete}/${progress.totalFiles}) - ${progress.currentFile}")
        }
    }
    
    private fun collectFilesToInstall(): List<String> {
        val files = mutableListOf<String>()
        
        for (spec in LanguageRegistry.languages) {
            // Add STT files
            files.add(spec.sttModelRelativePath)
            files.add(spec.sttTokensRelativePath)
            
            // Add additional STT files based on architecture
            when (spec.sttArchitecture) {
                SttArchitecture.TRANSDUCER -> {
                    val sttDir = spec.sttModelRelativePath.substringBeforeLast("/")
                    files.add("$sttDir/decoder.int8.onnx")
                    files.add("$sttDir/joiner.int8.onnx")
                }
                SttArchitecture.WHISPER -> {
                    val sttDir = spec.sttModelRelativePath.substringBeforeLast("/")
                    files.add("$sttDir/decoder.int8.onnx")
                }
                SttArchitecture.NEMO_CTC -> {
                    // Only model.int8.onnx and tokens.txt needed
                }
            }
            
            // Add TTS files
            files.add(spec.ttsModelRelativePath)
            files.add(spec.ttsTokensRelativePath)
            
            // Add the whole espeak-ng-data tree (espeak needs dict/voices/lang files, not just 3)
            val espeakDir = spec.ttsDataRelativePath
            if (espeakDir.endsWith("espeak-ng-data")) {
                files.addAll(listAssetFilesRecursive(espeakDir))
            }
        }

        // Silero VAD model (used for pause detection)
        files.add(VAD_PATH)

        return files.distinct()
    }

    private fun listAssetFilesRecursive(dir: String): List<String> {
        val children = context.assets.list(dir) ?: return emptyList()
        if (children.isEmpty()) return emptyList() // not a directory (or empty)
        val out = mutableListOf<String>()
        for (child in children) {
            val path = "$dir/$child"
            val sub = context.assets.list(path)
            if (sub != null && sub.isNotEmpty()) out.addAll(listAssetFilesRecursive(path))
            else out.add(path)
        }
        return out
    }

    private fun assetSize(path: String): Long = try {
        // openFd works for uncompressed assets (.onnx are stored uncompressed via noCompress)
        context.assets.openFd(path).use { it.length }
    } catch (e: IOException) {
        try {
            context.assets.open(path).use { it.available().toLong() }
        } catch (e2: IOException) { 0L }
    }

    private fun calculateTotalSize(filePaths: List<String>): Long = filePaths.sumOf { assetSize(it) }

    private fun copyAssetToFile(assetPath: String, targetFile: File): Long {
        targetFile.parentFile?.mkdirs()
        val tmp = File(targetFile.parentFile, targetFile.name + ".tmp")
        var bytesTransferred = 0L
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        bytesTransferred += n
                    }
                    output.flush()
                }
            }
            if (bytesTransferred == 0L) throw IOException("Asset is empty or missing: $assetPath")
            if (targetFile.exists()) targetFile.delete()
            if (!tmp.renameTo(targetFile)) throw IOException("Could not move $tmp to $targetFile")
        } catch (e: IOException) {
            Log.e(tag, "Failed to copy asset $assetPath", e)
            tmp.delete()
            throw IOException("Missing or unreadable model file in APK: $assetPath (${e.message})", e)
        }
        return bytesTransferred
    }

    companion object {
        const val VAD_PATH = "models/vad/silero_vad.onnx"
    }
}