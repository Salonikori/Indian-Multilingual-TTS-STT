#!/usr/bin/env python3
"""Download only the TTS and VAD models — STT files are already present.

Based on actual tts-models release contents (checked 2026-09-29):
- Hindi:   vits-piper-hi_IN-rohan-medium-int8  (Piper, ~20 MB, MIT)
- Malayalam: vits-piper-ml_IN-meera-medium-int8 (Piper, ~20 MB, MIT)
- Gujarati: vits-mimic3-gu_IN-cmu-indic_low    (mimic3, ~76 MB)
- Bengali:  vits-coqui-bn-custom_female          (Coqui, ~108 MB)
- Tamil, Marathi, Kannada, Telugu, Odia: NO TTS available in sherpa-onnx releases
- English:  vits-piper-en_US-lessac-medium-int8  (Piper, ~21 MB, MIT)
- VAD:      silero_vad.onnx                      (Apache-2.0, 0.6 MB)

Run from inside optional_model_manager/ with the venv active.
"""
from __future__ import annotations
import hashlib, json, shutil, sys, tarfile
from pathlib import Path
import urllib.request

ROOT     = Path(__file__).resolve().parents[1]
LAB_ROOT = ROOT / "models_lab"

MMS_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
VAD_URL  = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

# Actual available TTS models per language (verified against tts-models release 2026-09-29)
# Format: lang_code -> (archive_name, inner_dir_prefix, model_onnx_glob)
TTS_MODELS = {
    # Piper models (include espeak-ng-data/)
    "hi": ("vits-piper-hi_IN-rohan-medium-int8.tar.bz2",   "vits-piper-hi_IN-rohan-medium", "hi_IN-rohan-medium.onnx"),
    "ml": ("vits-piper-ml_IN-meera-medium-int8.tar.bz2",   "vits-piper-ml_IN-meera-medium", "ml_IN-meera-medium.onnx"),
    # mimic3 model (no espeak-ng-data, uses its own data dir)
    "gu": ("vits-mimic3-gu_IN-cmu-indic_low.tar.bz2",      "vits-mimic3-gu_IN-cmu-indic_low", "en_US-cmu-indic-low.onnx"),
    # Coqui model
    "bn": ("vits-coqui-bn-custom_female.tar.bz2",           "vits-coqui-bn-custom_female", "bn-custom_female.onnx"),
    # English Piper
    "en": ("vits-piper-en_US-lessac-medium-int8.tar.bz2",  "vits-piper-en_US-lessac-medium", "en_US-lessac-medium.onnx"),
}
# Languages with NO available TTS in sherpa-onnx releases
NO_TTS_LANGS = ["ta", "mr", "kn", "te", "or"]

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def download(url: str, dest: Path) -> None:
    print(f"  Downloading {url.split('/')[-1]} ...")
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url) as resp, dest.open("wb") as out:
        shutil.copyfileobj(resp, out)
    print(f"  Saved {dest.name} ({dest.stat().st_size:,} bytes)")

def extract_tar(archive: Path, dest_dir: Path) -> None:
    print(f"  Extracting {archive.name} ...")
    with tarfile.open(archive) as tf:
        tf.extractall(dest_dir)

records: list[dict] = []

# ── TTS models ───────────────────────────────────────────────────────────────
print("\n=== TTS models ===")
for lang, (archive_name, inner_dir, model_onnx_name) in TTS_MODELS.items():
    tts_dir   = LAB_ROOT / "models" / "tts" / lang
    model_ok  = (tts_dir / "model.onnx").exists()
    tokens_ok = (tts_dir / "tokens.txt").exists()
    if model_ok and tokens_ok:
        print(f"  [{lang}] already present — skipping")
        for fname in ("model.onnx", "tokens.txt"):
            p = tts_dir / fname
            records.append({"type":"tts","lang":lang,"file":str(p.relative_to(LAB_ROOT)),
                             "size_bytes":p.stat().st_size,"sha256":sha256(p)})
        continue

    tts_dir.mkdir(parents=True, exist_ok=True)
    archive = tts_dir / archive_name
    if not archive.exists():
        download(f"{MMS_BASE}/{archive_name}", archive)

    extract_tar(archive, tts_dir)

    # Flatten: archive typically unpacks to a subdirectory
    sub = tts_dir / inner_dir
    if sub.is_dir():
        for f in sub.iterdir():
            dest = tts_dir / f.name
            if not dest.exists():
                if f.is_dir():
                    shutil.copytree(f, dest)
                else:
                    f.rename(dest)
        shutil.rmtree(sub, ignore_errors=True)

    archive.unlink(missing_ok=True)

    # Rename model ONNX to model.onnx (convention used by LanguageRegistry)
    orig = tts_dir / model_onnx_name
    if orig.exists():
        orig.rename(tts_dir / "model.onnx")

    for fname in ("model.onnx", "tokens.txt"):
        p = tts_dir / fname
        if p.exists():
            print(f"  [{lang}] {fname}: {p.stat().st_size:,} bytes")
            records.append({"type":"tts","lang":lang,
                             "source":f"{MMS_BASE}/{archive_name}",
                             "file":str(p.relative_to(LAB_ROOT)),
                             "size_bytes":p.stat().st_size,"sha256":sha256(p)})
        else:
            print(f"  [{lang}] WARNING: {fname} not found after extraction — check inner layout")
            # List what we have to help debug
            for item in sorted(tts_dir.iterdir()):
                print(f"         found: {item.name}")

print(f"\n  Languages with no available TTS: {', '.join(NO_TTS_LANGS)}")
print("  These can be marked as STT-only or left as NOT_INSTALLED in LanguageRegistry.")

# ── VAD ──────────────────────────────────────────────────────────────────────
print("\n=== VAD — Silero VAD ===")
vad_dir  = LAB_ROOT / "models" / "vad"
vad_path = vad_dir / "silero_vad.onnx"
if vad_path.exists():
    print(f"  silero_vad.onnx already present ({vad_path.stat().st_size:,} bytes)")
else:
    vad_dir.mkdir(parents=True, exist_ok=True)
    download(VAD_URL, vad_path)
    print(f"  silero_vad.onnx: {vad_path.stat().st_size:,} bytes")
records.append({"type":"vad","lang":"all","file":str(vad_path.relative_to(LAB_ROOT)),
                "size_bytes":vad_path.stat().st_size,"sha256":sha256(vad_path)})

# ── Merge into manifest ───────────────────────────────────────────────────────
manifest_path = LAB_ROOT / "results" / "download_manifest.json"
manifest_path.parent.mkdir(parents=True, exist_ok=True)
existing: list[dict] = []
if manifest_path.exists():
    try:
        existing = json.loads(manifest_path.read_text())
        if isinstance(existing, dict):
            existing = existing.get("records", [])
    except Exception:
        existing = []
# Merge: keep existing STT records, add/update TTS+VAD
existing_keys = {r["file"] for r in existing}
merged = list(existing) + [r for r in records if r["file"] not in existing_keys]
manifest_path.write_text(json.dumps({"records": merged}, indent=2, ensure_ascii=False))
print(f"\nManifest written: {manifest_path}  ({len(merged)} records)")
print("\nDone. TTS + VAD files are ready.")
print("Next: record 20-30 WAV samples per language, then run models_lab/test_stt.py and test_tts.py.")
