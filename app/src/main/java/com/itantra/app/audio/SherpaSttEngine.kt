package com.itantra.app.audio

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaSttEngine(private val recognizer: OfflineRecognizer) : SttEngine {
    override suspend fun transcribe(samples: FloatArray, sampleRate: Int): SttResult =
        withContext(Dispatchers.Default) {
            val start = System.nanoTime()
            val stream = recognizer.createStream()
            val text = try {
                stream.acceptWaveform(samples, sampleRate)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally { stream.release() }
            SttResult(text, null, (System.nanoTime() - start) / 1_000_000)
        }
}
