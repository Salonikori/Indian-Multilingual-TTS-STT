# iTantra Android Prototype

[![Build APK](https://github.com/USERNAME/REPOSITORY/actions/workflows/build.yml/badge.svg)](https://github.com/USERNAME/REPOSITORY/actions/workflows/build.yml)

**Hindi/English Offline Speech-to-Text and Text-to-Speech Walkie-Talkie for ISRO**

An Android prototype for ISRO's Smart India Hackathon 2026 Problem Statement 26173: "AI-powered Communication System for Space Operations." This app provides reliable offline voice communication between mission control and field teams using Hindi and English speech recognition and synthesis.

## Problem Context

Space missions require robust communication systems that work in remote locations with limited connectivity. This prototype addresses the need for:
- Offline speech-to-text and text-to-speech capabilities
- Multi-language support (Hindi/English)
- Reliable message delivery in challenged network conditions
- Real-time voice communication for mission-critical operations

## Key Features

### Core Functionality
- **Offline STT/TTS**: Complete speech processing without internet dependency
- **Dual Language Support**: Hindi and English recognition and synthesis
- **Walkie-Talkie Interface**: Push-to-talk voice communication
- **Message Reliability**: Guaranteed delivery with retry mechanisms
- **Network Resilience**: Operates in poor connectivity conditions

### Technical Capabilities
- Real-time voice activity detection (VAD)
- Adaptive speech segmentation
- Efficient audio compression
- Bluetooth audio device support
- Background service operation
- Low-latency processing pipeline

## Performance Assessment Status

**IMPORTANT**: This prototype has **NOT** been comprehensively tested. All performance claims below are preliminary estimates based on limited development testing and require extensive validation before production use.

### Speech Recognition Quality - NOT MEASURED
- Hindi Recognition: Accuracy not measured (requires systematic testing)
- English Recognition: Accuracy not measured (requires systematic testing)
- Noise robustness: Not measured (requires controlled environment testing)
- Technical terminology: Not measured (requires domain-specific evaluation)

**Status**: Comprehensive accuracy testing with diverse speakers, accents, noise conditions, and technical vocabulary is required.

### Latency Performance - PARTIAL MEASUREMENTS
- Voice Activity Detection: <50ms (estimated from development testing)
- Speech-to-Text Processing: Not measured (requires systematic evaluation)
- Text-to-Speech Synthesis: Not measured (requires systematic evaluation)
- End-to-end communication: Not measured (requires two-device testing)

**Status**: Systematic latency measurement across the entire pipeline needs implementation.

### Resource Usage - NOT MEASURED
- RAM usage: Not measured (requires profiling across usage scenarios)
- Storage requirements: Model sizes known, runtime usage not measured
- Battery consumption: Not measured (requires long-term testing)
- CPU utilization: Not measured (requires performance profiling)

**Status**: Comprehensive resource profiling needed for deployment planning.

## Architecture

### Core Components
- **Audio Pipeline**: Capture, VAD, segmentation, compression
- **STT Engine**: Sherpa-ONNX with optimized models
- **TTS Engine**: Android system TTS with offline voices
- **Communication Layer**: Reliable UDP with automatic retry
- **UI Layer**: Modern Android Compose interface

### Model Integration
- **VAD Model**: Silero VAD (ONNX) for voice detection
- **STT Models**: Sherpa-ONNX models for Hindi/English
- **TTS Voices**: System-integrated offline voice synthesis
- **Compression**: Efficient audio encoding for transmission

## Getting Started

### Prerequisites
- Android 8.0+ (API 26+)
- 2GB+ RAM recommended
- 1GB+ storage space
- Microphone and speaker/headphones

### Installation
1. Download and install the APK
2. Grant microphone and storage permissions
3. Allow the app to run in background
4. Connect Bluetooth audio devices if needed

### Usage
1. **Language Selection**: Choose Hindi or English from settings
2. **Push-to-Talk**: Hold button to record, release to send
3. **Playback**: Received messages play automatically
4. **Device Pairing**: Connect with other app instances for communication

## Testing and Validation Status

### Automated Testing
- Unit tests: 46 tests implemented and passing
- Integration tests: Basic coverage implemented
- Performance benchmarks: Framework created, measurements needed
- Network simulation: Reliability mechanisms tested

### Manual Testing Requirements
- **CRITICAL**: Voice quality testing across noise conditions needed
- **CRITICAL**: Real-world accuracy validation with multiple speakers required
- **CRITICAL**: Two-device communication testing needed
- Bluetooth device compatibility validation required
- Battery usage profiling needed
- Network resilience testing in realistic conditions needed

## Technical Specifications

### Supported Audio Formats
- Input: 16kHz 16-bit PCM (microphone)
- Processing: Multiple sample rates supported
- Output: System-dependent (typically 44.1kHz)
- Compression: Efficient encoding for transmission

### Network Requirements
- Protocol: UDP with reliability layer
- Bandwidth: Estimated 10-50 kbps per conversation (not measured)
- Latency tolerance: Designed for 100ms+ network delays
- Offline capability: Full functionality without internet

### Model Details
- **VAD**: Silero VAD ONNX model (~1MB)
- **Hindi STT**: Optimized Sherpa model (~100MB)
- **English STT**: Optimized Sherpa model (~80MB)
- **TTS**: System voices (varies by Android version)

## Known Limitations and Testing Needs

### Critical Testing Requirements
- **Speech Recognition**: Comprehensive accuracy evaluation needed
- **Real-World Performance**: Multi-device testing required
- **Resource Usage**: Systematic profiling needed
- **Network Behavior**: Realistic condition testing required

### Development Constraints
- Limited testing on single development device
- No systematic performance measurement
- Manual IP configuration required
- Depends on Android system TTS quality

### Production Readiness Assessment
**This prototype requires extensive testing before production deployment:**
- Multi-device communication validation
- Diverse speaker accent testing
- Noise condition evaluation
- Battery life assessment
- Network resilience validation

## Development

### Build Requirements
- Android Studio Arctic Fox+
- Gradle 7.0+
- NDK for native components
- Kotlin 1.8+

### Architecture Patterns
- MVVM with Compose UI
- Repository pattern for data access
- Dependency injection with Hilt
- Coroutines for async operations

## Contributing

This prototype was developed for ISRO's Smart India Hackathon 2026. Critical contributions needed:
- Systematic performance measurement
- Multi-device testing infrastructure
- Speech recognition accuracy evaluation
- Real-world validation testing

## License and Acknowledgments

### Third-Party Libraries
- **Sherpa-ONNX**: Speech recognition engine
- **ONNX Runtime**: Model inference
- **Silero VAD**: Voice activity detection
- **Android Jetpack**: UI and system integration

### Research References
- Speech recognition research from academic institutions
- ISRO communication requirements and specifications
- Android audio processing best practices
- Network reliability protocols for mobile devices

---

**Developed for ISRO Smart India Hackathon 2026 - Problem Statement 26173**

**STATUS**: Prototype requires comprehensive testing and validation before production use.