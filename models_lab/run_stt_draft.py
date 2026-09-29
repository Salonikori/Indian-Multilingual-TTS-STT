#!/usr/bin/env python3
"""
Run STT on all WAV files in test_audio/hi/ and test_audio/en/ and produce:
  1. results/stt_hi_draft.json  — per-file hypothesis with RTF
  2. results/stt_en_draft.json  — per-file hypothesis with RTF
  3. test_audio/references_DRAFT.tsv — TSV with model hypotheses as placeholders
                                       (YOU MUST correct these with real transcripts)

The draft TSV can be edited and renamed to references.tsv before running WER.
"""
from __future__ import annotations
import json, time, pathlib
import numpy as np
import soundfile as sf
import sherpa_onnx

ROOT   = pathlib.Path(__file__).resolve().parent
MODELS = ROOT / "models"
AUDIO  = ROOT / "test_audio"
RESULTS = ROOT / "results"
RESULTS.mkdir(exist_ok=True)

LANGS = {
    "hi": {
        "type": "nemo_ctc",
        "model": str(MODELS / "stt/hi/model.int8.onnx"),
        "tokens": str(MODELS / "stt/hi/tokens.txt"),
    },
    "en": {
        "type": "transducer",
        "encoder": str(MODELS / "stt/en/encoder.int8.onnx"),
        "decoder": str(MODELS / "stt/en/decoder.int8.onnx"),
        "joiner":  str(MODELS / "stt/en/joiner.int8.onnx"),
        "tokens":  str(MODELS / "stt/en/tokens.txt"),
    },
}


def make_recognizer(cfg: dict) -> sherpa_onnx.OfflineRecognizer:
    if cfg["type"] == "nemo_ctc":
        return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=cfg["model"], tokens=cfg["tokens"],
            num_threads=2, provider="cpu", decoding_method="greedy_search")
    else:
        return sherpa_onnx.OfflineRecognizer.from_transducer(
            encoder=cfg["encoder"], decoder=cfg["decoder"],
            joiner=cfg["joiner"], tokens=cfg["tokens"],
            num_threads=2, provider="cpu", decoding_method="greedy_search")


def transcribe(rec: sherpa_onnx.OfflineRecognizer, wav_path: pathlib.Path) -> tuple[str, float, float]:
    audio, sr = sf.read(str(wav_path), dtype="float32", always_2d=False)
    if audio.ndim == 2:
        audio = audio.mean(axis=1)
    if sr != 16000:
        raise ValueError(f"{wav_path.name}: sample rate {sr} != 16000")
    duration = len(audio) / sr
    stream = rec.create_stream()
    stream.accept_waveform(sr, audio)
    t0 = time.perf_counter()
    rec.decode_stream(stream)
    elapsed = time.perf_counter() - t0
    text = stream.result.text.strip()
    rtf = elapsed / duration if duration > 0 else 0.0
    return text, duration, rtf


tsv_rows = ["language\twav_path\treference"]
all_results: dict[str, list] = {}

for lang, cfg in LANGS.items():
    wav_dir = AUDIO / lang
    wavs    = sorted(wav_dir.glob("*.wav"))
    if not wavs:
        print(f"[{lang}] No WAV files found in {wav_dir}")
        continue

    print(f"\n[{lang}] Loading model ...")
    t_load = time.perf_counter()
    rec = make_recognizer(cfg)
    load_ms = int((time.perf_counter() - t_load) * 1000)
    print(f"[{lang}] Model loaded in {load_ms} ms. Transcribing {len(wavs)} files ...")

    rows = []
    total_audio = 0.0
    total_infer = 0.0

    for wav in wavs:
        text, dur, rtf = transcribe(rec, wav)
        total_audio += dur
        total_infer += rtf * dur
        rows.append({
            "file": wav.name,
            "wav_path": str(wav.relative_to(ROOT)),
            "hypothesis": text,
            "duration_s": round(dur, 3),
            "rtf": round(rtf, 4),
        })
        print(f"  {wav.name} ({dur:.1f}s, RTF={rtf:.3f}): {text}")
        # Add to TSV draft (hypothesis as placeholder for reference)
        rel = str(wav.relative_to(AUDIO.parent)).replace("\\", "/")
        tsv_rows.append(f"{lang}\t{rel}\t{text}")

    agg_rtf = total_infer / total_audio if total_audio > 0 else 0
    summary = {
        "language": lang,
        "model_type": cfg["type"],
        "utterances": len(rows),
        "model_load_ms": load_ms,
        "total_audio_s": round(total_audio, 2),
        "aggregate_rtf": round(agg_rtf, 4),
        "note": "Host PC (x86-64 CPU). WER requires corrected references.tsv.",
        "rows": rows,
    }
    out = RESULTS / f"stt_{lang}_draft.json"
    out.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n[{lang}] Aggregate RTF: {agg_rtf:.3f}  Saved: {out.name}")
    all_results[lang] = rows
    del rec  # release model memory before loading next

# Write draft TSV
tsv_path = AUDIO / "references_DRAFT.tsv"
tsv_path.write_text("\n".join(tsv_rows), encoding="utf-8")
print(f"\nDraft TSV written: {tsv_path}")
print("ACTION REQUIRED: Open references_DRAFT.tsv, replace each model hypothesis")
print("with the correct human reference transcript, then rename to references.tsv")
print("and run test_stt.py to measure real WER.")
