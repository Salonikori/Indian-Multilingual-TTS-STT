#!/usr/bin/env python3
"""Push model files into the app's private filesDir using adb + run-as.

Strategy (required because direct adb push to /data/data/ is blocked on
production devices):
  1. adb push  local_file -> /data/local/tmp/itantra_stage/relative_path
  2. adb shell run-as APP_ID sh -c 'cp /data/local/tmp/... dest'
  3. adb shell rm -rf /data/local/tmp/itantra_stage   (clean up)

/data/local/tmp is world-writable and readable by the app process via run-as.
/sdcard is NOT used because the app's run-as context cannot read /sdcard on
Android 10+ (scoped storage).

Required local model layout (models_lab/models/):
  stt/{lang}/model.int8.onnx          Indic NeMo CTC (hi ta bn mr gu kn te ml or)
  stt/{lang}/tokens.txt
  stt/en/encoder.int8.onnx            English Zipformer transducer
  stt/en/decoder.int8.onnx
  stt/en/joiner.int8.onnx
  stt/en/tokens.txt
  tts/hi/model.onnx  tts/hi/tokens.txt   Piper hi_IN (+ espeak-ng-data/)
  tts/ml/model.onnx  tts/ml/tokens.txt   Piper ml_IN (+ espeak-ng-data/)
  tts/gu/model.onnx  tts/gu/tokens.txt   mimic3 gu_IN
  tts/bn/model.onnx  tts/bn/tokens.txt   Coqui bn
  tts/en/model.onnx  tts/en/tokens.txt   Piper en_US (+ espeak-ng-data/)
  vad/silero_vad.onnx
  (ta mr kn te or have no TTS — STT-only languages)

Device target:  /data/user/0/com.itantra.app/files/models/
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path, PurePosixPath

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
ROOT       = Path(__file__).resolve().parents[1]
LAB_MODELS = ROOT / "models_lab" / "models"
APP_ID     = "com.itantra.app"
# /data/user/0/... is the canonical path run-as resolves to on multi-user devices
DEVICE_APP_FILES = f"/data/user/0/{APP_ID}/files"
DEVICE_MODELS    = f"{DEVICE_APP_FILES}/models"
STAGE_DIR        = "/data/local/tmp/itantra_stage"

# Languages that have both STT and TTS available locally
TTS_LANGS = {"hi", "ml", "gu", "bn", "en"}
# Languages with STT only (no TTS model exists)
STT_ONLY_LANGS = {"ta", "mr", "kn", "te", "or"}
ALL_LANGS = TTS_LANGS | STT_ONLY_LANGS

# Piper TTS languages that need espeak-ng-data/
PIPER_LANGS = {"hi", "ml", "en"}

_failures: list[str] = []


# ---------------------------------------------------------------------------
# ADB helpers
# ---------------------------------------------------------------------------
def _find_adb() -> str:
    on_path = shutil.which("adb")
    if on_path:
        return on_path
    candidates = [
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
        os.path.expanduser(r"~\AppData\Local\Android\Sdk\platform-tools\adb.exe"),
        r"C:\Users\salon\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    sys.exit(
        "adb not found.  Add Android SDK platform-tools to PATH or install it.\n"
        "  Quick fix:  $env:PATH += ';C:\\Users\\salon\\AppData\\Local\\Android\\Sdk\\platform-tools'"
    )


ADB = _find_adb()


def _run(args: list[str], *, check: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True,
                          check=check, encoding="utf-8", errors="replace")


def adb_shell(cmd: str) -> subprocess.CompletedProcess:
    """Run a shell command on the device."""
    return _run([ADB, "shell", cmd])


def adb_push(local: Path, remote: str) -> bool:
    """Push a single local file to a device path. Returns True on success."""
    r = _run([ADB, "push", str(local), remote])
    if r.returncode != 0:
        msg = (r.stderr or r.stdout).strip()
        print(f"    ✗ adb push failed: {msg}", file=sys.stderr)
        _failures.append(f"push {local} -> {remote}: {msg}")
        return False
    return True


def run_as(cmd: str) -> subprocess.CompletedProcess:
    """Run a shell command as the app user via run-as."""
    return adb_shell(f"run-as {APP_ID} sh -c '{cmd}'")


# ---------------------------------------------------------------------------
# Transfer helpers
# ---------------------------------------------------------------------------
def stage_and_copy(local: Path, app_dest: str) -> bool:
    """
    Push local file to staging area, then copy into app private storage.
    app_dest is an absolute device path inside DEVICE_APP_FILES.
    Retries the adb push once on transient device-not-found errors.
    Returns True on success.
    """
    if not local.exists():
        print(f"    ✗ LOCAL FILE MISSING: {local}", file=sys.stderr)
        _failures.append(f"missing locally: {local}")
        return False

    rel = local.relative_to(LAB_MODELS)
    stage_path = f"{STAGE_DIR}/{rel.as_posix()}"
    stage_parent = str(PurePosixPath(stage_path).parent)

    # Create parent directories on staging (best-effort; ignore errors)
    adb_shell(f"mkdir -p {stage_parent}")

    # Push to staging — retry once on transient failure
    for attempt in range(2):
        r = _run([ADB, "push", str(local), stage_path])
        push_output = (r.stderr or r.stdout).strip()
        # "failed to read copy response: EOF" means data was sent but ack timed out.
        # Verify the staged file size to decide if we can proceed.
        if r.returncode != 0:
            if "failed to read copy response" in push_output or "EOF" in push_output:
                # Check if the file actually landed in staging
                size_check = adb_shell(f"wc -c < {stage_path} 2>/dev/null || echo -1")
                try:
                    staged_size = int(size_check.stdout.strip())
                except ValueError:
                    staged_size = -1
                if staged_size == local.stat().st_size:
                    print(f"    ⚠ push EOF (data confirmed in staging, {staged_size:,} bytes)")
                    break   # data is there, proceed to cp
                # Data not confirmed; retry if first attempt
                if attempt == 0:
                    import time; time.sleep(3)
                    continue
            elif attempt == 0 and "no devices" in push_output.lower():
                import time; time.sleep(3)
                continue
            print(f"    ✗ adb push failed: {push_output.splitlines()[-1]}", file=sys.stderr)
            _failures.append(f"push {local} -> {stage_path}: {push_output.splitlines()[-1]}")
            return False
        else:
            break  # success

    # Create destination parent in app private storage
    run_as(f"mkdir -p {str(PurePosixPath(app_dest).parent)}")

    # Copy from staging into app private storage
    r = run_as(f"cp {stage_path} {app_dest}")
    if r.returncode != 0:
        msg = (r.stderr or r.stdout).strip()
        print(f"    ✗ run-as cp {stage_path} -> {app_dest}: {msg}", file=sys.stderr)
        _failures.append(f"run-as cp {stage_path} -> {app_dest}: {msg}")
        return False

    return True


def device_file_exists(app_path: str) -> bool:
    r = run_as(f"ls {app_path}")
    return r.returncode == 0


def transfer_file(local: Path, app_dest: str, label: str = "") -> bool:
    """Transfer a single file, skip if already present and same size."""
    name = label or local.name
    local_size = local.stat().st_size if local.exists() else -1

    # Check if already present with matching size
    if device_file_exists(app_dest):
        r = run_as(f"wc -c < {app_dest}")
        if r.returncode == 0:
            try:
                device_size = int(r.stdout.strip())
                if device_size == local_size:
                    print(f"    ✓ skip (same size {device_size} bytes): {name}")
                    return True
            except ValueError:
                pass

    ok = stage_and_copy(local, app_dest)
    if ok:
        print(f"    ✓ transferred ({local_size:,} bytes): {name}")
    return ok


# ---------------------------------------------------------------------------
# Language installers
# ---------------------------------------------------------------------------
def install_stt(lang: str) -> bool:
    """Install STT files for one language. Returns True if all required files succeeded."""
    stt_local = LAB_MODELS / "stt" / lang
    stt_dest  = f"{DEVICE_MODELS}/{lang}/stt"
    ok = True

    if lang == "en":
        files = [
            ("tiny.en-encoder.int8.onnx", f"{stt_dest}/encoder.int8.onnx"),
            ("tiny.en-decoder.int8.onnx", f"{stt_dest}/decoder.int8.onnx"),
            ("tiny.en-tokens.txt",        f"{stt_dest}/tokens.txt"),
        ]
    else:
        files = [
            ("model.int8.onnx", f"{stt_dest}/model.int8.onnx"),
            ("tokens.txt",      f"{stt_dest}/tokens.txt"),
        ]

    for fname, dest in files:
        local = stt_local / fname
        if not local.exists():
            print(f"    ✗ REQUIRED STT FILE MISSING: {local}", file=sys.stderr)
            _failures.append(f"missing STT file: {local}")
            ok = False
            continue
        ok = transfer_file(local, dest, f"[{lang}] stt/{fname}") and ok

    return ok


def install_tts(lang: str) -> bool:
    """Install TTS files for one language. Returns True if all required files succeeded."""
    tts_local = LAB_MODELS / "tts" / lang
    tts_dest  = f"{DEVICE_MODELS}/{lang}/tts"
    ok = True

    for fname in ("model.onnx", "tokens.txt"):
        local = tts_local / fname
        if not local.exists():
            print(f"    ✗ REQUIRED TTS FILE MISSING: {local}", file=sys.stderr)
            _failures.append(f"missing TTS file: {local}")
            ok = False
            continue
        ok = transfer_file(local, f"{tts_dest}/{fname}", f"[{lang}] tts/{fname}") and ok

    # Piper models also need espeak-ng-data/
    if lang in PIPER_LANGS:
        espeak_local = tts_local / "espeak-ng-data"
        if not espeak_local.is_dir():
            print(f"    ✗ espeak-ng-data/ missing for [{lang}] — Piper will fail", file=sys.stderr)
            _failures.append(f"missing espeak-ng-data for {lang}")
            return False

        espeak_stage = f"{STAGE_DIR}/tts/{lang}/espeak-ng-data"
        espeak_dest  = f"{tts_dest}/espeak-ng-data"
        n_files = sum(1 for f in espeak_local.rglob("*") if f.is_file())
        total_kb = sum(f.stat().st_size for f in espeak_local.rglob("*") if f.is_file()) // 1024

        # Check if already installed (look for a known file inside it)
        probe = f"{espeak_dest}/phontab"
        r2 = run_as(f"ls {probe}")
        if r2.returncode == 0:
            print(f"    ✓ skip espeak-ng-data/ (already present): {n_files} files")
            return ok

        print(f"    staging espeak-ng-data/ ({n_files} files, {total_kb} KB) in one push ...")
        # Create staging parent
        adb_shell(f"mkdir -p {STAGE_DIR}/tts/{lang}")
        # Push entire directory in one adb call
        r2 = _run([ADB, "push", str(espeak_local), f"{STAGE_DIR}/tts/{lang}/"])
        if r2.returncode != 0:
            msg = (r2.stderr or r2.stdout).strip().splitlines()[-1]
            print(f"    ✗ adb push espeak-ng-data/: {msg}", file=sys.stderr)
            _failures.append(f"push espeak-ng-data [{lang}]: {msg}")
            return False
        pushed_line = (r2.stderr or r2.stdout).strip().splitlines()[-1]
        print(f"    push result: {pushed_line}")

        # Copy the whole directory into app private storage in one run-as call
        run_as(f"mkdir -p {tts_dest}")
        r2 = run_as(f"cp -r {espeak_stage} {tts_dest}/")
        if r2.returncode != 0:
            msg = (r2.stderr or r2.stdout).strip()
            print(f"    ✗ run-as cp -r espeak-ng-data/: {msg}", file=sys.stderr)
            _failures.append(f"run-as cp espeak-ng-data [{lang}]: {msg}")
            ok = False
        else:
            print(f"    ✓ espeak-ng-data/ installed")

    return ok


def install_vad() -> bool:
    """Install the Silero VAD model."""
    local = LAB_MODELS / "vad" / "silero_vad.onnx"
    dest  = f"{DEVICE_MODELS}/vad/silero_vad.onnx"
    if not local.exists():
        print(f"    ✗ VAD model missing: {local}", file=sys.stderr)
        _failures.append(f"missing VAD: {local}")
        return False
    return transfer_file(local, dest, "[vad] silero_vad.onnx")


# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------
def verify_device_layout() -> dict[str, list[str]]:
    """List all model files on device; return dict lang -> [files]."""
    r = run_as(f"find {DEVICE_MODELS} -type f 2>/dev/null")
    if r.returncode != 0 or not r.stdout.strip():
        return {}
    result: dict[str, list[str]] = {}
    for line in r.stdout.strip().splitlines():
        line = line.strip()
        if line:
            # e.g. /data/user/0/.../models/hi/stt/model.int8.onnx -> hi/stt/model.int8.onnx
            rel = line.replace(DEVICE_MODELS + "/", "")
            lang = rel.split("/")[0] if "/" in rel else "?"
            result.setdefault(lang, []).append(rel)
    return result


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main() -> None:
    # --- device check -------------------------------------------------------
    r = _run([ADB, "devices"])
    if r.returncode != 0:
        sys.exit(f"adb failed: {r.stderr.strip()}")
    device_lines = [l for l in r.stdout.splitlines()[1:] if l.strip() and "\tdevice" in l]
    unauth_lines = [l for l in r.stdout.splitlines()[1:] if "unauthorized" in l]
    if unauth_lines:
        sys.exit("Device is unauthorized. Accept the USB debugging prompt on your phone.")
    if not device_lines:
        sys.exit("No device connected. Connect via USB with USB debugging enabled.")
    if len(device_lines) > 1:
        sys.exit("Multiple devices found. Disconnect all but one.\n" + "\n".join(device_lines))
    print(f"Device: {device_lines[0].strip()}")

    # --- app check ----------------------------------------------------------
    r = _run([ADB, "shell", "pm", "list", "packages", APP_ID])
    if APP_ID not in r.stdout:
        sys.exit(f"App {APP_ID} not installed. Run: adb install app/build/outputs/apk/debug/app-debug.apk")
    print(f"App:    {APP_ID} installed ✓")

    # --- run-as check -------------------------------------------------------
    r = run_as("pwd")
    if r.returncode != 0 or not r.stdout.strip():
        sys.exit("run-as failed — app must be a debug build with android:debuggable=true")
    print(f"run-as: working at {r.stdout.strip()} ✓\n")

    # --- staging area -------------------------------------------------------
    print(f"Creating staging area {STAGE_DIR} ...")
    adb_shell(f"mkdir -p {STAGE_DIR}")

    # --- local model inventory ----------------------------------------------
    print("Local models available:")
    for lang in sorted(ALL_LANGS):
        stt_ok = (LAB_MODELS / "stt" / lang / "model.int8.onnx").exists() if lang != "en" else \
                 (LAB_MODELS / "stt" / "en" / "encoder.int8.onnx").exists()
        tts_ok = (LAB_MODELS / "tts" / lang / "model.onnx").exists() if lang in TTS_LANGS else None
        stt_s = "✓" if stt_ok else "✗ MISSING"
        tts_s = ("✓" if tts_ok else "✗ MISSING") if tts_ok is not None else "N/A"
        print(f"  [{lang}] STT={stt_s}  TTS={tts_s}")
    print()

    # --- install STT (all languages) ----------------------------------------
    print("=" * 60)
    print("Installing STT models ...")
    for lang in sorted(ALL_LANGS):
        print(f"\n  [{lang.upper()}] STT")
        install_stt(lang)

    # --- install TTS (only languages that have models) ----------------------
    print()
    print("=" * 60)
    print("Installing TTS models ...")
    for lang in sorted(TTS_LANGS):
        print(f"\n  [{lang.upper()}] TTS")
        install_tts(lang)

    print()
    print("Languages with no TTS (STT-only): " +
          ", ".join(sorted(STT_ONLY_LANGS)))

    # --- install VAD --------------------------------------------------------
    print()
    print("=" * 60)
    print("Installing VAD model ...")
    install_vad()

    # --- fix permissions ----------------------------------------------------
    print()
    print("Fixing permissions ...")
    run_as(f"find {DEVICE_MODELS} -type f -exec chmod 644 {{}} +")
    run_as(f"find {DEVICE_MODELS} -type d -exec chmod 755 {{}} +")

    # --- clean staging area -------------------------------------------------
    print("Cleaning staging area ...")
    adb_shell(f"rm -rf {STAGE_DIR}")

    # --- verify --------------------------------------------------------------
    print()
    print("=" * 60)
    print("Verifying device layout ...")
    layout = verify_device_layout()
    if not layout:
        print("  WARNING: could not list device files — verify manually in the app.", file=sys.stderr)
    else:
        for lang in sorted(layout):
            files = sorted(layout[lang])
            print(f"  [{lang}] {len(files)} file(s):")
            for f in files[:6]:
                print(f"    {f}")
            if len(files) > 6:
                print(f"    ... and {len(files)-6} more")

    # --- final result -------------------------------------------------------
    print()
    if _failures:
        print(f"COMPLETED WITH {len(_failures)} FAILURE(S):", file=sys.stderr)
        for f in _failures:
            print(f"  ✗ {f}", file=sys.stderr)
        sys.exit(1)
    else:
        print("All transfers succeeded ✓")
        print()
        print("Next steps:")
        print("  1. Open the iTantra app on your phone.")
        print("  2. Tap 'Languages and model status' — installed languages show file sizes.")
        print("  3. Tap 'Load active language' to test STT+TTS engine loading.")
        print("  4. Do NOT set ModelStatus=VALIDATED until WER/RTF tests pass on the device.")


if __name__ == "__main__":
    main()
