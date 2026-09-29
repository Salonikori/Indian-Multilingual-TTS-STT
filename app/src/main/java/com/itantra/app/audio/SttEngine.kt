package com.itantra.app.audio

data class SttResult(val text: String, val confidence: Float?, val decodeMillis: Long)
interface SttEngine { suspend fun transcribe(samples: FloatArray, sampleRate: Int = 16_000): SttResult }
