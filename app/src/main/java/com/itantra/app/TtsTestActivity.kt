package com.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.itantra.app.audio.PlaybackKind
import com.itantra.app.audio.PlaybackRouter
import com.itantra.app.tts.ManagerTtsEngine
import com.itantra.app.tts.TtsPipeline

/**
 * Hosts [TtsTestScreen].
 * Requires the caller to have already loaded a language via [LanguageManager] (stored in [AppState]).
 * Wires: TtsTestScreen → TtsPipeline(ManagerTtsEngine) → PlaybackRouter.
 */
class TtsTestActivity : ComponentActivity() {

    private lateinit var router: PlaybackRouter
    private lateinit var pipeline: TtsPipeline

    // Metrics surfaced to the UI
    private var synthesisTimeMs = mutableStateOf<Long?>(null)
    private var rtf             = mutableStateOf<Double?>(null)
    private var timeToFirstMs   = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = AppState.languageManager ?: run { finish(); return }

        router   = PlaybackRouter(this)
        val engine = ManagerTtsEngine(manager)
        pipeline = TtsPipeline(
            engine    = engine,
            playChunk = { chunk, kind ->
                val playKind = if (kind == TtsPipeline.PlaybackKind.ALERT)
                    PlaybackKind.ALERT else PlaybackKind.NORMAL
                router.play(chunk.samples, chunk.sampleRate, playKind)
                // Update metrics after first chunk completes
                synthesisTimeMs.value = chunk.synthesisMillis
                rtf.value             = chunk.rtf
            },
            scope = lifecycleScope
        )

        setContent {
            MaterialTheme {
                // Loaded language gives us the available code; we only support the
                // currently-loaded language since LanguageManager holds one at a time.
                val langCode = manager.activeLanguageCode() ?: "hi"
                val langLabel = when (langCode) {
                    "hi" -> "Hindi"; "en" -> "English"; "ml" -> "Malayalam"
                    "gu" -> "Gujarati"; "bn" -> "Bengali"; else -> langCode
                }
                TtsTestScreen(
                    languages       = listOf(langCode to langLabel),
                    onPlay          = { text, _, isAlert ->
                        val requestedAt = System.currentTimeMillis()
                        val kind = if (isAlert) TtsPipeline.PlaybackKind.ALERT
                                   else         TtsPipeline.PlaybackKind.NORMAL
                        if (isAlert) router.preemptQueuedNormalMessages()
                        pipeline.speak(text, langCode, kind)
                        timeToFirstMs.value = System.currentTimeMillis() - requestedAt
                    },
                    synthesisTimeMs = synthesisTimeMs.value,
                    rtf             = rtf.value,
                    timeToFirstAudioMs = timeToFirstMs.value
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // PlaybackRouter has no explicit close; AudioTrack resources are released per-play.
    }
}
