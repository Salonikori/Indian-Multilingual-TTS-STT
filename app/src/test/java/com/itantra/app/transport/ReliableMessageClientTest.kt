package com.itantra.app.transport

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

@kotlinx.coroutines.ExperimentalCoroutinesApi
class ReliableMessageClientTest {

    private val received = mutableListOf<MessagePayload>()
    private val metrics = mutableListOf<DeliveryMetric>()

    /**
     * Builds a client on the test's own scheduler and runs it once, so its collector is already
     * SUBSCRIBED to transport.incoming. Without this runCurrent(), anything the fake transport
     * emits is dropped, because a SharedFlow has no subscribers yet.
     */
    private fun TestScope.newClient(transport: FakeTransport): ReliableMessageClient {
        val client = ReliableMessageClient(
            transport = transport,
            scope = backgroundScope,
            onMessage = { received.add(it) },
            onMetric = { metrics.add(it) },
            deviceId = "test-device"
        )
        client.start()
        runCurrent()
        return client
    }

    private fun ackFor(id: String) = MessagePayload(
        type = MessageType.ACK, messageId = UUID.randomUUID().toString(), seq = 1,
        senderId = "remote", sentAtEpochMs = System.currentTimeMillis(), ackForMessageId = id
    )

    private fun incomingSpeech(id: String, text: String = "Hello from remote") = MessagePayload(
        type = MessageType.SPEECH, messageId = id, seq = 1, senderId = "remote",
        sentAtEpochMs = System.currentTimeMillis(), text = text, langCode = "en"
    )

    @Test fun sendTextCreatesCorrectPayload() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        val id = client.sendText("Hello", "en")
        runCurrent()

        assertEquals(1, transport.sentPayloads.size)
        val p = transport.sentPayloads.first()
        assertEquals(MessageType.SPEECH, p.type)
        assertEquals("Hello", p.text)
        assertEquals("en", p.langCode)
        assertEquals(id, p.messageId)
        assertEquals("test-device", p.senderId)
    }

    @Test fun alertRetriesUntilAckedThenStops() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        val id = client.sendText("Emergency!", "en", alert = true)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue("alert should have been re-sent", transport.sentPayloads.size > 1)

        transport.simulateIncoming(ackFor(id))
        runCurrent()
        val sentAtAck = transport.sentPayloads.size
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals("no more retries after the ACK", sentAtAck, transport.sentPayloads.size)
        assertEquals(1, metrics.size)
        assertTrue(metrics.first().isSuccess)
    }

    @Test fun speechAlsoRetries() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        client.sendText("Regular message", "en")
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue("SPEECH should be re-sent when no ACK arrives", transport.sentPayloads.size > 1)
        assertTrue(transport.sentPayloads.all { it.type == MessageType.SPEECH })
    }

    @Test fun speechWithoutAckIsReportedFailedExactlyOnce() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        val id = client.sendText("Never acknowledged", "en")
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1 + 3, transport.sentPayloads.size)   // original + 3 retries
        assertEquals(1, metrics.size)
        assertEquals(id, metrics.first().messageId)
        assertTrue(metrics.first().isFailed)
    }

    @Test fun alertWithoutAckIsReportedFailedExactlyOnce() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        client.sendText("Never acknowledged", "en", alert = true)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1 + 5, transport.sentPayloads.size)   // original + 5 retries
        assertEquals(1, metrics.size)
        assertTrue(metrics.first().isFailed)
    }

    @Test fun ackProducesOneSuccessMetric() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        val id = client.sendText("Test message", "en")
        runCurrent()
        transport.simulateIncoming(ackFor(id))
        runCurrent()

        assertEquals(1, metrics.size)
        assertEquals(id, metrics.first().messageId)
        assertTrue(metrics.first().ackRttMillis >= 0)
        assertTrue(metrics.first().estimatedOneWayMillis >= 0)
    }

    @Test fun duplicateAcksAreIgnored() = runTest {
        val transport = FakeTransport()
        val client = newClient(transport)

        val id = client.sendText("Test message", "en")
        runCurrent()
        transport.simulateIncoming(ackFor(id))
        transport.simulateIncoming(ackFor(id))
        runCurrent()

        assertEquals(1, metrics.size)
    }

    @Test fun ackForUnknownMessageIsIgnored() = runTest {
        val transport = FakeTransport()
        newClient(transport)

        transport.simulateIncoming(ackFor("unknown-message-id"))
        runCurrent()

        assertEquals(0, metrics.size)
    }

    @Test fun pingIsNotDeliveredAndNotAcked() = runTest {
        val transport = FakeTransport()
        newClient(transport)

        transport.simulateIncoming(
            MessagePayload(type = MessageType.PING, messageId = UUID.randomUUID().toString(), seq = 1,
                senderId = "remote", sentAtEpochMs = System.currentTimeMillis())
        )
        runCurrent()

        assertEquals(0, received.size)
        assertEquals(0, transport.sentPayloads.size)
    }

    @Test fun incomingMessageIsDeliveredAndAckedOnce() = runTest {
        val transport = FakeTransport()
        newClient(transport)

        transport.simulateIncoming(incomingSpeech("incoming-test"))
        runCurrent()

        assertEquals(1, received.size)
        assertEquals("Hello from remote", received.first().text)
        val acks = transport.sentPayloads.filter { it.type == MessageType.ACK }
        assertEquals(1, acks.size)
        assertEquals("incoming-test", acks.first().ackForMessageId)
        assertEquals("test-device", acks.first().senderId)
    }

    @Test fun duplicateIncomingIsDeliveredOnceButAckedEachTime() = runTest {
        val transport = FakeTransport()
        newClient(transport)

        val p = incomingSpeech("dup-1", "Duplicate message")
        transport.simulateIncoming(p)
        runCurrent()
        transport.simulateIncoming(p)   // sender did not see our first ACK and retried
        runCurrent()

        assertEquals("delivered to the app once", 1, received.size)
        assertEquals("but ACKed both times", 2, transport.sentPayloads.count { it.type == MessageType.ACK })
    }

    @Test fun sendTextFailsWhenNotConnectedAndLeavesNoState() = runTest {
        val transport = FakeTransport()
        transport.simulateDisconnection()
        val client = newClient(transport)

        try {
            client.sendText("Test message", "en")
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals("nothing sent", 0, transport.sentPayloads.size)
        assertEquals("no phantom FAILED metric", 0, metrics.size)
    }

    @Test fun sendErrorIsRethrownAndNotRetried() = runTest {
        val transport = FakeTransport()
        transport.failNextSends = 1
        val client = newClient(transport)

        try {
            client.sendText("Test message", "en")
            fail("expected the send error")
        } catch (e: java.io.IOException) {
            // expected
        }
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(0, transport.sentPayloads.size)
        assertEquals(0, metrics.size)
    }
}

class FakeTransport : Transport {
    val sentPayloads = mutableListOf<MessagePayload>()
    var failNextSends = 0

    // extraBufferCapacity matters: with no buffer, tryEmit() returns false whenever a collector
    // is subscribed, so simulated incoming messages would silently never arrive.
    private val incomingFlow = MutableSharedFlow<MessagePayload>(extraBufferCapacity = 64)
    private val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected("Test Device"))

    override val incoming = incomingFlow.asSharedFlow()
    override val connectionState = stateFlow.asStateFlow()

    override suspend fun connect() {}
    override suspend fun connectTo(address: String) {}
    override suspend fun disconnect() { stateFlow.value = ConnectionState.Disconnected }

    override suspend fun send(payload: MessagePayload) {
        if (failNextSends > 0) { failNextSends--; throw java.io.IOException("simulated send failure") }
        sentPayloads.add(payload)
    }

    fun simulateIncoming(payload: MessagePayload) {
        check(incomingFlow.tryEmit(payload)) { "tryEmit failed: increase extraBufferCapacity" }
    }

    fun simulateDisconnection() { stateFlow.value = ConnectionState.Disconnected }
}