# Final remediation status

This ZIP contains source-level fixes and the existing iTantra prototype/evaluation tools. It is **not a verified final working Android transceiver**.

## Fixes in this pass

- Corrected the Kotlin `ArrayDeque` compile issue in `UtteranceSegmenter`.
- Corrected zero-pre-roll segmentation so the first speech frame is retained.
- Corrected queued inbound handling after an outgoing send completes.
- Ensured language selection releases old engines off the UI thread, and prevents language switching during cleanup.
- Added transducer decoder/joiner and TTS data-directory preflight checks; native linkage failures now also trigger cleanup.
- Distinguished installed-but-unvalidated language packs from missing packs in the Languages screen.
- Preserved dependency license/notice resources during APK packaging.
- Added `scripts/check-build-prereqs.sh` so a clean checkout identifies missing build prerequisites directly.

## Checks run in this environment

- Pure Kotlin compile and smoke run: PTT/phone state transitions, queued inbound playback, and zero-pre-roll utterance segmentation — passed.
- Python compileall for `models_lab` and `optional_model_manager` — passed.
- Benchmark scripts' CLI help — passed.
- Synthetic WAV/reference corpus preparation and TTS rating aggregation — passed. Synthetic fixtures are test-only and are not reported as model measurements.
- Hard-rules source audit — passed.
- ZIP integrity — checked after packaging.

## Still blocking a release-ready submission

- No Gradle wrapper/system Gradle or Android SDK was available in the workspace; no Android Gradle build or JUnit suite was run.
- The official sherpa-onnx Android AAR/native runtime is not included. Kotlin API compatibility against a pinned AAR is not verified.
- No STT/TTS model packs or real speech corpus are bundled. All languages remain unvalidated/not installed.
- The launcher remains a model smoke-test screen. Microphone/VAD/STT → Bluetooth text → receiver TTS/alert is not wired into a complete in-app user flow.
- Bluetooth pairing/connection UI is not connected to the transport classes.
- No release APK, APK size, clean install, or physical-device/two-phone acceptance result exists.
- Encryption, signed model manifests, translation, group messaging, and BLE mesh relay are not implemented.

Do not represent the ZIP as a working transceiver or claim the demo acceptance criteria pass until these blockers are resolved and verified on two physical Android devices.
