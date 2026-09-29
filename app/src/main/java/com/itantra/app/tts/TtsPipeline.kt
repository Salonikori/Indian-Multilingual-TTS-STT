package com.itantra.app.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Streaming-style producer/consumer: the producer synthesizes chunk N+1 while
 * the playback consumer plays chunk N. Playback is injected to keep synthesis and
 * routing independently testable.
 */
class TtsPipeline(
    private val engine: TtsEngine,
    private val playChunk: suspend (TtsChunk, PlaybackKind) -> Unit,
    private val scope: CoroutineScope
) {
    enum class PlaybackKind { NORMAL, ALERT }

    fun speak(text: String, languageCode: String, kind: PlaybackKind): Job = scope.launch {
        val sentences = SherpaTtsEngine.splitSentences(text)
        if (sentences.isEmpty()) return@launch
        val queue = Channel<TtsChunk>(capacity = 1)
        val producer = launch(Dispatchers.Default) {
            try {
                for (sentence in sentences) queue.send(engine.synthesize(sentence, languageCode))
                queue.close()
            } catch (t: Throwable) {
                queue.close(t)
                throw t
            }
        }
        try {
            for (chunk in queue) playChunk(chunk, kind)
        } finally {
            producer.cancel()
            queue.cancel()
        }
    }
}
