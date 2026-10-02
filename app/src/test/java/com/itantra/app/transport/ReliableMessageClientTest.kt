package com.itantra.app.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ReliableMessageClientTest {
    
    private lateinit var fakeTransport: FakeTransport
    private lateinit var testScope: TestScope
    private lateinit var client: ReliableMessageClient
    private lateinit var clientJob: Job
    private val receivedMessages = mutableListOf<MessagePayload>()
    private val deliveryMetrics = mutableListOf<DeliveryMetric>()
    
    @Before
    fun setup() {
        fakeTransport = FakeTransport()
        testScope = TestScope(UnconfinedTestDispatcher())
        client = ReliableMessageClient(
            transport = fakeTransport,
            scope = testScope,
            onMessage = { receivedMessages.add(it) },
            onMetric = { deliveryMetrics.add(it) }
        )
        clientJob = client.start()
    }
    
    @After
    fun cleanup() {
        clientJob.cancel()
        testScope.cancel()
    }
    
    @Test
    fun sendTextCreatesCorrectPayload() = runTest {
        val messageId = client.sendText("Hello", "en")
        
        assertEquals(1, fakeTransport.sentPayloads.size)
        val payload = fakeTransport.sentPayloads.first()
        assertEquals(MessageType.SPEECH, payload.type)
        assertEquals("Hello", payload.text)
        assertEquals("en", payload.langCode)
        assertEquals(messageId, payload.messageId)
    }
    
    @Test
    fun alertMessagesRetryUntilAcked() = runTest {
        client.sendText("Emergency!", "en", alert = true)
        val originalPayload = fakeTransport.sentPayloads.first()
        
        // Simulate time passing for retries
        advanceTimeBy(2000)
        
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
        
        val retriesBeforeAck = fakeTransport.sentPayloads.size
        advanceTimeBy(5000)
        
        // Should not have sent more after ACK
        assertEquals(retriesBeforeAck, fakeTransport.sentPayloads.size)
    }
    
    @Test
    fun duplicateMessagesAreIgnored() = runTest {
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
        
        // Should only receive it once
        assertEquals(1, receivedMessages.size)
        assertEquals("Duplicate message", receivedMessages.first().text)
    }
    
    @Test
    fun ackDeliveryMetricsAreTracked() = runTest {
        val startTime = System.currentTimeMillis()
        val messageId = client.sendText("Test message", "en")
        
        // Simulate ACK after delay
        Thread.sleep(10) // Small delay to measure RTT
        val ackPayload = MessagePayload(
            type = MessageType.ACK,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis(),
            ackForMessageId = messageId
        )
        fakeTransport.simulateIncoming(ackPayload)
        
        assertEquals(1, deliveryMetrics.size)
        val metric = deliveryMetrics.first()
        assertEquals(messageId, metric.messageId)
        assertTrue("RTT should be positive", metric.ackRttMillis > 0)
        assertTrue("One-way estimate should be positive", metric.estimatedOneWayMillis > 0)
    }
    
    @Test
    fun pingMessagesAreIgnored() = runTest {
        val pingPayload = MessagePayload(
            type = MessageType.PING,
            messageId = UUID.randomUUID().toString(),
            seq = 1,
            senderId = "test",
            sentAtEpochMs = System.currentTimeMillis()
        )
        
        fakeTransport.simulateIncoming(pingPayload)
        
        // Should not appear in received messages
        assertEquals(0, receivedMessages.size)
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