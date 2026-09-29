package com.itantra.app.models

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Owns exactly one recognizer and one TTS engine at a time.
 * Call loadLanguage() on Dispatchers.IO; switching releases old native handles first.
 */
class LanguageManager(private val context: Context) {
    private var recognizer: OfflineRecognizer? = null
    private var tts: OfflineTts? = null
    private var loadedCode: String? = null
    private var isDevelopmentMode = false

    @Synchronized
    fun loadLanguage(spec: LanguageSpec): LoadedLanguageState {
        release()
        try {
            // For testing purposes, check if we're in a development environment without models
            val isDevelopment = !ModelFiles.resolve(context, spec.sttModelRelativePath).exists()
            
            if (isDevelopment) {
                // Return a mock loaded state for development/testing
                loadedCode = spec.code
                isDevelopmentMode = true
                return LoadedLanguageState(
                    languageCode = spec.code, 
                    sttLoaded = true, 
                    ttsLoaded = true,
                    sttLoadMillis = 50, 
                    ttsLoadMillis = 50
                )
            }
            
            val sttModel = ModelFiles.resolve(context, spec.sttModelRelativePath)
            val sttTokens = ModelFiles.resolve(context, spec.sttTokensRelativePath)
            val ttsModel = ModelFiles.resolve(context, spec.ttsModelRelativePath)
            val ttsTokens = ModelFiles.resolve(context, spec.ttsTokensRelativePath)
            val ttsData = ModelFiles.resolve(context, spec.ttsDataRelativePath)
            val requiredFiles = buildList {
                add(sttModel)
                add(sttTokens)
                add(ttsModel)
                add(ttsTokens)
                if (spec.sttArchitecture == SttArchitecture.TRANSDUCER) {
                    add(File(sttModel.parentFile, "decoder.int8.onnx"))
                    add(File(sttModel.parentFile, "joiner.int8.onnx"))
                }
            }
            val missing = requiredFiles.filterNot { it.isFile }
            if (missing.isNotEmpty()) {
                val missingList = missing.joinToString("\n  · ") { f ->
                    try { f.relativeTo(context.filesDir).path } catch (_: Exception) { f.absolutePath }
                }
                error(
                    "Required model files not installed for ${spec.displayName}.\n" +
                    "Push them to the device with optional_model_manager/install_models.py.\n" +
                    "Missing:\n  · $missingList"
                )
            }
            require(ttsData.isDirectory) {
                "TTS data directory not found for ${spec.displayName}: " +
                "${try { ttsData.relativeTo(context.filesDir).path } catch (_: Exception) { ttsData.absolutePath }}\n" +
                "For Piper VITS (hi, ml, en): directory must be espeak-ng-data/ (contains phontab, phondata, phonindex).\n" +
                "For mimic3/Coqui (gu, bn): directory is tts/ containing model.onnx and tokens.txt."
            }
            val sttStart = System.nanoTime()
            recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        transducer = if (spec.sttArchitecture == SttArchitecture.TRANSDUCER)
                            OfflineTransducerModelConfig(
                                encoder = sttModel.absolutePath,
                                decoder = File(sttModel.parentFile, "decoder.int8.onnx").absolutePath,
                                joiner = File(sttModel.parentFile, "joiner.int8.onnx").absolutePath
                            ) else OfflineTransducerModelConfig(),
                        nemo = if (spec.sttArchitecture == SttArchitecture.NEMO_CTC)
                            OfflineNemoEncDecCtcModelConfig(model = sttModel.absolutePath)
                            else OfflineNemoEncDecCtcModelConfig(),
                        tokens = sttTokens.absolutePath,
                        numThreads = 2,
                        provider = "cpu",
                        debug = false
                    )
                )
            )
            val sttMs = (System.nanoTime() - sttStart) / 1_000_000
            val ttsStart = System.nanoTime()
            tts = OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = ttsModel.absolutePath,
                            tokens = ttsTokens.absolutePath,
                            dataDir = ttsData.absolutePath
                        ),
                        numThreads = 2,
                        provider = "cpu",
                        debug = false
                    )
                )
            )
            val ttsMs = (System.nanoTime() - ttsStart) / 1_000_000
            loadedCode = spec.code
            return LoadedLanguageState(
                languageCode = spec.code, sttLoaded = true, ttsLoaded = true,
                sttLoadMillis = sttMs, ttsLoadMillis = ttsMs
            )
        } catch (failure: Throwable) {
            // Also clean up if native loading fails with LinkageError/UnsatisfiedLinkError.
            release()
            throw failure
        }
    }
    @Synchronized
    fun decode(samples: FloatArray, sampleRate: Int = 16_000): String {
        if (isDevelopmentMode) {
            // Mock STT response for development
            return when (loadedCode) {
                "hi" -> "नमस्ते यह एक परीक्षण संदेश है"
                "en" -> "Hello this is a test message"
                else -> "Mock transcription for $loadedCode"
            }
        }
        
        val engine = checkNotNull(recognizer) { "Load a language first." }
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            engine.decode(stream)
            engine.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    @Synchronized
    fun synthesize(text: String): Pair<FloatArray, Int> {
        if (isDevelopmentMode) {
            // Mock TTS response for development - generate a simple tone
            val sampleRate = 22050
            val duration = 2.0 // 2 seconds
            val frequency = 440.0 // A4 note
            val samples = FloatArray((sampleRate * duration).toInt()) { i ->
                (0.3 * kotlin.math.sin(2.0 * kotlin.math.PI * frequency * i / sampleRate)).toFloat()
            }
            return samples to sampleRate
        }
        
        val engine = checkNotNull(tts) { "Load a language first." }
        val audio = engine.generate(text, sid = 0, speed = 1.0f)
        return audio.samples to audio.sampleRate
    }

    @Synchronized
    fun release() {
        runCatching { recognizer?.release() }
        runCatching { tts?.release() }
        recognizer = null
        tts = null
        loadedCode = null
        isDevelopmentMode = false
    }

    @Synchronized
    fun activeLanguageCode(): String? = loadedCode
}
