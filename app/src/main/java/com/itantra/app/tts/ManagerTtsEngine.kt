package com.itantra.app.tts

import com.itantra.app.models.LanguageManager

/**
 * Bridges [TtsEngine] to [LanguageManager.synthesize] so [TtsPipeline] can drive
 * the already-loaded TTS engine without a direct [OfflineTts] reference.
 *
 * [LanguageManager.synthesize] is @Synchronized; calling it from Dispatchers.Default
 * (as [TtsPipeline] does) is safe.
 */
class ManagerTtsEngine(private val manager: LanguageManager) : TtsEngine {
    override suspend fun synthesize(text: String, languageCode: String): TtsChunk {
        val start = System.nanoTime()
        val (samples, sampleRate) = manager.synthesize(text)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val audioMs = if (sampleRate > 0) samples.size * 1000L / sampleRate else 1L
        return TtsChunk(
            samples         = samples,
            sampleRate      = sampleRate,
            synthesisMillis = elapsedMs,
            rtf             = elapsedMs.toDouble() / audioMs.coerceAtLeast(1),
            text            = text
        )
    }
}
