package com.itantra.app.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Adapter for sherpa-onnx OfflineTts. The exact constructor/result field names vary
 * across releases; compile against the pinned official AAR and adjust this adapter
 * to the matching official Kotlin example before claiming runtime compatibility.
 *
 * Provide a language-specific OfflineTts instance through the factory; never assume
 * that a VITS/Piper model supports arbitrary language switching.
 */
class SherpaTtsEngine(
    private val engineForLanguage: (String) -> OfflineTts
) : TtsEngine {
    override suspend fun synthesize(text: String, languageCode: String): TtsChunk =
        withContext(Dispatchers.Default) {
            require(text.isNotBlank()) { "Text must not be blank" }
            val engine = engineForLanguage(languageCode)
            val start = System.nanoTime()
            val audio = engine.generate(text)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            val samples = audio.samples
            val sampleRate = audio.sampleRate
            val audioMs = if (sampleRate > 0) samples.size * 1000L / sampleRate else 0L
            TtsChunk(
                samples = samples,
                sampleRate = sampleRate,
                synthesisMillis = elapsedMs,
                rtf = if (audioMs > 0) elapsedMs.toDouble() / audioMs else 0.0,
                text = text
            )
        }

    companion object {
        /** Simple sentence splitter; keeps punctuation attached to each chunk. */
        fun splitSentences(text: String, maxChars: Int = 220): List<String> {
            require(maxChars > 0)
            val normalized = text.trim().replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return emptyList()
            val sentences = Regex("(?<=[.!?।॥])\\s+").split(normalized)
                .filter { it.isNotBlank() }
            val out = ArrayList<String>()
            for (sentence in sentences) {
                if (sentence.length <= maxChars) out += sentence
                else {
                    var remaining = sentence
                    while (remaining.length > maxChars) {
                        val cut = remaining.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
                        out += remaining.substring(0, cut).trim()
                        remaining = remaining.substring(cut).trim()
                    }
                    if (remaining.isNotBlank()) out += remaining
                }
            }
            return out
        }
    }
}
