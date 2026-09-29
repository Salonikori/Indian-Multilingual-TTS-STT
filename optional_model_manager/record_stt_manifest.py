#!/usr/bin/env python3
"""Record already-downloaded STT model files into the download manifest.

Run this after cleaning up the duplicate nested directories (Stage 1).
Run from inside optional_model_manager/ with the venv active.
"""
from __future__ import annotations
import hashlib, json
from pathlib import Path

ROOT     = Path(__file__).resolve().parents[1]
LAB_ROOT = ROOT / "models_lab"

INDIC = ["hi", "ta", "bn", "mr", "gu", "kn", "te", "ml", "or"]
records: list[dict] = []

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

print("=== Recording STT manifest entries ===")

# Indic NeMo CTC
for lang in INDIC:
    base = LAB_ROOT / "models" / "stt" / lang
    for fname in ("model.int8.onnx", "tokens.txt"):
        p = base / fname
        if p.exists():
            digest = sha256(p)
            print(f"  [{lang}] {fname}: {p.stat().st_size:,} bytes  sha256={digest[:16]}...")
            records.append({
                "type": "stt", "lang": lang,
                "repo": "parismitaglobalsolutions/indicconformer-sherpa-onnx",
                "file": str(p.relative_to(LAB_ROOT)),
                "size_bytes": p.stat().st_size,
                "sha256": digest
            })
        else:
            print(f"  [{lang}] MISSING: {p}")

# English Transducer
en_base = LAB_ROOT / "models" / "stt" / "en"
for fname in ("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"):
    p = en_base / fname
    if p.exists():
        digest = sha256(p)
        print(f"  [en] {fname}: {p.stat().st_size:,} bytes  sha256={digest[:16]}...")
        records.append({
            "type": "stt", "lang": "en",
            "repo": "k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12",
            "file": str(p.relative_to(LAB_ROOT)),
            "size_bytes": p.stat().st_size,
            "sha256": digest
        })
    else:
        print(f"  [en] MISSING: {p}")

# Write manifest (STT-only for now; download_tts_vad.py will merge TTS+VAD)
manifest_path = LAB_ROOT / "results" / "download_manifest.json"
manifest_path.parent.mkdir(parents=True, exist_ok=True)
manifest_path.write_text(json.dumps({"records": records}, indent=2, ensure_ascii=False))
print(f"\nManifest written: {manifest_path}  ({len(records)} STT records)")
print("Next: run download_tts_vad.py to add TTS + VAD records.")
