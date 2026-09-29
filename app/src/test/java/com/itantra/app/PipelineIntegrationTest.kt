package com.itantra.app

import com.itantra.app.audio.SttEngine
import com.itantra.app.audio.SttResult
import com.itantra.app.models.LanguageSpec
import com.itantra.app.models.LanguageRegistry
import com.itantra.app.session.ConversationStateMachine
import com.itantra.app.session.ConversationMode
import com.itantra.app.session.SessionCommand
import com.itantra.app.session.SessionPhase
import com.itantra.app.session.InboundKind
import com.itantra.app.tts.TtsEngine
import com.itantra.app.tts.TtsChunk
import com.itantra.app.tts.TtsPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class PipelineIntegrationTest {

    // Mock STT Engine for testing
    class MockSttEngine : SttEngine {
        var lastSamples: FloatArray? = null
        var mockTranscription = "Hello world"
        
        override suspend fun transcribe(samples: FloatArray, sampleRate: Int): SttResult {
            lastSamples = samples
            return SttResult(
                text = mockTranscription,
                confidence = 0.95f,
                decodeMillis = 100L
            )
        }
    }

    // Mock TTS Engine for testing
    class MockTtsEngine : TtsEngine {
        var lastText: String? = null
        var lastLanguage: String? = null
        
        override suspend fun synthesize(text: String, languageCode: String): TtsChunk {
            lastText = text
            lastLanguage = languageCode
            return TtsChunk(
                samples = FloatArray(1000) { 0.1f },
                sampleRate = 22050,
                synthesisMillis = 200L,
                rtf = 0.5,
                text = text
            )
        }
    }

    @Test
    fun `STT engine processes audio samples correctly`() = runTest {
        val sttEngine = MockSttEngine()
        val samples = FloatArray(1600) { (it % 100) / 100.0f }
        
        val result = sttEngine.transcribe(samples, 16000)
        
        assertEquals("Hello world", result.text)
        assertEquals(100L, result.decodeMillis)
        // Check that samples were passed correctly (compare content, not reference)
        assertEquals(samples.size, sttEngine.lastSamples?.size)
        assertTrue(samples.contentEquals(sttEngine.lastSamples))
    }

    @Test
    fun `TTS pipeline synthesizes text with streaming`() = runTest {
        val ttsEngine = MockTtsEngine()
        val playbackResults = mutableListOf<Triple<TtsChunk, TtsPipeline.PlaybackKind, TtsPipeline.SentenceInfo>>()
        
        val pipeline = TtsPipeline(
            engine = ttsEngine,
            playChunk = { chunk, kind, info ->
                playbackResults.add(Triple(chunk, kind, info))
            },
            scope = this@runTest
        )
        
        val job = pipeline.speak("Hello. How are you?", "en", TtsPipeline.PlaybackKind.NORMAL)
        job.join()
        
        assertEquals(2, playbackResults.size)
        assertEquals("How are you?", ttsEngine.lastText)
        assertEquals("en", ttsEngine.lastLanguage)
        
        // Check sentence info
        val firstSentence = playbackResults[0].third
        assertTrue(firstSentence.isFirstSentence)
        assertFalse(firstSentence.isLastSentence)
        assertEquals(0, firstSentence.sentenceIndex)
        assertEquals(2, firstSentence.totalSentences)
        
        val secondSentence = playbackResults[1].third
        assertFalse(secondSentence.isFirstSentence)
        assertTrue(secondSentence.isLastSentence)
        assertEquals(1, secondSentence.sentenceIndex)
    }

    @Test
    fun `ConversationStateMachine manages PTT workflow correctly`() {
        val machine = ConversationStateMachine()
        
        // Initial state
        assertEquals(ConversationMode.PTT, machine.state.mode)
        assertEquals(SessionPhase.IDLE, machine.state.phase)
        assertFalse(machine.state.pttHeld)
        
        // Press PTT
        val press = machine.pressPtt()
        assertTrue(press.commands.contains(SessionCommand.ArmMicrophone))
        assertTrue(machine.state.pttHeld)
        assertEquals(SessionPhase.TRANSMITTING, machine.state.phase)
        
        // Release PTT
        val release = machine.releasePtt()
        assertTrue(release.commands.contains(SessionCommand.StopMicrophoneAndFinalize))
        assertFalse(machine.state.pttHeld)
        assertEquals(SessionPhase.FINALIZING, machine.state.phase)
        
        // Utterance finalized
        machine.utteranceFinalized()
        assertEquals(SessionPhase.PROCESSING, machine.state.phase)
        
        // Outgoing finished
        val finished = machine.outgoingFinished()
        assertEquals(SessionPhase.IDLE, machine.state.phase)
    }

    @Test
    fun `ConversationStateMachine handles incoming messages during transmission`() {
        val machine = ConversationStateMachine()
        
        // Start transmission
        machine.pressPtt()
        assertEquals(SessionPhase.TRANSMITTING, machine.state.phase)
        
        // Incoming message while transmitting - should be queued
        val incoming = machine.incoming("msg1", InboundKind.SPEECH)
        assertTrue(incoming.commands.contains(SessionCommand.QueueInbound("msg1", InboundKind.SPEECH)))
        assertEquals(1, machine.state.queuedInbound.size)
        
        // Finish transmission
        machine.releasePtt()
        machine.utteranceFinalized()
        
        // When outgoing finishes, queued message should play
        val outgoingDone = machine.outgoingFinished()
        assertEquals(SessionPhase.PLAYING_TTS, machine.state.phase)
        assertTrue(outgoingDone.commands.contains(SessionCommand.PlayInbound("msg1", InboundKind.SPEECH)))
        assertTrue(machine.state.queuedInbound.isEmpty())
    }

    @Test
    fun `ConversationStateMachine phone mode gates microphone during playback`() {
        val machine = ConversationStateMachine()
        
        // Switch to phone mode
        val modeSwitch = machine.setMode(ConversationMode.PHONE)
        assertTrue(modeSwitch.commands.contains(SessionCommand.StartContinuousListening))
        assertEquals(ConversationMode.PHONE, machine.state.mode)
        
        // Incoming message in phone mode
        val incoming = machine.incoming("msg1", InboundKind.SPEECH)
        assertTrue(incoming.commands.contains(SessionCommand.GateMicrophone))
        assertTrue(incoming.commands.contains(SessionCommand.PlayInbound("msg1", InboundKind.SPEECH)))
        
        // Playback finished
        val playbackDone = machine.playbackFinished()
        assertTrue(playbackDone.commands.contains(SessionCommand.UngateMicrophone))
        assertTrue(playbackDone.commands.contains(SessionCommand.StartContinuousListening))
    }

    @Test
    fun `LanguageRegistry provides correct language specifications`() {
        // Test that language registry has all expected languages
        val allLanguages = LanguageRegistry.ALL_LANGUAGES
        
        assertTrue(allLanguages.any { it.code == "hi" })
        assertTrue(allLanguages.any { it.code == "en" })
        
        val hindi = LanguageRegistry.ALL_LANGUAGES.find { it.code == "hi" }!!
        assertEquals("Hindi", hindi.displayName)
        assertEquals("models/hi/stt/model.int8.onnx", hindi.sttModelRelativePath)
        assertEquals("models/hi/tts/model.onnx", hindi.ttsModelRelativePath)
        
        val english = LanguageRegistry.ALL_LANGUAGES.find { it.code == "en" }!!
        assertEquals("English", english.displayName)
        assertEquals("models/en/stt/encoder.int8.onnx", english.sttModelRelativePath)
        assertEquals("models/en/tts/model.onnx", english.ttsModelRelativePath)
    }

    @Test
    fun `Pipeline components integrate without errors`() = runTest {
        // Integration test that verifies all components can work together
        val sttEngine = MockSttEngine()
        val ttsEngine = MockTtsEngine()
        val machine = ConversationStateMachine()
        
        // Simulate complete workflow
        sttEngine.mockTranscription = "How are you today"
        
        // 1. STT processes audio
        val audioSamples = FloatArray(1600) { 0.1f }
        val sttResult = sttEngine.transcribe(audioSamples, 16000)
        
        // 2. State machine manages conversation flow
        machine.pressPtt()
        machine.releasePtt()
        machine.utteranceFinalized()
        machine.outgoingFinished()
        
        // 3. TTS processes response text
        val ttsChunk = ttsEngine.synthesize("I am doing well", "en")
        
        // Verify all components worked
        assertEquals("How are you today", sttResult.text)
        assertEquals("I am doing well", ttsEngine.lastText)
        assertEquals("en", ttsEngine.lastLanguage)
        assertEquals(22050, ttsChunk.sampleRate)
        assertTrue(ttsChunk.samples.isNotEmpty())
    }
}