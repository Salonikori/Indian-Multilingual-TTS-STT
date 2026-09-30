package com.itantra.app.audio

import com.itantra.app.models.LanguageManager

/**
 * Bridges [SttEngine] to [LanguageManager.decode] so that [LiveSttController] can call the
 * already-loaded recognizer without needing a direct reference to [OfflineRecognizer].
 *
 * [LanguageManager.decode] is @Synchronized and runs the full decode pipeline; calling it
 * from Dispatchers.Default (as [LiveSttController] does) is safe.
 */
class ManagerSttEngine(private val manager: LanguageManager) : SttEngine {
    override suspend fun transcribe(samples: FloatArray, sampleRate: Int): SttResult {
        android.util.Log.d("iTantra-STT", "=== STT TRANSCRIBE START ===")
        android.util.Log.d("iTantra-STT", "Samples: ${samples.size}, Sample rate: ${sampleRate}Hz")
        android.util.Log.d("iTantra-STT", "Audio duration: ${String.format("%.2f", samples.size.toFloat() / sampleRate)}s")
        
        val start = System.nanoTime()
        val text = manager.decode(samples, sampleRate)
        val ms = (System.nanoTime() - start) / 1_000_000
        
        android.util.Log.d("iTantra-STT", "STT Result: '$text' (${ms}ms)")
        android.util.Log.d("iTantra-STT", "Text length: ${text.length} chars")
        
        return SttResult(text = text, confidence = null, decodeMillis = ms)
    }
}
