#!/usr/bin/env python3
"""
Strict model download script for iTantra CI/CD pipeline.
Downloads sherpa-onnx models from k2-fsa GitHub releases.
Exits non-zero on any failure.
"""

import os
import sys
import requests
import tarfile
import hashlib
import shutil
from pathlib import Path

# Model URLs from k2-fsa/sherpa-onnx GitHub releases
MODELS = {
    "whisper_tiny_en": {
        "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2",
        "files": {
            "sherpa-onnx-whisper-tiny.en/tiny.en-encoder.int8.onnx": "en/stt/encoder.int8.onnx",
            "sherpa-onnx-whisper-tiny.en/tiny.en-decoder.int8.onnx": "en/stt/decoder.int8.onnx",
            "sherpa-onnx-whisper-tiny.en/tiny.en-tokens.txt": "en/stt/tokens.txt"
        }
    },
    "piper_en_lessac": {
        "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
        "files": {
            "vits-piper-en_US-lessac-medium/en_US-lessac-medium.onnx": "en/tts/model.onnx",
            "vits-piper-en_US-lessac-medium/tokens.txt": "en/tts/tokens.txt",
            "vits-piper-en_US-lessac-medium/espeak-ng-data": "en/tts/espeak-ng-data"
        }
    },
    "whisper_base_multilingual": {
        "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.en.tar.bz2",
        "files": {
            "sherpa-onnx-whisper-base.en/base.en-encoder.int8.onnx": "hi/stt/encoder.int8.onnx",
            "sherpa-onnx-whisper-base.en/base.en-decoder.int8.onnx": "hi/stt/decoder.int8.onnx",
            "sherpa-onnx-whisper-base.en/base.en-tokens.txt": "hi/stt/tokens.txt"
        }
    },
    "piper_hi_rohan": {
        "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium.tar.bz2",
        "files": {
            "vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx": "hi/tts/model.onnx",
            "vits-piper-hi_IN-rohan-medium/tokens.txt": "hi/tts/tokens.txt",
            "vits-piper-hi_IN-rohan-medium/espeak-ng-data": "hi/tts/espeak-ng-data"
        }
    },
    "silero_vad": {
        "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        "files": {
            "silero_vad.onnx": "vad/silero_vad.onnx"
        }
    }
}

def sha256_file(filepath):
    """Calculate SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        for chunk in iter(lambda: f.read(4096), b""):
            h.update(chunk)
    return h.hexdigest()

def download_file(url, dest_path):
    """Download file with progress indication."""
    print(f"Downloading {url}")
    
    try:
        response = requests.get(url, stream=True)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        downloaded = 0
        
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        
        with open(dest_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        progress = (downloaded / total_size) * 100
                        print(f"\rProgress: {progress:.1f}%", end='', flush=True)
        
        print()  # New line after progress
        return True
        
    except Exception as e:
        print(f"Download failed: {e}")
        return False

def extract_archive(archive_path, extract_to):
    """Extract tar.bz2 archive."""
    print(f"Extracting {archive_path.name}")
    
    try:
        with tarfile.open(archive_path, 'r:bz2') as tar:
            tar.extractall(extract_to)
        return True
    except Exception as e:
        print(f"Extraction failed: {e}")
        return False

def copy_files(source_dir, files_map, assets_models):
    """Copy model files to assets structure."""
    
    for source_file, target_path in files_map.items():
        source_path = source_dir / source_file
        target_full_path = assets_models / target_path
        
        if source_path.is_dir():
            # Copy directory recursively (e.g., espeak-ng-data)
            if target_full_path.exists():
                shutil.rmtree(target_full_path)
            shutil.copytree(source_path, target_full_path)
            print(f"  Copied directory {source_file} -> {target_path}")
            
        elif source_path.exists():
            # Copy file
            target_full_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_path, target_full_path)
            
            # Print file info
            size = target_full_path.stat().st_size
            sha256 = sha256_file(target_full_path)
            print(f"  Copied {source_file} -> {target_path}")
            print(f"    Size: {size:,} bytes")
            print(f"    SHA-256: {sha256}")
            
        else:
            print(f"ERROR: Source file not found: {source_file}")
            return False
    
    return True

def main():
    print("iTantra Strict Model Download Script")
    print("===================================")
    
    # Get the project root directory
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    assets_models = project_root / "app" / "src" / "main" / "assets" / "models"
    downloads_dir = project_root / "downloads"
    
    print(f"Project root: {project_root}")
    print(f"Assets models: {assets_models}")
    print(f"Downloads cache: {downloads_dir}")
    
    if not assets_models.exists():
        print("ERROR: Assets models directory not found")
        sys.exit(1)
    
    # Create downloads directory
    downloads_dir.mkdir(exist_ok=True)
    
    success_count = 0
    total_models = len(MODELS)
    
    for model_id, model_info in MODELS.items():
        print(f"\n📦 Processing {model_id}")
        
        url = model_info["url"]
        
        if url.endswith('.onnx'):
            # Direct file download (silero_vad)
            filename = url.split('/')[-1]
            file_path = downloads_dir / filename
            
            if not file_path.exists():
                if not download_file(url, file_path):
                    print(f"❌ Failed to download {model_id}")
                    sys.exit(1)
            
            # Copy to destination
            target_path = assets_models / model_info["files"][filename]
            target_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(file_path, target_path)
            
            # Print file info
            size = target_path.stat().st_size
            sha256 = sha256_file(target_path)
            print(f"  Size: {size:,} bytes")
            print(f"  SHA-256: {sha256}")
            
        else:
            # Archive download
            archive_name = url.split('/')[-1]
            archive_path = downloads_dir / archive_name
            
            if not archive_path.exists():
                if not download_file(url, archive_path):
                    print(f"❌ Failed to download {model_id}")
                    sys.exit(1)
            
            # Extract archive
            extract_dir = downloads_dir / f"extract_{model_id}"
            if extract_dir.exists():
                shutil.rmtree(extract_dir)
            extract_dir.mkdir()
            
            if not extract_archive(archive_path, extract_dir):
                print(f"❌ Failed to extract {model_id}")
                sys.exit(1)
            
            # Copy files to assets
            if not copy_files(extract_dir, model_info["files"], assets_models):
                print(f"❌ Failed to copy files for {model_id}")
                sys.exit(1)
            
            # Clean up extraction directory
            shutil.rmtree(extract_dir)
        
        success_count += 1
        print(f"✅ Successfully processed {model_id}")
    
    print(f"\n📊 Summary: {success_count}/{total_models} models processed successfully")
    
    # Verify all 13 required files exist
    required_files = [
        "hi/stt/encoder.int8.onnx",
        "hi/stt/decoder.int8.onnx", 
        "hi/stt/tokens.txt",
        "hi/tts/model.onnx",
        "hi/tts/tokens.txt",
        "hi/tts/espeak-ng-data/phontab",
        "en/stt/encoder.int8.onnx",
        "en/stt/decoder.int8.onnx",
        "en/stt/tokens.txt", 
        "en/tts/model.onnx",
        "en/tts/tokens.txt",
        "en/tts/espeak-ng-data/phontab",
        "vad/silero_vad.onnx"
    ]
    
    missing_files = []
    total_size = 0
    
    for file_path in required_files:
        full_path = assets_models / file_path
        if full_path.exists():
            total_size += full_path.stat().st_size
        else:
            missing_files.append(file_path)
    
    if missing_files:
        print(f"\n❌ Missing required files:")
        for file_path in missing_files:
            print(f"  - {file_path}")
        sys.exit(1)
    else:
        print(f"\n✅ All 13 required files present")
        print(f"📊 Total models size: {total_size / 1024 / 1024:.1f} MB")
    
    print("\n🎉 Model download completed successfully!")

if __name__ == "__main__":
    main()