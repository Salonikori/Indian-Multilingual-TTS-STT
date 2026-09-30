package com.itantra.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicBoolean

data class AudioFrame(val samples: ShortArray, val sampleRate: Int, val capturedAtNanos: Long)

class AudioCapture(private val frameMillis: Int = 20) {
    private val running = AtomicBoolean(false)
    private val channel = Channel<AudioFrame>(Channel.BUFFERED)
    val frames: Flow<AudioFrame> = channel.receiveAsFlow()
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start() {
        android.util.Log.d("iTantra-AudioCapture", "=== AUDIO CAPTURE START ===")
        check(running.compareAndSet(false, true)) { "Already capturing" }
        
        val rate = 16_000
        android.util.Log.d("iTantra-AudioCapture", "Sample rate: ${rate}Hz")
        
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        android.util.Log.d("iTantra-AudioCapture", "Min buffer size: $min")
        check(min > 0) { 
            android.util.Log.e("iTantra-AudioCapture", "AudioRecord buffer query failed: $min")
            running.set(false); "AudioRecord buffer query failed: $min" 
        }
        
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        android.util.Log.d("iTantra-AudioCapture", "AudioRecord created, state: ${r.state}")
        
        check(r.state == AudioRecord.STATE_INITIALIZED) { 
            android.util.Log.e("iTantra-AudioCapture", "AudioRecord init failed, state: ${r.state}")
            r.release(); running.set(false); "AudioRecord init failed" 
        }
        
        recorder = r
        android.util.Log.d("iTantra-AudioCapture", "Starting recording...")
        r.startRecording()
        android.util.Log.d("iTantra-AudioCapture", "Recording state: ${r.recordingState}")
        
        worker = Thread({
            val buffer = ShortArray(rate * frameMillis / 1000)
            android.util.Log.d("iTantra-AudioCapture", "Audio worker thread started, buffer size: ${buffer.size}")
            var frameCount = 0
            try {
                while (running.get()) {
                    val n = r.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (n > 0) {
                        frameCount++
                        
                        // Calculate audio level for monitoring
                        val level = buffer.take(n).map { kotlin.math.abs(it.toInt()) }.average()
                        
                        if (frameCount % 50 == 0) { // Log every 50 frames (~1 second)
                            android.util.Log.d("iTantra-AudioCapture", "Frame $frameCount: read $n samples, avg level: ${String.format("%.1f", level)}")
                        }
                        
                        val frame = AudioFrame(buffer.copyOf(n), rate, System.nanoTime())
                        val sent = channel.trySend(frame)
                        if (!sent.isSuccess) {
                            android.util.Log.w("iTantra-AudioCapture", "Failed to send audio frame: ${sent.exceptionOrNull()}")
                        }
                    } else {
                        android.util.Log.w("iTantra-AudioCapture", "Audio read returned: $n")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("iTantra-AudioCapture", "Audio capture error: ${e.message}", e)
            } finally { 
                android.util.Log.d("iTantra-AudioCapture", "Stopping recording...")
                runCatching { r.stop() }
                android.util.Log.d("iTantra-AudioCapture", "Audio worker thread finished")
            }
        }, "iTantra-AudioRecord").apply { start() }
        
        android.util.Log.d("iTantra-AudioCapture", "✓ AudioCapture initialization complete")
    }

    fun stop() {
        running.set(false)
        runCatching { recorder?.stop() }
        worker?.join(500)
        recorder?.release()
        recorder = null; worker = null
    }
}
