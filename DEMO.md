# iTantra — 4-minute demo script (conditional; do not claim unverified behavior)

**Gate before using this script:** the current repository is not end-to-end integrated. The launcher is a model smoke-test screen, the Bluetooth connection UI is not connected to the transport, and no STT/TTS models or release APK are included. Therefore steps 2–4 below are the intended acceptance demo, not claims that this build can currently perform them. Do not present this script as passed until two physical phones complete a rehearsal without network access.

## Pre-demo checklist (complete before judges arrive)

- [ ] Use the exact release APK that passed a clean-install test; record APK SHA-256 and byte size.
- [ ] Both phones are charged above 70%; bring power banks and USB cables.
- [ ] Both phones use compatible arm64 builds and the same protocol/model revisions.
- [ ] Copy and checksum the exact validated Hindi STT and TTS model packs on both phones.
- [ ] Confirm model registry says `VALIDATED` only for packs with measured WER/RTF and completed listening checks.
- [ ] Grant microphone, nearby-Bluetooth, and notification permissions; confirm mic permission indicator appears when listening.
- [ ] Pair phones in Android Settings and test a Bluetooth connection before going on stage.
- [ ] Test PTT and phone mode, inbound alert, speaker volume, screen-off behavior, and the receiving phone's silent-mode behavior on these exact devices.
- [ ] Disable Wi-Fi and mobile data on both phones; enable Bluetooth after airplane mode is enabled. Verify no Internet is required.
- [ ] Keep alarm/media volumes at tested values; know that Android DND/OEM policy can still suppress alerts.
- [ ] Preload the Benchmark screen with no fake values. Keep the raw export and `BENCHMARKS.md` available.
- [ ] Have a pre-recorded screen capture of a successful *real device test* as a fallback only if clearly labeled recorded footage; never imply it is live.

## Four-minute run of show

### 0:00–0:35 — Offline setup

Show Phone A and Phone B. Enable airplane mode on both, then explicitly turn Bluetooth back on. Show Wi-Fi and mobile data disabled. Explain: “Speech recognition and speech synthesis are intended to run locally; the Bluetooth link carries transcript text, not recorded audio.” Do not claim end-to-end success until the live rehearsal has verified it.

### 0:35–1:25 — Hindi relay: Phone A → Phone B

On Phone A, select the installed and validated Hindi pack. Press and hold PTT, say: “नमस्ते, क्या आप मेरी आवाज़ सुन पा रहे हैं?”, then release PTT. Show the recognized transcript before/while it is delivered. Phone B should display the received text and synthesize it locally. Confirm the sound is generated on Phone B, not streamed from Phone A. If this does not work immediately, stop the live claim and use the fallback plan.

### 1:25–2:00 — Alert while Phone B is silent

Set Phone B to silent mode using its hardware/system control. From Phone A, trigger the pre-agreed alert phrase. Confirm that Phone B visibly receives the alert and that audio behavior matches the rehearsed device policy. Say explicitly if DND or OEM settings limit alert playback; do not claim an Android policy bypass.

### 2:00–2:50 — PTT-off / phone mode

Switch both devices to phone-like continuous conversation mode. Speak one short sentence on A and wait for B to respond. Demonstrate that the implementation gates microphone capture during local TTS playback to reduce feedback. If continuous listening or gating is not integrated in the build, skip this as an implemented feature and show its “Not implemented” entry instead.

### 2:50–3:35 — Benchmark screen

Open the Benchmark screen. Show device model/SoC/RAM, APK/model file-size snapshot, PSS snapshot and export controls. If a full 300-second silent CPU run was completed before the presentation, show the recorded result and method. Export CSV/JSON. Clearly distinguish current measurements from fields still labeled “Not measured”; do not type in estimates during the demo.

### 3:35–4:00 — Close with scope boundaries

Summarize the intended path: on-device STT → text-only Bluetooth → on-device TTS. State that the current report contains only measured results, and list any unmeasured latency/WER/CPU/RAM criteria as outstanding. Mention BLE mesh relay, translation and group messaging as future work, not current features.

## Fallback plan if Bluetooth fails on stage

1. Try once: confirm Bluetooth is enabled on both phones, airplane mode still permits Bluetooth, the system pairing remains, permissions are granted, and no other app is holding the RFCOMM connection. Do not spend more than 20 seconds troubleshooting.
2. If the connection does not recover, state that the live connection failed and switch to a clearly labeled pre-recorded capture from the same build/device setup, if available.
3. Show the actual source architecture, `scripts/audit-hard-rules.sh` output, model ledger and `BENCHMARKS.md` to distinguish implemented source from verified behavior.
4. Do not simulate a transcript, play a local audio file while claiming it arrived over Bluetooth, or present mock measurements as live data.
5. If no valid recording exists, skip the live speech claim and explain which integration/device acceptance item remains incomplete.
