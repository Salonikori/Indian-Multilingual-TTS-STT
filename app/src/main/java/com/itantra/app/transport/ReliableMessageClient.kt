package com.itantra.app.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class DeliveryMetric(val messageId: String, val ackRttMillis: Long, val estimatedOneWayMillis: Double)

class ReliableMessageClient(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val onMessage: (MessagePayload) -> Unit,
    private val onMetric: (DeliveryMetric) -> Unit = {}
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
            senderId = "android-${android.os.Build.MODEL.take(32)}",
            langCode = languageCode, sentAtEpochMs = System.currentTimeMillis(), text = text)
        sentAt[id] = System.currentTimeMillis()
        transport.send(payload)
        if (alert) retryJobs[id] = scope.launch {
            repeat(3) { attempt ->
                delay(1_500L * (attempt + 1))
                if (!sentAt.containsKey(id)) return@launch
                runCatching { transport.send(payload) }
            }
        }
        return id
    }
}
