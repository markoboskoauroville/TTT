import sys, json, time
sys.path.insert(0,'SCRATCH')
from kb import clear, tap_text, shot
from ocr import strip
import os, importlib; CASES=importlib.import_module(os.environ.get("CASES","cases")).CASES
ph,tag=sys.argv[1],sys.argv[2]; res={}
for i,c in enumerate(CASES):
    clear(ph); time.sleep(0.5); tap_text(ph,c); time.sleep(2.6)
    p=shot(ph,f'{tag}_{i:02d}'); res[c]=strip(ph,p); print(repr(c),res[c],flush=True)
json.dump(res,open(f'SCRATCH/res_{tag}.json','w'),ensure_ascii=False,indent=0)
