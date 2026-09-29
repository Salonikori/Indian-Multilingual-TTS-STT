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
import com.itantra.app.session.*
import com.itantra.app.transport.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*

class CommunicationActivity : ComponentActivity() {
    
    private var languageManager: LanguageManager? = null
    private var transport: BluetoothClassicTransport? = null
    private var playbackRouter: PlaybackRouter? = null
    private var audioCapture: AudioCapture? = null
    private var liveSttController: Any? = null
    private var conversationMachine = ConversationStateMachine()
    
    private var currentLanguage by mutableStateOf("hi")
    private var currentMode by mutableStateOf(ConversationMode.PTT)
    private var isPttPressed by mutableStateOf(false)
    private var microphoneGated by mutableStateOf(false)
    private var languageLoaded by mutableStateOf(false)
    private var languageLoading by mutableStateOf(false)
    
    private val messageQueue = mutableListOf<Pair<String, MessagePayload>>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        val connectionState by transport?.connectionState?.collectAsState() ?: remember { mutableStateOf(ConnectionState.Disconnected) }
        val sessionPhase by remember { derivedStateOf { conversationMachine.state.phase } }
        val context = LocalContext.current
        
        var showLanguageSelector by remember { mutableStateOf(false) }
        var showAlertPresets by remember { mutableStateOf(false) }
        var showConnectionDialog by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                
                // Connection Status
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Connection", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (icon, color, text) = when (connectionState) {
                                is ConnectionState.Connected -> Triple(Icons.Default.CheckCircle, Color.Green, "Connected")
                                is ConnectionState.Listening -> Triple(Icons.Default.Phone, Color.Blue, "Hosting")
                                is ConnectionState.Connecting -> Triple(Icons.Default.Refresh, Color.Yellow, "Connecting")
                                else -> Triple(Icons.Default.Close, Color.Red, "Disconnected")
                            }
                            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showConnectionDialog = true }) {
                            Text("Setup")
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
                val spec = LanguageRegistry.byCode(currentLanguage)
                
                languageManager?.release()
                languageManager = LanguageManager(this@CommunicationActivity)
                
                val loaded = languageManager!!.loadLanguage(spec)
                AppState.languageManager = languageManager
                
                languageLoaded = loaded.isLoaded
                setupAudioPipeline()
                
            } catch (e: Exception) {
                languageLoaded = false
            } finally {
                languageLoading = false
            }
        }
    }
    
    private fun setupAudioPipeline() {
        val manager = languageManager ?: return
        
        // Set up audio capture and STT
        audioCapture = AudioCapture()
        
        // Create a simplified STT controller wrapper
        liveSttController = object {
            private var isListening = false
            private var transcriptCallback: ((String) -> Unit)? = null
            
            fun startListening(onTranscript: (String) -> Unit) {
                if (isListening) return
                isListening = true
                transcriptCallback = onTranscript
                
                lifecycleScope.launch {
                    // Simulate STT - in real implementation, wire to audio capture
                    // This would connect to AudioCapture -> VAD -> STT pipeline
                }
            }
            
            fun stopListening() {
                isListening = false
                transcriptCallback = null
            }
            
            fun release() {
                stopListening()
            }
        }
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
            val payload = MessagePayload(
                type = MessageType.ALERT,
                messageId = UUID.randomUUID().toString(),
                seq = System.currentTimeMillis(),
                senderId = getLocalSenderId(),
                sentAtEpochMs = System.currentTimeMillis(),
                text = alertText,
                langCode = currentLanguage
            )
            
            transport?.send(payload)
        }
    }
    
    private fun handleIncomingMessage(payload: MessagePayload) {
        when (payload.type) {
            MessageType.SPEECH, MessageType.ALERT -> {
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
            
            playbackRouter?.play(
                samples = generateTtsForText(translationMessage),
                sampleRate = 22050,
                kind = if (payload.type == MessageType.ALERT) PlaybackKind.ALERT else PlaybackKind.NORMAL
            )
        }
    }
    
    private suspend fun generateTtsForText(text: String): FloatArray {
        return try {
            val manager = languageManager ?: return FloatArray(0)
            val (samples, _) = manager.synthesize(text)
            samples
        } catch (e: Exception) {
            FloatArray(0) // Return empty array if TTS fails
        }
    }
    
    private fun executeCommands(commands: List<SessionCommand>) {
        commands.forEach { command ->
            when (command) {
                SessionCommand.ArmMicrophone -> {
                    // Start audio capture and STT
                    lifecycleScope.launch {
                        (liveSttController as? Any)?.let { controller ->
                            // Simplified STT simulation
                            // In real implementation: audioCapture -> VAD -> STT -> onTranscript
                            kotlinx.coroutines.delay(2000)
                            sendSpeechMessage("Test message from PTT")
                            val transition = conversationMachine.outgoingFinished()
                            executeCommands(transition.commands)
                        }
                    }
                }
                
                SessionCommand.StopMicrophoneAndFinalize -> {
                    (liveSttController as? Any)?.let { 
                        // Stop listening logic would go here
                    }
                    val transition = conversationMachine.utteranceFinalized()
                    executeCommands(transition.commands)
                }
                
                SessionCommand.StartContinuousListening -> {
                    if (!microphoneGated) {
                        lifecycleScope.launch {
                            (liveSttController as? Any)?.let { controller ->
                                // Start continuous listening for phone mode
                                // Would connect to real STT pipeline
                            }
                        }
                    }
                }
                
                SessionCommand.StopListening -> {
                    (liveSttController as? Any)?.let { 
                        // Stop listening logic
                    }
                }
                
                is SessionCommand.PlayInbound -> {
                    val payload = messageQueue.find { it.first == command.messageId }?.second
                    if (payload != null) {
                        lifecycleScope.launch {
                            val samples = generateTtsForText(payload.text ?: "")
                            val playKind = if (command.kind == InboundKind.ALERT) PlaybackKind.ALERT else PlaybackKind.NORMAL
                            
                            playbackRouter?.play(samples, 22050, playKind)
                            
                            val transition = conversationMachine.playbackFinished()
                            executeCommands(transition.commands)
                        }
                    }
                }
                
                SessionCommand.GateMicrophone -> {
                    microphoneGated = true
                    (liveSttController as? Any)?.let { 
                        // Stop listening during TTS playback
                    }
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
    
    private fun sendSpeechMessage(text: String) {
        lifecycleScope.launch {
            val payload = MessagePayload(
                type = MessageType.SPEECH,
                messageId = UUID.randomUUID().toString(),
                seq = System.currentTimeMillis(),
                senderId = getLocalSenderId(),
                sentAtEpochMs = System.currentTimeMillis(),
                text = text,
                langCode = currentLanguage
            )
            
            transport?.send(payload)
        }
    }
    
    private fun getLocalSenderId(): String {
        return android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.take(64) ?: "android"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        (liveSttController as? Any)?.let { 
            // Release STT controller
        }
        languageManager?.release()
        lifecycleScope.launch {
            transport?.disconnect()
        }
    }
}

