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
    private val playChunk: suspend (TtsChunk, PlaybackKind, SentenceInfo) -> Unit,
    private val scope: CoroutineScope
) {
    enum class PlaybackKind { NORMAL, ALERT }
    
    data class SentenceInfo(
        val sentenceIndex: Int,
        val totalSentences: Int,
        val isFirstSentence: Boolean,
        val isLastSentence: Boolean
    )

    fun speak(text: String, languageCode: String, kind: PlaybackKind): Job = scope.launch {
        val sentences = SherpaTtsEngine.splitSentences(text)
        if (sentences.isEmpty()) return@launch
        
        println("TtsPipeline: Starting streaming synthesis of ${sentences.size} sentences")
        sentences.forEachIndexed { index, sentence -> 
            println("TtsPipeline: Sentence ${index + 1}: \"${sentence.take(50)}${if (sentence.length > 50) "..." else ""}\"")
        }
        
        val queue = Channel<TtsChunk>(capacity = 1)
        val producer = launch(Dispatchers.Default) {
            try {
                sentences.forEachIndexed { index, sentence ->
                    println("TtsPipeline: Synthesizing sentence ${index + 1}/${sentences.size}")
                    val start = System.currentTimeMillis()
                    val chunk = engine.synthesize(sentence, languageCode)
                    val synthesisTime = System.currentTimeMillis() - start
                    println("TtsPipeline: Sentence ${index + 1} synthesized in ${synthesisTime}ms")
                    queue.send(chunk)
                }
                queue.close()
            } catch (t: Throwable) {
                queue.close(t)
                throw t
            }
        }
        try {
            var sentenceIndex = 0
            for (chunk in queue) {
                val sentenceInfo = SentenceInfo(
                    sentenceIndex = sentenceIndex,
                    totalSentences = sentences.size,
                    isFirstSentence = sentenceIndex == 0,
                    isLastSentence = sentenceIndex == sentences.size - 1
                )
                println("TtsPipeline: Playing sentence ${sentenceIndex + 1}/${sentences.size}")
                playChunk(chunk, kind, sentenceInfo)
                sentenceIndex++
            }
        } finally {
            producer.cancel()
            queue.cancel()
        }
    }
}
