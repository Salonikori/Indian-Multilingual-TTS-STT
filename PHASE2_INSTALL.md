# Phase 2 — Install models on device and run smoke test

This document is the step-by-step runbook for Phase 2 of the checklist.
Complete Phase 1 (model downloads) before starting here.

---

## Prerequisites

| Requirement | How to check |
|---|---|
| Phase 0 complete | `./gradlew assembleDebug` succeeds with 0 errors |
| Phase 1 complete | `models_lab/results/download_manifest.json` exists and lists all files |
| ADB on PATH | `adb version` prints a version string |
| USB debugging on | Phone: Settings → Developer options → USB debugging |
| App installed | `adb install app/build/outputs/apk/debug/app-debug.apk` |

---

## Step 1 — Install the debug APK

```powershell
$adb = "C:\Users\salon\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Confirm it installed:
```powershell
& $adb shell pm list packages com.itantra.app
# Expected: package:com.itantra.app
```

---

## Step 2 — Push model files

```powershell
cd optional_model_manager
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python install_models.py
```

The script:
1. Verifies exactly one ADB device is connected
2. Verifies the app is installed
3. Creates `filesDir` subdirectories via `adb shell run-as`
4. Pushes each model file
5. Fixes file permissions (644 for files, 755 for directories)

### What gets pushed

```
/data/data/com.itantra.app/files/
  models/hi/stt/model.int8.onnx          ← IndicConformer NeMo CTC
  models/hi/stt/tokens.txt
  models/hi/tts/model.onnx               ← MMS VITS (no espeak-ng-data needed)
  models/hi/tts/tokens.txt
  ... (ta, bn, mr, gu, kn, te, ml, or — same layout)
  models/en/stt/encoder.int8.onnx        ← Zipformer GigaSpeech transducer
  models/en/stt/decoder.int8.onnx
  models/en/stt/joiner.int8.onnx
  models/en/stt/tokens.txt
  models/en/tts/model.onnx               ← Piper en_US-lessac-medium
  models/en/tts/tokens.txt
  models/en/tts/espeak-ng-data/          ← Required by Piper
  models/vad/silero_vad.onnx             ← Silero VAD
```

---

## Step 3 — Verify files on device

```powershell
$adb = "C:\Users\salon\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$APP = "com.itantra.app"
$FILES = "/data/data/$APP/files"

# List all installed model files with sizes
& $adb shell run-as $APP find $FILES/models -type f -ls

# Quick count check (expect: 2 per Indic lang × 9 = 18 STT files,
#  18 TTS files, English has 4 STT + 2 TTS + espeak-ng-data/, 1 VAD)
& $adb shell run-as $APP find $FILES/models -name "*.onnx" | wc -l
& $adb shell run-as $APP find $FILES/models -name "tokens.txt" | wc -l

# Check Hindi specifically
& $adb shell run-as $APP ls -lh $FILES/models/hi/stt/
& $adb shell run-as $APP ls -lh $FILES/models/hi/tts/

# Check English (must have encoder/decoder/joiner + espeak-ng-data/)
& $adb shell run-as $APP ls -lh $FILES/models/en/stt/
& $adb shell run-as $APP ls -lh $FILES/models/en/tts/
& $adb shell run-as $APP ls $FILES/models/en/tts/espeak-ng-data/ | head -5

# Check VAD
& $adb shell run-as $APP ls -lh $FILES/models/vad/
```

Expected sizes (approximate — measure locally after download):
- Each Indic NeMo CTC model: ~190 MB
- English encoder: ~73 MB; decoder: ~0.5 MB; joiner: ~0.3 MB
- Each MMS TTS model: ~30–60 MB (varies by language)
- Piper English TTS: ~75 MB + espeak-ng-data ~6 MB
- Silero VAD: ~2 MB

---

## Step 4 — Push a sample WAV for smoke test 1 (STT)

The STT smoke test button in the app reads from:
`/data/data/com.itantra.app/files/samples/smoke_sample.wav`

Requirements: **16 kHz, PCM-16, mono**, 2–10 seconds of clear speech.

```powershell
$adb = "C:\Users\salon\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$APP = "com.itantra.app"

# Create the samples directory
& $adb shell run-as $APP mkdir -p /data/data/$APP/files/samples

# Push your WAV (replace path with your actual file)
& $adb push C:\path\to\your_hindi_sample.wav /data/data/$APP/files/samples/smoke_sample.wav
& $adb shell run-as $APP chmod 644 /data/data/$APP/files/samples/smoke_sample.wav

# Verify
& $adb shell run-as $APP ls -lh /data/data/$APP/files/samples/
```

To record directly on the device and pull back:
```powershell
# Record 5 seconds via ADB (requires the app to be open and mic permission granted)
# Easier: use a voice recorder app on the phone and pull the file
& $adb pull /sdcard/your_recording.wav C:\path\to\local.wav
# Then convert to 16kHz PCM-16 mono with ffmpeg if needed:
# ffmpeg -i input.wav -ar 16000 -ac 1 -sample_fmt s16 smoke_sample.wav
```

---

## Step 5 — Run smoke test in the app

1. Open **iTantra Smoke Test** on the device.
2. The main screen automatically checks whether files are installed.
   - **Red card** = files missing for that language → check Step 2/3.
   - **Green card** = files present → proceed.
3. Press **Load active language**.
   - Watch for load time readout: `STT X ms; TTS Y ms`.
   - Typical first-load: 3–15 seconds depending on device.
   - If it fails: the error detail card shows the exact missing file path.
4. Switch to **English** using the chip — verify the Hindi engines are released before English loads.
5. With both languages loaded one at a time successfully: **Phase 2 STT engine check passed**.

For TTS (Smoke test 2):
- Press **Smoke test 2 · Synthesize and play** — you should hear the device speak.
- Note synthesis time in milliseconds.

Open **Languages and model status** from the main screen to see per-language install status for all 10 languages.

---

## Step 6 — Interpret results and what to record

After passing the smoke test, record the following before moving to Phase 3:

| Measurement | Where to record |
|---|---|
| Hindi STT load time (ms) | `models_lab/MODELS.md` — "Load test" column |
| English STT load time (ms) | `models_lab/MODELS.md` |
| Hindi TTS load time (ms) | `models_lab/MODELS.md` |
| English TTS load time (ms) | `models_lab/MODELS.md` |
| Device model / Android version | `BENCHMARKS.md` |
| Any crash or error messages | Note in `MODELS.md` under relevant language |

Do **not** set `ModelStatus.VALIDATED` yet — that requires WER measurements (Phase 1) and a TTS listening check.

---

## Common problems

### "Files not installed" even after running install_models.py

```powershell
# Check if the files actually arrived
& $adb shell run-as com.itantra.app find /data/data/com.itantra.app/files/models -name "*.onnx" 2>&1
```
If empty, the push failed silently. Re-run `install_models.py` and watch for `ERROR:` lines.

### "Permission denied" when running run-as

The app must be a **debug build** (`android:debuggable="true"` in manifest). Release builds don't allow `run-as`. The debug APK from `assembleDebug` has this set automatically.

### Load fails: "TTS data directory not found"

For Indic (MMS): the `tts/` directory must exist (created by pushing `tts/model.onnx`). If only STT was pushed, push the TTS files too.

For English (Piper): `espeak-ng-data/` must be inside `models/en/tts/`. Check:
```powershell
& $adb shell run-as com.itantra.app ls /data/data/com.itantra.app/files/models/en/tts/
# Must show: espeak-ng-data  model.onnx  tokens.txt
```

### Load fails: "No implementation found for ... sherpa-onnx"

The native `.so` files failed to load. Check:
```powershell
& $adb shell run-as com.itantra.app ls /data/app/~~*/com.itantra.app*/lib/arm64/
# Must show: libsherpa-onnx-jni.so  libonnxruntime.so  etc.
```
If missing, the APK wasn't installed correctly. Re-install with `adb install -r`.

### English TTS very slow first run

Piper with `espeak-ng-data` does phoneme lookup on first run. Subsequent synthesis is faster. Note first-run vs warm synthesis time separately.

---

## Phase 2 done when

- [ ] Both Hindi and English load on the device without errors.
- [ ] Load times are non-zero and recorded.
- [ ] Smoke test 2 (TTS) plays audio for both languages.
- [ ] Switching between languages releases the previous one (no OOM).
- [ ] Languages screen shows "INSTALLED · NOT VALIDATED" for the loaded languages.
