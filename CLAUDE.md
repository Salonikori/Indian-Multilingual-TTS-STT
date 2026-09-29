# iTantra Hard Rules

These are the repository's current non-negotiable engineering rules. The earlier project artifacts did not contain a `CLAUDE.md`; this file is added now as the explicit audit contract and must not be treated as proof that a previous file was checked.

1. **Offline by default.** Runtime STT, VAD, TTS, and Bluetooth text transport must work without Internet access. No Internet permission or HTTP/IP networking client is allowed in app code. If a future optional model downloader is added, isolate it in one named module and label it as the only networked component.
2. **Text-only transport.** Audio is captured and processed locally. Transport payloads may carry transcript/alert text and protocol metadata only; no PCM, WAV, encoded audio, samples, byte arrays containing audio, or audio file paths may enter the message payload or transport API.
3. **One active language.** Load only the explicitly selected language's STT/TTS models. On switching, release the previous native recognizer/TTS handles before opening the new pack. Never preload every language.
4. **No fake validation.** Never mark a language `VALIDATED`, report a metric, or claim a demo works without recorded test evidence and the exact device/model/sample context. Use `NOT_INSTALLED`, `Not measured`, and `Not implemented` when appropriate.
5. **Open-source and traceable assets.** Pin library/model versions and document upstream, exact artifact/revision, license, and checksum before bundling. Do not bundle unverified model files or binaries.
6. **Honest failure paths.** Missing/incompatible models and invalid payloads must produce visible failures. Failed STT/decryption must never be spoken. Do not silently substitute a fake engine.
7. **Human-safe audio behavior.** Do not claim guaranteed DND/silent-mode override; Android/OEM audio policy can prevent it. Do not make consequential changes automatically.
8. **Reproducibility.** Keep build/test instructions, raw benchmark exports, sample sizes, device identifiers, and methods. Distinguish static/source checks from Android build, emulator, and physical-device verification.
9. **Small, auditable changes.** Prefer small modules, tests for protocol/model integrity, and a clean README that lists all incomplete work.

## Current limitations

The source tree is not currently a complete, verified transceiver. The launcher is a model smoke-test screen; the conversation state machine, live capture/STT, Bluetooth transport, TTS playback, and benchmark collection are not yet integrated into one end-to-end user flow. No release APK or clean-install device result is available in this workspace.
