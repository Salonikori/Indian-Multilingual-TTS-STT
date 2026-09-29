# optional_model_manager

Developer utility for downloading and sideloading model packs into iTantra.
**Not an Android runtime component.** Never runs on-device.

## What's here

| Script | Purpose |
|---|---|
| `download_candidates.py` | Downloads STT, TTS, and VAD candidate artifacts into `models_lab/models/` |
| `install_models.py` | Pushes those files into the app's `filesDir` via `adb push` |
| `requirements.txt` | Python dependencies (`huggingface_hub`) |

## Models downloaded

| Component | Language | Source | License claim |
|---|---|---|---|
| STT (NeMo CTC) | hi, ta, bn, mr, gu, kn, te, ml, or | `parismitaglobalsolutions/indicconformer-sherpa-onnx` (HF) | MIT per model card — verify at pinned revision |
| STT (Transducer) | en | `k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12` (HF) | Apache-2.0 per model card |
| TTS (VITS/MMS) | hi, ta, bn, mr, gu, kn, te, ml, or | sherpa-onnx `tts-models` release (GitHub) | CC-BY-NC 4.0 per `facebook/mms-tts` card — **not for commercial distribution** |
| TTS (Piper VITS) | en | sherpa-onnx `tts-models` release (GitHub) | MIT (Piper/rhasspy) |
| VAD | all | sherpa-onnx `asr-models` release (GitHub) | Apache-2.0 (Silero) |

> **License check required.** The license claims above come from third-party model cards.
> Verify the exact terms at each source before distributing or shipping.

## Usage

```bash
# Windows
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Download all candidates into models_lab/models/
python download_candidates.py

# Connect an Android device with USB debugging, install the debug APK, then:
python install_models.py
```

## After installation

1. Open the app and navigate to the Languages screen — installed languages show file sizes.
2. Run `models_lab/test_stt.py` with real WAV/reference pairs to measure WER and RTF on your host.
3. Run `models_lab/test_tts.py` with 10 sentences per language; listen to all outputs.
4. Only after steps 2–3 pass, update `ModelStatus` to `VALIDATED` in `LanguageRegistry.kt`
   and record measurements in `models_lab/MODELS.md`.

## File layout produced (relative to `models_lab/models/`)

```
stt/
  {lang}/model.int8.onnx      # Indic (NEMO_CTC)
  {lang}/tokens.txt
  en/encoder.int8.onnx        # English (TRANSDUCER)
  en/decoder.int8.onnx
  en/joiner.int8.onnx
  en/tokens.txt
tts/
  {lang}/model.onnx           # MMS VITS (no espeak-ng-data needed)
  {lang}/tokens.txt
  en/model.onnx               # Piper VITS
  en/tokens.txt
  en/espeak-ng-data/          # Required by Piper
vad/
  silero_vad.onnx
```

## Device layout (app's filesDir)

```
/data/data/com.itantra.app/files/
  models/{lang}/stt/model.int8.onnx
  models/{lang}/stt/tokens.txt
  models/{lang}/tts/model.onnx
  models/{lang}/tts/tokens.txt
  models/en/stt/encoder.int8.onnx
  models/en/stt/decoder.int8.onnx
  models/en/stt/joiner.int8.onnx
  models/en/stt/tokens.txt
  models/en/tts/model.onnx
  models/en/tts/tokens.txt
  models/en/tts/espeak-ng-data/
  models/vad/silero_vad.onnx
```
