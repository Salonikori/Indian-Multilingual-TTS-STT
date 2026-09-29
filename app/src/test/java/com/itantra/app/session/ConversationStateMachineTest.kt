package com.itantra.app.session

import org.junit.Assert.*
import org.junit.Test

class ConversationStateMachineTest {
    @Test fun pttArmsOnlyWhileHeldAndFinalizesOnRelease() {
        val machine = ConversationStateMachine()
        assertTrue(machine.pressPtt().commands.contains(SessionCommand.ArmMicrophone))
        assertEquals(SessionPhase.TRANSMITTING, machine.state.phase)
        assertTrue(machine.releasePtt().commands.contains(SessionCommand.StopMicrophoneAndFinalize))
        assertFalse(machine.state.pttHeld)
        assertEquals(SessionPhase.FINALIZING, machine.state.phase)
    }

    @Test fun pttDoesNotArmInPhoneMode() {
        val machine = ConversationStateMachine()
        machine.setMode(ConversationMode.PHONE)
        assertTrue(machine.pressPtt().commands.isEmpty())
    }

    @Test fun incomingQueuesWhileTransmittingAndPlaysAfterFinalization() {
        val machine = ConversationStateMachine()
        machine.pressPtt()
        machine.incoming("m1", InboundKind.SPEECH)
        assertEquals(1, machine.state.queuedInbound.size)
        machine.releasePtt()
        machine.utteranceFinalized()
        val t = machine.outgoingFinished()
        assertEquals(SessionPhase.PLAYING_TTS, t.state.phase)
        assertTrue(t.commands.contains(SessionCommand.PlayInbound("m1", InboundKind.SPEECH)))
    }

    @Test fun phoneModeGatesMicDuringPlayback() {
        val machine = ConversationStateMachine()
        machine.setMode(ConversationMode.PHONE)
        val t = machine.incoming("m1", InboundKind.SPEECH)
        assertTrue(t.commands.contains(SessionCommand.GateMicrophone))
        val done = machine.playbackFinished()
        assertTrue(done.commands.contains(SessionCommand.UngateMicrophone))
        assertTrue(done.commands.contains(SessionCommand.StartContinuousListening))
    }

    @Test fun incomingMessagesAreQueuedInOrderDuringPlayback() {
        val machine = ConversationStateMachine()
        machine.incoming("m1", InboundKind.SPEECH)
        machine.incoming("m2", InboundKind.ALERT)
        val next = machine.playbackFinished()
        assertTrue(next.commands.contains(SessionCommand.PlayInbound("m2", InboundKind.ALERT)))
    }

    @Test fun queuedInboundStartsAfterOutgoingMessageFinishes() {
        val machine = ConversationStateMachine()
        machine.pressPtt()
        machine.incoming("peer-1", InboundKind.SPEECH)
        machine.releasePtt()
        val transition = machine.outgoingFinished()
        assertEquals(SessionPhase.PLAYING_TTS, transition.state.phase)
        assertTrue(transition.commands.contains(SessionCommand.PlayInbound("peer-1", InboundKind.SPEECH)))
        assertTrue(transition.state.queuedInbound.isEmpty())
    }
}
