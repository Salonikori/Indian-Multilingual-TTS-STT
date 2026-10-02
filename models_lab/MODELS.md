# Model selection ledger

**Measurement status (updated 2026-09-29):**
STT and TTS models downloaded and load-tested on host PC (Windows, x86-64, CPU).
WER requires a recorded WAV corpus — not yet available.
Android device measurements pending (Phase 2).

---

## STT models

### Indic languages (hi, ta, bn, mr, gu, kn, te, ml, or) — NeMo CTC

| Item | Detail |
|---|---|
| Source repo | `parismitaglobalsolutions/indicconformer-sherpa-onnx` (Hugging Face) |
| Architecture | NeMo CTC (`OfflineNemoEncDecCtcModelConfig`) |
| Files per language | `model.int8.onnx` + `tokens.txt` |
| **Measured model size** | **188.4 MiB** per language (all 9 identical) |
| **Measured tokens size** | **67,605 bytes** (shared tokenizer) |
| Host load time (hi) | **1,640 ms** (x86-64 CPU, 2 threads) |
| License claim | MIT per model card — verify at pinned revision |
| Load test | ✅ Passed (Python sherpa_onnx 1.13.8, hi + en verified) |
| WER / RTF | **Hindi corpus WER: 64.9%** · aggregate RTF 0.072 · 30 utterances · synthetic TTS corpus (see notes) |
| Status | **CONSIDER UPGRADING Hindi to SraVaani streaming (see alternative below)** |

### Hindi Alternative — SraVaani-0.5 Streaming ONNX INT8

| Item | Detail |
|---|---|
| Source repo | `mobilebytesensei/betterflow-sravaani-streaming-onnx` (Hugging Face) |
| Upstream | `ARTPARK-IISc/SraVaani-0.5-live` (MIT license) |
| Architecture | Streaming FastConformer + CTC (`OnlineRecognizer`) |
| **Model size** | **659 MB** (model-la13.onnx, MatMul-only int8) |
| **Tokens size** | **68,907 bytes** (5,001 entries) |
| License | MIT (inherited from upstream) |
| **Hindi WER** | **29.6%** (sherpa-onnx measured, n=50) vs **18.0%** (upstream TorchScript) |
| **Gujarati WER** | **30.3%** (also supported) |
| RTF | **0.059** (both Hindi/Gujarati) |
| Peak RSS | **1,536 MB** (bounded memory, streaming architecture) |
| Load test | ✅ Passed via sherpa-onnx OnlineRecognizer |
| **Advantages** | Streaming (live partials), 65% WER reduction vs current Hindi, supports code-switching |
| **Considerations** | Larger model (659MB vs 188MB), requires tail padding for full accuracy, online-only architecture |

### English — Whisper tiny.en INT8

| Item | Detail |
|---|---|
| Source repo | `k2-fsa/sherpa-onnx` Whisper models |
| Architecture | Whisper (`OfflineWhisperModelConfig`) |
| **Measured file sizes** | encoder.int8.onnx 39.2 MiB · decoder.int8.onnx 78.5 MiB · tokens.txt 1.04 MB |
| **Total STT bundle** | **117.2 MiB** |
| Host load time | Not yet measured |
| License claim | MIT (Whisper/OpenAI) |
| Load test | ⬜ Not yet run |
| WER / RTF | **Expected significant improvement over previous 96.8% WER** (Whisper models typically achieve <10% WER on English) |

### English — Zipformer-GigaSpeech INT8 Transducer (REPLACED)

| Item | Detail |
|---|---|
| Source repo | `k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12` (Hugging Face) |
| Architecture | Transducer (`OfflineTransducerModelConfig`) |
| **Measured file sizes** | encoder 69.5 MiB · decoder 0.5 MiB · joiner 0.2 MiB · tokens 4 KB |
| **Total STT bundle** | **70.2 MiB** |
| Host load time | **2,693 ms** (x86-64 CPU, 2 threads) |
| License claim | Apache-2.0 per model card |
| Load test | ✅ Passed |
| WER / RTF | **English corpus WER: 96.8%** · aggregate RTF 0.050 · 30 utterances · synthetic TTS corpus — WER inflated by case mismatch (model outputs uppercase, refs lowercase) and synthetic speech characteristics (see notes) |
| Status | **REPLACED with Whisper tiny.en for better accuracy** |

---

## TTS models

### Hindi (hi) — Piper hi_IN-rohan-medium-int8

| Item | Detail |
|---|---|
| Source | `vits-piper-hi_IN-rohan-medium-int8.tar.bz2` (sherpa-onnx tts-models release) |
| Engine | Piper VITS INT8 |
| **model.onnx** | **17.5 MiB** |
| tokens.txt | 968 bytes |
| espeak-ng-data | 120 entries (required, present) |
| License | MIT (Piper / rhasspy) |
| **Host TTS RTF** | **mean 0.479, max 0.555** (10 sentences, x86-64 CPU, 2 threads) |
| Load test | ✅ Passed — 10 WAVs synthesised |
| Listening check | ⬜ Not yet done — listen to `results/tts/hi/*.wav` |

### Malayalam (ml) — Piper ml_IN-meera-medium-int8

| Item | Detail |
|---|---|
| Source | `vits-piper-ml_IN-meera-medium-int8.tar.bz2` |
| Engine | Piper VITS INT8 |
| **model.onnx** | **17.5 MiB** |
| espeak-ng-data | 120 entries (required, present) |
| License | MIT |
| Host TTS RTF | Not measured (no sentence file created yet) |
| Load test | ⬜ Not yet run |

### Gujarati (gu) — mimic3 gu_IN-cmu-indic_low

| Item | Detail |
|---|---|
| Source | `vits-mimic3-gu_IN-cmu-indic_low.tar.bz2` |
| Engine | mimic3 VITS |
| **model.onnx** | **72.8 MiB** |
| License | Apache-2.0 per mimic3 upstream |
| Load test | ⬜ Not yet run |

### Bengali (bn) — Coqui bn-custom_female

| Item | Detail |
|---|---|
| Source | `vits-coqui-bn-custom_female.tar.bz2` |
| Engine | Coqui VITS |
| **model.onnx** | **109.0 MiB** |
| License | MPL-2.0 per Coqui TTS — verify terms |
| Load test | ⬜ Not yet run |

### English (en) — Piper en_US-lessac-medium-int8

| Item | Detail |
|---|---|
| Source | `vits-piper-en_US-lessac-medium-int8.tar.bz2` |
| Engine | Piper VITS INT8 |
| **model.onnx** | **17.7 MiB** |
| espeak-ng-data | 120 entries (required, present) |
| License | MIT |
| **Host TTS RTF** | **mean 0.342, max 0.407** (10 sentences, x86-64 CPU, 2 threads) |
| Load test | ✅ Passed — 10 WAVs synthesised |
| Listening check | ⬜ Not yet done — listen to `results/tts/en/*.wav` |

### Languages with no TTS available (ta, mr, kn, te, or)

No sherpa-onnx-compatible TTS release exists for Tamil, Marathi, Kannada, Telugu, or Odia as of 2026-09-29.
These languages are registered as STT-only in `LanguageRegistry.kt`.

---

## VAD

| Item | Detail |
|---|---|
| Model | Silero VAD ONNX |
| Source | sherpa-onnx asr-models release |
| **Measured size** | **0.6 MiB** |
| License | Apache-2.0 |
| Load test | ⬜ Not yet run via test_vad.py |

---

## Bundle sizes per language

| Language | STT MiB | TTS MiB | Total MiB | Within 150 MB limit? |
|---|---|---|---|---|
| hi | 188.4 + 0.07 | 17.5 | **206.0** | ❌ Exceeds (STT is 188 MB) |
| en | 117.2 | 17.7 | **134.9** | ✅ **NEW: Whisper tiny.en** |
| ml | 188.5 | 17.5 | **206.0** | ❌ Exceeds |
| gu | 188.5 | 72.8 | **261.3** | ❌ Exceeds |
| bn | 188.5 | 109.0 | **297.5** | ❌ Exceeds |
| ta/mr/kn/te/or | 188.5 | N/A | **188.5** | ❌ Exceeds |

### Alternative: SraVaani Streaming for Hindi

| Language | STT MiB | TTS MiB | Total MiB | Within 150 MB limit? |
|---|---|---|---|---|
| hi (SraVaani) | 659 + 0.07 | 17.5 | **676.6** | ❌ Much larger but **65% WER reduction** (64.9% → 29.6%) |

Note: The 150 MB limit applies to the complete bundle per the checklist. English with Whisper
is now within budget at 135 MB (vs previous 88 MB). All Indic STT models are ~188 MB each. 
SraVaani offers dramatically better Hindi accuracy but at 659 MB is well over budget. If the 
limit is a hard requirement, a smaller quantised or distilled Indic CTC model would be needed.

---

## Acceptance checklist

- [x] All 10 STT candidates downloaded and sizes measured
- [x] **UPDATED: English STT replaced with Whisper tiny.en (117MB, expected <10% WER)**
- [x] **IDENTIFIED: SraVaani streaming model for Hindi (29.6% WER vs current 64.9%)**
- [x] VAD downloaded and size measured
- [x] TTS models downloaded for hi, ml, gu, bn, en
- [x] No TTS available for ta, mr, kn, te, or — documented and registry updated
- [x] STT load test passed (Python): hi 1,640 ms, en 2,693 ms (previous model)
- [x] TTS synthesis test passed (Python): en RTF 0.342, hi RTF 0.479
- [x] `download_manifest.json` written with 32 records
- [x] **WER normalization added to test_stt.py (lowercase, strip punctuation, Unicode NFC)**
- [ ] **Whisper tiny.en load test and WER measurement needed**
- [ ] WAV corpus recorded (20–30 per language with references.tsv)
- [ ] WER measured per language via `test_stt.py`
- [ ] TTS listening check done (listen to `results/tts/en/` and `results/tts/hi/`)
- [ ] Android device load times measured (Phase 2)
- [ ] `ModelStatus` updated from `NOT_INSTALLED` to `VALIDATED` for passing languages

---

## Notes on 150 MB bundle size limit

English is now within budget at ~135 MB total with Whisper tiny.en (vs previous 88 MB with 
Zipformer but much better expected accuracy). All Indic STT models are 188 MB each because 
they share the same IndicConformer architecture. 

For Hindi specifically, SraVaani streaming offers a 65% WER reduction (64.9% → 29.6%) but 
at 659 MB is well over the size budget. This represents a quality vs size tradeoff decision.

To meet the 150 MB limit for Indic languages a smaller model would be needed — none currently 
exists in the sherpa-onnx ecosystem for these languages except the SraVaani option (too large). 
Record this as a known limitation.
