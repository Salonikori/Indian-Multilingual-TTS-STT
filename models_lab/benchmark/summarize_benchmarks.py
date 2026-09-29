#!/usr/bin/env python3
"""Generate BENCHMARKS.md only from supplied measured exports; missing data is explicitly listed."""
import argparse,json,statistics
from pathlib import Path

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def main():
 p=argparse.ArgumentParser();p.add_argument('--android-json',type=Path);p.add_argument('--stt-results-dir',type=Path,default=Path('../results'));p.add_argument('--tts-panel-json',type=Path);p.add_argument('--device-notes',type=Path);p.add_argument('--output',type=Path,default=Path('../../BENCHMARKS.md'));a=p.parse_args()
 lines=['# iTantra Benchmarks','','Generated from supplied exports. No placeholder measurements are inserted.',''];missing=['Android device model / SoC / total RAM for a measured run: no Android export supplied.','Release APK artifact size: no built release APK file size supplied; installed sourceDir APK size is a separate measurement.','Per-model file sizes: no installed model-file listing supplied.','True peak RAM during model load/inference with one language loaded: not measured; a sampled PSS high-water mark is not necessarily the process peak.','Armed-but-silent CPU usage for at least 300 seconds: no complete CPU sampling run supplied.','Per-language WER and sample counts: no actual STT result JSON files supplied.','TTS intelligibility/naturalness panel means and panel sample sizes: no completed listening-panel summary supplied.','speech_end_to_stt_ready_ms median/worst: no N≥20 latency samples supplied.','text_received_to_first_audio_ms median/worst: no N≥20 latency samples supplied.','tts_rtf median/worst on target device: no N≥20 TTS RTF samples supplied.','phone_a_speech_to_phone_b_audio_ms median/worst: no N≥20 two-phone measurements on a shared/recorded timebase supplied.'];device='Not recorded'
 if a.android_json and a.android_json.exists():
  d=read(a.android_json);dev=d.get('device',{});device=f"{dev.get('manufacturer','?')} {dev.get('model','?')} (SoC={dev.get('socModel',dev.get('hardware','?'))}, total RAM bytes={dev.get('totalRamBytes','not recorded')}, Android API {dev.get('sdkInt','?')})";lines += ['', '## Test context', '', str(d.get('testContext','not recorded'))]
  missing=[m for m in missing if not m.startswith('Android device model / SoC / total RAM')]
  app=d.get('app',{});n=app.get('installedApkBytes')
  if isinstance(n,(int,float)) and n>0:lines.append(f"- Installed APK/source APK size: {n} bytes; device: {device}; method: PackageManager sourceDir file length.")
  else:missing.append('Installed APK/source APK size: not present in Android export.')
  mem=d.get('memory',{}).get('totalPssKb')
  if isinstance(mem,(int,float)):lines.append(f"- Current total PSS snapshot: {mem} KiB; device: {device}; method: Debug.getMemoryInfo. Not a peak unless captured during peak use.")
  else:missing.append('RAM / peak RAM: no memory snapshot.')
  models=d.get('models',[])
  if models:
   missing=[m for m in missing if not m.startswith('Per-model file sizes:')]
   lines.append('- Model files (actual filesDir/models lengths):');lines.extend([f"  - `{m.get('path','?')}`: {m.get('bytes')} bytes" for m in models])
  else:missing.append('Per-model file sizes: no installed model files found in the Android export.')
  by={}
  for s in d.get('samples',[]):
   k=s.get('metric');v=s.get('value')
   if isinstance(v,(int,float)) and v>=0:by.setdefault(k,[]).append((float(v),s.get('unit',''),s.get('method','')))
  if 'process_total_pss_kb' in by:
   vs=by['process_total_pss_kb']; vals=[v[0] for v in vs]; lines.append(f"- Sampled PSS high-water mark during recorded run: {max(vals):.5g} KiB from {len(vals)} samples; device: {device}; method: Debug.getMemoryInfo. This is only the maximum of captured samples.")
  if 'silent_listening_cpu_percent_one_core' in by:
   vs=by['silent_listening_cpu_percent_one_core']; vals=[v[0] for v in vs]; lines.append(f"- Armed-but-silent process CPU: mean={statistics.mean(vals):.5g}% of one core, worst sample={max(vals):.5g}%, samples={len(vals)}; method: Process.getElapsedCpuTime delta divided by wall-clock delta; device: {device}.")
   if len(vals)>=300:missing=[m for m in missing if not m.startswith('Armed-but-silent CPU usage for at least 300 seconds:')]
   else:missing.append(f'Armed-but-silent CPU sampling incomplete: {len(vals)} samples; 300 one-second samples required.')
   if max(vals)>800:lines.append('  - **Suspicious-data flag:** CPU exceeded 800% of one core; inspect measurement intervals and process scheduling.')
  for k,vs in sorted(by.items()):
   if not (k.endswith('_ms') or k=='tts_rtf'):continue
   ns=[v[0] for v in vs]
   if len(ns)>=20:
    lines.append(f"- `{k}`: N={len(ns)}, median={statistics.median(ns):.5g} {vs[0][1]}, worst={max(ns):.5g} {vs[0][1]}; method(s): {', '.join(sorted(set(v[2] for v in vs)))}.")
    missing=[m for m in missing if not m.startswith(k+' median/worst:')]
   else:missing.append(f'{k}: {len(ns)} samples only; N≥20 required for median/worst latency report.')
   flags=[]
   if any(v==0 for v in ns):flags.append('contains zero; check timer placement/resolution')
   if k.endswith('_ms') and any(v>60000 for v in ns):flags.append('contains latency >60 s; inspect timeout/stall behavior and timestamp endpoints')
   if k=='tts_rtf' and any(v>10 for v in ns):flags.append('contains TTS RTF >10; verify wall-time/audio-duration units and model stalls')
   if flags:lines.append(f"  - **Suspicious-data flag:** `{k}` {'; '.join(flags)}.")
  missing.extend(d.get('notMeasured',[]))
 else:missing.append('Android benchmark JSON export not supplied.')
 stt=0
 if a.stt_results_dir.exists():
  for f in sorted(a.stt_results_dir.glob('*.json')):
   try:d=read(f)
   except Exception:continue
   if 'language' not in d or 'wer_corpus' not in d:continue
   stt+=1;hd=d.get('host_device',{});host=f"{hd.get('system','not recorded')}; machine={hd.get('machine','not recorded')}; processor={hd.get('processor','not recorded')}; RAM bytes={hd.get('ram_bytes','not recorded')}";lines.append(f"- STT `{d['language']}`: corpus WER={d['wer_corpus']:.6g}; samples/utterances={d.get('utterances','unknown')}; aggregate RTF={d.get('aggregate_rtf','unknown')}; host/device: {host}; note: {d.get('note','not recorded')}.")
 if stt:missing=[m for m in missing if not m.startswith('Per-language WER and sample counts:')]
 if a.tts_panel_json and a.tts_panel_json.exists():
  for d in read(a.tts_panel_json):lines.append(f"- TTS `{d['language']}` / `{d['model_id']}`: intelligibility mean {d['mean_intelligibility_1_to_5']:.3f}/5; naturalness mean {d['mean_naturalness_1_to_5']:.3f}/5; ratings={d['rating_count']}; unique samples={d['unique_audio_samples']}; listeners={d['unique_listeners']}; device={d.get('device_model','not recorded')}; SoC={d.get('soc_model','not recorded')}; RAM bytes={d.get('ram_bytes','not recorded')}.")
 if a.device_notes and a.device_notes.exists():lines+=['','## Device notes','',a.device_notes.read_text(encoding='utf-8').strip()]
 lines+=['','## Measurement device','',device,'','## Not measured','']+[f'- {x}' for x in sorted(set(missing))]
 lines+=['','## Method and suspicious-value notes','','- Latency metrics are summarized only when at least 20 valid samples exist.','- Debug PSS is a point-in-time snapshot, not peak RAM unless sampling overlaps peak model load/inference.','- CPU `/proc/self/stat` values are clock ticks; convert with device USER_HZ or cross-check `adb shell top`.','- End-to-end latency requires synchronized event clocks or a third-device recording of both phones; RTT/2 is not direct one-way latency.','- Investigate zero, negative, implausible, too-few-sample, or missing-device values instead of silently accepting them.','']
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text('\n'.join(lines),encoding='utf-8');print('Wrote',a.output)
if __name__=='__main__':main()
