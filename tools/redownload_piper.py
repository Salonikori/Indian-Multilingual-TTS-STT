#!/usr/bin/env python3
"""Re-download Piper TTS archives and extract them correctly.

Fixes the case where espeak-ng-data ended up empty after a botched extraction.
Run from inside tools/ (moved from optional_model_manager/ per audit requirements).
"""
from __future__ import annotations
import hashlib, json, shutil, tarfile, urllib.request
from pathlib import Path

ROOT     = Path(__file__).resolve().parents[1]
BASE     = ROOT / "models_lab" / "models" / "tts"
TMP      = ROOT / "models_lab" / "models" / "tmp_piper"
URL_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
MANIFEST = ROOT / "models_lab" / "results" / "download_manifest.json"

# (app_lang, archive_name, inner_dir_prefix, onnx_filename_in_archive)
PIPER_MODELS = [
    ("hi", "vits-piper-hi_IN-rohan-medium-int8.tar.bz2",
     "vits-piper-hi_IN-rohan-medium", "hi_IN-rohan-medium.onnx"),
    ("ml", "vits-piper-ml_IN-meera-medium-int8.tar.bz2",
     "vits-piper-ml_IN-meera-medium", "ml_IN-meera-medium.onnx"),
    ("en", "vits-piper-en_US-lessac-medium-int8.tar.bz2",
     "vits-piper-en_US-lessac-medium", "en_US-lessac-medium.onnx"),
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def is_complete(lang: str) -> bool:
    d = BASE / lang
    espeak = d / "espeak-ng-data"
    return (
        (d / "model.onnx").is_file()
        and (d / "tokens.txt").is_file()
        and espeak.is_dir()
        and any(espeak.iterdir())
    )


new_records: list[dict] = []

for lang, archive_name, inner_prefix, onnx_name in PIPER_MODELS:
    dest = BASE / lang
    dest.mkdir(parents=True, exist_ok=True)

    # Remove empty espeak-ng-data placeholder if present
    old_espeak = dest / "espeak-ng-data"
    if old_espeak.is_dir() and not any(old_espeak.iterdir()):
        old_espeak.rmdir()
        print(f"[{lang}] removed empty espeak-ng-data placeholder")

    if is_complete(lang):
        print(f"[{lang}] already complete — skipping download")
        for fname in ("model.onnx", "tokens.txt"):
            p = dest / fname
            new_records.append({"type": "tts", "lang": lang, "file": str(p.relative_to(ROOT / "models_lab")),
                                 "size_bytes": p.stat().st_size, "sha256": sha256(p)})
        continue

    # --- download ---
    archive_path = dest / archive_name
    print(f"[{lang}] Downloading {archive_name} ...")
    url = URL_BASE + archive_name
    with urllib.request.urlopen(url) as resp, archive_path.open("wb") as out:
        shutil.copyfileobj(resp, out)
    print(f"[{lang}] Downloaded: {archive_path.stat().st_size:,} bytes")

    # --- extract to tmp ---
    TMP.mkdir(parents=True, exist_ok=True)
    print(f"[{lang}] Extracting ...")
    with tarfile.open(archive_path) as tf:
        tf.extractall(TMP)
    archive_path.unlink()

    # Find the inner directory (may be vits-piper-...-medium or vits-piper-...-medium-int8)
    inner = TMP / inner_prefix
    if not inner.is_dir():
        # Try with -int8 suffix
        inner_int8 = TMP / (inner_prefix + "-int8")
        if inner_int8.is_dir():
            inner = inner_int8
        else:
            candidates = [p for p in TMP.iterdir() if p.is_dir()]
            if candidates:
                inner = candidates[0]
                print(f"[{lang}] using extracted dir: {inner.name}")
            else:
                print(f"[{lang}] ERROR: no directory found in archive")
                shutil.rmtree(TMP, ignore_errors=True)
                continue

    # --- move files ---
    src_onnx = inner / onnx_name
    if not src_onnx.exists():
        # Find any .onnx that isn't .json
        onnx_files = [p for p in inner.iterdir() if p.suffix == ".onnx" and not p.name.endswith(".json")]
        if onnx_files:
            src_onnx = onnx_files[0]
            print(f"[{lang}] found ONNX: {src_onnx.name}")
        else:
            print(f"[{lang}] ERROR: no ONNX file found")
            shutil.rmtree(TMP, ignore_errors=True)
            continue

    # model.onnx — overwrite if present (may be stale)
    dst_model = dest / "model.onnx"
    if dst_model.exists():
        dst_model.unlink()
    src_onnx.rename(dst_model)
    sz = (dest / "model.onnx").stat().st_size
    print(f"[{lang}] model.onnx: {sz:,} bytes")
    new_records.append({"type": "tts", "lang": lang,
                         "file": str((dest / "model.onnx").relative_to(ROOT / "models_lab")),
                         "size_bytes": sz, "sha256": sha256(dest / "model.onnx")})

    # tokens.txt
    src_tok = inner / "tokens.txt"
    if src_tok.exists():
        dst_tok = dest / "tokens.txt"
        if dst_tok.exists():
            dst_tok.unlink()
        src_tok.rename(dst_tok)
        sz_t = (dest / "tokens.txt").stat().st_size
        print(f"[{lang}] tokens.txt: {sz_t:,} bytes")
        new_records.append({"type": "tts", "lang": lang,
                             "file": str((dest / "tokens.txt").relative_to(ROOT / "models_lab")),
                             "size_bytes": sz_t, "sha256": sha256(dest / "tokens.txt")})

    # espeak-ng-data/
    espeak_src = inner / "espeak-ng-data"
    espeak_dst = dest / "espeak-ng-data"
    if espeak_src.is_dir():
        if espeak_dst.exists():
            shutil.rmtree(espeak_dst)
        shutil.copytree(espeak_src, espeak_dst)
        n = sum(1 for _ in espeak_dst.iterdir())
        print(f"[{lang}] espeak-ng-data: {n} entries")
    else:
        print(f"[{lang}] WARNING: espeak-ng-data not found in archive")

    shutil.rmtree(TMP, ignore_errors=True)


# Share espeak-ng-data between Piper langs (it's identical across all Piper models)
for src_lang, dst_lang in [("hi", "en"), ("hi", "ml")]:
    src_e = BASE / src_lang / "espeak-ng-data"
    dst_e = BASE / dst_lang / "espeak-ng-data"
    if src_e.is_dir() and any(src_e.iterdir()) and (not dst_e.exists() or not any(dst_e.iterdir())):
        if dst_e.exists():
            shutil.rmtree(dst_e)
        shutil.copytree(src_e, dst_e)
        print(f"Shared espeak-ng-data from {src_lang} -> {dst_lang}")


# --- update manifest ---
existing: list[dict] = []
if MANIFEST.exists():
    try:
        data = json.loads(MANIFEST.read_text())
        existing = data.get("records", data) if isinstance(data, dict) else data
    except Exception:
        existing = []

existing_files = {r["file"] for r in existing}
merged = list(existing) + [r for r in new_records if r["file"] not in existing_files]
MANIFEST.parent.mkdir(parents=True, exist_ok=True)
MANIFEST.write_text(json.dumps({"records": merged}, indent=2, ensure_ascii=False))
print(f"\nManifest updated: {len(merged)} records -> {MANIFEST}")


# --- final verification ---
print("\n=== Final verification ===")
all_ok = True
for lang in ["hi", "ml", "gu", "bn", "en"]:
    d = BASE / lang
    m  = (d / "model.onnx").is_file()
    t  = (d / "tokens.txt").is_file()
    piper = lang in ("hi", "ml", "en")
    e  = (d / "espeak-ng-data").is_dir() and any((d / "espeak-ng-data").iterdir()) if piper else True
    ok = m and t and e
    if not ok:
        all_ok = False
    m_sz = f"{(d/'model.onnx').stat().st_size/1e6:.1f} MB" if m else "MISSING"
    e_s  = (str(sum(1 for _ in (d/'espeak-ng-data').iterdir())) + " entries") if (piper and e) else ("N/A" if not piper else "MISSING")
    print(f"  [{lang}] model={m_sz}  tokens={t}  espeak={e_s}  => {'OK' if ok else 'INCOMPLETE'}")

if all_ok:
    print("\nAll TTS models correctly laid out.")
else:
    print("\nSome models still incomplete — check warnings above.")
