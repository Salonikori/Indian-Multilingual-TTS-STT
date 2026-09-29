# sherpa-onnx Android runtime (required before compiling model integration)

The official sherpa-onnx Android instructions recommend using the latest release archive or building the Android library. See:
- https://github.com/k2-fsa/sherpa-onnx/releases
- https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html
- https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxAar

This environment could not resolve GitHub, so no official binary was downloaded and none is vendored here. Do not use a random/unverified AAR.

1. Download the latest official Android archive from the sherpa-onnx release page.
2. Follow that release's README for the matching AAR/API/native `.so` layout.
3. Place the built library as `app/libs/sherpa_onnx.aar` (or adjust Gradle to the exact official output name).
4. Place only the `arm64-v8a` native `.so` files under `app/src/main/jniLibs/arm64-v8a/` if the chosen AAR does not bundle them.
5. Ensure the AAR exposes the Kotlin API package `com.k2fsa.sherpa.onnx`. Re-sync and compile.

Do not add the AAR and external `.so` copies simultaneously if that duplicates native libraries.
