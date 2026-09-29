package com.itantra.app.tts

import org.junit.Assert.*
import org.junit.Test

class TtsSentenceSplitterTest {
    @Test fun splitsHindiAndEnglishSentenceBoundaries() {
        val chunks = SherpaTtsEngine.splitSentences("Hello there. नमस्ते। How are you?")
        assertEquals(3, chunks.size)
        assertEquals("Hello there.", chunks[0])
        assertEquals("नमस्ते।", chunks[1])
        assertEquals("How are you?", chunks[2])
    }

    @Test fun splitsVeryLongSentencesAtWordBoundaries() {
        val chunks = SherpaTtsEngine.splitSentences("one two three four five six", maxChars = 10)
        assertTrue(chunks.all { it.length <= 10 })
        assertEquals("one two", chunks[0])
    }
}
