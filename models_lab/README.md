# iTantra models_lab — offline speech-model validation

This lab is deliberately separate from Android. It is designed to test real local ONNX models and real recorded WAVs before model selection.

## Status at creation

**No model is marked as validated or recommended for shipping yet.** This workspace does not bundle large third-party model files, and no real speech corpus has been supplied in `test_audio/`. The scripts report measurements only after a model and test corpus are provided. Never copy placeholder/example numbers into `MODELS.md`.

## Setup

Python 3.10 or 3.11 is recommended. Create a virtual environment, then:

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
python test_vad.py --help
python test_stt.py --help
python test_tts.py --help
```

The Python `sherpa-onnx` package and the model files must be compatible. Check the official current model catalogue and examples before downloading:
- https://github.com/k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/
- https://huggingface.co/models?library=sherpa-onnx

## Folder conventions

```text
models_lab/
  models/
    stt/
      hi/<model files>
      en/<model files>
    tts/
      hi/<model files>
      en/<model files>
    vad/silero_vad.onnx
  test_audio/
    hi/utt001.wav
    hi/utt001.txt
    en/utt001.wav
    en/utt001.txt
    references.tsv
  results/
  test_stt.py
  test_tts.py
  test_vad.py
  MODELS.md
```

For STT, either use `references.tsv` with columns `language<TAB>wav_path<TAB>reference`, or put one `.txt` file beside each WAV. Paths may be relative to this folder. Use one recording per utterance; avoid clipping and keep the reference transcript faithful to what was spoken.

## Important acceptance gates

1. Pin and record the exact model repository/revision and license before downloading.
2. Check the license for the model **and** its tokenizer/config/vocabulary and any required upstream checkpoint. A community conversion does not automatically inherit a permissive license.
3. Record all downloaded file sizes, not just the main ONNX file.
4. Run each model load check in this lab and record the result in `MODELS.md`.
5. Run held-out WAVs on the target laptop first, then repeat on actual low/mid-range Android devices before reporting phone performance.
6. Do not call a language “working” until both STT and TTS are validated for that language.
7. Model size >150 MB: do not ship by default. Investigate quantization/distillation or a smaller model and remeasure.

## Known limitations

- The inference and benchmark scripts do not access the network. Model acquisition is isolated in `../optional_model_manager/` and requires a separate environment and explicit user invocation.
- The STT runner supports sherpa-onnx offline transducer, CTC, and Whisper-style model layouts when their required files are supplied. See `--help`.
- TTS is wired for the sherpa-onnx VITS API shape; model families may require different configuration. If the selected release exposes a different API, update the adapter against that release's official example instead of silently treating it as successful.
- The VAD runner uses the sherpa-onnx Silero VAD API. This is segment detection on a local WAV, not a claim about microphone streaming latency.
- These scripts measure desktop/laptop performance only. Android CPU, RAM, APK size, idle CPU and end-to-end latency must be measured on the actual phones.


## Researched candidate and download helper

A community conversion candidate was found at:
https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx
Its model card documents Hindi plus other IndicConformer CTC language folders, and an English NeMo CTC model. It reports approximate sizes of ~190 MB per Indian-language model and ~170 MB for English, both over the 150 MB target. These are publisher estimates, not this lab's measured disk sizes. The model card attributes source licenses per family (AI4Bharat MIT; NVIDIA NeMo CC-BY-4.0), so preserve attribution and check exact revision/files before redistribution.

Optional online model acquisition is isolated in `../optional_model_manager/download_candidates.py`. It is the only project component that calls the Hugging Face Hub. To run it, create a separate environment in that directory and install `requirements.txt`; then run `python download_candidates.py` only after reviewing the exact candidate and license. The inference scripts in this folder do not download anything.

Smaller English candidate:
https://huggingface.co/k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12
Its model card lists an INT8 encoder around 72.9 MB plus decoder/joiner/token files and declares Apache-2.0. It is only a candidate; measure accuracy on your own recorded sentences.

The downloader intentionally does not fetch TTS or VAD automatically because a precise model revision, license and compatible file layout still need to be confirmed. Do not treat those missing artifacts as complete.
