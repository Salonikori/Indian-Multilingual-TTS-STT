# iTantra — offline Hindi/English voice-to-text-to-voice radio prototype

ISRO Smart India Hackathon 2026, problem statement 26173 (*Indian Multilingual TTS & STT Aided Neural
Transceiver Radio Access for low bitrate links*).

Phone A listens, detects the end of a sentence, converts speech to text **on the device**, and sends only that
text over Bluetooth. Phone B converts the text to speech **on the device** and plays it. Alerts are meant to
play at maximum volume and not be interrupted by other audio.

## Status (read this first)

This is a **prototype**. Most pieces are written, but several have **not been verified on a phone**, and no
accuracy figure has been measured on real human speech yet.

- **Languages implemented:** Hindi and English. The other eight required languages are future work (hidden in the app).
- **Not implemented:** encryption, Bluetooth LE mesh, translation between languages, Wi-Fi transport.
- **Build, test and audit results:** see `FINAL_VERIFICATION_REPORT.md`.

### Implemented in code, not yet verified on a device
Push-to-talk and phone mode, Bluetooth text transport with ACK/retry, delivery status in the message
timeline, alert playback path (alarm stream, max alarm volume, exclusive audio focus), foreground microphone
service. Each has unit tests only where logic can run on a PC; none of them has a recorded two-phone test.

### Measured so far
| Item | Result | Source / caveat |
|---|---|---|
| Speech-to-text accuracy on real human speech | **not measured** | Tooling is in `tools/prepare_real_speech_corpus.py`; see `REAL_SPEECH_TESTING.md` |
| Earlier Hindi / English WER (64.9% / 96.8%) | **invalid** | Measured on TTS-generated audio (a model hearing a model). The English figure was also from a different, since-replaced model. |
| STT / TTS latency, RTF on a phone | **not measured** | Use the Measurement screen, then `models_lab/benchmark/summarize_benchmarks.py` |
| RAM, idle CPU, battery | **not measured** | |
| APK size | **not recorded** | Build, then record `ls -l app/build/outputs/apk/debug/` |
| End-to-end phone A → phone B latency | **not measured** | Needs two phones |

Earlier PC-side runs reported text-to-speech RTF around 0.6 (Hindi) and 0.46 (English). Raw results for those
runs are not in this repository, and they say nothing about phone speed, so they are not used as results here.

## Models

| Role | Model | Notes |
|---|---|---|
| Hindi STT | NeMo CTC (IndicConformer conversion), int8, about 188 MiB | Larger than the 150 MB per-language budget used in `models_lab/MODELS.md` |
| English STT | Whisper tiny.en, int8 (encoder, decoder, tokens), about 118 MiB | Size is an estimate from the model archive; not re-measured |
| Hindi TTS | Piper `hi_IN-rohan-medium` int8, about 17.5 MiB | needs `espeak-ng-data` |
| English TTS | Piper `en_US-lessac-medium` int8, about 17.7 MiB | needs `espeak-ng-data` |
| Voice activity | Silero VAD | |

Details, licences and open questions: `models_lab/MODELS.md`.

## Architecture
```
mic (16 kHz) → Silero VAD → sentence segmenter → STT → text message → Bluetooth (RFCOMM, ACK + retry)
                                                                          ↓
speaker ← audio playback (normal / alert path) ← TTS ← text message on the other phone
```
Only text is ever sent. `MessagePayload` has no audio field, and `scripts/audit-hard-rules.sh` checks that.
The app has no INTERNET permission. Models are copied to the phone with `adb` (a developer-time step).

## Build and install
```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# models: download on a PC (tools/), then push to the phone
python tools/download_candidates.py      # see tools/README.md for the other download scripts
python optional_model_manager/install_models.py --languages hi,en
```
Requirements: Android 8.0 or newer (minSdk 26), ARM64 phone, roughly 350 MB free storage for the models,
Bluetooth. Release signing is optional: copy `keystore.properties.example` to `keystore.properties` and fill it
in. Never commit that file.

## Known limitations
- Accuracy on real speech is unknown. The Hindi model is large, and its accuracy has not been shown to be good.
- Only two phones over Bluetooth Classic have been designed for; behaviour with more devices is untested.
- Android alert behaviour can differ by phone brand and Do Not Disturb settings. Test on the phones you will use.
- Large Hindi STT model may be slow or memory-hungry on low-end phones. Not measured.

## Repository layout
`app/` Android source · `models_lab/` PC-side model tests and benchmark scripts · `tools/` dev-time download and
corpus tools (these use the network, the app does not) · `optional_model_manager/` offline model installer ·
`scripts/audit-hard-rules.sh` source-level rule checks.

## Documents
`models_lab/MODELS.md` models · `REAL_SPEECH_TESTING.md` how to measure WER on real speech · `DEMO_SCRIPT.md` ·
`DEPLOYMENT_GUIDE.md` · `DEPLOYMENT_CHECKLIST.md` · `FINAL_VERIFICATION_REPORT.md` · `FINAL_HARDWARE_TEST_REPORT.md`.

Problem statement contacts: see the ISRO problem statement page.