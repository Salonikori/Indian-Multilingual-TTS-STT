package com.itantra.app.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcmWav(val samples: FloatArray, val sampleRate: Int)

object WavReader {
    /** Minimal PCM16 mono/stereo WAV reader for the smoke-test asset; rejects unsupported formats. */
    fun readPcm16(file: File): PcmWav {
        val bytes = FileInputStream(file).use { it.readBytes() }
        require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
            "Not a WAV file"
        }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12; var channels = 0; var sampleRate = 0; var bits = 0; var format = 0
        var dataStart = -1; var dataSize = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = b.getInt(pos + 4).coerceAtLeast(0)
            val content = pos + 8
            if (content + size > bytes.size) break
            when (id) {
                "fmt " -> { format = b.getShort(content).toInt() and 0xffff
                    channels = b.getShort(content + 2).toInt() and 0xffff
                    sampleRate = b.getInt(content + 4)
                    bits = b.getShort(content + 14).toInt() and 0xffff }
                "data" -> { dataStart = content; dataSize = size; break }
            }
            pos = content + size + (size and 1)
        }
        require(format == 1 && bits == 16 && channels in 1..2 && sampleRate > 0 && dataStart >= 0) {
            "Expected PCM16 mono/stereo WAV"
        }
        val frames = dataSize / (2 * channels)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += b.getShort(dataStart + (i * channels + c) * 2) / 32768f
            out[i] = sum / channels
        }
        return PcmWav(out, sampleRate)
    }
}
