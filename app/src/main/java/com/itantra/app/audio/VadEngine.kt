package com.itantra.app.audio

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.Vad

/**
 * sherpa-onnx Silero VAD adapter with graceful degradation for development.
 *
 * API notes (v1.13.5):
 *  - The class is [Vad], not VoiceActivityDetector.
 *  - The silero config field in [VadModelConfig] is `sileroVadModelConfig`, not `sileroVad`.
 *  - Speech detection: call [Vad.acceptWaveform], then check [Vad.isSpeechDetected].
 *  - Completed segments are retrieved via [Vad.front] / [Vad.pop] / [Vad.empty].
 */
class VadEngine(modelPath: String, sampleRate: Int = 16_000, threshold: Float = 0.5f) {
    private val detector = try {
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
        println("VAD model loading failed: ${e.message}, using mock VAD")
        null
    }
    
    private var mockFrameCount = 0

    fun isSpeech(samples: FloatArray): Boolean {
        return if (detector != null) {
            detector.acceptWaveform(samples)
            val speechDetected = detector.isSpeechDetected()
            if (speechDetected) {
                android.util.Log.d("iTantra-VAD", "🗣️ SPEECH detected (${samples.size} samples)")
            }
            speechDetected
        } else {
            // Mock VAD: simulate speech detection based on audio energy
            val energy = samples.map { it * it }.average()
            val threshold = 0.01f
            mockFrameCount++
            // Simulate speech detection every 10-50 frames if energy is above threshold
            val speechDetected = energy > threshold && (mockFrameCount % 30 < 20)
            
            if (mockFrameCount % 100 == 0) { // Log every 100 frames
                android.util.Log.d("iTantra-VAD", "Mock VAD: energy=${String.format("%.6f", energy)}, threshold=$threshold, speech=$speechDetected, frame=$mockFrameCount")
            }
            
            if (speechDetected) {
                android.util.Log.d("iTantra-VAD", "🗣️ Mock SPEECH detected (energy: ${String.format("%.6f", energy)})")
            }
            
            speechDetected
        }
    }

    fun release() { 
        detector?.release() 
    }
}
