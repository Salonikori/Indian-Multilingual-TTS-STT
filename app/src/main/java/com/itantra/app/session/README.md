# Conversation state machine and pipeline event contract

`ConversationStateMachine` is pure Kotlin: it returns commands and never touches Android APIs.
The app layer must execute commands against `AudioCapture`, `VadEngine`, `SttEngine`,
`ReliableMessageClient`, `TtsEngine`, and `PlaybackRouter`, and call the corresponding
completion event after each asynchronous operation.

Pipeline benchmark events should use `SystemClock.elapsedRealtimeNanos()` and include
`mic.frame`, `vad.speech_start`, `vad.speech_end`, `stt.start`, `stt.end`,
`payload.encoded`, `transport.send`, `transport.receive`, `tts.start`, `tts.first_audio`,
`tts.end`, `playback.start`, and `playback.end`. Use a shared `messageId` across stages.
This file defines the event contract only; it does not claim that every hop is wired.

Phone mode echo policy: gate/stop microphone capture while our own TTS is playing, then
resume after playback. This is simpler to reason about than assuming acoustic echo
cancellation exists. It prevents software capture during playback but does not cancel
speaker/microphone echo during transitions or prevent all environmental feedback.
