#!/usr/bin/env python3
"""
Test script for prepare_real_speech_corpus.py

Creates a small sample corpus to verify the tool works correctly
without downloading large datasets.
"""

import sys
import tempfile
import shutil
from pathlib import Path
import subprocess

def test_corpus_tool():
    """Test the corpus preparation tool with a small sample"""
    print("🧪 Testing corpus preparation tool...")
    
    # Create temporary directory for test output
    with tempfile.TemporaryDirectory() as temp_dir:
        output_dir = Path(temp_dir) / "test_corpus"
        
        # Test with minimal samples to avoid large downloads
        cmd = [
            sys.executable, "prepare_real_speech_corpus.py",
            "--language", "en",
            "--dataset", "fleurs", 
            "--samples", "2",  # Very small for testing
            "--output", str(output_dir)
        ]
        
        print(f"Running: {' '.join(cmd)}")
        print("This may take a few minutes to download dataset metadata...")
        
        try:
            result = subprocess.run(cmd, cwd=Path(__file__).parent, 
                                  capture_output=True, text=True, timeout=300)
            
            if result.returncode == 0:
                print("✅ Tool executed successfully!")
                
                # Check if expected files were created
                expected_files = [
                    "references.tsv",
                    "corpus_metadata.json", 
                    "preparation_summary.txt"
                ]
                
                for filename in expected_files:
                    filepath = output_dir / filename
                    if filepath.exists():
                        print(f"✅ Created {filename}")
                    else:
                        print(f"❌ Missing {filename}")
                
                # Check for WAV files
                wav_files = list(output_dir.glob("*.wav"))
                if wav_files:
                    print(f"✅ Created {len(wav_files)} WAV files")
                else:
                    print("❌ No WAV files created")
                
                # Show summary
                summary_file = output_dir / "preparation_summary.txt"
                if summary_file.exists():
                    print("\n📋 Preparation Summary:")
                    print(summary_file.read_text(encoding='utf-8'))
                
            else:
                print("❌ Tool execution failed!")
                print("STDOUT:", result.stdout)
                print("STDERR:", result.stderr)
                return False
                
        except subprocess.TimeoutExpired:
            print("⏱️ Test timed out (5 minutes)")
            print("This might be normal for first run (dataset download)")
            return False
        except Exception as e:
            print(f"❌ Test failed with error: {e}")
            return False
    
    print("\n🎉 Test completed successfully!")
    return True

def check_dependencies():
    """Check if required dependencies are installed"""
    print("🔍 Checking dependencies...")
    
    missing = []
    required = ['datasets', 'soundfile', 'librosa', 'pandas', 'tqdm']
    
    for package in required:
        try:
            __import__(package)
            print(f"✅ {package}")
        except ImportError:
            print(f"❌ {package}")
            missing.append(package)
    
    if missing:
        print(f"\n💡 Install missing packages:")
        print(f"pip install {' '.join(missing)}")
        return False
    
    return True

def main():
    print("🚀 Corpus Preparation Tool Test")
    print("=" * 40)
    
    if not check_dependencies():
        print("\n❌ Missing dependencies. Install them first.")
        return 1
    
    if test_corpus_tool():
        print("\n✅ All tests passed!")
        return 0
    else:
        print("\n❌ Some tests failed.")
        return 1

if __name__ == "__main__":
    sys.exit(main())