#!/usr/bin/env bash
set -u
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
missing=0
check() { if "$@"; then :; else missing=1; fi; }
if command -v java >/dev/null 2>&1; then
  printf 'FOUND java: '; java -version 2>&1 | head -n 1
else echo 'MISSING Java (JDK 17 recommended)'; missing=1; fi
if [[ -x "$root/gradlew" && -f "$root/gradle/wrapper/gradle-wrapper.jar" ]]; then
  echo 'FOUND Gradle wrapper'
elif command -v gradle >/dev/null 2>&1; then
  printf 'FOUND system Gradle: '; gradle --version | head -n 3 | tail -n 1
else echo 'MISSING Gradle wrapper and system Gradle'; missing=1; fi
if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/platforms" ]]; then
  echo "FOUND Android SDK: $ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT/platforms" ]]; then
  echo "FOUND Android SDK: $ANDROID_SDK_ROOT"
else echo 'MISSING Android SDK (install SDK Platform 36 and configure ANDROID_HOME or ANDROID_SDK_ROOT)'; missing=1; fi
if [[ -f "$root/app/libs/sherpa_onnx.aar" ]]; then
  echo 'FOUND app/libs/sherpa_onnx.aar (local AAR)'
else
  # sherpa-onnx is now resolved via JitPack Maven: com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5
  # A local AAR is not required. Verify the JitPack entry in settings.gradle.kts instead.
  if grep -q "jitpack.io" "$root/settings.gradle.kts" 2>/dev/null; then
    echo 'FOUND JitPack repository in settings.gradle.kts (sherpa-onnx resolved via Maven)'
  else
    echo 'MISSING sherpa-onnx: neither app/libs/sherpa_onnx.aar nor JitPack in settings.gradle.kts'; missing=1
  fi
fi
if [[ $missing -eq 0 ]]; then
  echo 'Prerequisite check passed. This does not prove compilation or device operation.'
else
  echo 'Prerequisite check incomplete. Resolve the missing items above before building; no build result is implied.'
  exit 2
fi
