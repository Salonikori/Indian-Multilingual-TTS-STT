package com.itantra.app.models

import android.content.Context
import java.io.File

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

    fun installStatus(context: Context, spec: LanguageSpec): Boolean {
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
        }
        return (sttFiles + listOf(ttsModel, ttsTokens)).all { it.isFile } && ttsData.isDirectory
    }
}
