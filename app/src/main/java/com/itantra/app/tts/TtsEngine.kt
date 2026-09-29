package com.itantra.app.tts

data class TtsChunk(
    val samples: FloatArray,
    val sampleRate: Int,
    val synthesisMillis: Long,
    val rtf: Double,
    val text: String
)

data class TtsSynthesis(
    val chunks: List<TtsChunk>,
    val totalSynthesisMillis: Long,
    val totalAudioMillis: Long,
    val timeToFirstAudioMillis: Long
)

interface TtsEngine {
    /** Synthesizes one sentence/chunk. Implementations must not block the main thread. */
    suspend fun synthesize(text: String, languageCode: String): TtsChunk
}
