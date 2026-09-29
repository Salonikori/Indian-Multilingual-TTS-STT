package com.itantra.app.audio

import android.os.Debug
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class LiveSttPhase { LISTENING, SPEECH, TRANSCRIBING }
data class UtteranceView(val text: String, val audioLengthMillis: Long, val endToTextMillis: Long,
    val decodeMillis: Long, val rtf: Double)
data class LiveSttState(val phase: LiveSttPhase = LiveSttPhase.LISTENING,
    val latest: UtteranceView? = null, val message: String = "Armed; waiting for speech.",
    val idleCpuPercent: Double? = null)

class LiveSttController(private val vad: VadEngine, private val segmenter: UtteranceSegmenter,
    private val stt: SttEngine, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(LiveSttState())
    val state: StateFlow<LiveSttState> = mutable.asStateFlow()
    private var job: Job? = null

    fun start(frames: Flow<AudioFrame>) {
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            var lastCpu = Debug.threadCpuTimeNanos()
            var lastWall = System.nanoTime()
            frames.collect { frame ->
                val floats = FloatArray(frame.samples.size) { frame.samples[it] / 32768.0f }
                val speech = vad.isSpeech(floats)
                val segment = segmenter.accept(floats, speech)
                if (speech) mutable.value = mutable.value.copy(phase = LiveSttPhase.SPEECH, message = "Speech detected")
                else if (segment == null && mutable.value.phase != LiveSttPhase.TRANSCRIBING) {
                    val cpu = Debug.threadCpuTimeNanos(); val wall = System.nanoTime()
                    val pct = if (wall > lastWall) ((cpu-lastCpu).toDouble()/(wall-lastWall)*100).coerceIn(0.0,100.0) else 0.0
                    mutable.value = mutable.value.copy(phase = LiveSttPhase.LISTENING,
                        message = "Armed; waiting for speech.", idleCpuPercent = pct)
                    lastCpu = cpu; lastWall = wall
                }
                if (segment != null) {
                    mutable.value = mutable.value.copy(phase = LiveSttPhase.TRANSCRIBING, message = "Transcribing…")
                    val endedAt = System.nanoTime()
                    val result = stt.transcribe(segment.samples, 16_000)
                    val endToText = (System.nanoTime() - endedAt) / 1_000_000
                    val text = result.text.trim()
                    if (text.length < 2 || text.count { it.isLetterOrDigit() } < 2) {
                        mutable.value = mutable.value.copy(phase = LiveSttPhase.LISTENING,
                            message = "Could not transcribe: empty or low-quality result.")
                    } else mutable.value = mutable.value.copy(phase = LiveSttPhase.LISTENING,
                        latest = UtteranceView(text, segment.audioLengthMillis, endToText,
                            result.decodeMillis, result.decodeMillis.toDouble()/segment.audioLengthMillis.coerceAtLeast(1)),
                        message = "Transcript ready")
                }
            }
        }
    }
    fun stop() { job?.cancel(); job = null; segmenter.reset(); vad.release() }
}
