package com.itantra.app.audio

import java.util.ArrayDeque

class UtteranceSegmenter(val config: Config = Config()) {
    data class Config(
        val sampleRate: Int = 16_000,
        val frameMillis: Int = 20,
        val preRollMillis: Int = 250,
        val trailingSilenceMillis: Int = 600,
        val maxUtteranceMillis: Int = 15_000,
        val minUtteranceMillis: Int = 300
    ) {
        init {
            require(sampleRate > 0 && frameMillis > 0 && preRollMillis >= 0)
            require(trailingSilenceMillis >= frameMillis)
            require(maxUtteranceMillis >= minUtteranceMillis && minUtteranceMillis > 0)
        }
    }
    enum class EndReason { TRAILING_SILENCE, MAX_LENGTH }
    data class Segment(val samples: FloatArray, val audioLengthMillis: Long, val endedBy: EndReason)
    private val preRoll = ArrayDeque<FloatArray>()
    private val frames = ArrayList<FloatArray>()
    private var active = false
    private var silenceFrames = 0
    private var speechFrames = 0

    @Synchronized fun accept(samples: FloatArray, isSpeech: Boolean): Segment? {
        if (!active) {
            val keep = (config.preRollMillis + config.frameMillis - 1) / config.frameMillis
            if (keep > 0) {
                preRoll.addLast(samples.copyOf())
                while (preRoll.size > keep) preRoll.removeFirst()
            }
            if (!isSpeech) return null
            active = true
            speechFrames = 0
            preRoll.forEach { frames.add(it.copyOf()) }
            // With a zero-length pre-roll the current speech frame was not queued.
            if (preRoll.isEmpty()) frames.add(samples.copyOf())
            preRoll.clear()
        } else frames.add(samples.copyOf())

        if (isSpeech) { silenceFrames = 0; speechFrames++ } else silenceFrames++
        val count = frames.sumOf { it.size }
        val maxSamples = config.sampleRate * config.maxUtteranceMillis / 1000
        if (count >= maxSamples) return finish(EndReason.MAX_LENGTH)
        if (silenceFrames * config.frameMillis >= config.trailingSilenceMillis)
            return finish(EndReason.TRAILING_SILENCE)
        return null
    }

    @Synchronized fun reset() {
        preRoll.clear(); frames.clear(); active = false; silenceFrames = 0; speechFrames = 0
    }

    private fun finish(reason: EndReason): Segment? {
        val count = frames.sumOf { it.size }
        val all = FloatArray(count)
        var offset = 0
        frames.forEach { it.copyInto(all, offset); offset += it.size }
        val ms = count * 1000L / config.sampleRate
        val speechMs = speechFrames * config.frameMillis.toLong()
        val result = if (speechMs >= config.minUtteranceMillis) Segment(all, ms, reason) else null
        reset()
        return result
    }
}
