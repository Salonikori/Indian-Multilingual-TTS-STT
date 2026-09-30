package com.itantra.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UtteranceSegmenterFlushTest {
    private val frame = FloatArray(320) // 20 ms at 16 kHz

    @Test fun flushWithNothingCapturedReturnsNull() {
        val s = UtteranceSegmenter()
        repeat(10) { s.accept(frame, false) }
        assertNull(s.flush())
    }

    @Test fun flushEndsSentenceInProgressWithoutWaitingForSilence() {
        val s = UtteranceSegmenter()
        repeat(30) { assertNull(s.accept(frame, true)) }     // 600 ms speech, no silence yet
        val seg = s.flush()
        assertNotNull(seg)
        assertEquals(UtteranceSegmenter.EndReason.FLUSH, seg!!.endedBy)
    }

    @Test fun flushOfTooShortSpeechReturnsNull() {
        val s = UtteranceSegmenter()
        repeat(5) { s.accept(frame, true) }                  // 100 ms < 300 ms minimum
        assertNull(s.flush())
    }

    @Test fun flushTwiceReturnsSegmentOnlyOnce() {
        val s = UtteranceSegmenter()
        repeat(30) { s.accept(frame, true) }
        assertNotNull(s.flush())
        assertNull(s.flush())
    }
}