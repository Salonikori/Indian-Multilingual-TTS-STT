# Dependency Notes

## sherpa-onnx JitPack Integration

### Current Version
- **Version:** v1.13.8 (latest as of October 2026)
- **Source:** `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8`
- **Repository:** JitPack (https://jitpack.io)

### Build Performance
- **First Build:** 2-5 minutes (JitPack compiles library on-demand)
- **Subsequent Builds:** ~30-60 seconds (cached)
- **CI Builds:** Use Gradle caching for optimal performance

### Architecture Support
- **Supported:** arm64-v8a (modern Android devices)
- **Target:** Android API 26+ (Android 8.0+)
- **Native Libraries:** Included in AAR automatically

### Recent Updates (v1.13.8)
- Improved Android build stability
- Added Intel NPU support through OpenVINO
- Fixed various runtime issues
- Enhanced speaker diarization
- Updated ONNX Runtime to v1.28.2

### Troubleshooting

#### Slow Builds
- **Expected:** First JitPack build takes 2-5 minutes
- **Solution:** Use Gradle build cache and dependency caching
- **CI:** Ensure artifacts are cached between runs

#### Class Not Found Errors
- **Cause:** JitPack build failed or network issues
- **Solution:** Clean build and retry: `./gradlew clean build`
- **Check:** Verify JitPack status at https://jitpack.io/#k2-fsa/sherpa-onnx

#### Native Library Issues
- **UnsatisfiedLinkError:** Expected in unit tests (no Android runtime)
- **Real Device:** Verify arm64-v8a architecture
- **Emulator:** Use arm64 system images, not x86

### Build Optimizations Applied
1. **Repository Filtering:** JitPack only used for sherpa-onnx
2. **Resolution Strategy:** Cache dynamic versions for 1 hour  
3. **Gradle Optimizations:** Parallel builds, configuration cache
4. **CI Caching:** Gradle wrapper and dependencies cached

### Version History
- **v1.13.5:** Previous version (stable)
- **v1.13.6-v1.13.7:** Minor fixes and improvements
- **v1.13.8:** Current version (latest features)

### Testing
- **Unit Tests:** SherpaOnnxDependencyTest verifies class accessibility
- **Integration:** LanguageManager uses sherpa-onnx for STT/TTS
- **Runtime:** Real device testing required for native library validation

### References
- [sherpa-onnx GitHub](https://github.com/k2-fsa/sherpa-onnx)
- [JitPack Page](https://jitpack.io/#k2-fsa/sherpa-onnx)
- [Release Notes](https://github.com/k2-fsa/sherpa-onnx/releases)