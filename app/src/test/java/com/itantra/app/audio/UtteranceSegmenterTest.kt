package com.itantra.app.audio

import org.junit.Assert.*
import org.junit.Test

class UtteranceSegmenterTest {
    private val config = UtteranceSegmenter.Config(preRollMillis=240, trailingSilenceMillis=600)
    private fun frame(value: Float = 0.1f) = FloatArray(320) { value }

    @Test fun emitsAfterTrailingSilenceAndIncludesPreroll() {
        val s = UtteranceSegmenter(config)
        repeat(8) { s.accept(frame(0f), false) }
        repeat(20) { s.accept(frame(), true) }
        var out: UtteranceSegmenter.Segment? = null
        repeat(30) { out = s.accept(frame(0f), false) ?: out }
        assertNotNull(out)
        assertEquals(UtteranceSegmenter.EndReason.TRAILING_SILENCE, out!!.endedBy)
        assertTrue(out!!.audioLengthMillis >= 900)
    }

    @Test fun ignoresShortBlip() {
        val s = UtteranceSegmenter(config)
        s.accept(frame(), true)
        var out: UtteranceSegmenter.Segment? = null
        repeat(30) { out = s.accept(frame(0f), false) ?: out }
        assertNull(out)
    }

    @Test fun enforcesMaximumLength() {
        val s = UtteranceSegmenter(config.copy(maxUtteranceMillis=600))
        var out: UtteranceSegmenter.Segment? = null
        repeat(40) { out = s.accept(frame(), true) ?: out }
        assertNotNull(out)
        assertEquals(UtteranceSegmenter.EndReason.MAX_LENGTH, out!!.endedBy)
    }

    @Test fun zeroPreRollStillIncludesFirstSpeechFrame() {
        val segmenter = UtteranceSegmenter(UtteranceSegmenter.Config(
            preRollMillis = 0, frameMillis = 20, trailingSilenceMillis = 20,
            minUtteranceMillis = 20, maxUtteranceMillis = 100
        ))
        val speech = FloatArray(320) { 0.25f }
        assertNull(segmenter.accept(speech, true))
        val segment = segmenter.accept(FloatArray(320), false)
        assertNotNull(segment)
        assertEquals(40L, segment!!.audioLengthMillis)
        assertEquals(0.25f, segment.samples.first(), 0.0001f)
    }
}
