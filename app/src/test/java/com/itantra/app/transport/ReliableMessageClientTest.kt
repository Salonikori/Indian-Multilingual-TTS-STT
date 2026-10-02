package com.itantra.app.transport

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@kotlinx.coroutines.ExperimentalCoroutinesApi  
class ReliableMessageClientTest {
    
    private val receivedMessages = mutableListOf<MessagePayload>()
    private val deliveryMetrics = mutableListOf<DeliveryMetric>()
    
    @Before
    fun setup() {
        // Clean state for each test
        receivedMessages.clear()
        deliveryMetrics.clear()
    }
    
    @Test
    fun sendTextCreatesCorrectPayload() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val messageId = client.sendText("Hello", "en")
        runCurrent()
        
        assertEquals(1, fakeTransport.sentPayloads.size)
        val payload = fakeTransport.sentPayloads.first()
        assertEquals(MessageType.SPEECH, payload.type)
        assertEquals("Hello", payload.text)
        assertEquals("en", payload.langCode)
        assertEquals(messageId, payload.messageId)
        
        clientJob.cancel()
    }
    
    @Test
    fun alertMessagesRetryUntilAcked() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        client.sendText("Emergency!", "en", alert = true)
        val originalPayload = fakeTransport.sentPayloads.first()
        runCurrent()
        
        // Simulate time passing for retries
        advanceTimeBy(2000)
        runCurrent()
        
        // Should have retried at least once
        assertTrue("Should have retried alert message", fakeTransport.sentPayloads.size > 1)
        
        // Send ACK and verify no more retries
        val ackPayload = MessagePayload(
            type = MessageType.ACK,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis(),
            ackForMessageId = originalPayload.messageId
        )
        fakeTransport.simulateIncoming(ackPayload)
        runCurrent()
        
        val retriesBeforeAck = fakeTransport.sentPayloads.size
        advanceTimeBy(5000)
        runCurrent()
        
        // Should not have sent more after ACK
        assertEquals(retriesBeforeAck, fakeTransport.sentPayloads.size)
        
        clientJob.cancel()
    }
    
    @Test
    fun duplicateMessagesAreIgnored() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val payload = MessagePayload(
            type = MessageType.SPEECH,
            messageId = "duplicate-test",
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis(),
            text = "Duplicate message",
            langCode = "en"
        )
        
        // Send same message twice
        fakeTransport.simulateIncoming(payload)
        fakeTransport.simulateIncoming(payload)
        runCurrent()
        
        // Should only receive it once
        assertEquals(1, receivedMessages.size)
        assertEquals("Duplicate message", receivedMessages.first().text)
        
        clientJob.cancel()
    }
    
    @Test
    fun ackDeliveryMetricsAreTracked() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val messageId = client.sendText("Test message", "en")
        runCurrent()
        
        // Simulate ACK
        val ackPayload = MessagePayload(
            type = MessageType.ACK,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis(),
            ackForMessageId = messageId
        )
        fakeTransport.simulateIncoming(ackPayload)
        runCurrent()
        
        assertEquals(1, deliveryMetrics.size)
        val metric = deliveryMetrics.first()
        assertEquals(messageId, metric.messageId)
        assertTrue("RTT should be non-negative", metric.ackRttMillis >= 0)
        assertTrue("One-way estimate should be non-negative", metric.estimatedOneWayMillis >= 0)
        
        clientJob.cancel()
    }
    
    @Test
    fun speechMessagesDoNotRetry() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val messageId = client.sendText("Regular message", "en", alert = false)
        val originalPayload = fakeTransport.sentPayloads.first()
        assertEquals(MessageType.SPEECH, originalPayload.type)
        runCurrent()
        
        // SPEECH messages don't retry in current implementation
        advanceTimeBy(3000)
        runCurrent()
        
        // Only one message sent (no retries for SPEECH)
        assertEquals(1, fakeTransport.sentPayloads.size)
        
        clientJob.cancel()
    }
    
    @Test
    fun duplicateAcksForSameMessageAreIgnored() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val messageId = client.sendText("Test message", "en")
        runCurrent()
        
        val ackPayload = MessagePayload(
            type = MessageType.ACK,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis(),
            ackForMessageId = messageId
        )
        
        // Send same ACK twice
        fakeTransport.simulateIncoming(ackPayload)
        fakeTransport.simulateIncoming(ackPayload)
        runCurrent()
        
        // Should only generate one metric (second ACK for unknown message ID)
        assertEquals(1, deliveryMetrics.size)
        
        clientJob.cancel()
    }
    
    @Test
    fun ackForUnknownMessageIsIgnored() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val unknownAckPayload = MessagePayload(
            type = MessageType.ACK,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis(),
            ackForMessageId = "unknown-message-id"
        )
        
        fakeTransport.simulateIncoming(unknownAckPayload)
        runCurrent()
        
        // Should generate no metrics for unknown message
        assertEquals(0, deliveryMetrics.size)
        
        clientJob.cancel()
    }
    
    @Test
    fun pingMessagesAreIgnored() = runTest {
        val fakeTransport = FakeTransport()
        val client = ReliableMessageClient(
            transport = fakeTransport,
            scope = backgroundScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        val clientJob = client.start()
        
        val pingPayload = MessagePayload(
            type = MessageType.PING,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis()
        )
        
        fakeTransport.simulateIncoming(pingPayload)
        runCurrent()
        
        // Should not appear in received messages
        assertEquals(0, receivedMessages.size)
        
        clientJob.cancel()
    }
}

class FakeTransport : Transport {
    val sentPayloads = mutableListOf<MessagePayload>()
    private val incomingFlow = MutableSharedFlow<MessagePayload>()
    private val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected("Test Device"))
    
    override val incoming = incomingFlow.asSharedFlow()
    override val connectionState = stateFlow.asStateFlow()
    
    override suspend fun connect() {}
    override suspend fun connectTo(address: String) {}
    override suspend fun disconnect() {
        stateFlow.value = ConnectionState.Disconnected
    }
    
    override suspend fun send(payload: MessagePayload) {
        sentPayloads.add(payload)
    }
    
    fun simulateIncoming(payload: MessagePayload) {
        incomingFlow.tryEmit(payload)
    }
    
    fun simulateDisconnection() {
        stateFlow.value = ConnectionState.Disconnected
    }
}