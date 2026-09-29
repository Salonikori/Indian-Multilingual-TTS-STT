package com.itantra.app.transport

import org.junit.Assert.*
import org.junit.Test

class PayloadSerializerTest {
    private val sample = MessagePayload(
        type = MessageType.SPEECH, messageId = "msg-001", seq = 12, senderId = "phone-A",
        langCode = "hi", sentAtEpochMs = 1_700_000_000_000, text = "नमस्ते, कृपया आगे बढ़ें।"
    )

    @Test fun roundTripsHindiPayload() {
        assertEquals(sample, PayloadSerializer.decode(PayloadSerializer.encode(sample)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedJson() { PayloadSerializer.decode("{not json".toByteArray()) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizeFrame() { PayloadSerializer.decode(ByteArray(MessagePayload.MAX_PAYLOAD_BYTES + 1)) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSpeechWithoutText() { PayloadSerializer.encode(sample.copy(text = "")) }

    @Test fun normalSentenceIsCompact() {
        assertTrue(PayloadSerializer.encode(sample.copy(text = "Hello, how are you?")).size < 300)
    }
}
