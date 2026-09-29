#!/usr/bin/env python3
"""Download candidate STT and TTS model artifacts for iTantra.

STT  – AI4Bharat IndicConformer sherpa-onnx community conversion for all 9 Indic
       languages (NeMo CTC) + English Zipformer-GigaSpeech INT8 (Transducer).
TTS  – Facebook MMS-TTS sherpa-onnx conversions for all 9 Indic languages
       (no espeak-ng data needed — MMS uses character tokens directly);
       Piper en_US-lessac-medium for English (includes espeak-ng-data/).

None of this certifies accuracy, license acceptability, or Android runtime
compatibility. Inspect each model card. Measure WER/RTF with test_stt.py /
test_tts.py before promoting any model to VALIDATED in LanguageRegistry.kt.
"""
from __future__ import annotations
import json, hashlib, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAB_ROOT = ROOT / "models_lab"

# ---------------------------------------------------------------------------
# STT candidates
# ---------------------------------------------------------------------------
# AI4Bharat IndicConformer – NeMo CTC, INT8 ONNX, 16 kHz.
# Community-converted sherpa-onnx repo: parismitaglobalsolutions/indicconformer-sherpa-onnx
# License claim on model card: MIT (source weights from AI4Bharat IndicConformer).
# Verify the model card at https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx
# before use.  There is ONE shared tokens.txt for all Indic languages.
INDIC_STT_REPO = "parismitaglobalsolutions/indicconformer-sherpa-onnx"
# All 9 Indic language codes the Android registry supports.
INDIC_LANGS = ["hi", "ta", "bn", "mr", "gu", "kn", "te", "ml", "or"]

# English – Zipformer-GigaSpeech INT8 transducer (Apache-2.0).
# Hugging Face: k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12
EN_STT_REPO = "k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12"
EN_STT_FILES = [
    "encoder-epoch-30-avg-1.int8.onnx",   # → encoder.int8.onnx (app convention)
    "decoder-epoch-30-avg-1.int8.onnx",   # → decoder.int8.onnx
    "joiner-epoch-30-avg-1.int8.onnx",    # → joiner.int8.onnx
    "tokens.txt",
]

# ---------------------------------------------------------------------------
# TTS candidates
# ---------------------------------------------------------------------------
# Facebook MMS-TTS — pre-converted by k2-fsa to sherpa-onnx VITS format.
# Downloaded from the official sherpa-onnx tts-models release.
# License: CC-BY-NC 4.0 per facebook/mms-tts model card. Check terms before redistribution.
# MMS models use only model.onnx + tokens.txt; NO espeak-ng-data directory.
MMS_BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
MMS_LANG_MAP = {
    # sherpa-onnx filename tag : BCP-47-ish ISO 639-3 used by MMS
    "hi": "hin",
    "ta": "tam",
    "bn": "ben",
    "mr": "mar",
    "gu": "guj",
    "kn": "kan",
    "te": "tel",
    "ml": "mal",
    "or": "ory",
}

# English TTS – Piper en_US-lessac-medium (MIT).
# Includes espeak-ng-data/ directory required by OfflineTtsVitsModelConfig.dataDir.
EN_TTS_URL = (
    f"{MMS_BASE_URL}/vits-piper-en_US-lessac-medium.tar.bz2"
)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download_hf(repo_id: str, filename: str, dest_dir: Path, revision: str | None = None) -> Path:
    """Download a single file from Hugging Face Hub and return its local path."""
    try:
        from huggingface_hub import hf_hub_download
    except ImportError:
        sys.exit("huggingface_hub not installed. Run: pip install -r requirements.txt")
    local = hf_hub_download(
        repo_id=repo_id,
        filename=filename,
        revision=revision,
        local_dir=str(dest_dir),
        local_dir_use_symlinks=False,
    )
    return Path(local)


def download_url(url: str, dest_file: Path) -> None:
    """Download a URL to dest_file, streaming to avoid large memory use."""
    try:
        import urllib.request
        import shutil
        print(f"  Downloading {url} ...")
        with urllib.request.urlopen(url) as resp, open(dest_file, "wb") as out:
            shutil.copyfileobj(resp, out)
    except Exception as exc:
        sys.exit(f"Download failed for {url}: {exc}")


def extract_tar(archive: Path, dest_dir: Path) -> None:
    import tarfile
    print(f"  Extracting {archive.name} ...")
    with tarfile.open(archive) as tf:
        tf.extractall(dest_dir)


def main() -> None:
    try:
        from huggingface_hub import HfApi
    except ImportError:
        sys.exit("huggingface_hub not installed. Run: pip install -r requirements.txt")

    records: list[dict] = []

    # ------------------------------------------------------------------
    # 1. Indic STT via Hugging Face
    # ------------------------------------------------------------------
    api = HfApi()
    indic_info = api.model_info(INDIC_STT_REPO, files_metadata=True)
    revision = indic_info.sha
    print(f"\n=== Indic STT  repo={INDIC_STT_REPO}  revision={revision} ===")
    print(f"Card license metadata: {getattr(indic_info, 'license', None)}")
    print("Verify source weights and tokenizer license at the model card before use.\n")

    # shared tokens.txt (one file covers all Indic languages in this repo)
    for lang in INDIC_LANGS:
        dest_dir = LAB_ROOT / "models" / "stt" / lang
        dest_dir.mkdir(parents=True, exist_ok=True)

        # model
        model_src = f"{lang}/model.int8.onnx"
        p = download_hf(INDIC_STT_REPO, model_src, dest_dir, revision)
        target = dest_dir / "model.int8.onnx"
        if p != target:
            target.write_bytes(p.read_bytes())
        print(f"  [{lang}] model  {target.stat().st_size:>12,} bytes  sha256={sha256(target)}")
        records.append({"type": "stt", "lang": lang, "repo": INDIC_STT_REPO,
                        "revision": revision, "file": str(target.relative_to(LAB_ROOT)),
                        "size_bytes": target.stat().st_size, "sha256": sha256(target)})

        # tokens (shared; each language sub-dir in the repo has a copy)
        tokens_src = "tokens.txt"
        p = download_hf(INDIC_STT_REPO, tokens_src, dest_dir, revision)
        target = dest_dir / "tokens.txt"
        if p != target:
            target.write_bytes(p.read_bytes())
        print(f"  [{lang}] tokens {target.stat().st_size:>12,} bytes  sha256={sha256(target)}")
        records.append({"type": "stt_tokens", "lang": lang, "repo": INDIC_STT_REPO,
                        "revision": revision, "file": str(target.relative_to(LAB_ROOT)),
                        "size_bytes": target.stat().st_size, "sha256": sha256(target)})

    # ------------------------------------------------------------------
    # 2. English STT via Hugging Face
    # ------------------------------------------------------------------
    en_info = api.model_info(EN_STT_REPO, files_metadata=True)
    en_revision = en_info.sha
    print(f"\n=== English STT  repo={EN_STT_REPO}  revision={en_revision} ===")
    print(f"Card license metadata: {getattr(en_info, 'license', None)}\n")

    en_stt_dir = LAB_ROOT / "models" / "stt" / "en"
    en_stt_dir.mkdir(parents=True, exist_ok=True)

    # Canonical names the Android app expects (decoder/joiner siblings of encoder)
    rename_map = {
        "encoder-epoch-30-avg-1.int8.onnx": "encoder.int8.onnx",
        "decoder-epoch-30-avg-1.int8.onnx": "decoder.int8.onnx",
        "joiner-epoch-30-avg-1.int8.onnx":  "joiner.int8.onnx",
        "tokens.txt":                        "tokens.txt",
    }
    for src_name, dst_name in rename_map.items():
        p = download_hf(EN_STT_REPO, src_name, en_stt_dir, en_revision)
        target = en_stt_dir / dst_name
        if p.name != dst_name:
            target.write_bytes(p.read_bytes())
        else:
            target = p
        print(f"  [en] {dst_name:35s} {target.stat().st_size:>12,} bytes  sha256={sha256(target)}")
        records.append({"type": "stt", "lang": "en", "repo": EN_STT_REPO,
                        "revision": en_revision, "file": str(target.relative_to(LAB_ROOT)),
                        "size_bytes": target.stat().st_size, "sha256": sha256(target)})

    # ------------------------------------------------------------------
    # 3. Indic TTS — MMS VITS from sherpa-onnx tts-models release
    # ------------------------------------------------------------------
    print(f"\n=== Indic TTS — Facebook MMS (CC-BY-NC 4.0) ===")
    print("Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models")
    print("Check CC-BY-NC 4.0 terms before any distribution.\n")

    for lang, mms_tag in MMS_LANG_MAP.items():
        tts_dir = LAB_ROOT / "models" / "tts" / lang
        tts_dir.mkdir(parents=True, exist_ok=True)
        archive_name = f"vits-mms-{mms_tag}.tar.bz2"
        archive_path = tts_dir / archive_name
        if not archive_path.exists():
            download_url(f"{MMS_BASE_URL}/{archive_name}", archive_path)
        extract_tar(archive_path, tts_dir)
        # MMS archives unpack to vits-mms-{tag}/ ; flatten into tts_dir
        extracted = tts_dir / f"vits-mms-{mms_tag}"
        if extracted.is_dir():
            for f in extracted.iterdir():
                dest = tts_dir / f.name
                if not dest.exists():
                    f.rename(dest)
            try:
                extracted.rmdir()
            except OSError:
                pass
        archive_path.unlink(missing_ok=True)

        # Rename to convention: model.onnx, tokens.txt (model already named model.onnx by MMS export)
        for fname in ("model.onnx", "tokens.txt"):
            p = tts_dir / fname
            if p.exists():
                print(f"  [{lang}] {fname:12s} {p.stat().st_size:>12,} bytes  sha256={sha256(p)}")
                records.append({"type": "tts", "lang": lang, "source": f"{MMS_BASE_URL}/{archive_name}",
                                "file": str(p.relative_to(LAB_ROOT)),
                                "size_bytes": p.stat().st_size, "sha256": sha256(p)})

    # ------------------------------------------------------------------
    # 4. English TTS — Piper en_US-lessac-medium (MIT)
    # ------------------------------------------------------------------
    print(f"\n=== English TTS — Piper en_US-lessac-medium (MIT) ===")
    en_tts_dir = LAB_ROOT / "models" / "tts" / "en"
    en_tts_dir.mkdir(parents=True, exist_ok=True)
    archive_path = en_tts_dir / "vits-piper-en_US-lessac-medium.tar.bz2"
    if not archive_path.exists():
        download_url(EN_TTS_URL, archive_path)
    extract_tar(archive_path, en_tts_dir)
    extracted = en_tts_dir / "vits-piper-en_US-lessac-medium"
    if extracted.is_dir():
        for f in extracted.iterdir():
            dest = en_tts_dir / f.name
            if not dest.exists():
                if f.is_dir():
                    import shutil
                    shutil.copytree(f, dest)
                else:
                    f.rename(dest)
        import shutil
        shutil.rmtree(extracted, ignore_errors=True)
    archive_path.unlink(missing_ok=True)

    # Rename Piper ONNX to model.onnx (app convention)
    piper_onnx = next(en_tts_dir.glob("en_US-lessac-medium.onnx"), None)
    if piper_onnx:
        piper_onnx.rename(en_tts_dir / "model.onnx")
        piper_onnx = en_tts_dir / "model.onnx"
    else:
        piper_onnx = en_tts_dir / "model.onnx"

    for item in sorted(en_tts_dir.rglob("*")):
        if item.is_file():
            print(f"  [en-tts] {str(item.relative_to(en_tts_dir)):45s} {item.stat().st_size:>10,} bytes")
            records.append({"type": "tts", "lang": "en", "source": EN_TTS_URL,
                            "file": str(item.relative_to(LAB_ROOT)),
                            "size_bytes": item.stat().st_size, "sha256": sha256(item)})

    # ------------------------------------------------------------------
    # 5. VAD — Silero VAD (Apache-2.0 per upstream repo)
    # ------------------------------------------------------------------
    print("\n=== VAD — Silero VAD (Apache-2.0) ===")
    VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    vad_dir = LAB_ROOT / "models" / "vad"
    vad_dir.mkdir(parents=True, exist_ok=True)
    vad_path = vad_dir / "silero_vad.onnx"
    if not vad_path.exists():
        download_url(VAD_URL, vad_path)
    print(f"  silero_vad.onnx  {vad_path.stat().st_size:>10,} bytes  sha256={sha256(vad_path)}")
    records.append({"type": "vad", "lang": "all", "source": VAD_URL,
                    "file": str(vad_path.relative_to(LAB_ROOT)),
                    "size_bytes": vad_path.stat().st_size, "sha256": sha256(vad_path)})

    # ------------------------------------------------------------------
    # 6. Write manifest
    # ------------------------------------------------------------------
    manifest = {"records": records}
    out = LAB_ROOT / "results" / "download_manifest.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nManifest saved to {out}")
    print("NEXT STEP: run install_models.py with a connected device to push files into the app's filesDir.")


if __name__ == "__main__":
    main()
