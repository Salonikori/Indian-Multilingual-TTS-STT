package com.itantra.app.audio

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.Vad

/**
 * sherpa-onnx Silero VAD adapter.
 *
 * API notes (v1.13.5):
 *  - The class is [Vad], not VoiceActivityDetector.
 *  - The silero config field in [VadModelConfig] is `sileroVadModelConfig`, not `sileroVad`.
 *  - Speech detection: call [Vad.acceptWaveform], then check [Vad.isSpeechDetected].
 *  - Completed segments are retrieved via [Vad.front] / [Vad.pop] / [Vad.empty].
 */
class VadEngine(modelPath: String, sampleRate: Int = 16_000, threshold: Float = 0.5f) {
    private val detector = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelPath,
                threshold = threshold,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.1f,
                maxSpeechDuration = 15.0f,
                windowSize = 512
            ),
            sampleRate = sampleRate,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
    )

    fun isSpeech(samples: FloatArray): Boolean {
        detector.acceptWaveform(samples)
        return detector.isSpeechDetected()
    }

    fun release() = detector.release()
}
