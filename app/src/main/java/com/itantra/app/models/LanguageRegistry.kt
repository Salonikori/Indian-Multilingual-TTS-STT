package com.itantra.app.models

enum class ModelStatus { VALIDATED, EXPERIMENTAL, NOT_INSTALLED }
enum class SttArchitecture { NEMO_CTC, TRANSDUCER }

data class LanguageSpec(
    val code: String,
    val displayName: String,
    val sttArchitecture: SttArchitecture,
    val sttModelRelativePath: String,
    val sttTokensRelativePath: String,
    val ttsModelRelativePath: String,
    val ttsTokensRelativePath: String,
    val ttsDataRelativePath: String,
    val status: ModelStatus,
    /** Only populated from actual recorded measurements; null means not measured. */
    val measuredBundleBytes: Long? = null,
    val validationNote: String = "No complete STT+TTS device validation recorded."
)

object LanguageRegistry {
    /** Indic language with both STT and TTS available. */
    private fun indicFull(code: String, name: String) = LanguageSpec(
        code = code,
        displayName = name,
        sttArchitecture = SttArchitecture.NEMO_CTC,
        sttModelRelativePath = "models/$code/stt/model.int8.onnx",
        sttTokensRelativePath = "models/$code/stt/tokens.txt",
        ttsModelRelativePath = "models/$code/tts/model.onnx",
        ttsTokensRelativePath = "models/$code/tts/tokens.txt",
        ttsDataRelativePath = "models/$code/tts",
        status = ModelStatus.NOT_INSTALLED,
        validationNote = "Candidate STT export only; TTS pack and measurements are not recorded."
    )

    /** Piper VITS language: dataDir must point to espeak-ng-data/, not tts/ root. */
    private fun piperFull(code: String, name: String) = LanguageSpec(
        code = code,
        displayName = name,
        sttArchitecture = SttArchitecture.NEMO_CTC,
        sttModelRelativePath = "models/$code/stt/model.int8.onnx",
        sttTokensRelativePath = "models/$code/stt/tokens.txt",
        ttsModelRelativePath = "models/$code/tts/model.onnx",
        ttsTokensRelativePath = "models/$code/tts/tokens.txt",
        // Piper VITS native layer expects phontab directly inside dataDir.
        // espeak-ng-data/ is the subdirectory that contains phontab, phondata, phonindex.
        ttsDataRelativePath = "models/$code/tts/espeak-ng-data",
        status = ModelStatus.NOT_INSTALLED,
        validationNote = "Candidate STT export only; TTS pack and measurements are not recorded."
    )

    /** Indic language where only STT is available; TTS has no sherpa-onnx release. */
    private fun indicSttOnly(code: String, name: String) = LanguageSpec(
        code = code,
        displayName = name,
        sttArchitecture = SttArchitecture.NEMO_CTC,
        sttModelRelativePath = "models/$code/stt/model.int8.onnx",
        sttTokensRelativePath = "models/$code/stt/tokens.txt",
        ttsModelRelativePath = "models/$code/tts/model.onnx",   // placeholder — no TTS exists
        ttsTokensRelativePath = "models/$code/tts/tokens.txt",  // placeholder
        ttsDataRelativePath = "models/$code/tts",               // placeholder
        status = ModelStatus.NOT_INSTALLED,
        validationNote = "STT candidate available; no compatible TTS model found in sherpa-onnx releases as of 2026-09-29."
    )

    // Registry entries are availability declarations, not proof of language validation.
    // Do not change a status to VALIDATED until MODELS.md contains real measurements
    // and a documented listening check for the exact pack revision.
    val languages: List<LanguageSpec> = listOf(
        // Current Implementation: Hindi and English (Depth over Breadth)
        piperFull("hi", "Hindi"),          // Piper hi_IN-rohan-medium-int8 (needs espeak-ng-data/)
        LanguageSpec("en", "English", SttArchitecture.TRANSDUCER,
            "models/en/stt/encoder.int8.onnx", "models/en/stt/tokens.txt",
            "models/en/tts/model.onnx", "models/en/tts/tokens.txt",
            // Piper en_US: dataDir must be espeak-ng-data/, same as Hindi
            "models/en/tts/espeak-ng-data",
            ModelStatus.NOT_INSTALLED, validationNote = "STT/TTS assets and target-device validation not recorded.")
    )
    
    // Future Work: Additional 8 Indian Languages (Total = 10 as per ISRO requirement)
    // These are prepared for integration but hidden to focus on depth over breadth
    /*
    val futureLanguages: List<LanguageSpec> = listOf(
        // STT + TTS available
        piperFull("ml", "Malayalam"),      // Piper ml_IN-meera-medium-int8 (needs espeak-ng-data/)
        indicFull("gu", "Gujarati"),       // mimic3 gu_IN-cmu-indic_low (no espeak-ng-data)
        indicFull("bn", "Bengali"),        // Coqui bn-custom_female (no espeak-ng-data)

        // STT only — no TTS available in sherpa-onnx releases
        indicSttOnly("ta", "Tamil"),
        indicSttOnly("mr", "Marathi"),
        indicSttOnly("kn", "Kannada"),
        indicSttOnly("te", "Telugu"),
        indicSttOnly("or", "Odia")
    )
    */

    // Alias for backward compatibility with test code
    val ALL_LANGUAGES: List<LanguageSpec> get() = languages

    fun byCode(code: String): LanguageSpec = languages.first { it.code == code }
}
