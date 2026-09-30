package com.itantra.app.audio

import android.os.Debug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LiveSttPhase { LISTENING, SPEECH, TRANSCRIBING }

data class UtteranceView(
    val text: String, val audioLengthMillis: Long, val endToTextMillis: Long,
    val decodeMillis: Long, val rtf: Double
)

data class LiveSttState(
    val phase: LiveSttPhase = LiveSttPhase.LISTENING,
    val latest: UtteranceView? = null,
    val message: String = "Armed; waiting for speech.",
    val idleCpuPercent: Double? = null
)

/**
 * mic frames -> VAD -> segmenter -> STT.
 *
 * Fixes compared with the previous version:
 *  1. Each finished utterance is handed to [onUtterance] exactly ONCE. The old code exposed it only as
 *     `state.latest`, which is replayed to every new collector and re-emitted on every state change,
 *     so messages were sent repeatedly or a stale one was re-sent on the next push-to-talk press.
 *  2. STT runs on its own worker, so audio spoken while a sentence is being transcribed is no
 *     longer dropped (the frame reader is never blocked by a slow decode).
 *  3. [stopAndFlush] (push-to-talk release) finishes the sentence in progress, waits for all
 *     queued/in-flight transcriptions, delivers them, then returns.
 *  4. [stop] no longer releases the VAD; call [release] once when the screen is destroyed.
 */
class LiveSttController(
    private val vad: VadEngine,
    private val segmenter: UtteranceSegmenter,
    private val stt: SttEngine,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow(LiveSttState())
    val state: StateFlow<LiveSttState> = mutable.asStateFlow()
    private var reader: Job? = null
    private var worker: Job? = null
    private var queue: Channel<UtteranceSegmenter.Segment>? = null

    /** Start listening. [onUtterance] is called once per transcribed sentence, on a background thread. */
    @Synchronized
    fun start(frames: Flow<AudioFrame>, onUtterance: (UtteranceView) -> Unit = {}) {
        cancelJobs()
        segmenter.reset()
        vad.reset()
        mutable.value = LiveSttState()

        val q = Channel<UtteranceSegmenter.Segment>(Channel.UNLIMITED)
        queue = q

        worker = scope.launch(Dispatchers.Default) {
            for (segment in q) transcribeAndDeliver(segment, onUtterance)
        }

        reader = scope.launch(Dispatchers.Default) {
            var lastCpu = Debug.threadCpuTimeNanos()
            var lastWall = System.nanoTime()

            frames.collect { frame ->
                val floats = FloatArray(frame.samples.size) { frame.samples[it] / 32768.0f }
                val speech = vad.isSpeech(floats)
                val segment = segmenter.accept(floats, speech)

                if (speech) {
                    mutable.update { it.copy(phase = LiveSttPhase.SPEECH, message = "Speech detected") }
                } else if (segment == null) {
                    val cpu = Debug.threadCpuTimeNanos()
                    val wall = System.nanoTime()
                    val pct = if (wall > lastWall)
                        ((cpu - lastCpu).toDouble() / (wall - lastWall) * 100).coerceIn(0.0, 100.0) else 0.0
                    lastCpu = cpu; lastWall = wall

                    mutable.update {
                        if (it.phase == LiveSttPhase.TRANSCRIBING) it
                        else it.copy(phase = LiveSttPhase.LISTENING, message = "Armed; waiting for speech.", idleCpuPercent = pct)
                    }
                }

                if (segment != null) q.trySend(segment)
            }
        }
    }

    /**
     * Push-to-talk release: stop reading the mic, end the sentence in progress,
     * wait until everything queued has been transcribed and delivered to [onUtterance], then return.
     */
    suspend fun stopAndFlush() {
        val r = synchronized(this) { reader.also { reader = null } }
        r?.cancelAndJoin()

        val q = synchronized(this) { queue }
        segmenter.flush()?.let { q?.trySend(it) }
        q?.close()

        val w = synchronized(this) { worker.also { worker = null } }
        w?.join()

        synchronized(this) { queue = null }
        mutable.update { it.copy(phase = LiveSttPhase.LISTENING, message = "Idle") }
    }

    /** Stop immediately and DROP anything not yet transcribed (used for gating/mode switches). */
    @Synchronized
    fun stop() {
        cancelJobs()
        segmenter.reset()
    }

    /** Stop and free the VAD. Call once, when the screen is destroyed or the language is reloaded. */
    fun release() {
        stop()
        vad.release()
    }

    private fun cancelJobs() {
        reader?.cancel(); reader = null
        worker?.cancel(); worker = null
        queue?.close(); queue = null
    }

    private suspend fun transcribeAndDeliver(segment: UtteranceSegmenter.Segment, onUtterance: (UtteranceView) -> Unit) {
        mutable.update { it.copy(phase = LiveSttPhase.TRANSCRIBING, message = "Transcribing…") }
        val startedAt = System.nanoTime()

        val result = try {
            stt.transcribe(segment.samples, 16_000)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            mutable.update { it.copy(phase = LiveSttPhase.LISTENING, message = "Transcription failed: ${t.message ?: t::class.java.simpleName}") }
            return
        }

        val endToText = (System.nanoTime() - startedAt) / 1_000_000
        val text = result.text.trim()

        if (text.length < 2 || text.count { it.isLetterOrDigit() } < 2) {
            mutable.update { it.copy(phase = LiveSttPhase.LISTENING, message = "Could not transcribe: empty or low-quality result.") }
            return
        }

        val view = UtteranceView(
            text = text,
            audioLengthMillis = segment.audioLengthMillis,
            endToTextMillis = endToText,
            decodeMillis = result.decodeMillis,
            rtf = result.decodeMillis.toDouble() / segment.audioLengthMillis.coerceAtLeast(1)
        )

        mutable.update { it.copy(phase = LiveSttPhase.LISTENING, latest = view, message = "Transcript ready") }
        onUtterance(view)
    }
}