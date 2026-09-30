package com.itantra.app.audio

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * sherpa-onnx Silero VAD adapter.
 *
 * API notes (v1.13.5): class is [Vad]; config field is `sileroVadModelConfig`;
 * per-frame "speech now" is [Vad.isSpeechDetected]; finished segments are read with
 * [Vad.empty] / [Vad.pop].
 *
 * Changes vs the previous version:
 *  - finished segments are drained every call (they were never popped, so the internal queue grew forever);
 *  - reset() gives a clean detector at the start of each listening session by rebuilding it
 *    (uses only APIs already used in this file);
 *  - release() is safe to call twice.
 */
class VadEngine(
    private val modelPath: String,
    private val sampleRate: Int = 16_000,
    private val threshold: Float = 0.5f
) {
    private var detector: Vad = create()
    private var released = false

    private fun create(): Vad = try {
        Vad(
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
    } catch (e: Exception) {
        throw RuntimeException(
            "VAD model loading failed: ${e.message}. Ensure the VAD model exists at: $modelPath", e
        )
    }

    @Synchronized
    fun isSpeech(samples: FloatArray): Boolean {
        check(!released) { "VadEngine already released" }
        detector.acceptWaveform(samples)
        val speech = detector.isSpeechDetected()
        while (!detector.empty()) detector.pop()   // we only use the per-frame flag
        return speech
    }

    /** Fresh detector state (call when a new listening session starts). */
    @Synchronized
    fun reset() {
        check(!released) { "VadEngine already released" }
        detector.release()
        detector = create()
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        detector.release()
    }
}
