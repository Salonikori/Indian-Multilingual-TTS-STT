package com.itantra.app.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class DeliveryMetric(
    val messageId: String, 
    val ackRttMillis: Long, 
    val estimatedOneWayMillis: Double
) {
    val isSuccess: Boolean = ackRttMillis >= 0
    val isFailed: Boolean = ackRttMillis < 0
}

class ReliableMessageClient(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val onMessage: (MessagePayload) -> Unit,
    private val onMetric: (DeliveryMetric) -> Unit = {},
    private val deviceId: String = "android-${android.os.Build.MODEL?.take(32) ?: "unknown"}"
) {
    private val sequence = AtomicLong(1)
    private val seen = LinkedHashSet<String>()
    private val sentAt = ConcurrentHashMap<String, Long>()
    private val retryJobs = ConcurrentHashMap<String, Job>()

    fun start(): Job = scope.launch {
        transport.incoming.collect { payload ->
            when (payload.type) {
                MessageType.ACK -> payload.ackForMessageId?.let { id ->
                    val started = sentAt.remove(id)
                    if (started != null) {
                        val rtt = System.currentTimeMillis() - started
                        onMetric(DeliveryMetric(id, rtt, rtt / 2.0))
                        retryJobs.remove(id)?.cancel()
                    }
                }
                MessageType.PING -> Unit
                else -> {
                    // Send ACK for all SPEECH/ALERT messages (including duplicates)
                    val ackPayload = MessagePayload(
                        type = MessageType.ACK,
                        messageId = UUID.randomUUID().toString(),
                        seq = sequence.getAndIncrement(),
                        senderId = deviceId,
                        sentAtEpochMs = System.currentTimeMillis(),
                        ackForMessageId = payload.messageId
                    )
                    runCatching { transport.send(ackPayload) }
                    
                    // De-duplicate and deliver message
                    synchronized(seen) {
                        if (!seen.add(payload.messageId)) return@collect
                        if (seen.size > 2_000) seen.remove(seen.first())
                    }
                    onMessage(payload)
                }
            }
        }
    }

    suspend fun sendText(text: String, languageCode: String, alert: Boolean = false): String {
        val id = UUID.randomUUID().toString()
        val payload = MessagePayload(type = if (alert) MessageType.ALERT else MessageType.SPEECH,
            messageId = id, seq = sequence.getAndIncrement(),
            senderId = deviceId,
            langCode = languageCode, sentAtEpochMs = System.currentTimeMillis(), text = text)
        
        sentAt[id] = System.currentTimeMillis()
        
        // Check transport connection before sending
        if (transport.connectionState.value !is ConnectionState.Connected) {
            throw IllegalStateException("Transport not connected")
        }
        
        transport.send(payload)
        
        // Setup retry logic based on message type
        retryJobs[id] = if (alert) {
            // ALERT: More aggressive retries (5 attempts, shorter delays)
            scope.launch {
                repeat(5) { attempt ->
                    delay(800L * (attempt + 1)) // 800ms, 1.6s, 2.4s, 3.2s, 4s
                    if (!sentAt.containsKey(id)) return@launch // ACK received
                    runCatching { transport.send(payload) }
                }
                // Mark as failed after all retries
                sentAt.remove(id)
                onMetric(DeliveryMetric(id, -1, -1.0)) // Negative values indicate failure
            }
        } else {
            // SPEECH: Bounded retries (3 attempts, standard delays)
            scope.launch {
                repeat(3) { attempt ->
                    delay(1_500L * (attempt + 1)) // 1.5s, 3s, 4.5s
                    if (!sentAt.containsKey(id)) return@launch // ACK received
                    runCatching { transport.send(payload) }
                }
                // Mark as failed after all retries  
                sentAt.remove(id)
                onMetric(DeliveryMetric(id, -1, -1.0)) // Negative values indicate failure
            }
        }
        
        return id
    }
}
