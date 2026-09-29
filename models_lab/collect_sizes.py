#!/usr/bin/env python3
"""Collect measured file sizes and TTS RTF results for MODELS.md."""
import json, pathlib

BASE = pathlib.Path(__file__).resolve().parent / "models"
RESULTS = pathlib.Path(__file__).resolve().parent / "results"


def sz(p):
    p = pathlib.Path(p)
    return p.stat().st_size if p.exists() else 0


def mib(b):
    return round(b / 1024 / 1024, 1)


print("=== STT file sizes ===")
for lang in ["hi", "ta", "bn", "mr", "gu", "kn", "te", "ml", "or"]:
    m = sz(BASE / "stt" / lang / "model.int8.onnx")
    t = sz(BASE / "stt" / lang / "tokens.txt")
    print(f"  [{lang}] model={mib(m)} MiB  tokens={t} bytes")

for fname in ["encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"]:
    print(f"  [en] {fname}: {mib(sz(BASE / 'stt' / 'en' / fname))} MiB")

print()
print("=== TTS file sizes ===")
for lang in ["hi", "ml", "gu", "bn", "en"]:
    m = sz(BASE / "tts" / lang / "model.onnx")
    t = sz(BASE / "tts" / lang / "tokens.txt")
    print(f"  [{lang}] model={mib(m)} MiB  tokens={t} bytes")

print()
print("=== VAD ===")
print(f"  silero_vad.onnx: {mib(sz(BASE / 'vad' / 'silero_vad.onnx'))} MiB")

print()
print("=== TTS RTF results ===")
for lang in ["en", "hi"]:
    rp = RESULTS / "tts" / lang / "tts_results.json"
    if rp.exists():
        r = json.loads(rp.read_text(encoding="utf-8"))
        print(f"  [{lang}] mean_rtf={r['mean_rtf']:.3f}  max_rtf={r['max_rtf']:.3f}  sentences={r['sentences']}")
    else:
        print(f"  [{lang}] no result file found")
