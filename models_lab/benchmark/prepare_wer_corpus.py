#!/usr/bin/env python3
"""Build a language-tagged references.tsv from test_audio/<language>/<stem>.wav + .txt pairs."""
import argparse,csv
from pathlib import Path

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--audio-root',type=Path,default=Path('../test_audio'));p.add_argument('--output',type=Path,default=Path('../test_audio/references.tsv'));a=p.parse_args();root=a.audio_root.resolve();rows=[];missing=[]
 for langdir in sorted(x for x in root.iterdir() if x.is_dir()):
  for wav in sorted(langdir.glob('*.wav')):
   txt=wav.with_suffix('.txt')
   if not txt.is_file():missing.append(str(txt));continue
   ref=txt.read_text(encoding='utf-8-sig').strip()
   if not ref:raise SystemExit(f'Empty reference transcript: {txt}')
   rows.append((langdir.name,str(wav.relative_to(a.output.resolve().parent)),ref))
 if not rows:raise SystemExit(f'No WAV/TXT pairs found under {root}; add real recordings and same-stem transcript files.')
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with a.output.open('w',encoding='utf-8',newline='') as f:
  w=csv.writer(f,delimiter='\t');w.writerow(['language','wav_path','reference']);w.writerows(rows)
 print(f'Wrote {a.output}: {len(rows)} utterances in {len(set(r[0] for r in rows))} separate language(s).')
 for lang in sorted(set(r[0] for r in rows)):print(f'{lang}: {sum(r[0]==lang for r in rows)} utterances')
 if missing:print(f'WARNING: {len(missing)} WAV files had no transcript; skipped. First: {missing[0]}')
if __name__=='__main__':main()
