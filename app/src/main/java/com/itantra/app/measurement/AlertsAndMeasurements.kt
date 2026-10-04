package com.itantra.app.measurement

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Centralized measurement and alerting system for iTantra.
 * 
 * Tracks:
 * - Bluetooth connection latency and reliability
 * - STT processing times and accuracy
 * - Audio capture quality metrics
 * - System performance indicators
 * 
 * Provides:
 * - Real-time alerts for connection issues
 * - Performance dashboards
 * - Historical metrics storage
 */
class AlertsAndMeasurements {
    
    // Measurement state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()
    
    private val _latencyMeasurements = MutableStateFlow<List<LatencyRecord>>(emptyList())
    val latencyMeasurements = _latencyMeasurements.asStateFlow()
    
    private val _alertState = MutableStateFlow<AlertState?>(null)
    val alertState = _alertState.asStateFlow()
    
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics = _performanceMetrics.asStateFlow()
    
    companion object {
        const val HIGH_LATENCY_THRESHOLD_MS = 1000L
        const val CONNECTION_TIMEOUT_MS = 5000L
        const val MAX_STORED_MEASUREMENTS = 100
        const val ALERT_DURATION_MS = 5000L
        
        /**
         * Test emergency alert system with maximum priority
         */
        fun testEmergencyAlert(context: Context) {
            try {
                // Play a test alert sound using MediaPlayer
                val mediaPlayer = android.media.MediaPlayer()
                
                // Generate a brief test tone (440Hz for 1 second)
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                
                // Save current volume
                val originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                
                // Set to maximum alarm volume
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                
                // Use ToneGenerator for immediate alert sound
                val toneGen = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.ToneGenerator.MAX_VOLUME
                )
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                
                // Show toast notification
                android.widget.Toast.makeText(
                    context,
                    "Emergency Alert Test - Maximum Volume",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                // Restore original volume after a delay (done in background)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, originalVolume, 0)
                    toneGen.release()
                }, 2000)
                
            } catch (e: Exception) {
                // Fallback: show toast only
                android.widget.Toast.makeText(
                    context,
                    "Emergency Alert Test (Audio Error: ${e.message})",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private val messageCounter = AtomicLong(0)
    
    /**
     * Record Bluetooth connection attempt
     */
    fun recordConnectionAttempt(deviceName: String) {
        _connectionState.value = ConnectionState.CONNECTING
        updatePerformanceMetrics { it.copy(connectionAttempts = it.connectionAttempts + 1) }
    }
    
    /**
     * Record successful Bluetooth connection
     */
    fun recordConnectionSuccess(deviceName: String, latencyMs: Long) {
        _connectionState.value = ConnectionState.CONNECTED
        recordLatency(LatencyType.CONNECTION, latencyMs)
        updatePerformanceMetrics { 
            it.copy(
                connectionSuccesses = it.connectionSuccesses + 1,
                lastConnectionTime = System.currentTimeMillis()
            )
        }
        clearAlert() // Clear any connection failure alerts
    }
    
    /**
     * Record Bluetooth connection failure
     */
    fun recordConnectionFailure(deviceName: String, error: String) {
        _connectionState.value = ConnectionState.FAILED
        updatePerformanceMetrics { it.copy(connectionFailures = it.connectionFailures + 1) }
        showAlert(
            type = AlertType.CONNECTION_FAILED,
            title = "Connection Failed",
            message = "Failed to connect to $deviceName: $error",
            severity = AlertSeverity.ERROR
        )
    }
    
    /**
     * Record Bluetooth disconnection
     */
    fun recordDisconnection(reason: String?) {
        _connectionState.value = ConnectionState.DISCONNECTED
        reason?.let { 
            showAlert(
                type = AlertType.CONNECTION_LOST,
                title = "Connection Lost", 
                message = "Bluetooth connection lost: $it",
                severity = AlertSeverity.WARNING
            )
        }
    }
    
    /**
     * Record message transmission latency
     */
    fun recordMessageLatency(messageId: Long, latencyMs: Long) {
        recordLatency(LatencyType.MESSAGE_DELIVERY, latencyMs)
        
        // Alert on high latency
        if (latencyMs > HIGH_LATENCY_THRESHOLD_MS) {
            showAlert(
                type = AlertType.HIGH_LATENCY,
                title = "High Latency Detected",
                message = "Message took ${latencyMs}ms to deliver (threshold: ${HIGH_LATENCY_THRESHOLD_MS}ms)",
                severity = AlertSeverity.WARNING
            )
        }
    }
    
    /**
     * Record STT processing metrics
     */
    fun recordSttProcessing(audioLengthMs: Long, processingTimeMs: Long, confidence: Float?) {
        recordLatency(LatencyType.STT_PROCESSING, processingTimeMs)
        
        val rtf = processingTimeMs.toDouble() / audioLengthMs.toDouble()
        updatePerformanceMetrics { 
            it.copy(
                sttProcessingCount = it.sttProcessingCount + 1,
                totalSttTimeMs = it.totalSttTimeMs + processingTimeMs,
                averageRtf = (it.averageRtf * it.sttProcessingCount + rtf) / (it.sttProcessingCount + 1)
            )
        }
        
        // Alert on very slow STT processing
        if (rtf > 2.0) {
            showAlert(
                type = AlertType.SLOW_STT,
                title = "Slow STT Processing",
                message = "Speech recognition is running slowly (RTF: %.2f)".format(rtf),
                severity = AlertSeverity.WARNING
            )
        }
    }
    
    /**
     * Record audio capture quality issues
     */
    fun recordAudioQualityIssue(issue: AudioQualityIssue) {
        updatePerformanceMetrics { 
            it.copy(audioQualityIssues = it.audioQualityIssues + 1)
        }
        
        showAlert(
            type = AlertType.AUDIO_QUALITY,
            title = "Audio Quality Issue",
            message = when (issue) {
                AudioQualityIssue.LOW_VOLUME -> "Audio input level is very low"
                AudioQualityIssue.CLIPPING -> "Audio input is clipping/distorted"
                AudioQualityIssue.NOISE -> "High noise levels detected"
                AudioQualityIssue.DROPOUT -> "Audio input dropout detected"
            },
            severity = AlertSeverity.WARNING
        )
    }
    
    /**
     * Record message send/receive for throughput tracking
     */
    fun recordMessage(direction: MessageDirection, sizeBytes: Int) {
        val messageId = messageCounter.incrementAndGet()
        updatePerformanceMetrics { metrics ->
            when (direction) {
                MessageDirection.SENT -> metrics.copy(
                    messagesSent = metrics.messagesSent + 1,
                    bytesSent = metrics.bytesSent + sizeBytes
                )
                MessageDirection.RECEIVED -> metrics.copy(
                    messagesReceived = metrics.messagesReceived + 1,
                    bytesReceived = metrics.bytesReceived + sizeBytes
                )
            }
        }
    }
    
    private fun recordLatency(type: LatencyType, latencyMs: Long) {
        val measurement = LatencyRecord(
            type = type,
            latencyMs = latencyMs,
            timestamp = System.currentTimeMillis()
        )
        
        _latencyMeasurements.value = (_latencyMeasurements.value + measurement)
            .takeLast(MAX_STORED_MEASUREMENTS)
    }
    
    private fun showAlert(type: AlertType, title: String, message: String, severity: AlertSeverity) {
        _alertState.value = AlertState(
            type = type,
            title = title,
            message = message,
            severity = severity,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun clearAlert() {
        _alertState.value = null
    }
    
    private fun updatePerformanceMetrics(update: (PerformanceMetrics) -> PerformanceMetrics) {
        _performanceMetrics.value = update(_performanceMetrics.value)
    }
    
    /**
     * Get connection reliability as percentage
     */
    fun getConnectionReliability(): Float {
        val metrics = _performanceMetrics.value
        return if (metrics.connectionAttempts > 0) {
            (metrics.connectionSuccesses.toFloat() / metrics.connectionAttempts.toFloat()) * 100f
        } else 0f
    }
    
    /**
     * Get average message latency for a specific type
     */
    fun getAverageLatency(type: LatencyType): Double {
        val measurements = _latencyMeasurements.value.filter { it.type == type }
        return if (measurements.isNotEmpty()) {
            measurements.map { it.latencyMs }.average()
        } else 0.0
    }
    
    /**
     * Reset all measurements (useful for testing)
     */
    fun resetMeasurements() {
        _latencyMeasurements.value = emptyList()
        _performanceMetrics.value = PerformanceMetrics()
        _alertState.value = null
        messageCounter.set(0)
    }
}

// Data classes for measurement state
data class LatencyRecord(
    val type: LatencyType,
    val latencyMs: Long,
    val timestamp: Long
)

data class AlertState(
    val type: AlertType,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long
)

data class PerformanceMetrics(
    val connectionAttempts: Int = 0,
    val connectionSuccesses: Int = 0,
    val connectionFailures: Int = 0,
    val lastConnectionTime: Long = 0,
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val sttProcessingCount: Int = 0,
    val totalSttTimeMs: Long = 0,
    val averageRtf: Double = 0.0,
    val audioQualityIssues: Int = 0
)

// Enums
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}

enum class LatencyType {
    CONNECTION, MESSAGE_DELIVERY, STT_PROCESSING
}

enum class AlertType {
    CONNECTION_FAILED, CONNECTION_LOST, HIGH_LATENCY, SLOW_STT, AUDIO_QUALITY
}

enum class AlertSeverity {
    INFO, WARNING, ERROR
}

enum class MessageDirection {
    SENT, RECEIVED
}

enum class AudioQualityIssue {
    LOW_VOLUME, CLIPPING, NOISE, DROPOUT
}

// Composable components for UI display

@Composable
fun AlertDisplay(alertState: AlertState?) {
    val context = LocalContext.current
    
    alertState?.let { alert ->
        LaunchedEffect(alert) {
            // Show system toast for important alerts
            if (alert.severity == AlertSeverity.ERROR) {
                Toast.makeText(context, alert.message, Toast.LENGTH_LONG).show()
            }
            
            // Auto-dismiss after duration
            delay(AlertsAndMeasurements.ALERT_DURATION_MS)
            // Note: In real implementation, would call clearAlert() on measurements instance
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (alert.severity) {
                    AlertSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
                    AlertSeverity.WARNING -> Color(0xFFFFF3E0) // Light orange
                    AlertSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (alert.severity) {
                        AlertSeverity.ERROR -> Icons.Default.Warning // Using Warning icon as Error
                        AlertSeverity.WARNING -> Icons.Default.Warning
                        AlertSeverity.INFO -> Icons.Default.Phone // Using Phone as placeholder for Info
                    },
                    contentDescription = alert.severity.name,
                    tint = when (alert.severity) {
                        AlertSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        AlertSeverity.WARNING -> Color(0xFFE65100) // Dark orange
                        AlertSeverity.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (alert.severity) {
                            AlertSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            AlertSeverity.WARNING -> Color(0xFFE65100)
                            AlertSeverity.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (alert.severity) {
                            AlertSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            AlertSeverity.WARNING -> Color(0xFFE65100)
                            AlertSeverity.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusIndicator(connectionState: ConnectionState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> Color.Green
                        ConnectionState.CONNECTING -> Color.Yellow
                        ConnectionState.FAILED -> Color.Red
                        ConnectionState.DISCONNECTED -> Color.Gray
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = when (connectionState) {
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.FAILED -> "Failed"
                ConnectionState.DISCONNECTED -> "Disconnected"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MeasurementsDashboard(measurements: AlertsAndMeasurements) {
    val performanceMetrics by measurements.performanceMetrics.collectAsState()
    val latencyMeasurements by measurements.latencyMeasurements.collectAsState()
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Connection metrics
            MetricRow("Connection Success Rate", "${measurements.getConnectionReliability().toInt()}%")
            MetricRow("Messages Sent", performanceMetrics.messagesSent.toString())
            MetricRow("Messages Received", performanceMetrics.messagesReceived.toString())
            
            // Latency metrics
            val avgConnectionLatency = measurements.getAverageLatency(LatencyType.CONNECTION)
            if (avgConnectionLatency > 0) {
                MetricRow("Avg Connection Time", "${avgConnectionLatency.toInt()}ms")
            }
            
            val avgMessageLatency = measurements.getAverageLatency(LatencyType.MESSAGE_DELIVERY)
            if (avgMessageLatency > 0) {
                MetricRow("Avg Message Latency", "${avgMessageLatency.toInt()}ms")
            }
            
            // STT metrics
            if (performanceMetrics.sttProcessingCount > 0) {
                MetricRow("STT Processes", performanceMetrics.sttProcessingCount.toString())
                MetricRow("Avg RTF", "%.3f".format(performanceMetrics.averageRtf))
            }
            
            if (performanceMetrics.audioQualityIssues > 0) {
                MetricRow("Audio Issues", performanceMetrics.audioQualityIssues.toString())
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}