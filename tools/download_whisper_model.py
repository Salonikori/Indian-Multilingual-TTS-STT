#!/usr/bin/env python3
"""
Download Whisper tiny.en model for iTantra English STT replacement.

This script downloads the correct sherpa-onnx Whisper tiny.en INT8 model
to replace the previous streaming model that had 97% WER due to architecture mismatch.

Downloads from: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2
Places files in: models_lab/models/stt/en/

Files:
- tiny.en-encoder.int8.onnx (12 MB) -> encoder.int8.onnx 
- tiny.en-decoder.int8.onnx (105 MB) -> decoder.int8.onnx
- tiny.en-tokens.txt (1 MB) -> tokens.txt

Source: k2-fsa/sherpa-onnx
License: MIT (Whisper/OpenAI)
Expected WER: <10% (vs previous 96.8% with streaming/offline mismatch)
"""
import os
import sys
import subprocess
import tarfile
import shutil
from pathlib import Path
from urllib.request import urlretrieve
from urllib.error import URLError

def download_with_progress(url: str, destination: Path) -> None:
    """Download file with progress indication."""
    def progress_hook(block_num, block_size, total_size):
        if total_size > 0:
            percent = min(100, (block_num * block_size * 100) // total_size)
            mb_downloaded = (block_num * block_size) / (1024 * 1024)
            total_mb = total_size / (1024 * 1024)
            print(f"\r  Progress: {percent:3d}% ({mb_downloaded:.1f}/{total_mb:.1f} MB)", end="", flush=True)
    
    try:
        urlretrieve(url, destination, reporthook=progress_hook)
        print()  # New line after progress
    except URLError as e:
        raise SystemExit(f"Download failed: {e}")

def main():
    # Paths
    root = Path(__file__).parent.parent
    models_dir = root / "models_lab" / "models" / "stt" / "en"
    temp_dir = root / "temp_whisper_download"
    
    # Model info
    model_url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2"
    archive_name = "sherpa-onnx-whisper-tiny.en.tar.bz2"
    
    print("iTantra English STT Model Replacement")
    print("=" * 50)
    print("Downloading Whisper tiny.en INT8 model to fix 97% WER issue")
    print(f"Source: {model_url}")
    print(f"Target: {models_dir}")
    print()
    
    # Check if already exists
    if models_dir.exists() and (models_dir / "encoder.int8.onnx").exists():
        print("✓ Whisper model files already exist")
        encoder_size = (models_dir / "encoder.int8.onnx").stat().st_size / (1024 * 1024)
        decoder_size = (models_dir / "decoder.int8.onnx").stat().st_size / (1024 * 1024)
        print(f"  encoder.int8.onnx: {encoder_size:.1f} MB")
        print(f"  decoder.int8.onnx: {decoder_size:.1f} MB")
        print()
        print("To re-download, delete the models_lab/models/stt/en/ directory first.")
        return
    
    # Create directories
    temp_dir.mkdir(exist_ok=True)
    models_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        # Download
        archive_path = temp_dir / archive_name
        print(f"Downloading {archive_name}...")
        download_with_progress(model_url, archive_path)
        
        # Extract
        print("Extracting archive...")
        with tarfile.open(archive_path, 'r:bz2') as tar:
            tar.extractall(temp_dir)
        
        # Find extracted directory
        extracted_dir = None
        for item in temp_dir.iterdir():
            if item.is_dir() and "whisper" in item.name.lower():
                extracted_dir = item
                break
        
        if not extracted_dir:
            raise SystemExit("Could not find extracted model directory")
        
        print(f"Found extracted directory: {extracted_dir.name}")
        
        # Copy and rename files
        file_mappings = [
            ("tiny.en-encoder.int8.onnx", "encoder.int8.onnx"),
            ("tiny.en-decoder.int8.onnx", "decoder.int8.onnx"),
            ("tiny.en-tokens.txt", "tokens.txt")
        ]
        
        print("Installing model files...")
        for src_name, dst_name in file_mappings:
            src_path = extracted_dir / src_name
            dst_path = models_dir / dst_name
            
            if not src_path.exists():
                print(f"⚠️  Warning: {src_name} not found in archive")
                continue
            
            shutil.copy2(src_path, dst_path)
            size_mb = dst_path.stat().st_size / (1024 * 1024)
            print(f"  ✓ {dst_name}: {size_mb:.1f} MB")
        
        # Verify installation
        required_files = ["encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"]
        missing = [f for f in required_files if not (models_dir / f).exists()]
        
        if missing:
            raise SystemExit(f"Installation incomplete. Missing files: {missing}")
        
        print()
        print("✅ Whisper tiny.en model installed successfully!")
        print()
        print("Model Summary:")
        total_size = 0
        for filename in required_files:
            file_path = models_dir / filename
            size_mb = file_path.stat().st_size / (1024 * 1024)
            total_size += size_mb
            print(f"  {filename}: {size_mb:.1f} MB")
        print(f"  Total: {total_size:.1f} MB")
        
        print()
        print("Next steps:")
        print("1. Test the model: python models_lab/test_stt.py --model-type whisper --language en \\")
        print("   --encoder models_lab/models/stt/en/encoder.int8.onnx \\")
        print("   --decoder models_lab/models/stt/en/decoder.int8.onnx \\")
        print("   --tokens models_lab/models/stt/en/tokens.txt")
        print("2. Install on Android device: python optional_model_manager/install_models.py")
        print("3. Expected WER: <10% (significant improvement from previous 96.8%)")
        
    except Exception as e:
        raise SystemExit(f"Error during installation: {e}")
    
    finally:
        # Cleanup
        if temp_dir.exists():
            shutil.rmtree(temp_dir)

if __name__ == "__main__":
    main()