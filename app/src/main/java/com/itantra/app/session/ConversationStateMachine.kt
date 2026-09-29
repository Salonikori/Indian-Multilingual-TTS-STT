package com.itantra.app.session

/**
 * Pure Kotlin policy state machine. Effects are returned as commands so audio,
 * STT, TTS and transport remain outside the state machine and are testable separately.
 */
enum class ConversationMode { PTT, PHONE }
enum class SessionPhase { IDLE, TRANSMITTING, FINALIZING, PROCESSING, PLAYING_TTS }
enum class InboundKind { SPEECH, ALERT }

sealed interface SessionCommand {
    data object ArmMicrophone : SessionCommand
    data object StopMicrophoneAndFinalize : SessionCommand
    data object StartContinuousListening : SessionCommand
    data object StopListening : SessionCommand
    data class QueueInbound(val messageId: String, val kind: InboundKind) : SessionCommand
    data class PlayInbound(val messageId: String, val kind: InboundKind) : SessionCommand
    data object DrainInboundQueue : SessionCommand
    data object GateMicrophone : SessionCommand
    data object UngateMicrophone : SessionCommand
}

data class ConversationSnapshot(
    val mode: ConversationMode = ConversationMode.PTT,
    val phase: SessionPhase = SessionPhase.IDLE,
    val pttHeld: Boolean = false,
    val queuedInbound: List<Pair<String, InboundKind>> = emptyList()
)

data class Transition(val state: ConversationSnapshot, val commands: List<SessionCommand> = emptyList())

class ConversationStateMachine(initial: ConversationSnapshot = ConversationSnapshot()) {
    var state: ConversationSnapshot = initial
        private set

    fun setMode(mode: ConversationMode): Transition {
        if (state.mode == mode) return Transition(state)
        val commands = mutableListOf<SessionCommand>()
        if (state.phase == SessionPhase.TRANSMITTING) commands += SessionCommand.StopMicrophoneAndFinalize
        commands += if (mode == ConversationMode.PHONE) {
            SessionCommand.StartContinuousListening
        } else {
            SessionCommand.StopListening
        }
        state = state.copy(mode = mode, phase = SessionPhase.IDLE, pttHeld = false)
        return Transition(state, commands)
    }

    fun pressPtt(): Transition {
        if (state.mode != ConversationMode.PTT || state.pttHeld) return Transition(state)
        state = state.copy(pttHeld = true, phase = SessionPhase.TRANSMITTING)
        return Transition(state, listOf(SessionCommand.ArmMicrophone))
    }

    fun releasePtt(): Transition {
        if (state.mode != ConversationMode.PTT || !state.pttHeld) return Transition(state)
        state = state.copy(pttHeld = false, phase = SessionPhase.FINALIZING)
        return Transition(state, listOf(SessionCommand.StopMicrophoneAndFinalize))
    }

    fun utteranceFinalized(): Transition {
        state = state.copy(phase = SessionPhase.PROCESSING)
        return Transition(state)
    }

    fun outgoingFinished(): Transition {
        val next = state.queuedInbound.firstOrNull()
        if (next != null) {
            state = state.copy(
                phase = SessionPhase.PLAYING_TTS,
                queuedInbound = state.queuedInbound.drop(1)
            )
            val commands = mutableListOf<SessionCommand>()
            if (state.mode == ConversationMode.PHONE) commands += SessionCommand.GateMicrophone
            commands += SessionCommand.PlayInbound(next.first, next.second)
            return Transition(state, commands)
        }
        state = state.copy(phase = SessionPhase.IDLE)
        return Transition(state, if (state.mode == ConversationMode.PHONE) {
            listOf(SessionCommand.StartContinuousListening)
        } else emptyList())
    }

    fun incoming(messageId: String, kind: InboundKind): Transition {
        if (state.phase == SessionPhase.TRANSMITTING || state.phase == SessionPhase.FINALIZING ||
            state.phase == SessionPhase.PROCESSING) {
            state = state.copy(queuedInbound = state.queuedInbound + (messageId to kind))
            return Transition(state, listOf(SessionCommand.QueueInbound(messageId, kind)))
        }
        if (state.phase == SessionPhase.PLAYING_TTS) {
            state = state.copy(queuedInbound = state.queuedInbound + (messageId to kind))
            return Transition(state, listOf(SessionCommand.QueueInbound(messageId, kind)))
        }
        state = state.copy(phase = SessionPhase.PLAYING_TTS)
        val commands = mutableListOf<SessionCommand>()
        if (state.mode == ConversationMode.PHONE) commands += SessionCommand.GateMicrophone
        commands += SessionCommand.PlayInbound(messageId, kind)
        return Transition(state, commands)
    }

    fun playbackFinished(): Transition {
        val next = state.queuedInbound.firstOrNull()
        if (next != null) {
            state = state.copy(phase = SessionPhase.PLAYING_TTS, queuedInbound = state.queuedInbound.drop(1))
            return Transition(state, listOf(SessionCommand.PlayInbound(next.first, next.second)))
        }
        state = state.copy(phase = SessionPhase.IDLE)
        val commands = mutableListOf<SessionCommand>()
        if (state.mode == ConversationMode.PHONE) {
            commands += SessionCommand.UngateMicrophone
            commands += SessionCommand.StartContinuousListening
        } else {
            commands += SessionCommand.DrainInboundQueue
        }
        return Transition(state, commands)
    }
}
