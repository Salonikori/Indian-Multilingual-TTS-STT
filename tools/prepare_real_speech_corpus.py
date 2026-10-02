#!/usr/bin/env python3
"""
Create real speech test corpus for iTantra WER evaluation.

Downloads ~30 short test utterances per language from Google FLEURS (or Mozilla Common Voice)
and creates a proper test corpus to replace TTS-generated synthetic audio.

This addresses the requirement that TTS-generated audio is invalid for STT accuracy measurement
(circular validation). Real human speech is needed for proper WER assessment.

Target languages: Hindi (hi_in), English (en_us)
Output: models_lab/test_audio_real/ with proper WAV files and references.tsv

Data sources:
- Google FLEURS: https://huggingface.co/datasets/google/fleurs
- Mozilla Common Voice: https://huggingface.co/datasets/mozilla-foundation/common_voice_13_0

License compliance:
- FLEURS: CC BY 4.0
- Common Voice: CC0 1.0 (Public Domain)
"""

import os
import sys
import json
import shutil
import logging
from pathlib import Path
from typing import List, Dict, Tuple, Optional
import argparse

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def check_dependencies():
    """Check if required packages are available."""
    missing = []
    
    try:
        import librosa
    except ImportError:
        missing.append("librosa")
    
    try:
        import soundfile as sf
    except ImportError:
        missing.append("soundfile")
    
    try:
        from datasets import load_dataset
    except ImportError:
        missing.append("datasets")
    
    if missing:
        print("Missing required packages. Install with:")
        print(f"pip install {' '.join(missing)}")
        sys.exit(1)

def download_fleurs_samples(language: str, num_samples: int = 30) -> List[Dict]:
    """
    Download samples from Google FLEURS dataset.
    
    Args:
        language: Language code (hi_in for Hindi, en_us for English)
        num_samples: Number of samples to download
    
    Returns:
        List of sample dictionaries with audio and transcription
    """
    try:
        from datasets import load_dataset
        
        logger.info(f"Loading FLEURS dataset for {language}...")
        
        # Load FLEURS test split for the specified language
        dataset = load_dataset("google/fleurs", language, split="test", streaming=True)
        
        samples = []
        
        logger.info(f"Extracting {num_samples} samples from FLEURS {language} test set...")
        
        for i, sample in enumerate(dataset):
            if len(samples) >= num_samples:
                break
            
            # Extract necessary information
            audio_data = sample["audio"]["array"]
            sample_rate = sample["audio"]["sampling_rate"]
            transcription = sample["transcription"].strip()
            
            # Skip very short utterances (< 1 second) or very long ones (> 10 seconds)
            duration = len(audio_data) / sample_rate
            if duration < 1.0 or duration > 10.0:
                continue
            
            # Skip empty transcriptions
            if not transcription:
                continue
            
            samples.append({
                "audio": audio_data,
                "sample_rate": sample_rate,
                "transcription": transcription,
                "duration": duration,
                "source": f"FLEURS_{language}",
                "index": i
            })
            
            if len(samples) % 5 == 0:
                logger.info(f"  Collected {len(samples)} samples...")
        
        logger.info(f"Successfully collected {len(samples)} samples from FLEURS {language}")
        return samples
        
    except Exception as e:
        logger.error(f"Error downloading FLEURS {language}: {e}")
        return []

def download_common_voice_samples(language: str, num_samples: int = 30) -> List[Dict]:
    """
    Download samples from Mozilla Common Voice dataset.
    
    Args:
        language: Language code (hi for Hindi, en for English)
        num_samples: Number of samples to download
    
    Returns:
        List of sample dictionaries with audio and transcription
    """
    try:
        from datasets import load_dataset
        
        logger.info(f"Loading Common Voice dataset for {language}...")
        
        # Load Common Voice test split
        dataset = load_dataset("mozilla-foundation/common_voice_13_0", language, split="test", streaming=True)
        
        samples = []
        
        logger.info(f"Extracting {num_samples} samples from Common Voice {language} test set...")
        
        for i, sample in enumerate(dataset):
            if len(samples) >= num_samples:
                break
            
            # Extract necessary information
            audio_data = sample["audio"]["array"]
            sample_rate = sample["audio"]["sampling_rate"]
            transcription = sample["sentence"].strip()
            
            # Skip very short utterances (< 1 second) or very long ones (> 10 seconds)
            duration = len(audio_data) / sample_rate
            if duration < 1.0 or duration > 10.0:
                continue
            
            # Skip empty transcriptions
            if not transcription:
                continue
            
            samples.append({
                "audio": audio_data,
                "sample_rate": sample_rate,
                "transcription": transcription,
                "duration": duration,
                "source": f"CommonVoice_{language}",
                "index": i
            })
            
            if len(samples) % 5 == 0:
                logger.info(f"  Collected {len(samples)} samples...")
        
        logger.info(f"Successfully collected {len(samples)} samples from Common Voice {language}")
        return samples
        
    except Exception as e:
        logger.error(f"Error downloading Common Voice {language}: {e}")
        return []

def convert_and_save_audio(samples: List[Dict], output_dir: Path, language: str) -> List[Tuple[str, str]]:
    """
    Convert audio samples to 16kHz mono WAV and save with transcriptions.
    
    Args:
        samples: List of audio samples
        output_dir: Output directory
        language: Language code for filename prefix
    
    Returns:
        List of (filename, transcription) tuples
    """
    import librosa
    import soundfile as sf
    
    # Create language-specific directory
    lang_dir = output_dir / language
    lang_dir.mkdir(parents=True, exist_ok=True)
    
    saved_samples = []
    
    logger.info(f"Converting and saving {len(samples)} audio files for {language}...")
    
    for i, sample in enumerate(samples):
        try:
            # Generate filename
            filename = f"{language}_{i+1:03d}.wav"
            filepath = lang_dir / filename
            
            # Convert to 16kHz mono if needed
            audio = sample["audio"]
            sr = sample["sample_rate"]
            
            # Convert to mono if stereo
            if len(audio.shape) > 1:
                audio = librosa.to_mono(audio.T)
            
            # Resample to 16kHz if needed
            if sr != 16000:
                audio = librosa.resample(audio, orig_sr=sr, target_sr=16000)
            
            # Save as WAV
            sf.write(filepath, audio, 16000, format='WAV', subtype='PCM_16')
            
            saved_samples.append((filename, sample["transcription"]))
            
            if (i + 1) % 10 == 0:
                logger.info(f"  Saved {i + 1} files...")
                
        except Exception as e:
            logger.warning(f"Failed to save sample {i}: {e}")
            continue
    
    logger.info(f"Successfully saved {len(saved_samples)} audio files for {language}")
    return saved_samples

def create_references_tsv(samples_by_lang: Dict[str, List[Tuple[str, str]]], output_file: Path, dataset_info: Dict):
    """
    Create references.tsv file with all samples and metadata.
    
    Args:
        samples_by_lang: Dictionary mapping language -> [(filename, transcription), ...]
        output_file: Output TSV file path
        dataset_info: Metadata about the datasets used
    """
    logger.info(f"Creating references.tsv at {output_file}...")
    
    with output_file.open('w', encoding='utf-8', newline='') as f:
        # Write header
        f.write("language\tfilename\treference\n")
        
        # Write samples
        for language, samples in samples_by_lang.items():
            for filename, transcription in samples:
                # Use relative path for filename
                f.write(f"{language}\t{language}/{filename}\t{transcription}\n")
    
    logger.info(f"Created references.tsv with {sum(len(samples) for samples in samples_by_lang.values())} samples")
    
    # Also create a metadata file
    metadata_file = output_file.parent / "dataset_info.json"
    with metadata_file.open('w', encoding='utf-8') as f:
        json.dump(dataset_info, f, indent=2, ensure_ascii=False)
    
    logger.info(f"Created dataset metadata at {metadata_file}")

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", choices=["fleurs", "common_voice"], default="fleurs",
                       help="Dataset to use (default: fleurs)")
    parser.add_argument("--languages", nargs="+", default=["hi", "en"],
                       help="Languages to download (default: hi en)")
    parser.add_argument("--num-samples", type=int, default=30,
                       help="Number of samples per language (default: 30)")
    parser.add_argument("--output-dir", default="models_lab/test_audio_real",
                       help="Output directory (default: models_lab/test_audio_real)")
    parser.add_argument("--dry-run", action="store_true",
                       help="Show what would be downloaded without actually downloading")
    
    args = parser.parse_args()
    
    # Check dependencies
    check_dependencies()
    
    # Set up paths
    root = Path(__file__).parent.parent
    output_dir = root / args.output_dir
    
    if args.dry_run:
        print("DRY RUN - Would download:")
        print(f"  Dataset: {args.dataset}")
        print(f"  Languages: {args.languages}")
        print(f"  Samples per language: {args.num_samples}")
        print(f"  Output directory: {output_dir}")
        return
    
    print("iTantra Real Speech Corpus Creator")
    print("=" * 50)
    print(f"Dataset: {args.dataset}")
    print(f"Languages: {args.languages}")
    print(f"Samples per language: {args.num_samples}")
    print(f"Output directory: {output_dir}")
    print()
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Download and process samples
    all_samples = {}
    dataset_info = {
        "dataset": args.dataset,
        "languages": args.languages,
        "num_samples_requested": args.num_samples,
        "created_at": "2024-10-02",
        "source": "prepare_real_speech_corpus.py",
        "license": "CC BY 4.0 (FLEURS)" if args.dataset == "fleurs" else "CC0 1.0 (Common Voice)",
        "samples_by_language": {}
    }
    
    for language in args.languages:
        logger.info(f"\n--- Processing {language} ---")
        
        # Map language codes for datasets
        if args.dataset == "fleurs":
            # FLEURS uses specific locale codes
            dataset_lang = "hi_in" if language == "hi" else "en_us"
            samples = download_fleurs_samples(dataset_lang, args.num_samples)
        else:
            # Common Voice uses simple language codes
            samples = download_common_voice_samples(language, args.num_samples)
        
        if not samples:
            logger.warning(f"No samples downloaded for {language}")
            continue
        
        # Convert and save audio files
        saved_files = convert_and_save_audio(samples, output_dir, language)
        all_samples[language] = saved_files
        
        # Update metadata
        dataset_info["samples_by_language"][language] = {
            "samples_downloaded": len(samples),
            "samples_saved": len(saved_files),
            "average_duration": sum(s["duration"] for s in samples) / len(samples),
            "source_dataset": f"{args.dataset}_{dataset_lang if args.dataset == 'fleurs' else language}"
        }
    
    if not all_samples:
        logger.error("No samples were successfully downloaded for any language")
        return
    
    # Create references.tsv
    references_file = output_dir / "references.tsv"
    create_references_tsv(all_samples, references_file, dataset_info)
    
    # Print summary
    print("\n" + "=" * 50)
    print("CORPUS CREATION COMPLETE")
    print("=" * 50)
    
    total_samples = sum(len(samples) for samples in all_samples.values())
    print(f"Total samples: {total_samples}")
    
    for language, samples in all_samples.items():
        print(f"  {language}: {len(samples)} samples")
    
    print(f"\nFiles created:")
    print(f"  Audio files: {output_dir}/{{hi,en}}/{{lang}}_001.wav, ...")
    print(f"  References: {references_file}")
    print(f"  Metadata: {output_dir}/dataset_info.json")
    
    print(f"\nDataset info:")
    print(f"  Source: {dataset_info['dataset'].upper()}")
    print(f"  License: {dataset_info['license']}")
    
    print(f"\nNext steps:")
    print(f"1. Test STT accuracy:")
    print(f"   python models_lab/test_stt.py --references {references_file} --audio-dir {output_dir} --model-type whisper --language en \\")
    print(f"     --encoder models_lab/models/stt/en/encoder.int8.onnx \\")
    print(f"     --decoder models_lab/models/stt/en/decoder.int8.onnx \\")
    print(f"     --tokens models_lab/models/stt/en/tokens.txt")
    print(f"2. Results will be labeled as 'real human speech ({args.dataset.upper()} test subset, N={total_samples})'")

if __name__ == "__main__":
    main()