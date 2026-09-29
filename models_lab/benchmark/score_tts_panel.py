#!/usr/bin/env python3
"""Average TTS listening scores. CSV: language,model_id,device_model,soc_model,ram_bytes,sample_id,listener_id,intelligibility,naturalness,notes."""
import argparse,csv,json,statistics
from collections import defaultdict
from pathlib import Path

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('csv_file',type=Path);p.add_argument('--output',type=Path,default=Path('tts_panel_summary.json'));a=p.parse_args();g=defaultdict(lambda:{'i':[],'n':[],'samples':set(),'listeners':set()})
 with a.csv_file.open(encoding='utf-8-sig',newline='') as f:
  for r in csv.DictReader(f):
   device=(r.get('device_model','').strip(),r.get('soc_model','').strip(),r.get('ram_bytes','').strip());x=g[(r['language'].strip(),r['model_id'].strip(),*device)]
   for col,key in [('intelligibility','i'),('naturalness','n')]:
    v=float(r[col]);
    if not 1<=v<=5: raise SystemExit(f'{col} must be 1..5: {r}')
    x[key].append(v)
   x['samples'].add(r['sample_id']);x['listeners'].add(r['listener_id'])
 out=[]
 for (lang,model,device_model,soc_model,ram_bytes),x in sorted(g.items()):out.append({'language':lang,'model_id':model,'device_model':device_model or 'not recorded','soc_model':soc_model or 'not recorded','ram_bytes':int(ram_bytes) if ram_bytes.isdigit() else None,'rating_count':len(x['i']),'unique_audio_samples':len(x['samples']),'unique_listeners':len(x['listeners']),'mean_intelligibility_1_to_5':statistics.mean(x['i']),'mean_naturalness_1_to_5':statistics.mean(x['n']),'note':'Descriptive only; limited to recorded samples/listeners.'})
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(out,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
