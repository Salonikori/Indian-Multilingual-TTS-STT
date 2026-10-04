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
                MessageType.PING, MessageType.PONG -> Unit
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

    /**
     * Sends a SPEECH or ALERT message and retries until it is ACKed.
     * Throws IllegalStateException if the link is down, and rethrows send errors, so the caller can show them.
     * If no ACK arrives after all retries, a DeliveryMetric with ackRttMillis = -1 is reported (FAILED).
     */
    suspend fun sendText(text: String, languageCode: String, alert: Boolean = false): String {
        if (transport.connectionState.value !is ConnectionState.Connected) {
            throw IllegalStateException("Transport not connected")
        }
        val id = UUID.randomUUID().toString()
        val payload = MessagePayload(
            type = if (alert) MessageType.ALERT else MessageType.SPEECH,
            messageId = id, seq = sequence.getAndIncrement(),
            senderId = deviceId,
            langCode = languageCode, sentAtEpochMs = System.currentTimeMillis(), text = text
        )

        sentAt[id] = System.currentTimeMillis()
        try {
            transport.send(payload)
        } catch (e: Exception) {
            sentAt.remove(id)          // nothing was sent, so nothing to track
            throw e
        }

        // ALERT: 5 retries (0.8 s steps). SPEECH: 3 retries (1.5 s steps).
        retryJobs[id] = if (alert) launchRetries(id, payload, attempts = 5, stepMillis = 800L)
        else launchRetries(id, payload, attempts = 3, stepMillis = 1_500L)
        return id
    }

    private fun launchRetries(id: String, payload: MessagePayload, attempts: Int, stepMillis: Long): Job =
        scope.launch {
            for (attempt in 1..attempts) {
                delay(stepMillis * attempt)                 // growing backoff
                if (!sentAt.containsKey(id)) return@launch  // ACK received
                runCatching { transport.send(payload) }
            }
            delay(stepMillis * (attempts + 1))              // give the last retry time to be ACKed
            if (sentAt.remove(id) != null) {                // still no ACK: report failure once
                retryJobs.remove(id)
                onMetric(DeliveryMetric(id, -1, -1.0))
            }
        }
}