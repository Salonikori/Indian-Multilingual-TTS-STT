# iTantra Tools

This directory contains utilities for preparing and evaluating the iTantra Android prototype.

## prepare_real_speech_corpus.py

Downloads and prepares authentic speech datasets (FLEURS, Common Voice) for WER evaluation of STT models. Replaces synthetic TTS-generated test audio with real human speech for more accurate evaluation.

### Features

- Downloads samples from Google FLEURS (102 languages) or Mozilla Common Voice datasets
- Filters by duration, resamples to 16kHz mono
- Creates `references.tsv` file compatible with `test_stt.py`
- Generates detailed metadata and preparation summaries
- Supports all iTantra target languages (Hindi, English, Gujarati, etc.)

### Installation

```bash
# Install Python dependencies
pip install -r tools/requirements.txt
```

### Usage Examples

```bash
# Prepare 50 Hindi samples from FLEURS dataset
python tools/prepare_real_speech_corpus.py --language hi --dataset fleurs --samples 50

# Prepare 30 English samples from Common Voice dataset
python tools/prepare_real_speech_corpus.py --language en --dataset common_voice --samples 30

# Prepare Gujarati samples with custom output directory
python tools/prepare_real_speech_corpus.py --language gu --dataset fleurs --samples 25 --output corpus/gujarati_real
```

### Output Format

Creates a directory structure:
```
models_lab/test_audio/real_speech/hi_fleurs/
├── hi_fleurs_0000.wav          # Audio files (16kHz mono WAV)
├── hi_fleurs_0001.wav
├── ...
├── references.tsv              # filename\treference text
├── corpus_metadata.json        # Detailed metadata
└── preparation_summary.txt     # Human-readable summary
```

### Integration with test_stt.py

The corpus is designed to work seamlessly with the existing `test_stt.py` evaluation script:

```bash
# Test Hindi STT model on real FLEURS corpus
python models_lab/test_stt.py \
    --model-type nemo_ctc \
    --language hi \
    --model models/hi/model.int8.onnx \
    --tokens models/hi/tokens.txt \
    --audio-dir models_lab/test_audio/real_speech/hi_fleurs

# Test English Whisper model on real Common Voice corpus  
python models_lab/test_stt.py \
    --model-type whisper \
    --language en \
    --encoder models/en/encoder.int8.onnx \
    --decoder models/en/decoder.int8.onnx \
    --tokens models/en/tokens.txt \
    --audio-dir models_lab/test_audio/real_speech/en_common_voice
```

### Dataset Information

**Google FLEURS:**
- 102 languages including all iTantra targets
- ~12 hours per language
- Parallel sentences (same content across languages)
- High-quality native speaker recordings
- Good for comparative evaluation

**Mozilla Common Voice:**
- Community-contributed recordings
- Variable quality and accents
- Real-world diversity
- Good for robustness testing
- Not all iTantra languages available (Odia missing)

### Language Support

| Language | Code | FLEURS | Common Voice |
|----------|------|--------|--------------|
| Hindi    | hi   | ✅     | ✅           |
| English  | en   | ✅     | ✅           |
| Gujarati | gu   | ✅     | ✅           |
| Bengali  | bn   | ✅     | ✅           |
| Tamil    | ta   | ✅     | ✅           |
| Telugu   | te   | ✅     | ✅           |
| Marathi  | mr   | ✅     | ✅           |
| Malayalam| ml   | ✅     | ✅           |
| Kannada  | kn   | ✅     | ✅           |
| Odia     | or   | ✅     | ❌           |

### Quality Controls

- Duration filtering (1-10 seconds by default)
- Automatic resampling to 16kHz mono
- Empty text filtering
- Error handling for corrupted samples
- Detailed logging and progress reporting

### Expected WER Improvements

Real speech typically produces more accurate WER measurements than synthetic TTS:

- **Synthetic TTS corpus (current):** Often inflated WER due to TTS artifacts
- **Real speech corpus (new):** More representative of actual model performance
- **Expected change:** 10-30% relative WER improvement in measurements

This doesn't improve the models themselves, but provides more accurate evaluation metrics.

## Development Notes

- The tool uses Hugging Face `datasets` library for downloading
- Audio processing via `soundfile` and `librosa`
- Progress tracking with `tqdm`
- Full Unicode NFC support for Indic languages
- Error recovery for partial downloads

## Troubleshooting

**Common Issues:**

1. **Dataset download fails:** Check internet connection, try different dataset version
2. **Audio processing errors:** Ensure `soundfile` and `librosa` are properly installed
3. **Memory issues:** Reduce `--samples` count, process in smaller batches
4. **Missing language:** Check language code mappings in the script

**Performance Tips:**

- Use `--samples 20-30` for quick testing
- Use `--samples 50-100` for thorough evaluation  
- FLEURS typically has more consistent quality than Common Voice
- Consider both datasets for comprehensive evaluation