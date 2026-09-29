# iTantra — Offline multilingual speech-to-text / text-to-speech transceiver

iTantra is an Android proof-of-concept for relaying spoken messages between phones by recognizing speech locally, sending only the resulting text over Bluetooth Classic RFCOMM, and synthesizing that text on the receiving phone. The intended design is offline-first: audio is captured and processed on-device, while the transport carries text and protocol metadata rather than audio. **This repository is currently a source prototype and evaluation scaffold, not a verified end-to-end transceiver:** the launcher is a model smoke-test screen; the conversation state machine, live STT, Bluetooth transport, TTS playback and alert path are not wired together into one user flow; model assets and the sherpa-onnx Android AAR are not included; and no physical-device acceptance run or release APK has been produced.

## Architecture

```text
                       OFFLINE ANDROID PHONE A
┌────────────────────────────────────────────────────────────────┐
│ Microphone → AudioRecord → VAD/utterance segmentation → STT     │
│                                                   │ transcript │
└───────────────────────────────────────────────────┼────────────┘
                                                    │ text only
                                                    ▼
                                     Bluetooth Classic RFCOMM
                                     length-prefixed JSON frames
                                                    │
                                                    ▼
                       OFFLINE ANDROID PHONE B
┌────────────────────────────────────────────────────────────────┐
│ JSON validation → language selection → local TTS → AudioTrack   │
└────────────────────────────────────────────────────────────────┘

Current implementation boundary:
- Source modules exist for several boxes, but no end-to-end controller wires them together.
- The current launcher exposes model smoke tests, Languages, and Benchmark screens.
- No Internet permission or HTTP/IP client is present in the Android runtime source.
```

## Remediation changes in this source snapshot

- Fixed `UtteranceSegmenter` compilation by explicitly using `java.util.ArrayDeque` and fixed the zero-pre-roll edge case so the first speech frame is not dropped.
- Fixed the conversation policy queue transition: inbound messages queued while sending now begin playback when the outgoing send finishes, instead of remaining stranded until another playback event.
- Language selection now releases the previously selected native engines asynchronously and disables switching during cleanup; model loading also checks transducer decoder/joiner files and the TTS data directory, and cleans up after native linkage failures.
- The Languages screen distinguishes a complete installed pack from an installed-but-unvalidated or missing pack; measured size remains separate from observed installed bytes.
- APK packaging no longer excludes `META-INF/LICENSE*` and `META-INF/NOTICE*` files, preserving dependency attribution notices where provided.

These are source-level fixes and pure-Kotlin smoke checks, not a substitute for a full Android build. The runtime AAR, real model packs, integrated live relay flow, release APK, and device acceptance run are still missing as listed below.

## Current build status — read before presenting

- App ID: `com.itantra.app`.
- Kotlin + Jetpack Compose; `minSdk 26`, `targetSdk 35`, `compileSdk 36`, `arm64-v8a` ABI filter.
- The repository does **not** include Gradle wrapper scripts/JAR, an Android SDK, a compiled sherpa-onnx AAR, model files, or a release APK.
- `app/build.gradle.kts` includes the sherpa AAR only if `app/libs/sherpa_onnx.aar` exists. The source imports sherpa-onnx APIs, so the AAR matching those APIs is a required build prerequisite.
- **No release build, APK size, clean install, or physical-device run has been verified.** Do not state otherwise in a submission or live demo.

## Setup

### 1. Prerequisites

Install Android Studio with JDK 17, Android SDK Platform 36, and an Android NDK compatible with the exact sherpa-onnx Android release you choose. Use a physical arm64 Android device for microphone and Bluetooth checks. Install Python 3.10+ for the offline model-lab scripts.

### 2. Model lab

From the repository root:

```bash
cd models_lab
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell: .venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python test_stt.py --help
python test_tts.py --help
```

The lab scripts are evaluation tools; this repo contains no model weights or recorded speech corpus. They do not access the network. The only network-capable model acquisition code is isolated in `optional_model_manager/download_candidates.py`; it requires a separate environment and explicit invocation. Put genuine recorded WAVs and same-stem reference `.txt` files under `models_lab/test_audio/<language>/`. Follow `models_lab/benchmark/README.md` to prepare references, run a separate WER result for each language, collect listening-panel ratings, and generate `BENCHMARKS.md`. Never pool WER across languages or use an empty panel template as evidence.

### 3. Install the native runtime and model assets

1. Obtain the official sherpa-onnx Android release/build output from the upstream project, read that release's instructions, and record the exact version/revision and license.
2. Place the compatible AAR at `app/libs/sherpa_onnx.aar` as described in `app/libs/README.md`. If that AAR does not bundle the native libraries, place the matching `arm64-v8a` `.so` files in `app/src/main/jniLibs/arm64-v8a/`. Do not duplicate native libraries between the AAR and `jniLibs`.
3. Select exact STT and TTS artifacts, verify their licenses and formats, and place their files in app-private storage. Expected paths are described in the existing source comments and `LanguageRegistry.kt`; layouts are model-specific and must not be guessed from the file extension.
4. Keep each language pack separate under `filesDir/models/<language-code>/`. Do not bundle or load unverified models. For optional candidate acquisition only, see `optional_model_manager/README.md`; this is the only networked component and is never imported by Android runtime STT/TTS/transport. The registry currently marks all languages `NOT_INSTALLED`.

### 4. Android build

The repository does not ship `gradlew`; use Android Studio's Gradle integration with a compatible installed Gradle, or generate and commit the wrapper for the chosen Gradle version before distributing the repository. From Android Studio:

1. Open the `iTantra-android-smoke` directory.
2. Ensure JDK 17, SDK Platform 36, and the required NDK are installed.
3. Add the matching official sherpa AAR and required native libraries.
4. Sync Gradle and run `:app:testDebugUnitTest` and `:app:assembleDebug`.
5. Fix any compile/test failures against the exact AAR version; no successful Android compile is claimed by this source snapshot.
6. For a release artifact, configure signing and run `:app:assembleRelease`; record the APK byte size with `stat -c %s app/build/outputs/apk/release/app-release.apk` (or the platform-equivalent command).

### 5. Device install and Bluetooth pairing

The current app does **not** contain a complete in-app pairing/connect workflow or end-to-end speech-relay flow. Bluetooth transport source supports a paired device address and RFCOMM service UUID, but UI-to-transport integration is not implemented. Until that integration and two-device tests pass, these are setup notes for future integration, not a validated pairing procedure:

1. Install the same build on two arm64 Android phones and grant microphone and nearby-Bluetooth permissions.
2. Pair the phones in Android Settings using the system Bluetooth pairing UI.
3. In a future integrated connection screen, choose one phone as host/listener and connect the other using the host's paired Bluetooth address. The current UI does not yet expose these actions.
4. Test with Wi-Fi and mobile data disabled; Bluetooth must remain enabled. Verify transcript display and received TTS independently before attempting the full demo.

## Supported languages and status

“Listed” means a registry entry exists; it does not mean that a working model pack is installed. **No language is currently validated by this project.**

| Language | Code | App status | Evidence |
|---|---|---|---|
| Hindi | `hi` | `NOT_INSTALLED` | STT candidate identified; no complete installed STT+TTS pack or device validation |
| English | `en` | `NOT_INSTALLED` | STT candidates identified; no complete installed STT+TTS pack or device validation |
| Tamil | `ta` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Bengali | `bn` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Marathi | `mr` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Gujarati | `gu` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Kannada | `kn` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Telugu | `te` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Malayalam | `ml` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |
| Odia | `or` | `NOT_INSTALLED` | STT candidate only; TTS pack and measurements absent |

The `VALIDATED` state must only be used after recording model revision/license, local bundle size, STT WER and sample count, STT RTF, and a human TTS intelligibility/naturalness check for the exact pack. Candidate availability in an upstream repository is not validation.

## Benchmark results

Copied from the current `BENCHMARKS.md` report. There are **no measured performance results** in the supplied exports.

| Criterion | Result | Method/status |
|---|---|---|
| Device model / SoC / total RAM | Not measured | No Android benchmark export supplied |
| Release APK size | Not measured | No release APK artifact supplied |
| Per-model file sizes | Not measured | No installed model-file listing supplied |
| Peak RAM with one language loaded | Not measured | No load/inference high-water samples; a single PSS snapshot is not peak RAM |
| 5-minute armed-silent CPU | Not measured | No complete 300-second run supplied |
| WER per language and sample count | Not measured | No actual STT result JSON or recorded reference corpus supplied |
| TTS intelligibility/naturalness | Not measured | No completed listening panel |
| Speech-end → STT-ready median/worst, N≥20 | Not measured | No latency samples supplied |
| Text-received → first-audio median/worst, N≥20 | Not measured | No latency samples supplied |
| TTS RTF median/worst, N≥20 | Not measured | No TTS timing samples supplied |
| Phone A speech → Phone B audio onset, N≥20 | Not measured | No two-phone measurements on a shared/recorded timebase supplied |

See `BENCHMARKS.md` and `models_lab/benchmark/README.md` for collection and suspicious-value rules. The benchmark screen can capture snapshots and export raw samples, but its latency form is manual entry; it does not automatically instrument the live end-to-end pipeline.

## Security and repository audit

Run:

```bash
bash scripts/audit-hard-rules.sh
```

Source-level audit findings:

- Android manifest has no `android.permission.INTERNET`.
- No HTTP/IP networking client/API or HTTP-client dependency is declared in app runtime source/dependencies. Bluetooth Classic RFCOMM is the intentional local transport. Gradle repositories are build-time dependency sources, not runtime app networking.
- `MessagePayload` contains text and protocol metadata only; `Transport.send` accepts `MessagePayload`, not audio arrays/files. The byte array in the Bluetooth frame is the UTF-8 serialized JSON payload, not PCM/WAV data.
- Native STT/TTS engine construction is located in `LanguageManager`; `loadLanguage` calls `release()` before resolving/creating the selected language's engines. This is a source-level lifecycle check, not a heap/native-memory proof.
- The earlier artifacts did not include `CLAUDE.md`; this repo adds it now as the current hard-rule contract. The audit script does not replace dependency resolution, APK inspection, or device testing.

## Licenses and attributions

No model weights, sherpa AAR, or native `.so` files are bundled in this source snapshot. Verify the exact revision and license for every artifact before redistribution.

| Component / candidate | Role | Version / license evidence | Current status |
|---|---|---|---|
| AndroidX Activity Compose `1.10.1`, Core KTX `1.16.0`, Lifecycle Runtime KTX `2.9.1`, Compose BOM `2025.06.01` and Compose libraries | Android UI/runtime | Upstream AndroidX/Jetpack projects generally use Apache-2.0; preserve the license/notice files for the exact resolved artifacts | Declared dependencies; resolved dependency tree not generated in this environment |
| Kotlin Android / Compose plugins `2.1.21` | Language/build plugins | Kotlin is distributed under Apache-2.0; verify exact plugin distribution notices | Declared, not build-verified |
| Kotlinx Coroutines Android `1.10.2` | Async tasks/flows | Apache-2.0 upstream | Declared, not build-verified |
| JUnit `4.13.2` | Unit tests | JUnit 4 has EPL-1.0 / GPL-2.0-with-classpath-exception licensing; retain upstream notices | Test dependency; tests not run here |
| sherpa-onnx | Local STT/TTS/VAD runtime | Upstream project is Apache-2.0; exact Android AAR revision not selected or bundled. Verify notices for the chosen release | Required build dependency; absent |
| AI4Bharat IndicConformer converted ONNX candidates | Indic STT | `models_lab/MODELS.md` records the conversion repository's model-card license claim for source weights; exact exported files, revision and tokenizer terms remain unpinned | Candidate only; no files bundled or evaluated |
| Silero VAD ONNX | Speech activity detection | Exact model file/revision/license not pinned in this repo; verify the chosen file's own terms | Candidate only; no file bundled or evaluated |
| English Zipformer GigaSpeech candidate | English STT | Model ledger cites its upstream model card as Apache-2.0; exact revision and complete artifact set must be pinned before use | Candidate only; not downloaded/tested |
| Hindi/English/additional-language TTS | Local synthesis | No exact TTS model selected; therefore no model-specific license can yet be attributed | Not implemented as a shippable model pack |

A complete transitive dependency/license inventory, generated SBOM, model checksums, and final distribution notices are **not implemented**. Do not redistribute model or native binary artifacts until the exact artifact-level terms are verified.

## Not implemented

- End-to-end wiring from microphone → VAD segmentation → STT → Bluetooth text send → receiver validation → TTS synthesis → playback.
- Live speech-to-message delivery; current launcher is a model smoke-test UI.
- UI-driven Bluetooth host/connect/disconnect and pairing workflow; only transport source exists.
- Durable outbox, persistent message history, peer discovery, robust two-device reconnection validation.
- Verified, installed Hindi/English model packs; all ten language entries are `NOT_INSTALLED`.
- Fully wired Silero VAD lifecycle and confirmation that the selected model's API semantics match the pinned sherpa-onnx version.
- Reliable non-interruptible alerts across DND/OEM policies; Android does not guarantee this.
- Automatic language mismatch handling, translated messages, group messaging, BLE mesh relay, and encryption/key verification.
- Model pack installer, trusted signed manifest/key distribution, and Internet model downloader.
- Automatic pipeline latency instrumentation and fully automatic BENCHMARKS.md regeneration from all live app runs.
- Gradle wrapper, verified fresh-clone Android build, signed release APK, clean-install run, and two-phone acceptance evidence.

## Future work

1. **BLE mesh relay:** add store-and-forward routing, hop limits, loop prevention, TTL and message deduplication; specify how Bluetooth Classic and BLE roles coexist on target devices.
2. **Translation:** optionally translate recognized text between supported languages with explicit user-visible labeling and local/offline model evaluation; same-language relay remains the current scope.
3. **Group messaging:** add recipient/group addressing, ordering, deduplication, membership and delivery status without introducing audio transport.
4. **Release hardening:** integrate the pipeline, pin and validate models/runtime, add cryptographic peer verification, run all automated tests, produce a signed APK and collect physical-device evidence.
