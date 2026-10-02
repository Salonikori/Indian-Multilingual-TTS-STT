package com.itantra.app

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.app.audio.*
import com.itantra.app.models.*
import com.itantra.app.benchmark.BenchmarkStore
import com.itantra.app.session.*
import com.itantra.app.transport.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import com.itantra.app.measurement.*
import java.util.UUID

enum class DeliveryStatus { SENDING, DELIVERED, FAILED }

class CommunicationActivity : ComponentActivity() {
    
    private var languageManager: LanguageManager? = null
    private var transport: BluetoothClassicTransport? = null
    private var playbackRouter: PlaybackRouter? = null
    private var audioCapture: AudioCapture? = null
    private var liveSttController: LiveSttController? = null
    private var vadEngine: VadEngine? = null
    private var sttEngine: ManagerSttEngine? = null
    private var conversationMachine = ConversationStateMachine()
    private var benchmarkStore: BenchmarkStore? = null
    
    // Measurement and alert system
    private val alertsAndMeasurements = AlertsAndMeasurements()
    
    // Simple delivery tracking for Step 2
    private val messageDeliveryStatus = mutableMapOf<String, DeliveryStatus>()
    private val sentMessageIds = mutableSetOf<String>()
    private val pipelineEventSink = PipelineEventSink { event ->
        benchmarkStore?.add(
            metric = "pipeline_${event.name}_ms",
            value = event.elapsedRealtimeNanos / 1_000_000.0,
            unit = "ms",
            method = "pipeline_timing",
            timestampEpochMs = System.currentTimeMillis()
        )
        println("PipelineEvent: ${event.name} at ${event.elapsedRealtimeNanos / 1_000_000}ms - ${event.details}")
    }
    
    // Job for finalizing (PTT release) operations
    private var finalizeJob: Job? = null
    
    private var currentLanguage by mutableStateOf("hi")
    private var currentMode by mutableStateOf(ConversationMode.PTT)
    private var isPttPressed by mutableStateOf(false)
    private var microphoneGated by mutableStateOf(false)
    private var languageLoaded by mutableStateOf(false)
    private var languageLoading by mutableStateOf(false)
    
    private val messageQueue = mutableListOf<Pair<String, MessagePayload>>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize benchmark store
        benchmarkStore = BenchmarkStore(this)
        benchmarkStore?.setTestContext("Phase 7 Pipeline Timing - Communication Activity")
        
        // Initialize components
        transport = BluetoothClassicTransport(this)
        playbackRouter = PlaybackRouter(this)
        
        // Set up message listening
        lifecycleScope.launch {
            transport?.incoming?.collect { payload ->
                handleIncomingMessage(payload)
            }
        }
        
        setContent {
            MaterialTheme {
                CommunicationScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CommunicationScreen() {
        val connectionState by transport?.connectionState?.collectAsState() ?: remember { mutableStateOf(com.itantra.app.transport.ConnectionState.Disconnected) }
        val sessionPhase by remember { derivedStateOf { conversationMachine.state.phase } }
        val context = LocalContext.current
        
        // Measurement state
        val measurementConnectionState by alertsAndMeasurements.connectionState.collectAsState()
        val alertState by alertsAndMeasurements.alertState.collectAsState()
        
        var showLanguageSelector by remember { mutableStateOf(false) }
        var showAlertPresets by remember { mutableStateOf(false) }
        var showConnectionDialog by remember { mutableStateOf(false) }
        var showMeasurementsDashboard by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Alert Display (always at top when present)
            AlertDisplay(alertState = alertState)
            
            // Header
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("iTantra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Secure Voice Communication", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            // Language and Connection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Language Selector
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Language", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { showLanguageSelector = true }) {
                            Text(if (currentLanguage == "hi") "हिंदी" else "English")
                        }
                        if (languageLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (languageLoaded) {
                            Text("✓ Loaded", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        } else {
                            TextButton(onClick = { loadLanguage() }) {
                                Text("Load")
                            }
                        }
                    }
                }
                
                // Connection Status with measurements
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Connection", style = MaterialTheme.typography.labelMedium)
                        
                        // Enhanced connection status indicator
                        ConnectionStatusIndicator(
                            connectionState = when (connectionState) {
                                is ConnectionState.Connected -> measurementConnectionState
                                is ConnectionState.Connecting -> com.itantra.app.measurement.ConnectionState.CONNECTING
                                else -> com.itantra.app.measurement.ConnectionState.DISCONNECTED
                            }
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { showConnectionDialog = true }) {
                                Text("Setup", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { showMeasurementsDashboard = true }) {
                                Text("Stats", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            
            // Mode Toggle
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Communication Mode", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PTT")
                        Switch(
                            checked = currentMode == ConversationMode.PHONE,
                            onCheckedChange = { phone ->
                                val newMode = if (phone) ConversationMode.PHONE else ConversationMode.PTT
                                setMode(newMode)
                            }
                        )
                        Text("Phone")
                    }
                    Text(
                        if (currentMode == ConversationMode.PTT) 
                            "Press and hold PTT button to transmit" 
                        else 
                            "Voice activated - speak normally",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Main Communication Area
            Card(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // PTT Button
                        if (currentMode == ConversationMode.PTT) {
                            val pttColor = when (sessionPhase) {
                                SessionPhase.TRANSMITTING -> MaterialTheme.colorScheme.primary
                                SessionPhase.FINALIZING, SessionPhase.PROCESSING -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            
                            Button(
                                onClick = { 
                                    if (sessionPhase != SessionPhase.TRANSMITTING) {
                                        pressPtt()
                                    } else {
                                        releasePtt()
                                    }
                                },
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape),
                                colors = ButtonDefaults.buttonColors(containerColor = pttColor)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = "Push to Talk",
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text("PTT", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Phone mode indicator
                            val micColor = if (microphoneGated) Color.Red else 
                                          if (sessionPhase == SessionPhase.TRANSMITTING) MaterialTheme.colorScheme.primary 
                                          else MaterialTheme.colorScheme.surfaceVariant
                            
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Microphone",
                                modifier = Modifier.size(64.dp),
                                tint = micColor
                            )
                            Text(
                                when {
                                    microphoneGated -> "Microphone muted (TTS playing)"
                                    sessionPhase == SessionPhase.TRANSMITTING -> "Listening..."
                                    sessionPhase == SessionPhase.PLAYING_TTS -> "Playing message..."
                                    else -> "Ready to speak"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        // Status indicator
                        Text(
                            when (sessionPhase) {
                                SessionPhase.IDLE -> "Ready"
                                SessionPhase.TRANSMITTING -> "Recording..."
                                SessionPhase.FINALIZING -> "Processing..."
                                SessionPhase.PROCESSING -> "Sending..."
                                SessionPhase.PLAYING_TTS -> "Playing..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Alert Button
            Button(
                onClick = { showAlertPresets = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ALERT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            
            // Status Message
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Dialogs
        if (showLanguageSelector) {
            LanguageSelectorDialog(
                currentLanguage = currentLanguage,
                onLanguageSelected = { lang ->
                    currentLanguage = lang
                    languageLoaded = false
                    showLanguageSelector = false
                },
                onDismiss = { showLanguageSelector = false }
            )
        }
        
        if (showAlertPresets) {
            AlertPresetsDialog(
                language = currentLanguage,
                onAlertSelected = { alert -> sendAlert(alert) },
                onDismiss = { showAlertPresets = false }
            )
        }
        
        if (showConnectionDialog) {
            BluetoothConnectionDialog(
                onDismiss = { showConnectionDialog = false }
            )
        }
        
        if (showMeasurementsDashboard) {
            MeasurementsDashboardDialog(
                measurements = alertsAndMeasurements,
                onDismiss = { showMeasurementsDashboard = false }
            )
        }
    }
    
    @Composable
    private fun LanguageSelectorDialog(
        currentLanguage: String,
        onLanguageSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Language") },
            text = {
                Column {
                    listOf("hi" to "हिंदी (Hindi)", "en" to "English").forEach { (code, name) ->
                        TextButton(
                            onClick = { onLanguageSelected(code) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
    
    @Composable
    private fun AlertPresetsDialog(
        language: String,
        onAlertSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        val presets = remember(language) {
            val arrayId = if (language == "hi") R.array.alert_presets_hi else R.array.alert_presets_en
            context.resources.getStringArray(arrayId).toList()
        }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Emergency Alerts") },
            text = {
                LazyColumn {
                    items(presets) { preset ->
                        TextButton(
                            onClick = {
                                onAlertSelected(preset)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(preset, modifier = Modifier.fillMaxWidth())
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
    
    @Composable
    private fun BluetoothConnectionDialog(onDismiss: () -> Unit) {
        // Simplified connection dialog - reuse existing Bluetooth test functionality
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Bluetooth Connection") },
            text = { Text("Use the Bluetooth Test screen from the old main menu to establish connection, then return here.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        )
    }
    
    private fun loadLanguage() {
        lifecycleScope.launch {
            try {
                languageLoading = true
                
                // Pipeline timing: Language load start
                pipelineEventSink.emit(PipelineEvent(
                    "language_load_start",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    details = mapOf("language" to currentLanguage)
                ))
                
                // Memory before load
                val memoryBefore = benchmarkStore?.currentTotalPssKb() ?: 0
                
                val spec = LanguageRegistry.byCode(currentLanguage)
                
                languageManager?.release()
                languageManager = LanguageManager(this@CommunicationActivity)
                
                val loaded = languageManager!!.loadLanguage(spec)
                AppState.languageManager = languageManager
                
                // Memory after load
                val memoryAfter = benchmarkStore?.currentTotalPssKb() ?: 0
                
                // Pipeline timing: Language load complete
                pipelineEventSink.emit(PipelineEvent(
                    "language_load_complete",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    details = mapOf(
                        "language" to currentLanguage,
                        "stt_loaded" to loaded.sttLoaded.toString(),
                        "tts_loaded" to loaded.ttsLoaded.toString(),
                        "memory_delta_kb" to (memoryAfter - memoryBefore).toString()
                    )
                ))
                
                benchmarkStore?.add("memory_after_language_load_kb", memoryAfter.toDouble(), "kb")
                benchmarkStore?.add("memory_delta_language_load_kb", (memoryAfter - memoryBefore).toDouble(), "kb")
                
                languageLoaded = loaded.isLoaded
                setupAudioPipeline()
                
            } catch (e: Exception) {
                languageLoaded = false
                pipelineEventSink.emit(PipelineEvent(
                    "language_load_error",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    details = mapOf("error" to (e.message ?: "unknown"))
                ))
            } finally {
                languageLoading = false
            }
        }
    }
    
    private fun showError(message: String) {
        android.util.Log.e("iTantra", message)
        lifecycleScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(this@CommunicationActivity, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupAudioPipeline() {
        val manager = languageManager ?: return
        
        // Release previous controller/VAD first to avoid leaks
        liveSttController?.release()
        audioCapture?.stop()
        
        // Create VAD engine with graceful fallback
        val vadModelPath = java.io.File(filesDir, "models/vad/silero_vad.onnx").absolutePath
        vadEngine = VadEngine(modelPath = vadModelPath)
        
        // Create STT engine that bridges to LanguageManager
        sttEngine = ManagerSttEngine(manager)
        
        // Create utterance segmenter for speech boundaries
        val segmenter = UtteranceSegmenter()
        
        // Create the live STT controller that coordinates the pipeline
        liveSttController = LiveSttController(
            vad = vadEngine!!,
            segmenter = segmenter,
            stt = sttEngine!!,
            scope = lifecycleScope
        )
        
        // Set up audio capture
        audioCapture = AudioCapture()
        
        println("Audio pipeline initialized successfully")
    }
    
    private fun setMode(mode: ConversationMode) {
        currentMode = mode
        val transition = conversationMachine.setMode(mode)
        executeCommands(transition.commands)
    }
    
    private fun pressPtt() {
        if (!languageLoaded || currentMode != ConversationMode.PTT) return
        isPttPressed = true
        val transition = conversationMachine.pressPtt()
        executeCommands(transition.commands)
    }
    
    private fun releasePtt() {
        if (!isPttPressed) return
        isPttPressed = false
        val transition = conversationMachine.releasePtt()
        executeCommands(transition.commands)
    }
    
    private fun sendAlert(alertText: String) {
        lifecycleScope.launch {
            val sendStartTime = System.currentTimeMillis()
            val messageId = UUID.randomUUID().toString()
            
            val payload = MessagePayload(
                type = MessageType.ALERT,
                messageId = messageId,
                seq = System.currentTimeMillis(),
                senderId = getLocalSenderId(),
                sentAtEpochMs = System.currentTimeMillis(),
                text = alertText,
                langCode = currentLanguage
            )
            
            // Record message send
            alertsAndMeasurements.recordMessage(MessageDirection.SENT, alertText.length)
            
            try {
                transport?.send(payload)
                
                // Simulate delivery confirmation for measurement
                val deliveryLatency = System.currentTimeMillis() - sendStartTime
                alertsAndMeasurements.recordMessageLatency(messageId.hashCode().toLong(), deliveryLatency)
                
            } catch (e: Exception) {
                // Record send failure
                alertsAndMeasurements.recordConnectionFailure("Unknown", e.message ?: "Send failed")
            }
        }
    }
    
    private fun handleIncomingMessage(payload: MessagePayload) {
        // Record received message
        alertsAndMeasurements.recordMessage(
            MessageDirection.RECEIVED, 
            payload.text?.length ?: 0
        )
        
        when (payload.type) {
            MessageType.SPEECH, MessageType.ALERT -> {
                // Pipeline timing: Message received
                pipelineEventSink.emit(PipelineEvent(
                    "message_received",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    messageId = payload.messageId,
                    details = mapOf(
                        "type" to payload.type.name,
                        "text_length" to (payload.text?.length ?: 0).toString(),
                        "language" to (payload.langCode ?: "unknown")
                    )
                ))
                
                val kind = if (payload.type == MessageType.ALERT) InboundKind.ALERT else InboundKind.SPEECH
                
                // Check language mismatch
                if (payload.langCode != currentLanguage) {
                    handleLanguageMismatch(payload)
                    return
                }
                
                messageQueue.add(payload.messageId to payload)
                val transition = conversationMachine.incoming(payload.messageId, kind)
                executeCommands(transition.commands)
            }
            else -> { /* ACK and PING handled by transport */ }
        }
    }
    
    private fun handleLanguageMismatch(payload: MessagePayload) {
        // Create a simple "translation not supported" message
        lifecycleScope.launch {
            val translationMessage = if (currentLanguage == "hi") {
                "अनुवाद समर्थित नहीं है। संदेश: ${payload.text}"
            } else {
                "Translation not supported. Message: ${payload.text}"
            }
            
            val (samples, sampleRate) = generateTtsForText(translationMessage)
            
            playbackRouter?.play(
                samples = samples,
                sampleRate = sampleRate,
                kind = if (payload.type == MessageType.ALERT) PlaybackKind.ALERT else PlaybackKind.NORMAL
            )
        }
    }
    
    private suspend fun generateTtsForText(text: String): Pair<FloatArray, Int> {
        return withContext(Dispatchers.Default) {
            try {
                val manager = languageManager 
                if (manager == null) {
                    showError("No language manager available for TTS")
                    return@withContext Pair(FloatArray(0), 16000)
                }
                
                val (samples, sampleRate) = manager.synthesize(text)
                Pair(samples, sampleRate)
            } catch (e: Exception) {
                showError("TTS synthesis failed: ${e.message}")
                Pair(FloatArray(0), 16000)
            }
        }
    }
    
    private fun executeCommands(commands: List<SessionCommand>) {
        commands.forEach { command ->
            when (command) {
                SessionCommand.ArmMicrophone -> {
                    android.util.Log.d("iTantra-PTT", "🎤 PTT PRESSED - Starting audio capture")
                    
                    if (!languageLoaded) {
                        showError("No language loaded")
                        return@forEach
                    }
                    
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        showError("Microphone permission not granted")
                        // TODO: Request permission at runtime
                        return@forEach
                    }
                    
                    val controller = liveSttController
                    val capture = audioCapture
                    
                    if (controller == null || capture == null) {
                        showError("Audio pipeline not initialized")
                        return@forEach
                    }
                    
                    try {
                        capture.start()
                        
                        // Start controller with callback that sends each utterance exactly once
                        controller.start(capture.frames) { utterance ->
                            android.util.Log.d("iTantra-PTT", "📝 TRANSCRIPT RECEIVED: '${utterance.text}'")
                            if (utterance.text.isNotBlank()) {
                                android.util.Log.d("iTantra-PTT", "✅ Sending message: '${utterance.text}'")
                                sendSpeechMessage(utterance.text)
                            } else {
                                android.util.Log.w("iTantra-PTT", "⚠️ Empty transcript, not sending message")
                            }
                        }
                        
                    } catch (e: Exception) {
                        showError("Failed to start microphone: ${e.message}")
                    }
                }
                
                SessionCommand.StopMicrophoneAndFinalize -> {
                    // Wait for any existing finalize job to complete first
                    finalizeJob?.let { job ->
                        lifecycleScope.launch {
                            job.join()
                            performFinalization()
                        }
                    } ?: run {
                        performFinalization()
                    }
                }
                
                SessionCommand.StartContinuousListening -> {
                    if (!microphoneGated && languageLoaded) {
                        startContinuousListening()
                    }
                }
                
                SessionCommand.StopListening -> {
                    gracefulStopListening()
                }
                
                is SessionCommand.PlayInbound -> {
                    val payload = messageQueue.find { it.first == command.messageId }?.second
                    if (payload != null) {
                        playInboundMessage(payload, command)
                    }
                }
                
                SessionCommand.GateMicrophone -> {
                    microphoneGated = true
                    gracefulStopListening()  // Stop listening during TTS playback
                }
                
                SessionCommand.UngateMicrophone -> {
                    microphoneGated = false
                }
                
                is SessionCommand.QueueInbound -> {
                    // Message queued - no immediate action needed
                }
                
                SessionCommand.DrainInboundQueue -> {
                    // Clear processed messages
                    messageQueue.clear()
                }
            }
        }
    }
    
    private fun performFinalization() {
        finalizeJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Stop and flush to get the last words transcribed and sent
                liveSttController?.stopAndFlush()
                
                // Stop AudioCapture off main thread (can block ~500ms)
                audioCapture?.stop()
                
            } catch (e: Exception) {
                showError("Error during finalization: ${e.message}")
            } finally {
                // Always call state machine transitions even if nothing was said
                withContext(Dispatchers.Main) {
                    val transition1 = conversationMachine.utteranceFinalized()
                    executeCommands(transition1.commands)
                    val transition2 = conversationMachine.outgoingFinished() 
                    executeCommands(transition2.commands)
                }
            }
        }
    }
    
    private fun gracefulStopListening() {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                liveSttController?.stopAndFlush()
                audioCapture?.stop()
            } catch (e: Exception) {
                showError("Error stopping listening: ${e.message}")
            }
        }
    }
    
    private fun playInboundMessage(payload: MessagePayload, command: SessionCommand.PlayInbound) {
        lifecycleScope.launch {
            try {
                // Pipeline timing: TTS start
                pipelineEventSink.emit(PipelineEvent(
                    "tts_synthesis_start",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    messageId = command.messageId,
                    details = mapOf(
                        "text_length" to (payload.text?.length ?: 0).toString(),
                        "alert" to (command.kind == InboundKind.ALERT).toString()
                    )
                ))
                
                val (samples, sampleRate) = generateTtsForText(payload.text ?: "")
                
                if (samples.isEmpty()) {
                    showError("TTS failed to generate audio")
                    // Still let state machine finish to avoid stalling
                    val transition = conversationMachine.playbackFinished()
                    executeCommands(transition.commands)
                    return@launch
                }
                
                // Pipeline timing: TTS complete
                pipelineEventSink.emit(PipelineEvent(
                    "tts_synthesis_complete",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    messageId = command.messageId,
                    details = mapOf("samples_count" to samples.size.toString(), "sample_rate" to sampleRate.toString())
                ))
                
                val playKind = if (command.kind == InboundKind.ALERT) PlaybackKind.ALERT else PlaybackKind.NORMAL
                
                // Pipeline timing: Audio playback start
                pipelineEventSink.emit(PipelineEvent(
                    "audio_playback_start",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    messageId = command.messageId
                ))
                
                playbackRouter?.play(samples, sampleRate, playKind)
                
                // Pipeline timing: Audio playback complete
                pipelineEventSink.emit(PipelineEvent(
                    "audio_playback_complete",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    messageId = command.messageId
                ))
                
            } catch (e: Exception) {
                showError("Playback failed: ${e.message}")
            } finally {
                // Always finish playback to avoid stalling the state machine
                val transition = conversationMachine.playbackFinished()
                executeCommands(transition.commands)
            }
        }
    }
    
    private fun sendSpeechMessage(text: String) {
        lifecycleScope.launch {
            val sendStartTime = System.currentTimeMillis()
            val messageId = UUID.randomUUID().toString()
            
            // Pipeline timing: Message send start
            pipelineEventSink.emit(PipelineEvent(
                "message_send_start",
                android.os.SystemClock.elapsedRealtimeNanos(),
                messageId = messageId,
                details = mapOf(
                    "type" to "SPEECH",
                    "text_length" to text.length.toString(),
                    "language" to currentLanguage
                )
            ))
            
            val payload = MessagePayload(
                type = MessageType.SPEECH,
                messageId = messageId,
                seq = System.currentTimeMillis(),
                senderId = getLocalSenderId(),
                sentAtEpochMs = System.currentTimeMillis(),
                text = text,
                langCode = currentLanguage
            )
            
            // Record message send
            alertsAndMeasurements.recordMessage(MessageDirection.SENT, text.length)
            
            try {
                transport?.send(payload)
                
                // Pipeline timing: Message sent to transport
                pipelineEventSink.emit(PipelineEvent(
                    "message_sent_to_transport",
                    android.os.SystemClock.elapsedRealtimeNanos(),
                    messageId = messageId
                ))
                
                // Record delivery latency
                val deliveryLatency = System.currentTimeMillis() - sendStartTime
                alertsAndMeasurements.recordMessageLatency(messageId.hashCode().toLong(), deliveryLatency)
                
            } catch (e: Exception) {
                showError("Failed to send message: ${e.message}")
                alertsAndMeasurements.recordConnectionFailure("Transport", e.message ?: "Send failed")
            }
        }
    }
    
    private fun getLocalSenderId(): String {
        return android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.take(64) ?: "android"
    }
    
    private fun startContinuousListening() {
        if (microphoneGated || !languageLoaded) return
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showError("Microphone permission not granted")
            return
        }
        
        val controller = liveSttController
        val capture = audioCapture
        
        if (controller == null || capture == null) {
            showError("Audio pipeline not initialized")
            return
        }
        
        try {
            capture.start()
            
            // In phone mode, use callback but don't call outgoingFinished() per sentence
            controller.start(capture.frames) { utterance ->
                if (utterance.text.isNotBlank()) {
                    sendSpeechMessage(utterance.text)
                    // Note: In phone mode we don't call outgoingFinished per sentence
                    // The state machine handles continuous listening differently
                }
            }
            
            println("Continuous listening started")
        } catch (e: Exception) {
            showError("Failed to start continuous listening: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        finalizeJob?.cancel()
        
        // Disconnect transport before super.onDestroy() so the lifecycle scope is still active
        runBlocking {
            transport?.disconnect()
        }
        
        // Release audio resources - call controller.release() once (not separate vadEngine.release() AND controller.stop())
        liveSttController?.release()
        audioCapture?.stop()
        languageManager?.release()
        
        super.onDestroy()
    }
}

@Composable
private fun MeasurementsDashboardDialog(
    measurements: AlertsAndMeasurements,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Performance Metrics") },
        text = {
            MeasurementsDashboard(measurements = measurements)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                measurements.resetMeasurements()
            }) {
                Text("Reset")
            }
        }
    )
}

