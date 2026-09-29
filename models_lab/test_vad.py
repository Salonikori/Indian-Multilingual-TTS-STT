#!/usr/bin/env python3
"""Detect speech regions in a WAV with sherpa-onnx Silero VAD."""
from __future__ import annotations
import argparse, json
from pathlib import Path
import soundfile as sf
import numpy as np

ROOT = Path(__file__).resolve().parent

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("wav", help="Input WAV; mono 16 kHz recommended")
    p.add_argument("--model", default="models/vad/silero_vad.onnx")
    p.add_argument("--threshold", type=float, default=0.5)
    p.add_argument("--min-silence", type=float, default=0.25)
    p.add_argument("--min-speech", type=float, default=0.25)
    p.add_argument("--max-speech", type=float, default=20.0)
    p.add_argument("--output", default="results/vad_segments.json")
    args = p.parse_args()
    try:
        import sherpa_onnx
    except ImportError as e:
        raise SystemExit("Install requirements first: pip install -r requirements.txt") from e
    wav = Path(args.wav)
    if not wav.is_absolute(): wav = ROOT / wav
    model = Path(args.model)
    if not model.is_absolute(): model = ROOT / model
    if not model.is_file(): raise SystemExit(f"VAD model not found: {model}")
    samples, sr = sf.read(wav, dtype="float32", always_2d=False)
    if samples.ndim == 2: samples = samples.mean(axis=1)
    if sr != 16000: raise SystemExit(f"Expected 16000 Hz WAV, got {sr}; resample first.")
    vad = sherpa_onnx.VoiceActivityDetector(
        config=sherpa_onnx.VadModelConfig(
            silero_vad=sherpa_onnx.SileroVadModelConfig(
                model=str(model), threshold=args.threshold, min_silence_duration=args.min_silence,
                min_speech_duration=args.min_speech, max_speech_duration=args.max_speech,
                window_size=512, sample_rate=16000,
            ),
            sample_rate=16000, num_threads=1, provider="cpu",
        ),
        buffer_size_in_seconds=60,
    )
    vad.accept_waveform(np.asarray(samples, dtype=np.float32))
    vad.flush()
    segments = []
    while not vad.empty():
        s = vad.front
        segments.append({"start": s.start, "duration": len(s.samples)/sr, "end": s.start + len(s.samples)/sr})
        vad.pop()
    for seg in segments: print(f"{seg['start']:.3f}s - {seg['end']:.3f}s ({seg['duration']:.3f}s)")
    out = ROOT / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"wav": str(wav), "segments": segments}, indent=2), encoding="utf-8")
    print(f"{len(segments)} segments; saved {out}")

if __name__ == "__main__": main()
