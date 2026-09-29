#!/usr/bin/env bash
# Run with a single physical device connected and the app installed.
set -euo pipefail
OUT="${1:-results/device_capture}"
mkdir -p "$OUT"
adb devices -l | tee "$OUT/adb_devices.txt"
adb shell getprop ro.product.manufacturer > "$OUT/manufacturer.txt"
adb shell getprop ro.product.model > "$OUT/model.txt"
adb shell getprop ro.soc.model > "$OUT/soc_model.txt"
adb shell getprop ro.hardware > "$OUT/hardware.txt"
adb shell getprop ro.build.version.release > "$OUT/android_release.txt"
adb shell cat /proc/meminfo > "$OUT/system_meminfo.txt"
adb shell dumpsys meminfo com.itantra.app > "$OUT/dumpsys_meminfo_idle.txt"
adb shell pm path com.itantra.app > "$OUT/apk_paths.txt"
# APK installed package path(s) can be pulled for exact APK bytes:
while read -r line; do path="${line#package:}"; [ -n "$path" ] && adb pull "$path" "$OUT/$(basename "$path")"; done < "$OUT/apk_paths.txt"
# Run this while app is armed-but-silent for >= 5 minutes; Ctrl-C to stop.
adb shell top -b -d 1 -n 360 -p "$(adb shell pidof -s com.itantra.app | tr -d '\r')" > "$OUT/top_6min.txt" || true
# Capture during model load/inference separately for a RAM high-water sample.
adb shell dumpsys meminfo com.itantra.app > "$OUT/dumpsys_meminfo_after.txt"
echo "Device capture saved to $OUT. Inspect top samples; this script does not claim a peak unless samples overlap peak usage."
