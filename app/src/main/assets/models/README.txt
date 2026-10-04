Model files for iTantra offline STT/TTS

STRUCTURE:
- hi/stt/model.int8.onnx - Hindi NeMo CTC STT model (~100MB)
- hi/stt/tokens.txt - Hindi STT tokens
- hi/tts/model.onnx - Hindi Piper VITS TTS model 
- hi/tts/tokens.txt - Hindi TTS tokens
- hi/tts/espeak-ng-data/ - Hindi Piper phoneme data (phontab, phondata, phonindex)

- en/stt/encoder.int8.onnx - English Whisper STT encoder (~40MB)
- en/stt/decoder.int8.onnx - English Whisper STT decoder (~40MB)  
- en/stt/tokens.txt - English STT tokens
- en/tts/model.onnx - English Piper VITS TTS model
- en/tts/tokens.txt - English TTS tokens
- en/tts/espeak-ng-data/ - English Piper phoneme data

DEPLOYMENT:
1. LOCAL DEV: Placeholder files (small) enable development without real models
2. CI BUILD: scripts/download_models.py replaces placeholders with real models
3. RUNTIME: AssetModelInstaller copies models from assets/ to app filesDir on first run

SOURCES:
- Hindi NeMo CTC: sherpa-onnx releases
- English Whisper: sherpa-onnx tiny.en int8 
- Piper TTS: hi_IN-rohan-medium, en_US models
- espeak-ng-data: Piper TTS phoneme data

TOTAL SIZE: ~200MB (estimated with real models)