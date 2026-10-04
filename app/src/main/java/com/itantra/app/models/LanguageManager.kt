package com.itantra.app.models

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
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

    @Synchronized
    fun loadLanguage(spec: LanguageSpec): LoadedLanguageState {
        release()
        try {
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
                when (spec.sttArchitecture) {
                    SttArchitecture.TRANSDUCER -> {
                        add(File(sttModel.parentFile, "decoder.int8.onnx"))
                        add(File(sttModel.parentFile, "joiner.int8.onnx"))
                    }
                    SttArchitecture.WHISPER -> {
                        add(File(sttModel.parentFile, "decoder.int8.onnx"))
                    }
                    SttArchitecture.NEMO_CTC -> {
                        // No additional files needed
                    }
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
                        whisper = if (spec.sttArchitecture == SttArchitecture.WHISPER)
                            OfflineWhisperModelConfig(
                                encoder = sttModel.absolutePath,
                                decoder = File(sttModel.parentFile, "decoder.int8.onnx").absolutePath,
                                language = spec.whisperLanguage,
                                task = "transcribe"
                            ) else OfflineWhisperModelConfig(),
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
            // Clean up if native loading fails with LinkageError/UnsatisfiedLinkError.
            release()
            throw failure
        }
    }
    @Synchronized
    fun decode(samples: FloatArray, sampleRate: Int = 16_000): String {
        val engine = recognizer ?: error("No STT engine loaded. Call loadLanguage() first.")
        
        return try {
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                engine.decode(stream)
                engine.getResult(stream).text
            } finally {
                stream.release()
            }
        } catch (e: Exception) {
            throw RuntimeException("STT processing failed: ${e.message}", e)
        }
    }

    @Synchronized
    fun synthesize(text: String): Pair<FloatArray, Int> {
        val engine = checkNotNull(tts) { "No TTS engine loaded. Call loadLanguage() first." }
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
    }

    @Synchronized
    fun activeLanguageCode(): String? = loadedCode
}
