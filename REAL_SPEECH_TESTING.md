# Real Human Speech WER Testing Guide

## ⚠️ CRITICAL: Current WER Claims Are Invalid

**All current WER measurements in this project are based on synthetic TTS-generated audio, not real human speech. These numbers (64.9% Hindi, 96.8% English) are INVALID for production assessment.**

## Why Real Speech Testing Is Required

1. **Circular Validation Problem**: Using TTS to generate test audio, then testing STT on that same TTS output, creates artificial validation that doesn't represent real-world performance
2. **Production Readiness**: Real applications must handle human speech patterns, accents, background noise, and natural speech variations
3. **Honest Assessment**: Professional development requires honest performance metrics

## Step-by-Step Real Speech Testing Procedure

### Prerequisites

```bash
# Install required dependencies
pip install -r tools/requirements.txt

# Ensure you have ~2GB disk space and stable internet connection
# Requires Python 3.8+ and preferably a Unix-like environment
```

### Step 1: Create Real Speech Corpus

```bash
# Navigate to project root
cd iTantra-android-smoke/

# Create real human speech test corpus (Hindi + English)
python tools/prepare_real_speech_corpus.py \
  --dataset fleurs \
  --languages hi en \
  --num-samples 30 \
  --output-dir models_lab/test_audio_real

# This downloads ~30 short audio clips per language from Google FLEURS
# License: CC BY 4.0 (Google) - attribution required
```

### Step 2: Test Hindi STT Model

```bash
# Test Hindi model on real speech
python models_lab/test_stt.py \
  --references models_lab/test_audio_real/references.tsv \
  --audio-dir models_lab/test_audio_real \
  --language hi \
  --model-type nemo_ctc \
  --model-dir models_lab/models/stt/hi \
  --output models_lab/results/stt_hi_real_speech.json
```

### Step 3: Test English STT Model  

```bash
# Test English model on real speech
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

### Step 4: Update Documentation

After obtaining real measurements:

1. **Update result files**: Replace `models_lab/results/stt_*_real_speech.json` with actual measurements
2. **Update documentation**: Replace "NOT MEASURED" claims in `DEPLOYMENT_CHECKLIST.md`, `DEMO_SCRIPT.md` with actual WER percentages  
3. **Attribution**: Add dataset attribution to documentation (Google FLEURS CC BY 4.0)
4. **Commit results**: Commit the real measurement files and updated docs

## Expected Outcomes

### Realistic WER Expectations

**Real human speech WER is typically HIGHER than synthetic corpus WER because:**

- Human speech has natural variations, hesitations, accents
- Background noise and recording quality variations  
- Model domain mismatch (training vs. real speech patterns)

**Conservative estimates for iTantra models:**
- **Hindi WER**: Likely 70-85% (worse than synthetic 64.9%)
- **English WER**: Likely >95% (English model has known issues)

### Production Assessment Criteria

**Production-ready STT typically requires:**
- **WER < 30%** for general use
- **WER < 15%** for critical applications  
- **WER < 10%** for professional transcription

**Current iTantra models may NOT meet production criteria** - this testing will provide honest assessment.

## File Locations After Testing

```
models_lab/
├── test_audio_real/              # Real human speech corpus
│   ├── hi/hi_001.wav, hi_002.wav, ...
│   ├── en/en_001.wav, en_002.wav, ...  
│   ├── references.tsv            # Ground truth transcriptions
│   └── dataset_info.json         # Source attribution
├── results/
│   ├── stt_hi_real_speech.json   # Real Hindi WER measurements
│   └── stt_en_real_speech.json   # Real English WER measurements
```

## Current Status Files

- `models_lab/results/stt_hi_real_speech.json` - Placeholder (NOT_MEASURED)
- `models_lab/results/stt_en_real_speech.json` - Placeholder (NOT_MEASURED)

**These files currently contain "NOT_MEASURED" and must be replaced with actual test results.**

## License and Attribution Requirements

**Google FLEURS Dataset (CC BY 4.0)**:
- Must attribute: "Test data from Google FLEURS dataset (Conneau et al., 2022), licensed under CC BY 4.0"
- Must include dataset citation in any publications or reports
- Commercial use allowed with proper attribution

## Integration with Project Workflow

1. **Before claiming production readiness**: Complete this real speech testing
2. **Before ISRO demo**: Have honest WER numbers from real speech
3. **Before deployment**: Ensure models meet minimum WER thresholds for intended use case
4. **Documentation updates**: Replace all "NOT MEASURED" claims with actual results

---

**Bottom Line**: This project currently demonstrates functional architecture but lacks validated performance metrics. Real speech testing is essential before any production or deployment claims.