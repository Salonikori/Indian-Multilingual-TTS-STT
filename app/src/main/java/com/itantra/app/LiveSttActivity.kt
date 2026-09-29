package com.itantra.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.app.audio.AudioCapture
import com.itantra.app.audio.AudioCaptureService
import com.itantra.app.audio.LiveSttController
import com.itantra.app.audio.ManagerSttEngine
import com.itantra.app.audio.UtteranceSegmenter
import com.itantra.app.audio.VadEngine
import com.itantra.app.models.LanguageManager
import java.io.File

/**
 * Hosts [LiveSttScreen]. Receives a ready-loaded [LanguageManager] from
 * [AppState.languageManager] so the recognizer doesn't need to be reloaded.
 *
 * The caller (MainActivity SmokeScreen) must have called [LanguageManager.loadLanguage]
 * before launching this activity, and must set [AppState.languageManager].
 *
 * Pass [EXTRA_LANGUAGE_CODE] so the activity can display the language name.
 */
class LiveSttActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LANGUAGE_CODE = "language_code"
    }

    private var capture: AudioCapture? = null
    private var vad: VadEngine? = null
    private var controller: LiveSttController? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPipeline() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the already-loaded manager from the process-wide holder
        val manager: LanguageManager = AppState.languageManager
            ?: run { finish(); return }   // shouldn't happen; guard anyway

        val vadModelPath = File(filesDir, "models/vad/silero_vad.onnx").absolutePath
        val vadEngine = VadEngine(modelPath = vadModelPath)
        this.vad = vadEngine

        val segmenter = UtteranceSegmenter()
        val sttEngine = ManagerSttEngine(manager)
        val ctrl = LiveSttController(vadEngine, segmenter, sttEngine, lifecycleScope)
        this.controller = ctrl

        setContent {
            MaterialTheme {
                LiveSttScreen(
                    controller = ctrl,
                    onStart = { requestMicAndStart() },
                    onStop  = { stopPipeline() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPipeline()
    }

    // ─── Permission + pipeline ────────────────────────────────────────────────

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startPipeline()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startPipeline() {
        // Start foreground service so Android doesn't kill the mic while we're active
        val svcIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svcIntent)
        else startService(svcIntent)

        val cap = AudioCapture()
        cap.start()
        capture = cap
        controller?.start(cap.frames)
    }

    private fun stopPipeline() {
        controller?.stop()
        capture?.stop()
        capture = null
        startService(Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        })
    }
}
