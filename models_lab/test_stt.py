#!/usr/bin/env python3
"""Run local sherpa-onnx offline STT models over a WAV/reference TSV corpus."""
from __future__ import annotations
import argparse, csv, json, time, platform, os
from pathlib import Path
import soundfile as sf
import numpy as np
from jiwer import wer
import unicodedata
import re

ROOT = Path(__file__).resolve().parent

def normalize_text(text: str) -> str:
    """Normalize text for WER scoring: lowercase, strip punctuation, collapse whitespace, NFC Unicode."""
    # Unicode NFC normalization for proper Hindi/Devanagari character handling
    text = unicodedata.normalize('NFC', text)
    
    # Convert to lowercase
    text = text.lower()
    
    # Remove punctuation and special characters, keep only letters, digits, and spaces
    text = re.sub(r'[^\w\s]', '', text)
    
    # Collapse whitespace
    text = ' '.join(text.split())
    
    return text.strip()

def load_rows(tsv: Path):
    rows = []
    with tsv.open("r", encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if not row or not row.get("wav_path") or not row.get("reference"):
                continue
            wav = Path(row["wav_path"])
            if not wav.is_absolute(): wav = ROOT / wav
            rows.append((row.get("language", "unknown").strip(), wav, row["reference"].strip()))
    return rows

def make_recognizer(args):
    try:
        import sherpa_onnx
    except ImportError as e:
        raise SystemExit("Install requirements first: pip install -r requirements.txt") from e
    if args.model_type == "nemo_ctc":
        cfg = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=args.model, tokens=args.tokens, num_threads=args.threads,
            provider=args.provider, decoding_method=args.decoding_method,
        )
    elif args.model_type == "transducer":
        cfg = sherpa_onnx.OfflineRecognizer.from_transducer(
            encoder=args.encoder, decoder=args.decoder, joiner=args.joiner,
            tokens=args.tokens, num_threads=args.threads, provider=args.provider,
            decoding_method=args.decoding_method,
        )
    elif args.model_type == "ctc":
        cfg = sherpa_onnx.OfflineRecognizer.from_ctc(
            model=args.model, tokens=args.tokens, num_threads=args.threads,
            provider=args.provider,
        )
    elif args.model_type == "whisper":
        cfg = sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=args.encoder, decoder=args.decoder, tokens=args.tokens,
            num_threads=args.threads, provider=args.provider,
        )
    else:
        raise ValueError(args.model_type)
    return cfg

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--references", default="test_audio/references.tsv",
                   help="TSV columns: language, wav_path, reference")
    p.add_argument("--model-type", choices=["transducer", "ctc", "nemo_ctc", "whisper"], required=True)
    p.add_argument("--language", required=True, help="Language tag for this model, e.g. hi or en")
    p.add_argument("--encoder", help="Transducer/Whisper encoder ONNX")
    p.add_argument("--decoder", help="Transducer/Whisper decoder ONNX")
    p.add_argument("--joiner", help="Transducer joiner ONNX")
    p.add_argument("--model", help="Single CTC ONNX model")
    p.add_argument("--tokens", required=True)
    p.add_argument("--threads", type=int, default=2)
    p.add_argument("--provider", default="cpu")
    p.add_argument("--decoding-method", default="greedy_search")
    p.add_argument("--output", default="results/stt_results.json")
    args = p.parse_args()
    for name in (["tokens", "model"] if args.model_type in ("ctc", "nemo_ctc") else
                 ["tokens", "encoder", "decoder"] if args.model_type == "whisper" else
                 ["tokens", "encoder", "decoder", "joiner"]):
        if not getattr(args, name):
            p.error(f"--{name.replace('_','-')} is required for {args.model_type}")
    rows = [r for r in load_rows((ROOT / args.references).resolve()) if r[0] == args.language]
    if not rows:
        raise SystemExit(f"No rows for language={args.language!r} in {args.references}. Add real WAVs and references.")
    for _, wav, _ in rows:
        if not wav.is_file(): raise SystemExit(f"Missing WAV: {wav}")
    recognizer = make_recognizer(args)  # model-load check happens here
    refs, hyps, durations, infer_seconds = [], [], [], []
    for lang, wav, ref in rows:
        audio, sr = sf.read(wav, dtype="float32", always_2d=False)
        if audio.ndim == 2: audio = audio.mean(axis=1)
        if sr != 16000:
            raise SystemExit(f"{wav}: sample rate is {sr}; resample to 16000 Hz before testing.")
        stream = recognizer.create_stream()
        stream.accept_waveform(sr, np.asarray(audio, dtype=np.float32))
        start = time.perf_counter()
        recognizer.decode_stream(stream)
        elapsed = time.perf_counter() - start
        hyp = stream.result.text.strip()
        
        # Normalize both reference and hypothesis for fair WER scoring
        ref_norm = normalize_text(ref)
        hyp_norm = normalize_text(hyp)
        
        refs.append(ref_norm); hyps.append(hyp_norm)
        durations.append(len(audio) / sr); infer_seconds.append(elapsed)
        print(f"{wav.name}\tWER={wer(ref_norm, hyp_norm):.4f}\tRTF={elapsed/(len(audio)/sr):.4f}")
        print(f"  REF: {ref} -> {ref_norm}")
        print(f"  HYP: {hyp} -> {hyp_norm}")
    total_audio = sum(durations); total_infer = sum(infer_seconds)
    ram_bytes = None
    try:
        with open("/proc/meminfo", "r", encoding="utf-8") as memf:
            first = next(line for line in memf if line.startswith("MemTotal:"))
            ram_bytes = int(first.split()[1]) * 1024
    except (OSError, StopIteration, ValueError):
        pass
    report = {
        "host_device": {"system": platform.platform(), "machine": platform.machine(), "processor": platform.processor() or "not reported by OS", "ram_bytes": ram_bytes},
        "language": args.language, "model_type": args.model_type,
        "utterances": len(rows), "wer_corpus": float(wer(refs, hyps)),
        "mean_utterance_wer": float(np.mean([wer(r,h) for r,h in zip(refs,hyps)])),
        "audio_seconds": total_audio, "inference_seconds": total_infer,
        "aggregate_rtf": total_infer / total_audio,
        "note": "Measured on this host and corpus; not an Android measurement. WER calculated on normalized text (lowercase, no punctuation, collapsed whitespace, Unicode NFC).",
        "normalization_note": "Both hypothesis and reference texts normalized before scoring for fair comparison"
    }
    out = ROOT / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\nSUMMARY\n" + json.dumps(report, ensure_ascii=False, indent=2))
    print(f"Saved {out}")

if __name__ == "__main__": main()
