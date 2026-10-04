package com.itantra.app.models

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Model file resolution with asset fallback support.
 * 
 * The system works in this order:
 * 1. First check if models exist in app-private filesDir (copied by AssetModelInstaller)
 * 2. If not, check if models exist in assets as fallback (bundled in APK)
 * 3. When loading models, AssetModelInstaller copies from assets to filesDir if needed
 * 
 * This enables:
 * - Development with placeholder assets
 * - Seamless first-run experience 
 * - Proper native library integration (requires file paths, not InputStreams)
 */
object ModelFiles {
    /** Resolve a registry-relative path inside app-private files only. */
    fun resolve(context: Context, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Model path must not be blank" }
        val root = context.filesDir.canonicalFile
        val candidate = File(root, relativePath).canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) {
            "Model path escapes app-private storage: $relativePath"
        }
        return candidate
    }

    /**
     * Check if a file exists in assets.
     */
    private fun existsInAssets(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { true }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Check if models are installed in filesDir, or available as fallback in assets.
     */
    fun installStatus(context: Context, spec: LanguageSpec): Boolean {
        return installStatusInFilesDir(context, spec) || installStatusInAssets(context, spec)
    }

    /**
     * Check if models are installed in app-private filesDir only.
     */
    fun installStatusInFilesDir(context: Context, spec: LanguageSpec): Boolean {
        val sttModel = resolve(context, spec.sttModelRelativePath)
        val sttTokens = resolve(context, spec.sttTokensRelativePath)
        val ttsModel = resolve(context, spec.ttsModelRelativePath)
        val ttsTokens = resolve(context, spec.ttsTokensRelativePath)
        val ttsData = resolve(context, spec.ttsDataRelativePath)
        val sttFiles = when (spec.sttArchitecture) {
            SttArchitecture.NEMO_CTC -> listOf(sttModel, sttTokens)
            SttArchitecture.TRANSDUCER -> listOf(
                sttModel,
                File(sttModel.parentFile, "decoder.int8.onnx"),
                File(sttModel.parentFile, "joiner.int8.onnx"),
                sttTokens
            )
            SttArchitecture.WHISPER -> listOf(
                sttModel, // encoder.int8.onnx
                File(sttModel.parentFile, "decoder.int8.onnx"),
                sttTokens
            )
        }
        return (sttFiles + listOf(ttsModel, ttsTokens)).all { it.isFile } && ttsData.isDirectory
    }

    /**
     * Check if models are available in assets as fallback.
     */
    fun installStatusInAssets(context: Context, spec: LanguageSpec): Boolean {
        // Check STT files in assets
        val sttAssetFiles = when (spec.sttArchitecture) {
            SttArchitecture.NEMO_CTC -> listOf(
                spec.sttModelRelativePath,
                spec.sttTokensRelativePath
            )
            SttArchitecture.TRANSDUCER -> {
                val sttDir = spec.sttModelRelativePath.substringBeforeLast("/")
                listOf(
                    spec.sttModelRelativePath,
                    "$sttDir/decoder.int8.onnx",
                    "$sttDir/joiner.int8.onnx",
                    spec.sttTokensRelativePath
                )
            }
            SttArchitecture.WHISPER -> {
                val sttDir = spec.sttModelRelativePath.substringBeforeLast("/")
                listOf(
                    spec.sttModelRelativePath, // encoder.int8.onnx
                    "$sttDir/decoder.int8.onnx",
                    spec.sttTokensRelativePath
                )
            }
        }

        // Check TTS files in assets
        val ttsAssetFiles = listOf(
            spec.ttsModelRelativePath,
            spec.ttsTokensRelativePath
        )

        // Check espeak-ng-data files for Piper VITS
        val espeakAssetFiles = if (spec.ttsDataRelativePath.endsWith("espeak-ng-data")) {
            listOf(
                "${spec.ttsDataRelativePath}/phontab",
                "${spec.ttsDataRelativePath}/phondata",
                "${spec.ttsDataRelativePath}/phonindex"
            )
        } else {
            emptyList()
        }

        val allAssetFiles = sttAssetFiles + ttsAssetFiles + espeakAssetFiles

        return allAssetFiles.all { assetPath ->
            existsInAssets(context, assetPath)
        }
    }

    /**
     * Debug method to list install status for all supported languages.
     */
    fun debugInstallStatus(context: Context): Map<String, Map<String, Boolean>> {
        return LanguageRegistry.languages.associate { spec ->
            spec.code to mapOf(
                "filesDir" to installStatusInFilesDir(context, spec),
                "assets" to installStatusInAssets(context, spec),
                "overall" to installStatus(context, spec)
            )
        }
    }
}
