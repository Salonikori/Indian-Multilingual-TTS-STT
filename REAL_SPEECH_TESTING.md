# Real Human Speech Testing Framework

## Current Testing Status: NOT COMPLETED

**IMPORTANT**: No speech recognition accuracy measurements have been completed using real human speech. All performance assessments require this testing before any production or deployment decisions.

## Purpose and Requirements

### Why Real Speech Testing Is Critical

This framework addresses the fundamental requirement that speech recognition systems must be validated on actual human speech patterns rather than synthetic or development data:

1. **Realistic Performance Assessment**: Real speech includes natural variations, accents, hesitations, and background conditions
2. **Production Readiness Validation**: Deployment decisions require honest accuracy measurements
3. **ISRO Mission Requirements**: Space operations demand verified communication system reliability

### Testing Framework Overview

The testing infrastructure is implemented but requires execution to generate actual measurements:

```
tools/prepare_real_speech_corpus.py  # Corpus preparation (ready to use)
models_lab/test_stt.py              # STT accuracy testing (ready to use)
models_lab/results/                 # Results storage (contains NOT_MEASURED placeholders)
```

## Testing Procedure (Ready to Execute)

### Prerequisites

```bash
# Python environment setup
pip install -r tools/requirements.txt

# System requirements: ~2GB disk space, internet connection for dataset download
# Requires Python 3.8+ and preferably Unix-like environment
```

### Step 1: Real Speech Corpus Creation

```bash
# From project root directory
cd iTantra-android-smoke/

# Download and prepare real human speech samples
python tools/prepare_real_speech_corpus.py \
  --dataset fleurs \
  --languages hi en \
  --num-samples 30 \
  --output-dir models_lab/test_audio_real

# Creates ~30 audio clips per language from Google FLEURS dataset
# License: CC BY 4.0 - attribution requirements noted below
```

### Step 2: Hindi Model Testing

```bash
# Execute Hindi STT accuracy measurement
python models_lab/test_stt.py \
  --references models_lab/test_audio_real/references.tsv \
  --audio-dir models_lab/test_audio_real \
  --language hi \
  --model-type nemo_ctc \
  --model-dir models_lab/models/stt/hi \
  --output models_lab/results/stt_hi_real_speech.json
```

### Step 3: English Model Testing

```bash
# Execute English STT accuracy measurement
python models_lab/test_stt.py \
  --references models_lab/test_audio_real/references.tsv \
  --audio-dir models_lab/test_audio_real \
  --language en \
  --model-type whisper_int8 \
  --encoder models_lab/models/stt/en/encoder.int8.onnx \
  --decoder models_lab/models/stt/en/decoder.int8.onnx \
  --tokens models_lab/models/stt/en/tokens.txt \
  --output models_lab/results/stt_en_real_speech.json
```

### Step 4: Results Integration

Upon completion of testing:

1. **Verify measurements**: Check `models_lab/results/stt_*_real_speech.json` contain actual results
2. **Update documentation**: Replace "NOT MEASURED" claims with actual accuracy percentages
3. **Add attributions**: Include dataset attribution (Google FLEURS CC BY 4.0)
4. **Assessment**: Compare results against requirements for intended use case

## Expected Testing Outcomes

### Realistic Performance Expectations

Real human speech recognition accuracy typically differs from development estimates due to:

- Natural speech variations and speaker accents
- Recording quality and background noise variations
- Model domain adaptation between training and real-world conditions

### Production Assessment Framework

**Accuracy thresholds for different use cases:**
- **Mission-critical applications**: >90% accuracy required
- **General communication**: >85% accuracy acceptable
- **Development/testing**: >70% accuracy demonstrates viability

**Current model readiness assessment requires actual measurements to determine fitness for intended ISRO use case.**

## File Structure After Testing

```
models_lab/
├── test_audio_real/              # Real human speech test corpus
│   ├── hi/hi_001.wav, hi_002.wav, ...
│   ├── en/en_001.wav, en_002.wav, ...
│   ├── references.tsv            # Ground truth transcriptions
│   └── dataset_info.json         # Source attribution information
├── results/
│   ├── stt_hi_real_speech.json   # Hindi accuracy measurements
│   └── stt_en_real_speech.json   # English accuracy measurements
```

## Current Placeholder Files

**Important**: The following files currently contain "NOT_MEASURED" placeholders:
- `models_lab/results/stt_hi_real_speech.json`
- `models_lab/results/stt_en_real_speech.json`

These placeholders must be replaced with actual measurement results before any accuracy claims can be made.

## Dataset Attribution Requirements

**Google FLEURS Dataset (CC BY 4.0 License):**
- Attribution: "Test audio from Google FLEURS dataset (Conneau et al., 2022), licensed under CC BY 4.0"
- Citation required in documentation and reports
- Commercial usage permitted with proper attribution

## Integration with Development Workflow

### Before Production Claims
- Complete real speech accuracy testing
- Verify models meet accuracy requirements for intended use
- Document actual performance with proper attribution

### Before ISRO Demonstration  
- Execute testing framework to obtain honest accuracy measurements
- Ensure documentation reflects actual rather than estimated performance
- Prepare realistic performance expectations based on measurements

### Before Deployment
- Validate accuracy meets mission requirements
- Complete comprehensive testing across expected usage conditions
- Document limitations and operational parameters based on test results

---

**Current Status**: Testing framework implemented and ready for execution. Actual speech recognition accuracy measurements must be completed before any performance claims or deployment decisions.