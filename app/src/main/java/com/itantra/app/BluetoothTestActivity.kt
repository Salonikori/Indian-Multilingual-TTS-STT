package com.itantra.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.app.transport.*
import kotlinx.coroutines.launch
import java.util.*

class BluetoothTestActivity : ComponentActivity() {
    
    private lateinit var transport: BluetoothClassicTransport
    private val timeline = mutableStateListOf<TimelineItem>()
    private val ackMap = mutableMapOf<String, Long>() // messageId -> sentTime
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        transport = BluetoothClassicTransport(this)
        
        // Listen for incoming messages
        lifecycleScope.launch {
            transport.incoming.collect { payload ->
                handleIncomingMessage(payload)
            }
        }
        
        setContent {
            MaterialTheme {
                BluetoothTestScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BluetoothTestScreen() {
        val connectionState by transport.connectionState.collectAsState()
        val pairedDevices = remember { mutableStateOf(listOf<BluetoothDevice>()) }
        var messageText by remember { mutableStateOf("") }
        var isAlert by remember { mutableStateOf(false) }
        var showMessageTimeline by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready to connect") }
        
        // Load paired devices on start
        LaunchedEffect(Unit) {
            pairedDevices.value = getPairedDevices()
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Bluetooth Test - Phase 5", style = MaterialTheme.typography.headlineMedium)
            
            Text("Status: $statusMessage", style = MaterialTheme.typography.bodyMedium)
            
            // Connection controls
            BluetoothConnectionScreen(
                pairedDevices = pairedDevices.value,
                state = connectionState,
                onHost = {
                    lifecycleScope.launch {
                        try {
                            statusMessage = "Starting host mode..."
                            transport.connect()
                        } catch (e: Exception) {
                            statusMessage = "Host failed: ${e.message}"
                        }
                    }
                },
                onConnect = { address ->
                    lifecycleScope.launch {
                        try {
                            statusMessage = "Connecting to $address..."
                            transport.connectTo(address)
                        } catch (e: Exception) {
                            statusMessage = "Connect failed: ${e.message}"
                        }
                    }
                },
                onDisconnect = {
                    lifecycleScope.launch {
                        transport.disconnect()
                        statusMessage = "Disconnected"
                    }
                }
            )
            
            HorizontalDivider()
            
            // Message sending section
            Text("Send Message", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message text") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { sendMessage(messageText, isAlert) }
                )
            )
            
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Normal")
                Switch(checked = isAlert, onCheckedChange = { isAlert = it })
                Text("Alert")
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { sendMessage(messageText, isAlert) },
                    enabled = connectionState is ConnectionState.Connected && messageText.isNotBlank()
                ) {
                    Text("Send ${if (isAlert) "Alert" else "Message"}")
                }
                
                Button(onClick = { messageText = "Hello from iTantra!" }) {
                    Text("Sample")
                }
            }
            
            HorizontalDivider()
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showMessageTimeline = !showMessageTimeline }) {
                    Text(if (showMessageTimeline) "Hide Timeline" else "Show Timeline")
                }
                
                Button(onClick = { 
                    timeline.clear()
                    ackMap.clear()
                    statusMessage = "Timeline cleared"
                }) {
                    Text("Clear Timeline")
                }
            }
            
            if (showMessageTimeline) {
                Card {
                    Column(Modifier.padding(8.dp)) {
                        Text("Message Timeline", style = MaterialTheme.typography.titleMedium)
                        MessageTimeline(timeline)
                    }
                }
            }
            
            // Test instructions
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Test Instructions:", style = MaterialTheme.typography.titleSmall)
                    Text("1. Pair phones in Android Settings first")
                    Text("2. One phone: press Host, other: select device and Connect")
                    Text("3. Send messages both ways")
                    Text("4. Test disconnect/reconnect resilience")
                    Text("5. Verify ACKs and duplicate detection work")
                }
            }
        }
    }
    
    private fun sendMessage(text: String, isAlert: Boolean) {
        if (text.isBlank()) return
        
        lifecycleScope.launch {
            try {
                val payload = MessagePayload(
                    type = if (isAlert) MessageType.ALERT else MessageType.SPEECH,
                    messageId = UUID.randomUUID().toString(),
                    seq = System.currentTimeMillis(),
                    senderId = getLocalSenderId(),
                    sentAtEpochMs = System.currentTimeMillis(),
                    text = text,
                    langCode = "en"
                )
                
                // Track for ACK timing
                ackMap[payload.messageId] = System.currentTimeMillis()
                
                // Add to timeline immediately as sent
                timeline.add(0, TimelineItem(payload, sentByMe = true, delivered = false))
                
                transport.send(payload)
                
            } catch (e: Exception) {
                // Update status on error
            }
        }
    }
    
    private fun handleIncomingMessage(payload: MessagePayload) {
        when (payload.type) {
            MessageType.SPEECH, MessageType.ALERT -> {
                // Add received message to timeline
                timeline.add(0, TimelineItem(payload, sentByMe = false, delivered = true))
            }
            MessageType.ACK -> {
                // Find the original message and mark as delivered
                val sentTime = ackMap.remove(payload.ackForMessageId)
                val rtt = sentTime?.let { System.currentTimeMillis() - it }
                
                // Update timeline item to show delivered
                val index = timeline.indexOfFirst { 
                    it.payload.messageId == payload.ackForMessageId && it.sentByMe 
                }
                if (index >= 0) {
                    val original = timeline[index]
                    timeline[index] = original.copy(delivered = true, ackRttMillis = rtt)
                }
                
                // Also add ACK to timeline for visibility
                timeline.add(0, TimelineItem(payload, sentByMe = false, delivered = true, ackRttMillis = rtt))
            }
            MessageType.PING -> {
                // Pings are handled internally, just log
                println("BluetoothTest: Received ping")
            }
            MessageType.PONG -> {
                // Pongs are handled internally, just log
                println("BluetoothTest: Received pong")
            }
        }
    }
    
    private fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            val adapter = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(BluetoothManager::class.java)?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
            
            val permission = if (Build.VERSION.SDK_INT >= 31) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH
            }
            
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                emptyList()
            } else {
                adapter?.bondedDevices?.toList() ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun getLocalSenderId(): String {
        return android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.take(64) ?: "android"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            transport.disconnect()
        }
    }
}