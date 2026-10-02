#!/usr/bin/env python3
"""
Real Speech Corpus Preparation Tool

Downloads and prepares authentic speech datasets (FLEURS, Common Voice) for 
WER evaluation of iTantra STT models. Creates test corpora with real human
speech instead of synthetic TTS-generated samples.

Usage:
    python prepare_real_speech_corpus.py --language hi --dataset fleurs --samples 50
    python prepare_real_speech_corpus.py --language en --dataset common_voice --samples 30

Requirements:
    pip install datasets soundfile librosa pandas tqdm
"""

import argparse
import os
import sys
from pathlib import Path
import json
import csv
from typing import Dict, List, Tuple, Optional
import logging
from dataclasses import dataclass

try:
    from datasets import load_dataset
    import soundfile as sf
    import librosa
    import pandas as pd
    from tqdm import tqdm
except ImportError as e:
    print(f"Error: Missing required dependency: {e}")
    print("Install with: pip install datasets soundfile librosa pandas tqdm")
    sys.exit(1)


@dataclass
class CorpusConfig:
    """Configuration for corpus preparation"""
    language: str
    dataset_name: str
    samples_count: int
    output_dir: str
    target_sample_rate: int = 16000
    max_duration: float = 10.0  # seconds
    min_duration: float = 1.0   # seconds


class RealSpeechCorpusPreparator:
    """Prepares real speech corpora from public datasets"""
    
    # Language code mappings
    FLEURS_LANG_CODES = {
        'hi': 'hi_in',  # Hindi (India)
        'en': 'en_us',  # English (US)
        'gu': 'gu_in',  # Gujarati (India)
        'bn': 'bn_in',  # Bengali (India)
        'ta': 'ta_in',  # Tamil (India)
        'te': 'te_in',  # Telugu (India)
        'mr': 'mr_in',  # Marathi (India)
        'ml': 'ml_in',  # Malayalam (India)
        'kn': 'kn_in',  # Kannada (India)
        'or': 'or_in',  # Odia (India)
    }
    
    COMMON_VOICE_LANG_CODES = {
        'hi': 'hi',     # Hindi
        'en': 'en',     # English
        'gu': 'gu-IN',  # Gujarati
        'bn': 'bn',     # Bengali
        'ta': 'ta',     # Tamil
        'te': 'te',     # Telugu
        'mr': 'mr',     # Marathi
        'ml': 'ml',     # Malayalam
        'kn': 'kn',     # Kannada
        # Note: Odia not available in Common Voice
    }
    
    def __init__(self, config: CorpusConfig):
        self.config = config
        self.logger = self._setup_logging()
        
        # Create output directory
        Path(config.output_dir).mkdir(parents=True, exist_ok=True)
        
    def _setup_logging(self) -> logging.Logger:
        """Setup logging configuration"""
        logger = logging.getLogger(__name__)
        logger.setLevel(logging.INFO)
        
        handler = logging.StreamHandler()
        formatter = logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        
        return logger
    
    def prepare_corpus(self) -> str:
        """Prepare the real speech corpus"""
        self.logger.info(f"Preparing {self.config.dataset_name} corpus for {self.config.language}")
        self.logger.info(f"Target: {self.config.samples_count} samples")
        
        if self.config.dataset_name.lower() == 'fleurs':
            return self._prepare_fleurs_corpus()
        elif self.config.dataset_name.lower() == 'common_voice':
            return self._prepare_common_voice_corpus()
        else:
            raise ValueError(f"Unsupported dataset: {self.config.dataset_name}")
    
    def _prepare_fleurs_corpus(self) -> str:
        """Prepare corpus from Google FLEURS dataset"""
        lang_code = self.FLEURS_LANG_CODES.get(self.config.language)
        if not lang_code:
            raise ValueError(f"Language {self.config.language} not supported in FLEURS")
        
        self.logger.info(f"Loading FLEURS dataset for {lang_code}")
        
        try:
            # Load test split of FLEURS dataset
            dataset = load_dataset("google/fleurs", lang_code, split="test")
        except Exception as e:
            self.logger.error(f"Failed to load FLEURS dataset: {e}")
            raise
        
        return self._process_dataset(dataset, "fleurs")
    
    def _prepare_common_voice_corpus(self) -> str:
        """Prepare corpus from Mozilla Common Voice dataset"""
        lang_code = self.COMMON_VOICE_LANG_CODES.get(self.config.language)
        if not lang_code:
            raise ValueError(f"Language {self.config.language} not supported in Common Voice")
        
        self.logger.info(f"Loading Common Voice dataset for {lang_code}")
        
        try:
            # Use latest Common Voice version (10.0)
            dataset = load_dataset("mozilla-foundation/common_voice_10_0", lang_code, split="test")
        except Exception as e:
            self.logger.error(f"Failed to load Common Voice dataset: {e}")
            # Fallback to older version
            try:
                dataset = load_dataset("mozilla-foundation/common_voice_8_0", lang_code, split="test")
                self.logger.info("Fallback to Common Voice 8.0")
            except Exception as e2:
                self.logger.error(f"Failed to load fallback dataset: {e2}")
                raise
        
        return self._process_dataset(dataset, "common_voice")
    
    def _process_dataset(self, dataset, dataset_source: str) -> str:
        """Process the loaded dataset and create corpus"""
        samples = []
        references = []
        metadata = []
        
        self.logger.info(f"Processing {len(dataset)} available samples")
        
        # Determine audio and text field names based on dataset source
        if dataset_source == "fleurs":
            audio_field = "audio"
            text_field = "transcription"
        else:  # common_voice
            audio_field = "audio"
            text_field = "sentence"
        
        processed_count = 0
        skipped_count = 0
        
        for i, sample in enumerate(tqdm(dataset, desc="Processing samples")):
            if processed_count >= self.config.samples_count:
                break
            
            try:
                # Get audio and text
                audio_data = sample[audio_field]
                text = sample[text_field].strip()
                
                if not text:
                    skipped_count += 1
                    continue
                
                # Get audio array and sampling rate
                audio_array = audio_data['array']
                sample_rate = audio_data['sampling_rate']
                
                # Check duration
                duration = len(audio_array) / sample_rate
                if duration < self.config.min_duration or duration > self.config.max_duration:
                    skipped_count += 1
                    continue
                
                # Resample if necessary
                if sample_rate != self.config.target_sample_rate:
                    audio_array = librosa.resample(
                        audio_array, 
                        orig_sr=sample_rate, 
                        target_sr=self.config.target_sample_rate
                    )
                
                # Save audio file
                filename = f"{self.config.language}_{dataset_source}_{processed_count:04d}.wav"
                filepath = os.path.join(self.config.output_dir, filename)
                
                sf.write(filepath, audio_array, self.config.target_sample_rate)
                
                # Store metadata
                samples.append(filename)
                references.append(text)
                metadata.append({
                    'filename': filename,
                    'reference': text,
                    'duration': duration,
                    'sample_rate': self.config.target_sample_rate,
                    'dataset_source': dataset_source,
                    'original_index': i
                })
                
                processed_count += 1
                
            except Exception as e:
                self.logger.warning(f"Failed to process sample {i}: {e}")
                skipped_count += 1
                continue
        
        # Create references.tsv file for WER evaluation
        references_file = os.path.join(self.config.output_dir, "references.tsv")
        with open(references_file, 'w', encoding='utf-8', newline='') as f:
            writer = csv.writer(f, delimiter='\t')
            writer.writerow(['filename', 'reference'])
            for filename, reference in zip(samples, references):
                writer.writerow([filename, reference])
        
        # Create detailed metadata file
        metadata_file = os.path.join(self.config.output_dir, "corpus_metadata.json")
        with open(metadata_file, 'w', encoding='utf-8') as f:
            json.dump({
                'config': {
                    'language': self.config.language,
                    'dataset': self.config.dataset_name,
                    'target_sample_rate': self.config.target_sample_rate,
                    'samples_requested': self.config.samples_count,
                    'samples_processed': processed_count,
                    'samples_skipped': skipped_count
                },
                'samples': metadata
            }, f, indent=2, ensure_ascii=False)
        
        # Create summary
        summary = f"""
Real Speech Corpus Preparation Complete
======================================

Language: {self.config.language}
Dataset: {self.config.dataset_name}
Samples processed: {processed_count}
Samples skipped: {skipped_count}
Output directory: {self.config.output_dir}

Files created:
- {processed_count} WAV files (16 kHz mono)
- references.tsv (for WER evaluation)
- corpus_metadata.json (detailed metadata)

Usage with test_stt.py:
    python models_lab/test_stt.py --language {self.config.language} --audio_dir {self.config.output_dir}
        """
        
        self.logger.info(summary)
        
        # Write summary to file
        summary_file = os.path.join(self.config.output_dir, "preparation_summary.txt")
        with open(summary_file, 'w', encoding='utf-8') as f:
            f.write(summary)
        
        return self.config.output_dir


def main():
    """Main entry point"""
    parser = argparse.ArgumentParser(
        description="Prepare real speech corpus for WER evaluation",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    # Prepare 50 Hindi samples from FLEURS
    python prepare_real_speech_corpus.py --language hi --dataset fleurs --samples 50
    
    # Prepare 30 English samples from Common Voice
    python prepare_real_speech_corpus.py --language en --dataset common_voice --samples 30
    
    # Prepare Gujarati samples with custom output directory
    python prepare_real_speech_corpus.py --language gu --dataset fleurs --samples 25 --output corpus/gujarati_real
        """
    )
    
    parser.add_argument(
        '--language', '-l',
        required=True,
        choices=['hi', 'en', 'gu', 'bn', 'ta', 'te', 'mr', 'ml', 'kn', 'or'],
        help='Language code (hi=Hindi, en=English, etc.)'
    )
    
    parser.add_argument(
        '--dataset', '-d',
        required=True,
        choices=['fleurs', 'common_voice'],
        help='Dataset source (fleurs or common_voice)'
    )
    
    parser.add_argument(
        '--samples', '-n',
        type=int,
        default=30,
        help='Number of samples to prepare (default: 30)'
    )
    
    parser.add_argument(
        '--output', '-o',
        default=None,
        help='Output directory (default: models_lab/test_audio/real_speech/{language}_{dataset})'
    )
    
    parser.add_argument(
        '--max-duration',
        type=float,
        default=10.0,
        help='Maximum audio duration in seconds (default: 10.0)'
    )
    
    parser.add_argument(
        '--min-duration',
        type=float,
        default=1.0,
        help='Minimum audio duration in seconds (default: 1.0)'
    )
    
    args = parser.parse_args()
    
    # Set default output directory if not specified
    if args.output is None:
        script_dir = Path(__file__).parent
        project_root = script_dir.parent
        args.output = project_root / "models_lab" / "test_audio" / "real_speech" / f"{args.language}_{args.dataset}"
    
    # Create configuration
    config = CorpusConfig(
        language=args.language,
        dataset_name=args.dataset,
        samples_count=args.samples,
        output_dir=str(args.output),
        max_duration=args.max_duration,
        min_duration=args.min_duration
    )
    
    # Prepare corpus
    try:
        preparator = RealSpeechCorpusPreparator(config)
        output_dir = preparator.prepare_corpus()
        print(f"\n✅ Corpus prepared successfully in: {output_dir}")
        print(f"📊 Use with: python models_lab/test_stt.py --language {args.language} --audio_dir {output_dir}")
        
    except Exception as e:
        print(f"\n❌ Failed to prepare corpus: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()