# iTantra Model Tools

This directory contains development-time tools for downloading and preparing models and datasets for the iTantra project. These tools are **outside** the main app directory to comply with the offline-only requirement.

## Tools Overview

### Model Download Tools

**`download_whisper_model.py`**
- Downloads Whisper tiny.en INT8 model for English STT
- Replaces previous streaming model that had 97% WER due to architecture mismatch  
- Expected WER: <10% with proper offline Whisper configuration
- Usage: `python tools/download_whisper_model.py`

### Dataset Preparation Tools

**`prepare_real_speech_corpus.py`**  
- Creates real human speech test corpus to replace TTS-generated synthetic audio
- Downloads from Google FLEURS (CC BY 4.0) or Mozilla Common Voice (CC0 1.0)
- Generates proper WER test set with ~30 samples per language (Hindi, English)
- Usage: `python tools/prepare_real_speech_corpus.py --dataset fleurs --languages hi en`

**`test_corpus_creator.py`**
- Creates small mock corpus for testing the pipeline without downloading large datasets
- Useful for development and CI testing
- Usage: `python tools/test_corpus_creator.py`

## Installation

```bash
# Install required dependencies
pip install -r tools/requirements.txt

# Note: Requires Python 3.8+ and ~2GB disk space for datasets
```

## Key Dependencies

- `librosa` - Audio processing and resampling to 16kHz mono
- `soundfile` - WAV file I/O
- `datasets` - HuggingFace datasets library for FLEURS/Common Voice
- `jiwer` - WER calculation (also used by models_lab/test_stt.py)
- `sherpa-onnx` - STT model testing

## Compliance Notes

**Offline Requirement:** These tools run at development time only. The main iTantra app (under `app/`) has no INTERNET permission and cannot access these datasets at runtime.

**Licensing:** All tools only download open-source datasets:
- FLEURS: CC BY 4.0 (Google)
- Common Voice: CC0 1.0 / Public Domain (Mozilla)
- Whisper models: MIT (OpenAI via sherpa-onnx)

**Data Flow:**
1. Development time: Download models/datasets with these tools
2. Install models: Use `optional_model_manager/install_models.py` to push to device  
3. Runtime: App uses only local models, no network access

## Usage Examples

### Complete Model Setup Workflow

```bash
# 1. Download English STT model
python tools/download_whisper_model.py

# 2. Create real speech test corpus  
python tools/prepare_real_speech_corpus.py --dataset fleurs --languages hi en --num-samples 30

# 3. Test WER on real speech
python models_lab/test_stt.py --references models_lab/test_audio_real/references.tsv \
  --audio-dir models_lab/test_audio_real --model-type whisper --language en \
  --encoder models_lab/models/stt/en/encoder.int8.onnx \
  --decoder models_lab/models/stt/en/decoder.int8.onnx \
  --tokens models_lab/models/stt/en/tokens.txt

# 4. Install models on Android device
python optional_model_manager/install_models.py
```

### Quick Testing with Mock Data

```bash
# Create mock corpus (no internet needed)
python tools/test_corpus_creator.py

# Test pipeline with mock data
python models_lab/test_stt.py --references models_lab/test_audio_real_mock/references.tsv \
  --audio-dir models_lab/test_audio_real_mock --model-type whisper --language en \
  --encoder models_lab/models/stt/en/encoder.int8.onnx \
  --decoder models_lab/models/stt/en/decoder.int8.onnx \
  --tokens models_lab/models/stt/en/tokens.txt
```

## Directory Structure After Running Tools

```
models_lab/
├── models/
│   └── stt/en/               # Downloaded by download_whisper_model.py
│       ├── encoder.int8.onnx
│       ├── decoder.int8.onnx
│       └── tokens.txt
├── test_audio/               # Original synthetic corpus (keep for smoke tests)
├── test_audio_real/          # Real speech corpus (created by prepare_real_speech_corpus.py)
│   ├── hi/hi_001.wav, ...
│   ├── en/en_001.wav, ...
│   ├── references.tsv
│   └── dataset_info.json
└── results/                  # WER test results
    └── stt_results.json
```