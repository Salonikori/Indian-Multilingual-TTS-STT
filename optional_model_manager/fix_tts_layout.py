#!/usr/bin/env python3
"""Fix TTS directory layouts after extraction — moves model.onnx and tokens.txt
to the expected top-level position and organises espeak-ng-data correctly.
Run from the repo root or optional_model_manager/ with the venv active.
"""
import shutil
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "models_lab" / "models" / "tts"

def fix_piper(lang: str, inner_dir: str, onnx_name: str) -> None:
    tts   = BASE / lang
    inner = tts / inner_dir           # the subdirectory the archive created
    espeak_src_inner = tts / "espeak-ng-data" / inner_dir  # if it got nested inside espeak-ng-data

    # Locate the actual inner directory regardless of where it ended up
    candidate = None
    if inner.is_dir():
        candidate = inner
    elif espeak_src_inner.is_dir():
        candidate = espeak_src_inner
    else:
        # Search recursively
        for p in tts.rglob(onnx_name):
            candidate = p.parent
            break

    if candidate is None:
        # Already correct?
        if (tts / "model.onnx").exists() and (tts / "tokens.txt").exists():
            print(f"  [{lang}] already correct — skipping")
            return
        print(f"  [{lang}] ERROR: cannot find {onnx_name}")
        return

    # Move model file
    src_onnx = candidate / onnx_name
    dst_onnx = tts / "model.onnx"
    if src_onnx.exists() and not dst_onnx.exists():
        src_onnx.rename(dst_onnx)
        print(f"  [{lang}] model.onnx  {dst_onnx.stat().st_size:,} bytes")

    # Move tokens.txt
    src_tok = candidate / "tokens.txt"
    dst_tok = tts / "tokens.txt"
    if src_tok.exists() and not dst_tok.exists():
        src_tok.rename(dst_tok)
        print(f"  [{lang}] tokens.txt  {dst_tok.stat().st_size:,} bytes")

    # Remove leftover inner dir
    shutil.rmtree(candidate, ignore_errors=True)

    # Ensure espeak-ng-data is a direct child of tts/lang/ (not nested)
    # If it got moved inside another dir, fix it
    espeak_wrong = None
    for p in tts.rglob("espeak-ng-data"):
        if p.parent != tts:
            espeak_wrong = p
            break
    if espeak_wrong and (tts / "espeak-ng-data").exists() is False:
        espeak_wrong.rename(tts / "espeak-ng-data")
        print(f"  [{lang}] moved espeak-ng-data to correct location")

    espeak = tts / "espeak-ng-data"
    if espeak.is_dir():
        n = sum(1 for _ in espeak.iterdir())
        print(f"  [{lang}] espeak-ng-data: {n} entries")
    else:
        print(f"  [{lang}] WARNING: no espeak-ng-data directory found")


def fix_rename_only(lang: str, src_name: str) -> None:
    tts = BASE / lang
    src = tts / src_name
    dst = tts / "model.onnx"
    if src.exists() and not dst.exists():
        src.rename(dst)
        print(f"  [{lang}] renamed {src_name} -> model.onnx  {dst.stat().st_size:,} bytes")
    elif dst.exists():
        print(f"  [{lang}] model.onnx already present")
    else:
        print(f"  [{lang}] ERROR: {src_name} not found")


def share_espeak(src_lang: str, dst_lang: str) -> None:
    """Copy espeak-ng-data from one Piper model to another (they are identical)."""
    src = BASE / src_lang / "espeak-ng-data"
    dst = BASE / dst_lang / "espeak-ng-data"
    if src.is_dir() and not dst.exists():
        shutil.copytree(src, dst)
        print(f"  [{dst_lang}] copied espeak-ng-data from [{src_lang}] ({sum(1 for _ in dst.iterdir())} entries)")
    elif dst.exists():
        print(f"  [{dst_lang}] espeak-ng-data already present")
    else:
        print(f"  [{dst_lang}] WARNING: source espeak-ng-data [{src_lang}] not found")


print("=== Fixing Hindi (hi) ===")
fix_piper("hi", "vits-piper-hi_IN-rohan-medium-int8", "hi_IN-rohan-medium.onnx")

print("\n=== Fixing Malayalam (ml) ===")
fix_piper("ml", "vits-piper-ml_IN-meera-medium-int8", "ml_IN-meera-medium.onnx")

print("\n=== Fixing Gujarati (gu) ===")
fix_rename_only("gu", "gu_IN-cmu-indic_low.onnx")

print("\n=== Fixing English (en) ===")
fix_piper("en", "vits-piper-en_US-lessac-medium-int8", "en_US-lessac-medium.onnx")
# English Piper also needs espeak-ng-data — share from hi (same data)
share_espeak("hi", "en")

print("\n=== Bengali (bn) ===")
bn = BASE / "bn"
if (bn / "model.onnx").exists() and (bn / "tokens.txt").exists():
    print(f"  [bn] already correct — model.onnx {(bn/'model.onnx').stat().st_size:,} bytes")
else:
    print("  [bn] WARNING: model.onnx or tokens.txt missing")

print("\n=== Final verification ===")
for lang in ["hi", "ml", "gu", "bn", "en"]:
    d = BASE / lang
    m  = (d / "model.onnx").exists()
    t  = (d / "tokens.txt").exists()
    e  = (d / "espeak-ng-data").is_dir()
    piper_lang = lang in ("hi", "ml", "en")
    ok = m and t and (e if piper_lang else True)
    model_sz = f"{(d/'model.onnx').stat().st_size/1e6:.1f} MB" if m else "MISSING"
    espeak_s  = f"{sum(1 for _ in (d/'espeak-ng-data').iterdir())} entries" if e else ("N/A" if not piper_lang else "MISSING")
    status = "OK" if ok else "INCOMPLETE"
    print(f"  [{lang}] model={model_sz}  tokens={t}  espeak-ng-data={espeak_s}  => {status}")
