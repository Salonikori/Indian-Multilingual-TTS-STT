# iTantra Benchmarks

Generated from supplied exports. No placeholder measurements are inserted.


## Measurement device

Not recorded

## Not measured

- Android benchmark JSON export not supplied.
- Android device model / SoC / total RAM for a measured run: no Android export supplied.
- Armed-but-silent CPU usage for at least 300 seconds: no complete CPU sampling run supplied.
- Per-language WER and sample counts: no actual STT result JSON files supplied.
- Per-model file sizes: no installed model-file listing supplied.
- Release APK artifact size: no built release APK file size supplied; installed sourceDir APK size is a separate measurement.
- TTS intelligibility/naturalness panel means and panel sample sizes: no completed listening-panel summary supplied.
- True peak RAM during model load/inference with one language loaded: not measured; a sampled PSS high-water mark is not necessarily the process peak.
- phone_a_speech_to_phone_b_audio_ms median/worst: no N≥20 two-phone measurements on a shared/recorded timebase supplied.
- speech_end_to_stt_ready_ms median/worst: no N≥20 latency samples supplied.
- text_received_to_first_audio_ms median/worst: no N≥20 latency samples supplied.
- tts_rtf median/worst on target device: no N≥20 TTS RTF samples supplied.

## Method and suspicious-value notes

- Latency metrics are summarized only when at least 20 valid samples exist.
- Debug PSS is a point-in-time snapshot, not peak RAM unless sampling overlaps peak model load/inference.
- CPU `/proc/self/stat` values are clock ticks; convert with device USER_HZ or cross-check `adb shell top`.
- End-to-end latency requires synchronized event clocks or a third-device recording of both phones; RTT/2 is not direct one-way latency.
- Investigate zero, negative, implausible, too-few-sample, or missing-device values instead of silently accepting them.
