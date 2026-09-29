# iTantra benchmark collection protocol

All results must be measured. Do not copy estimated values into `BENCHMARKS.md`. Keep separate records for each physical device, language, exact model revision, and test corpus.

## 1. Device / APK / model sizes / RAM / CPU

1. Build and install the exact APK under test, connect one phone with USB debugging enabled, and run `bash models_lab/benchmark/device_capture.sh results/device_capture` from the repository root. The script records model/SoC properties, system RAM information, APK bytes and `dumpsys meminfo`, plus raw `top` samples for up to six minutes.
2. Open the app, load exactly one language, capture memory at idle, during model load, and during inference. The in-app `Debug.MemoryInfo` value is a point-in-time PSS, not automatically peak RAM. Keep `dumpsys meminfo` output and report the observed maximum across the captured samples.
3. For CPU, start the app's five-minute silent-listening sampler while armed-but-silent, with no utterances. Also use `top_6min.txt` as a cross-check. Keep the raw file. Report sampling duration, the exact `top` fields/CPU convention, and the device model. Do not convert raw `/proc/self/stat` ticks to percentage without checking the device's clock-tick rate.
4. Installed APK `sourceDir` bytes are not necessarily the same as a release APK. Record `stat -c %s app/build/outputs/apk/release/app-release.apk` separately if that artifact exists.

## 2. Offline WER per language

Corpus convention: `models_lab/test_audio/<language>/<utterance-id>.wav` plus a same-stem `.txt` reference transcript. WAVs must be genuine recorded utterances, 16 kHz mono is expected by the current runner. Normalize transcript conventions before evaluation and document whether punctuation/case are retained.

From `models_lab/`:

```bash
python benchmark/prepare_wer_corpus.py --audio-root test_audio --output test_audio/references.tsv
# Run separately per language and exact model configuration; never pool language WER.
python test_stt.py --model-type nemo_ctc --language hi --model models/stt/hi/model.onnx --tokens models/stt/hi/tokens.txt --references test_audio/references.tsv --output results/stt_hi.json
python test_stt.py --model-type transducer --language en --encoder models/stt/en/encoder.onnx --decoder models/stt/en/decoder.onnx --joiner models/stt/en/joiner.onnx --tokens models/stt/en/tokens.txt --references test_audio/references.tsv --output results/stt_en.json
```

Adjust model type/paths to the actual model. Do not use these illustrative commands as evidence that those files exist. Record utterance count and corpus WER separately for every language. `test_stt.py` uses the installed local sherpa-onnx runtime; inference itself is offline.

## 3. TTS listening panel

Use one row per listener × audio sample in `tts_listening_panel_template.csv`. Score intelligibility and naturalness from 1 (poor/unintelligible) to 5 (clear/natural). Randomize sample order, avoid showing model identity to listeners where practical, and retain the individual ratings. Generate means and counts:

```bash
python benchmark/score_tts_panel.py benchmark/tts_listening_panel_template.csv --output results/tts_panel_summary.json
```

Do not report an average from the untouched empty template. State unique listeners, unique audio samples, and total ratings for each language/model.

## 4. Latency, at least N=20

Use a fixed, published sentence list per language, at least 20 repeated utterances per metric. For each run, record:
- `speech_end_to_stt_ready_ms`: monotonic timestamp at VAD utterance-finalized/speech-end to final STT text callback.
- `text_received_to_first_audio_ms`: timestamp on inbound text accepted by receiver to first PCM sample actually submitted/played. Specify whether `AudioTrack.play()` or an audio loopback onset is the endpoint; loopback is stronger evidence.
- `tts_rtf`: TTS synthesis wall time divided by generated audio duration (unitless), separately for each sentence.
- `phone_a_speech_to_phone_b_audio_ms`: record Phone A utterance onset/end and Phone B audio onset with a third recorder capturing both devices, or a verified shared-clock setup. State precisely which endpoints are measured. Do not use RTT/2 as measured one-way latency.

The in-app sample form saves raw measurements and method notes; it does not itself instrument the live speech/transport/TTS pipeline. Do not enter estimates. Median and worst-case should only be reported when at least 20 valid samples exist for the exact metric/device/model.

## 5. Export and report

In-app: load exactly one language, open **Benchmark screen**, capture a snapshot, run the silent CPU sample for 300 seconds, save actual latency/RTF samples, then export JSON or CSV via Android share sheet. To pull the most recent app-private cache export directly with adb, first list files with `adb shell run-as com.itantra.app ls cache/benchmark-export`, then use `adb exec-out run-as com.itantra.app cat cache/benchmark-export/<filename>.json > android_export.json` (replace the filename with the actual listed name). To inspect Android memory live:

```bash
adb shell dumpsys meminfo com.itantra.app
adb shell pidof com.itantra.app
adb shell top -b -d 1 -n 360 -p <PID>
```

Generate `BENCHMARKS.md` from the exported files (run from `models_lab/benchmark`):

```bash
python summarize_benchmarks.py --android-json /path/to/export.json --stt-results-dir ../results --tts-panel-json ../results/tts_panel_summary.json --device-notes /path/to/device_notes.txt --output ../../BENCHMARKS.md
```

The report generator keeps unmeasured items under **Not measured**. Confirm each number is tied to device model/SoC/RAM, exact model revision, language, method, sample count, and raw source file. Investigate zeros and implausible measurements instead of reporting them without qualification.
