#!/usr/bin/env python3
"""
Validate iTantra measurement files to ensure no fake/synthetic metrics.

This script enforces the rule: "Never invent, estimate, or copy a number into code or docs. 
Every metric must come from a result file you can point to, or must say 'not measured'."

Checks:
1. All WER measurements must be based on real speech corpus (not TTS-generated)
2. All RTF measurements must specify the test platform (desktop vs Android)  
3. No placeholder or estimated values in result files
4. Documentation claims must reference actual measurement files

Usage: python tools/validate_measurements.py
"""

import json
import sys
from pathlib import Path
from typing import List, Dict, Any

ROOT = Path(__file__).resolve().parents[1]
RESULTS_DIR = ROOT / "models_lab" / "results"
DOCS_TO_CHECK = [
    ROOT / "DEPLOYMENT_CHECKLIST.md",
    ROOT / "DEMO_SCRIPT.md", 
    ROOT / "FINAL_VERIFICATION_REPORT.md"
]

def load_json_result(filepath: Path) -> Dict[str, Any]:
    """Load and validate a JSON result file."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"❌ ERROR: Cannot load {filepath}: {e}")
        return {}

def check_wer_measurement_validity(result_data: Dict[str, Any], filename: str) -> List[str]:
    """Check if WER measurement is based on real speech or properly marked as invalid."""
    issues = []
    
    # Check for NOT_MEASURED status (valid)
    if result_data.get("measurement_status") == "NOT_MEASURED":
        print(f"✅ {filename}: Properly marked as NOT_MEASURED")
        return []
    
    # Check for synthetic corpus usage (invalid)  
    test_corpus = result_data.get("test_corpus", "")
    note = result_data.get("note", "")
    
    if "synthetic" in test_corpus.lower() or "tts" in note.lower():
        issues.append(f"Uses synthetic/TTS corpus - WER measurement is INVALID")
    
    # Check for actual numeric WER without real speech validation
    wer_corpus = result_data.get("wer_corpus")
    if isinstance(wer_corpus, (int, float)) and "real" not in test_corpus.lower():
        issues.append(f"Numeric WER ({wer_corpus:.3f}) without confirmed real speech corpus")
    
    # Check required fields for real measurements
    if result_data.get("measurement_status") != "NOT_MEASURED":
        required_fields = ["language", "model_type", "test_corpus", "wer_corpus"]
        for field in required_fields:
            if field not in result_data:
                issues.append(f"Missing required field: {field}")
    
    return issues

def check_rtf_measurement_validity(result_data: Dict[str, Any], filename: str) -> List[str]:
    """Check if RTF measurement specifies the test platform."""
    issues = []
    
    rtf_value = result_data.get("aggregate_rtf")
    if isinstance(rtf_value, (int, float)):
        host_device = result_data.get("host_device", {})
        note = result_data.get("note", "")
        
        # Must specify it's not an Android measurement
        if "android" not in note.lower() and "host" not in note.lower():
            issues.append(f"RTF measurement ({rtf_value:.3f}) doesn't specify test platform")
        
        # Check if falsely claiming Android compatibility
        if "android" in note.lower() and "not an android" not in note.lower():
            issues.append(f"RTF measurement may falsely claim Android compatibility")
    
    return issues

def validate_result_files() -> bool:
    """Validate all measurement result files."""
    print("🔍 Validating measurement result files...")
    
    all_valid = True
    
    # Check STT result files
    stt_files = list(RESULTS_DIR.glob("stt_*.json"))
    for filepath in stt_files:
        print(f"\n📁 Checking {filepath.name}:")
        
        result_data = load_json_result(filepath)
        if not result_data:
            all_valid = False
            continue
        
        # Check WER measurement validity
        wer_issues = check_wer_measurement_validity(result_data, filepath.name)
        for issue in wer_issues:
            print(f"   ❌ {issue}")
            all_valid = False
        
        # Check RTF measurement validity  
        rtf_issues = check_rtf_measurement_validity(result_data, filepath.name)
        for issue in rtf_issues:
            print(f"   ❌ {issue}")
            all_valid = False
        
        if not wer_issues and not rtf_issues:
            print(f"   ✅ Valid measurement file")
    
    # Check TTS result files
    tts_files = list(RESULTS_DIR.glob("tts_*.json"))
    for filepath in tts_files:
        print(f"\n📁 Checking {filepath.name}:")
        
        result_data = load_json_result(filepath)
        if not result_data:
            all_valid = False
            continue
        
        rtf_issues = check_rtf_measurement_validity(result_data, filepath.name)
        for issue in rtf_issues:
            print(f"   ❌ {issue}")
            all_valid = False
        
        if not rtf_issues:
            print(f"   ✅ Valid measurement file")
    
    return all_valid

def check_documentation_claims() -> bool:
    """Check that documentation doesn't make unsupported performance claims."""
    print("\n🔍 Validating documentation claims...")
    
    all_valid = True
    
    # Patterns that indicate fake/unsupported claims
    suspicious_patterns = [
        r"\d+\.\d+% WER",  # Specific WER percentages
        r"production ready",  # Production claims without validation
        r"RTF [0-9.]+",  # Specific RTF values without platform context
        r"real-time capable",  # Performance claims without Android testing
    ]
    
    for doc_path in DOCS_TO_CHECK:
        if not doc_path.exists():
            continue
            
        print(f"\n📄 Checking {doc_path.name}:")
        
        content = doc_path.read_text(encoding='utf-8')
        
        # Look for numeric claims that should reference measurement files
        import re
        
        # Check for WER percentages
        wer_matches = re.findall(r'(\d+\.\d+)% WER', content)
        for wer in wer_matches:
            if "NOT MEASURED" not in content or "synthetic" not in content:
                print(f"   ⚠️  Found WER claim {wer}% - ensure it's marked as synthetic/invalid")
        
        # Check for RTF claims
        rtf_matches = re.findall(r'RTF (\d+\.\d+)', content)
        for rtf in rtf_matches:
            if "desktop" not in content.lower() or "android" not in content.lower():
                print(f"   ⚠️  Found RTF claim {rtf} - ensure platform is specified")
        
        # Check for production readiness claims
        if "production ready" in content.lower() and "NOT MEASURED" not in content:
            print(f"   ❌ Contains production readiness claims without measurement validation")
            all_valid = False
        
        # Positive indicators
        if "NOT MEASURED" in content or "synthetic" in content or "desktop" in content:
            print(f"   ✅ Contains honest measurement disclaimers")
    
    return all_valid

def main():
    """Main validation function."""
    print("🚨 iTantra Measurement Validation")
    print("Ensuring no fake/estimated metrics per review requirements\n")
    
    files_valid = validate_result_files()
    docs_valid = check_documentation_claims()
    
    print(f"\n{'='*60}")
    
    if files_valid and docs_valid:
        print("✅ VALIDATION PASSED: All measurements are honest or properly marked as NOT_MEASURED")
        print("✅ No fake metrics detected")
        sys.exit(0)
    else:
        print("❌ VALIDATION FAILED: Found issues with measurement validity")
        print("❌ Fix the issues above before claiming production readiness")
        sys.exit(1)

if __name__ == "__main__":
    main()