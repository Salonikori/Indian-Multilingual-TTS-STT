#!/usr/bin/env python3
"""Synthesize a local sentence list with a sherpa-onnx VITS model and measure RTF."""
from __future__ import annotations
import argparse, json, time
from pathlib import Path
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--model", required=True, help="VITS model ONNX")
    p.add_argument("--tokens", required=True)
    p.add_argument("--data-dir", required=True, help="Directory containing lexicon/tokens data if required by model")
    p.add_argument("--sentences", required=True, help="UTF-8 text file, one sentence per line (aim for 10 per language)")
    p.add_argument("--language", required=True)
    p.add_argument("--output-dir", default="results/tts")
    p.add_argument("--threads", type=int, default=2)
    p.add_argument("--provider", default="cpu")
    p.add_argument("--speaker-id", type=int, default=0)
    p.add_argument("--speed", type=float, default=1.0)
    args = p.parse_args()
    try:
        import sherpa_onnx
    except ImportError as e:
        raise SystemExit("Install requirements first: pip install -r requirements.txt") from e
    sentence_file = (ROOT / args.sentences).resolve()
    sentences = [x.strip() for x in sentence_file.read_text(encoding="utf-8").splitlines() if x.strip()]
    if len(sentences) < 10:
        raise SystemExit(f"Need at least 10 non-empty sentences; found {len(sentences)}.")
    # sherpa-onnx 1.13.x Python API uses OfflineTtsConfig / OfflineTtsModelConfig
    tts_config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=args.model,
                tokens=args.tokens,
                data_dir=args.data_dir,
            ),
            num_threads=args.threads,
            provider=args.provider,
        )
    )
    tts = sherpa_onnx.OfflineTts(tts_config)
    out_dir = ROOT / args.output_dir / args.language
    out_dir.mkdir(parents=True, exist_ok=True)
    results = []
    for i, sentence in enumerate(sentences[:10], 1):
        start = time.perf_counter()
        audio = tts.generate(sentence, sid=args.speaker_id, speed=args.speed)
        elapsed = time.perf_counter() - start
        samples = np.asarray(audio.samples, dtype=np.float32)
        duration = len(samples) / audio.sample_rate
        path = out_dir / f"{args.language}_{i:02d}.wav"
        sf.write(path, samples, audio.sample_rate)
        rtf = elapsed / duration if duration else float("inf")
        results.append({"sentence": sentence, "wav": str(path), "audio_seconds": duration,
                        "synthesis_seconds": elapsed, "rtf": rtf, "sample_rate": audio.sample_rate})
        print(f"{path.name}: audio={duration:.3f}s synth={elapsed:.3f}s RTF={rtf:.4f}")
    report = {"language": args.language, "model": args.model, "sentences": len(results),
              "mean_rtf": float(np.mean([r["rtf"] for r in results])),
              "max_rtf": float(max(r["rtf"] for r in results)), "results": results,
              "note": "Measured on this host; not an Android measurement."}
    report_path = out_dir / "tts_results.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\nSUMMARY\n" + json.dumps({k:v for k,v in report.items() if k != "results"}, indent=2))
    print(f"Saved WAVs and report under {out_dir}")

if __name__ == "__main__": main()
