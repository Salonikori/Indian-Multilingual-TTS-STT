#!/usr/bin/env python3
"""
Test script for prepare_real_speech_corpus.py without downloading large datasets.

Creates a small mock corpus to verify the pipeline works correctly before
attempting to download real datasets like FLEURS or Common Voice.
"""

import json
import numpy as np
import soundfile as sf
from pathlib import Path

def create_mock_corpus():
    """Create a small mock corpus for testing."""
    
    root = Path(__file__).parent.parent
    output_dir = root / "models_lab" / "test_audio_real_mock"
    
    print("Creating mock real speech corpus for testing...")
    print(f"Output directory: {output_dir}")
    
    # Create directories
    (output_dir / "hi").mkdir(parents=True, exist_ok=True)
    (output_dir / "en").mkdir(parents=True, exist_ok=True)
    
    # Mock data
    mock_samples = {
        "hi": [
            ("hi_001.wav", "नमस्ते, मेरा नाम राजू है।"),
            ("hi_002.wav", "आज का मौसम बहुत सुहावना है।"),
            ("hi_003.wav", "मुझे हिंदी भाषा पसंद है।")
        ],
        "en": [
            ("en_001.wav", "Hello, my name is John."),
            ("en_002.wav", "The weather is nice today."),
            ("en_003.wav", "I like learning new languages.")
        ]
    }
    
    # Generate mock audio files (1 second of synthetic sine wave)
    sample_rate = 16000
    duration = 1.0
    t = np.linspace(0, duration, int(sample_rate * duration), False)
    
    all_samples = {}
    
    for language, samples in mock_samples.items():
        lang_samples = []
        
        for filename, transcription in samples:
            # Generate different frequency for each file (mock speech variation)
            freq = 440 + len(filename) * 50  # Vary frequency
            audio = 0.3 * np.sin(2 * np.pi * freq * t)
            
            # Add some noise to make it more realistic
            noise = 0.05 * np.random.randn(len(audio))
            audio = audio + noise
            
            # Save as WAV
            filepath = output_dir / language / filename
            sf.write(filepath, audio, sample_rate, format='WAV', subtype='PCM_16')
            
            lang_samples.append((filename, transcription))
            print(f"  Created {language}/{filename}")
        
        all_samples[language] = lang_samples
    
    # Create references.tsv
    references_file = output_dir / "references.tsv"
    with references_file.open('w', encoding='utf-8', newline='') as f:
        f.write("language\tfilename\treference\n")
        for language, samples in all_samples.items():
            for filename, transcription in samples:
                f.write(f"{language}\t{language}/{filename}\t{transcription}\n")
    
    # Create metadata
    dataset_info = {
        "dataset": "mock",
        "languages": ["hi", "en"],
        "num_samples_requested": 3,
        "created_at": "2024-10-02",
        "source": "test_corpus_creator.py",
        "license": "Mock data (CC0 1.0 equivalent)",
        "samples_by_language": {
            lang: {
                "samples_downloaded": len(samples),
                "samples_saved": len(samples),
                "average_duration": 1.0,
                "source_dataset": "mock_synthetic"
            }
            for lang, samples in all_samples.items()
        }
    }
    
    metadata_file = output_dir / "dataset_info.json"
    with metadata_file.open('w', encoding='utf-8') as f:
        json.dump(dataset_info, f, indent=2, ensure_ascii=False)
    
    print(f"\nMock corpus created:")
    print(f"  References: {references_file}")
    print(f"  Metadata: {metadata_file}")
    print(f"  Total samples: {sum(len(samples) for samples in all_samples.values())}")
    
    print(f"\nTest with:")
    print(f"python models_lab/test_stt.py --references {references_file} --audio-dir {output_dir} \\")
    print(f"  --model-type whisper --language en \\")
    print(f"  --encoder models_lab/models/stt/en/encoder.int8.onnx \\")
    print(f"  --decoder models_lab/models/stt/en/decoder.int8.onnx \\")
    print(f"  --tokens models_lab/models/stt/en/tokens.txt")

if __name__ == "__main__":
    create_mock_corpus()