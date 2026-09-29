package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

enum class PlaybackKind { NORMAL, ALERT }
enum class PhoneAudioMode { MEDIA, VOICE_COMMUNICATION }

/**
 * Ordered NORMAL queue and priority ALERT queue. Alerts preempt queued normal work
 * and are serialized with other alerts. An already-playing alert is not stopped by
 * another alert or by a normal message.
 *
 * ALERT uses USAGE_ALARM + transient-exclusive focus request and maxes STREAM_ALARM
 * while active, restoring the prior stream volume afterward. Android DND policy,
 * OEM routing, and user/system restrictions can still suppress sound; this class
 * cannot bypass those policies.
 */
class PlaybackRouter(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val normalMutex = Mutex()
    private val alertMutex = Mutex()
    private val alertActive = AtomicBoolean(false)
    @Volatile private var normalGeneration = 0L

    suspend fun play(samples: FloatArray, sampleRate: Int, kind: PlaybackKind,
                     phoneMode: PhoneAudioMode = PhoneAudioMode.MEDIA) {
        require(sampleRate > 0)
        when (kind) {
            PlaybackKind.ALERT -> alertMutex.withLock {
                alertActive.set(true)
                try { playAlert(samples, sampleRate) } finally { alertActive.set(false) }
            }
            PlaybackKind.NORMAL -> {
                // Each alert invalidates queued normal messages. A normal message checks
                // the generation immediately before playback, so stale queued work drops.
                val generation = normalGeneration
                normalMutex.withLock {
                    if (!alertActive.get() && generation == normalGeneration) {
                        playNormal(samples, sampleRate, phoneMode)
                    }
                }
            }
        }
    }

    /** Call when an alert arrives to discard pending normal messages. */
    fun preemptQueuedNormalMessages() { normalGeneration++ }

    private suspend fun playNormal(samples: FloatArray, rate: Int, mode: PhoneAudioMode) =
        withContext(Dispatchers.IO) {
            val usage = if (mode == PhoneAudioMode.VOICE_COMMUNICATION)
                AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
            val attrs = AudioAttributes.Builder().setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val focus = requestFocus(attrs, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            try { writeSamples(samples, rate, attrs) }
            finally { abandonFocus(focus) }
        }

    private suspend fun playAlert(samples: FloatArray, rate: Int) = withContext(Dispatchers.IO) {
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focus = requestFocus(attrs, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val tone = attentionTone(rate)
            writeSamples(tone, rate, attrs)
            writeSamples(samples, rate, attrs)
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
            abandonFocus(focus)
        }
    }

    private fun requestFocus(attrs: AudioAttributes, gain: Int): AudioFocusRequest? {
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(gain).setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* Focus policy: don't duck/cancel an active alert. */ }
                .build()
            val result = audioManager.requestAudioFocus(req)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw IllegalStateException("Audio focus request denied: $result")
            return req
        }
        @Suppress("DEPRECATION")
        val result = audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, gain)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            throw IllegalStateException("Audio focus request denied: $result")
        return null
    }

    private fun abandonFocus(req: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= 26 && req != null) audioManager.abandonAudioFocusRequest(req)
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun writeSamples(samples: FloatArray, rate: Int, attrs: AudioAttributes) {
        val minBytes = AudioTrack.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT)
        require(minBytes > 0) { "AudioTrack buffer query failed: $minBytes" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(rate)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minBytes, samples.size * 2 / 4))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            track.play()
            val pcm = ShortArray(samples.size) { i ->
                (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            var offset = 0
            while (offset < pcm.size) {
                val n = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                if (n < 0) throw IllegalStateException("AudioTrack write failed: $n")
                offset += n
            }
            // Blocking writes queue PCM; wait for playback head to drain before release.
            val expectedFrames = pcm.size
            while (track.playbackHeadPosition.toLong() < expectedFrames.toLong()) {
                Thread.sleep(10)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun attentionTone(rate: Int): FloatArray {
        val durationMs = 160
        val n = rate * durationMs / 1000
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            val envelope = (1.0 - i.toDouble() / n).coerceIn(0.0, 1.0)
            (0.22 * envelope * sin(2.0 * PI * 880.0 * t)).toFloat()
        }
    }
}
