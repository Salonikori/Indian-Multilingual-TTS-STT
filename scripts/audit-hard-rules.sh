#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
fail=0
say() { printf '%s\n' "$*"; }
check_absent() {
  local label="$1" pattern="$2" paths="$3"
  if grep -RInE "$pattern" $paths 2>/dev/null; then
    say "FAIL: $label"
    fail=1
  else
    say "PASS: $label"
  fi
}
# Runtime app source and manifest only. Bluetooth RFCOMM is the intentional local transport.
check_absent "No Android INTERNET permission" 'android\.permission\.INTERNET' 'app/src/main/AndroidManifest.xml'
check_absent "No IP/HTTP networking APIs in runtime Kotlin (Bluetooth transport excluded)" 'java\.net\.|javax\.net\.|HttpURLConnection|OkHttpClient|Retrofit\.Builder|io\.ktor\.client|URL\(' "app/src/main/java/com/itantra/app/audio app/src/main/java/com/itantra/app/models app/src/main/java/com/itantra/app/session app/src/main/java/com/itantra/app/tts app/src/main/java/com/itantra/app/benchmark app/src/main/java/com/itantra/app/MainActivity.kt app/src/main/java/com/itantra/app/LanguagesActivity.kt app/src/main/java/com/itantra/app/BenchmarkActivity.kt"
if grep -nE 'FloatArray|ShortArray|audioBytes|pcm|wavPath|samples|audioFile|audioData' app/src/main/java/com/itantra/app/transport/MessagePayload.kt app/src/main/java/com/itantra/app/transport/Transport.kt; then
  say "FAIL: audio-like field/type found in payload or transport contract"; fail=1
else
  say "PASS: MessagePayload/Transport API has no audio fields or sample arrays"
fi
engine_hits="$(grep -RIlE 'OfflineRecognizer\(|OfflineTts\(' app/src/main/java --include='*.kt' || true)"
if [[ "$engine_hits" != "app/src/main/java/com/itantra/app/models/LanguageManager.kt" ]]; then
  say "FAIL: native STT/TTS engine construction outside LanguageManager: ${engine_hits:-none}"; fail=1
else
  say "PASS: native STT/TTS engine construction is confined to LanguageManager"
fi
if grep -q 'fun loadLanguage' app/src/main/java/com/itantra/app/models/LanguageManager.kt && grep -q 'release()' app/src/main/java/com/itantra/app/models/LanguageManager.kt; then
  say "PASS: language manager has explicit release-before-load lifecycle (manual source inspection still required)"
else
  say "FAIL: missing release-before-load lifecycle"; fail=1
fi
if grep -RInE 'from (requests|urllib|httpx|huggingface_hub)|import (requests|urllib|httpx|huggingface_hub)|urlopen\(|requests\.(get|post|put|delete)\(' models_lab --include='*.py' 2>/dev/null; then
  say "FAIL: network-capable Python code found outside optional_model_manager"; fail=1
else
  say "PASS: model-lab inference/benchmark Python contains no network clients"
fi
manager_hits="$(grep -RIlE 'huggingface_hub|requests\.|urlopen\(' optional_model_manager --include='*.py' || true)"
if [[ -z "$manager_hits" ]]; then
  say "PASS: optional_model_manager has no network-capable code (downloaders live in tools/)"
else
  say "FAIL: unexpected network-capable optional manager files: $manager_hits"; fail=1
fi
if grep -qE 'implementation\("(com\.squareup\.retrofit2|com\.squareup\.okhttp3|io\.ktor:ktor-client|com\.google\.firebase)' app/build.gradle.kts; then
  say "FAIL: unexpected network client dependency"; fail=1
else
  say "PASS: no HTTP client/Firebase dependency declared"
fi
if [[ $fail -ne 0 ]]; then exit 1; fi
say "Audit result: source-level checks passed. This does not replace Gradle dependency resolution, APK inspection, or device testing."