# iTantra Development Tools

**Purpose**: Development-time tools for model management and testing corpus preparation  
**Compliance**: These tools operate outside the main application to maintain offline-only requirements

## Tools Overview

This directory contains development utilities that support the iTantra prototype by providing model download capabilities and testing infrastructure while maintaining the application's offline-only architecture.

### Model Management Tools

#### `download_whisper_model.py`
- **Function**: Downloads Whisper models for English STT integration
- **Target**: Optimized models suitable for mobile deployment
- **Output**: Model files compatible with Sherpa-ONNX integration
- **Usage**: `python tools/download_whisper_model.py`

#### `download_candidates.py`
- **Function**: Downloads various model candidates for evaluation
- **Purpose**: Enables comparison of different model architectures and sizes
- **Output**: Complete model sets for testing and validation
- **Usage**: `python tools/download_candidates.py`

### Testing Infrastructure Tools

#### `prepare_real_speech_corpus.py`
- **Function**: Creates real human speech test corpus for accuracy validation
- **Data Sources**: Google FLEURS (CC BY 4.0), Mozilla Common Voice (CC0 1.0)
- **Output**: Standardized test corpus with ground truth transcriptions
- **Usage**: `python tools/prepare_real_speech_corpus.py --dataset fleurs --languages hi en`

#### `test_corpus_creator.py`  
- **Function**: Generates mock test corpus for development testing
- **Purpose**: Enables pipeline testing without requiring large dataset downloads
- **Output**: Small synthetic corpus for continuous integration testing
- **Usage**: `python tools/test_corpus_creator.py`

## Installation and Requirements

### Dependencies Setup
```bash
# Install required Python dependencies
pip install -r tools/requirements.txt

# System requirements: Python 3.8+, ~2GB disk space for datasets
```

### Key Dependencies
- **librosa**: Audio processing and format conversion
- **soundfile**: Audio file I/O operations
- **datasets**: HuggingFace datasets integration for corpus download
- **jiwer**: Word Error Rate calculation utilities
- **sherpa-onnx**: STT model integration and testing

## Offline Compliance Architecture

### Development vs Runtime Separation
**Critical Design Principle**: Complete separation between development-time model acquisition and runtime application operation.

#### Development Phase (Internet Access)
- Model download and preparation using these tools
- Test corpus creation from public datasets
- Model validation and accuracy measurement
- Development testing and validation procedures

#### Runtime Phase (Offline Only)
- Application operates with pre-installed models only
- No network access permissions or capabilities
- Complete offline speech processing pipeline
- Local-only model loading and processing

### Data Flow Architecture
```
Development: Internet → Tools → Models/Corpus → Local Storage
Deployment: Local Storage → install_models.py → Android Device
Runtime: Android Device → Local Models Only (No Network)
```

## Usage Procedures

### Complete Model Preparation Workflow

#### Step 1: Model Download
```bash
# Download optimized STT models
python tools/download_whisper_model.py

# Download additional model candidates for evaluation
python tools/download_candidates.py
```

#### Step 2: Test Corpus Preparation
```bash
# Create real human speech test corpus
python tools/prepare_real_speech_corpus.py \
  --dataset fleurs \
  --languages hi en \
  --num-samples 30 \
  --output-dir models_lab/test_audio_real
```

#### Step 3: Model Validation
```bash
# Test STT accuracy with real speech corpus
python models_lab/test_stt.py \
  --references models_lab/test_audio_real/references.tsv \
  --audio-dir models_lab/test_audio_real \
  --model-type whisper \
  --language en
```

#### Step 4: Device Deployment  
```bash
# Deploy models to Android device
python install_models.py --languages hi,en --device-id TARGET_DEVICE
```

### Development Testing Workflow

#### Quick Testing with Mock Data
```bash
# Generate mock corpus (no internet required)
python tools/test_corpus_creator.py

# Test pipeline with synthetic data
python models_lab/test_stt.py \
  --references models_lab/test_audio_mock/references.tsv \
  --audio-dir models_lab/test_audio_mock \
  --model-type whisper \
  --language en
```

## File Structure Organization

### After Tool Execution
```
models_lab/
├── models/                   # Downloaded models (via download tools)
│   ├── stt/
│   │   ├── hi/              # Hindi STT models
│   │   └── en/              # English STT models
│   ├── tts/
│   │   ├── hi/              # Hindi TTS models  
│   │   └── en/              # English TTS models
│   └── vad/                 # Voice activity detection model
├── test_audio_real/         # Real speech corpus (via prepare_real_speech_corpus.py)
│   ├── hi/hi_001.wav, ...
│   ├── en/en_001.wav, ...
│   ├── references.tsv       # Ground truth transcriptions
│   └── dataset_info.json    # Attribution and metadata
├── test_audio_mock/         # Mock corpus (via test_corpus_creator.py)
└── results/                 # Test results and measurements
    ├── stt_hi_results.json
    └── stt_en_results.json
```

## Licensing and Attribution

### Dataset Licensing Compliance
- **Google FLEURS**: CC BY 4.0 license requiring attribution
- **Mozilla Common Voice**: CC0 1.0 / Public Domain license  
- **Model Sources**: MIT and Apache 2.0 licenses (OpenAI, sherpa-onnx)

### Attribution Requirements
Tools automatically generate proper attribution files and maintain license compliance:
- `dataset_info.json`: Contains required attribution information
- License files included with downloaded models
- Proper citation format generated for documentation

### Commercial Use Compliance
All tools download only openly licensed models and datasets suitable for commercial use by ISRO and other government applications.

## Integration with Main Project

### Development Workflow Integration
1. **Model Preparation**: Use tools to download and prepare models during development
2. **Testing and Validation**: Employ testing infrastructure for quality assurance
3. **Model Deployment**: Use deployment tools to transfer models to target devices  
4. **Runtime Operation**: Application operates independently with local models

### Continuous Integration Support
- **Automated Testing**: Mock corpus generation supports automated testing pipelines
- **Validation Framework**: Systematic testing procedures for quality assurance
- **Performance Monitoring**: Tools support performance regression testing
- **Compliance Verification**: Automated verification of offline-only operation

---

**Tools Summary**: Complete development infrastructure supporting iTantra model management while maintaining strict offline compliance requirements for the main application.

**Design Principle**: Enable comprehensive development and testing capabilities while ensuring production application operates in complete offline mode appropriate for ISRO operational requirements.