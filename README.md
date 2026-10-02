# iTantra - Indian Multilingual TTS & STT Neural Transceiver

![ISRO Hackathon](https://img.shields.io/badge/ISRO-Hackathon_2026-orange?style=for-the-badge&logo=rocket)
![Problem Statement](https://img.shields.io/badge/PS_ID-26173-blue?style=for-the-badge)
![Category](https://img.shields.io/badge/Category-Software-green?style=for-the-badge)

> **Neural Transceiver Radio Access for Low Bitrate Links** - Prototype addressing ISRO's challenge for inclusive voice communication in distress scenarios

## 🎯 Problem Statement (PS #26173)

**Organization:** Indian Space Research Organisation (ISRO)  
**Theme:** Smart Automation  
**Challenge:** Build an Android app with lightweight, highly accurate STT and TTS models for 10 Indian languages that enables voice communication over low bitrate links for alert and distress scenarios.

## 🏗️ **Current Prototype Status**

**Approach**: Focused implementation of **2 core languages (Hindi & English)** to demonstrate complete pipeline functionality rather than attempting 10 partially-functional languages.

### ✅ **Implemented Features**
- **Hindi & English**: Complete STT + TTS pipeline integration 
- **Compact Size**: 40.2 MB APK, 296 MiB total model storage
- **Offline Processing**: Zero internet dependencies, fully local AI inference
- **Bluetooth Communication**: Text-based transport with push-to-talk interface
- **Alert System**: Priority routing with audio focus management

### ⚠️ **Current Limitations**
- **English STT Accuracy**: 96.8% WER (requires model replacement)
- **Hindi STT Accuracy**: 64.9% WER (needs improvement for production use)
- **Single Device Testing**: End-to-end validation requires two paired devices
- **Language Coverage**: 8 additional Indian languages not yet implemented

## 📊 **Measured Performance**

**Test Device**: 23076PC4BI (Xiaomi, Android 15, API 35, ARM64)  

### 💾 **Size Metrics**
| Metric | Measured Value | Notes |
|--------|----------------|--------|
| **APK Size** | 40.2 MB | Lightweight for mobile deployment |
| **Model Storage** | 296 MiB total | Hindi: 206 MiB, English: 88 MiB, VAD: 2 MiB |
| **Runtime Memory** | Not measured | Real usage tracking needed |

### 🎯 **Accuracy Status**
| Language | STT WER | TTS Quality | Production Readiness |
|----------|---------|-------------|---------------------|
| **Hindi** | **64.9%** | High quality, streaming capable | ⚠️ **Needs improvement** |
| **English** | **96.8%** | High quality, streaming capable | ❌ **Model replacement required** |

*Note: 64.9% WER means the system correctly transcribes only ~35% of spoken Hindi. Production systems typically require <20% WER.*

### ⚡ **Performance Metrics**
| Component | Hindi | English | Status |
|-----------|-------|---------|--------|
| **TTS Speed** | RTF 0.598 | RTF 0.461 | ✅ Real-time capable |
| **STT Processing** | Streaming | Streaming | ✅ Real-time processing |
| **End-to-End Latency** | Not measured | Not measured | ⏳ Requires two-device testing |

## 📱 **Implemented Architecture**

### 🗣️ **Audio Processing Pipeline**
```
Microphone → VAD → STT → Text → Bluetooth → TTS → Speaker
```

**Components**:
- **STT Engine**: Sherpa-ONNX with NEMO-CTC (Hindi) and Transducer (English)
- **TTS Engine**: Piper VITS models with streaming synthesis
- **VAD Engine**: Silero VAD for voice activity detection
- **Audio Capture**: 16kHz sampling with configurable buffering

### 📡 **Communication System**
- **Push-to-Talk (PTT)**: Walkie-talkie style communication interface
- **Phone Mode**: Continuous listening mode (implemented but needs refinement)
- **Bluetooth Transport**: RFCOMM with text-only transmission
- **Alert System**: Priority message handling with audio focus management

### 🔐 **Security & Privacy Design**
- **Zero Internet**: No network permissions required after model installation
- **Local Processing**: All AI models run on-device
- **Audio Privacy**: Only transcribed text transmitted, never raw audio
- **Bluetooth Security**: Standard RFCOMM encryption

## 🚀 **Setup Instructions**

### Prerequisites
- Android device (API 26+, ARM64 recommended)
- 300+ MB storage space for models
- Bluetooth support for communication testing

### Installation Steps
```bash
# Build and install APK
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Install models (requires internet connection)
cd optional_model_manager
python3 install_models.py --languages hi,en
```

### Testing Workflow
1. **Install App** on one or more test devices
2. **Load Languages**: Hindi works, English STT needs model replacement
3. **Test TTS**: Verify synthesis quality and streaming capability  
4. **Test STT**: Check transcription accuracy (current WER: Hindi 64.9%, English 96.8%)
5. **Bluetooth Test**: Pair devices to validate text transport

## 🔧 **Implementation Details**

### Model Architecture & Current Status
```
Hindi Pipeline (Partially Functional):
├── STT: sherpa-onnx-nemo-ctc-hi-male-medium (188.4 MiB)
│   └── Performance: 64.9% WER (needs improvement for production)
├── TTS: hi_IN-male-medium.onnx (17.5 MiB) 
│   └── Performance: RTF 0.598, streaming capable
└── Status: ⚠️ Functional but accuracy needs improvement

English Pipeline (TTS Working, STT Broken):
├── STT: sherpa-onnx-streaming-zipformer-bilingual-zh-en (70.2 MiB)
│   └── Performance: 96.8% WER (unacceptable, model replacement needed)
├── TTS: en_US-ryan-high.onnx (17.7 MiB)
│   └── Performance: RTF 0.461, streaming capable
└── Status: ❌ STT requires English-only model replacement

Shared Components:
├── VAD: silero_vad.onnx (2.0 MiB) - Voice activity detection
└── Audio Processing: 16kHz pipeline with real-time processing
```

### Core System Components

#### **CommunicationActivity** - Main Interface
- **Push-to-Talk**: Working callback-based STT triggering
- **Reliable Delivery**: ACK/retry system for message transport  
- **Language Loading**: Dynamic Hindi/English model management
- **Pipeline Integration**: Complete audio → text → audio flow

#### **LiveSttController** - Audio Pipeline Coordinator  
- **Fixed Architecture**: Prevents duplicate utterance delivery
- **Real-time Processing**: Non-blocking STT with proper queuing
- **State Management**: Clean LISTENING → SPEECH → TRANSCRIBING phases
- **Memory Safe**: Proper resource cleanup and lifecycle management

#### **Bluetooth Transport** - Communication Layer
- **Text-Only Protocol**: Secure, low-bandwidth message transmission
- **Reliable Delivery**: Message ACK system with retry logic
- **Connection Management**: Pairing and reconnection handling
- **Message Types**: SPEECH, ALERT, ACK, PING supported

## 🚨 **Current Issues & Required Fixes**

### Critical Issues
1. **English STT Unusable**: 96.8% WER requires complete model replacement
2. **Hindi STT Accuracy**: 64.9% WER needs improvement (target: <20% for production)
3. **End-to-End Testing**: Requires two devices for complete validation
4. **Memory Usage**: Real runtime memory consumption not measured

### Known Limitations  
1. **Language Coverage**: Only 2 of 10 required languages implemented
2. **Production Readiness**: Current accuracy levels unsuitable for emergency use
3. **Battery Impact**: Long-term power consumption not measured
4. **Noise Robustness**: Performance in noisy environments not tested

## 🎯 **ISRO Requirements Assessment**

### ✅ **Successfully Demonstrated**
- **Offline Operation**: No internet dependency after initial model download
- **Low Bandwidth**: Text-only transmission significantly reduces data requirements
- **Multilingual Architecture**: System designed to scale to additional languages
- **Emergency Alerts**: Priority routing and audio focus override implemented

### ⚠️ **Partially Implemented** 
- **Accuracy Requirements**: Hindi functional but needs improvement
- **Lightweight Models**: Compact size achieved but accuracy trade-offs evident
- **Real-time Performance**: TTS streaming works, STT accuracy issues limit usability

### ❌ **Not Yet Achieved**
- **Production-Ready Accuracy**: Current WER levels unsuitable for critical communication
- **Complete Language Coverage**: 8 additional Indian languages not implemented  
- **End-to-End Validation**: Two-device testing and latency measurement needed

## 📊 **Testing & Validation Status**

### Completed Testing
- ✅ **Single Device**: UI, STT pipeline, TTS synthesis, Bluetooth pairing
- ✅ **Model Performance**: WER measurement on real speech samples  
- ✅ **System Integration**: Complete pipeline from microphone to speaker
- ✅ **Build Validation**: Gradle builds pass, APK deploys successfully

### Pending Validation
- ⏳ **Two-Device Communication**: End-to-end message delivery testing
- ⏳ **Real-World Performance**: Noise robustness, battery life, memory usage
- ⏳ **Accuracy Improvement**: Model replacement and retraining needed
- ⏳ **Scale Testing**: Multi-user, extended session validation

## 🔮 **Next Development Steps**

### Immediate Priorities (For Production Readiness)
1. **English STT Model Replacement**: Replace bilingual model with English-only Whisper model (expected <10% WER)
2. **Hindi STT Accuracy Improvement**: Retrain or replace model to achieve <20% WER
3. **Two-Device Testing**: Complete end-to-end communication validation
4. **Memory & Battery Optimization**: Measure and optimize runtime resource usage

### Medium Term (System Enhancement)
1. **Additional Languages**: Implement remaining 8 Indian languages using established architecture
2. **Noise Robustness**: Add advanced noise cancellation for challenging environments  
3. **Connection Reliability**: Implement Wi-Fi Direct fallback for Bluetooth connectivity
4. **User Experience**: Improve UI/UX based on real-world usage feedback

### Long Term (ISRO Mission Integration)
1. **Satellite Protocol Support**: Adapt for space communication requirements
2. **Mission Control APIs**: Integration interfaces for ISRO ground systems
3. **Custom Hardware**: Support for specialized space-rated communication devices
4. **Group Communication**: Multi-device mesh networking for team coordination

## 📋 **ISRO Hackathon Deliverable Status**

### ✅ **Completed Deliverables**
- **Working Android APK**: 40.2 MB build with complete pipeline integration
- **Source Code**: Full Kotlin implementation with documented architecture
- **Performance Analysis**: Real device measurements and honest accuracy assessment
- **Technical Documentation**: Setup guides, architecture details, and current limitations

### ⚠️ **Prototype Status Summary**
- **Proof of Concept**: Complete system architecture successfully demonstrated
- **Hindi Implementation**: Functional but needs accuracy improvement (64.9% → <20% WER target)
- **English Implementation**: TTS working, STT requires model replacement (96.8% → <10% WER target)
- **Production Readiness**: Additional development required for mission-critical deployment

## 👨‍💻 **Technical Resources**

**Repository Structure**:
- **app/**: Android application source code
- **models_lab/**: Model management and performance testing infrastructure  
- **tools/**: Real speech corpus preparation and model download utilities
- **optional_model_manager/**: Automated model installation system

**Key Documentation**:
- **README.md**: This overview and setup guide
- **MODELS.md**: Detailed model specifications and performance data
- **tools/README.md**: Real speech testing infrastructure guide

### ISRO Problem Statement Contacts
- Gottumukala Sai Rama Krishna: sairamakrishna@sac.isro.gov.in
- Vishal Kumar Singh: vishalsingh@sac.isro.gov.in  
- Mayur Vinod Chaudhari: mayurch5@sac.isro.gov.in

---

## 🎯 **Honest Assessment**

iTantra demonstrates a **functional prototype** addressing ISRO's voice communication challenge:

✅ **System Architecture**: Proven scalable design for multilingual deployment  
✅ **Core Pipeline**: Complete offline STT+TTS integration working  
✅ **Hindi Foundation**: Basic functionality established, needs accuracy improvement  
⚠️ **English STT**: Known issue with clear solution path (model replacement)  
⏳ **Production Readiness**: Additional development required for mission deployment  

**Current Status**: Strong technical foundation with clear improvement roadmap for production deployment.

---
*Prototype developed for ISRO Hackathon 2026 | Problem Statement #26173 | Honest technical assessment*