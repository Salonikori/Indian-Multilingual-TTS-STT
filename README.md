# iTantra - Secure Multilingual Voice Communication

**Production-Ready Offline Voice Communication System**

iTantra is a secure, offline voice communication application that enables real-time multilingual conversation between two phones using only Bluetooth Classic connectivity. No internet connection required.

## ✨ **Key Features**

🔒 **Complete Offline Operation** - No internet permissions, no network APIs  
🗣️ **Multilingual STT/TTS** - Hindi and English speech recognition and synthesis  
📱 **Dual Communication Modes** - Push-to-Talk (PTT) and Phone modes  
🚨 **Emergency Alerts** - One-tap emergency messages with priority routing  
🔗 **Bluetooth Classic Transport** - Secure local communication via RFCOMM  
⚡ **Real-time Processing** - Low-latency voice pipeline with streaming TTS  
🔐 **Security Hardened** - Audio stays local, only text transmitted  
📊 **Performance Instrumented** - Complete pipeline timing and memory tracking  

## 🚀 **Quick Start**

### Requirements
- Two Android phones (API 21+)
- Bluetooth Classic support
- ~1GB storage for language models

### Installation
1. **Clone Repository**
   ```bash
   git clone https://github.com/Salonikori/Indian-Multilingual-TTS-STT.git
   cd iTantra-android-smoke
   ```

2. **Install Models** (see [PHASE2_INSTALL.md](PHASE2_INSTALL.md))
   ```bash
   cd optional_model_manager
   python install_models.py
   ```

3. **Build and Deploy**
   ```bash
   ./gradlew assembleDebug
   # Install APK on both phones
   ```

### Usage
1. **Pair Phones** - Use Android Settings → Bluetooth to pair devices
2. **Launch iTantra** - Open app on both phones
3. **Load Language** - Select Hindi/English and press "Load"
4. **Connect** - One phone hosts, other connects
5. **Communicate** - Use PTT button or Phone mode to talk

## 📋 **Development Phases**

### ✅ Phase 1-3: Foundation
- Offline STT/TTS engine integration
- Language model management  
- Benchmark and performance testing

### ✅ Phase 4: TTS and Alert Path ([PHASE4_TTS_ALERT_TESTING.md](PHASE4_TTS_ALERT_TESTING.md))
- Streaming TTS with sentence-level processing
- Alert priority system with volume override
- Performance metrics and timing verification

### ✅ Phase 5: Bluetooth Link ([PHASE5_BLUETOOTH_TESTING.md](PHASE5_BLUETOOTH_TESTING.md))  
- Bluetooth Classic transport implementation
- Message ACK system and duplicate detection
- Connection resilience and automatic retry

### ✅ Phase 6: Full Communication Loop ([PHASE6_FULL_LOOP_TESTING.md](PHASE6_FULL_LOOP_TESTING.md))
- Production main screen and interface
- Complete pipeline: mic → VAD → STT → transport → TTS → audio
- PTT/Phone mode behaviors with mic gating
- Language mismatch handling

### ✅ Phase 7: Security Audit ([PHASE7_SECURITY_AUDIT.md](PHASE7_SECURITY_AUDIT.md))
- Comprehensive pipeline timing instrumentation
- Security hard rules verification
- Memory usage validation
- APK security audit confirmation

## 🔐 **Security Guarantees**

✅ **No Internet Permission** - App cannot access network  
✅ **No Audio Transmission** - Only text sent over Bluetooth  
✅ **Offline Operation** - Works without Wi-Fi or cellular  
✅ **Local Processing** - All STT/TTS happens on device  
✅ **Memory Safe** - Proper language model lifecycle  
✅ **Runtime Validation** - Active guards against data leaks  

## 📊 **Performance**

- **End-to-End Latency**: ~2-4 seconds (PTT press to audio output)
- **Memory Usage**: ~300-500 MB with one language loaded
- **Model Loading**: ~5-15 seconds per language
- **Streaming TTS**: First sentence plays before full synthesis
- **Connection Range**: ~10 meters (Bluetooth Classic)

## 🛠️ **Architecture**

### Communication Pipeline
```
Microphone → AudioCapture → VAD → STT → MessagePayload → 
BluetoothTransport → Remote Phone → TTS → PlaybackRouter → Speaker
```

### Key Components
- **CommunicationActivity** - Main user interface
- **ConversationStateMachine** - Pipeline coordination
- **BluetoothClassicTransport** - Secure local messaging
- **LanguageManager** - STT/TTS model lifecycle
- **PlaybackRouter** - Alert vs normal audio routing

## 📱 **Supported Languages**

- **Hindi (hi)** - Full STT and TTS support
- **English (en)** - Full STT and TTS support
- **Extensible** - Architecture supports additional languages

## 🧪 **Testing**

Each phase includes comprehensive testing documentation:

- **Phase 4**: TTS streaming, alert priority testing
- **Phase 5**: Bluetooth reliability, two-phone messaging  
- **Phase 6**: End-to-end communication workflows
- **Phase 7**: Security audit and performance validation

## 🤝 **Contributing**

This project demonstrates secure, offline voice communication using open-source models. Contributions welcome for:

- Additional language support
- Performance optimizations  
- Security enhancements
- Testing improvements

## 📄 **License**

[Add your license here]

## 🔗 **Links**

- [Installation Guide](PHASE2_INSTALL.md)
- [Security Audit](PHASE7_SECURITY_AUDIT.md)
- [Performance Benchmarks](BENCHMARKS.md)
- [Architecture Documentation](DEMO.md)

---

**iTantra: Secure. Multilingual. Offline. Ready.**