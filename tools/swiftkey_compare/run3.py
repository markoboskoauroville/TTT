import sys, json, time, os, importlib, subprocess, re
sys.path.insert(0,'SCRATCH')
from kb import clear, tap_text, shot, PH
from cases3 import CASES
ph,tag=sys.argv[1],sys.argv[2]; res={}
dev,_=PH[ph]
for i,c in enumerate(CASES):
    clear(ph); time.sleep(0.6); tap_text(ph,c.rstrip()); time.sleep(2.2); tap_text(ph,' '); time.sleep(2.0)
    # read the field itself: the page's textarea value through the UI tree is not exposed, so screenshot + OCR the box
    p=shot(ph,f'{tag}_{i:02d}')
    out=p[:-4]+'_box.png'
    subprocess.run(['convert',p,'-crop','1000x80+20+455','-resize','200%','-threshold','60%',out])
    t=subprocess.run(['tesseract',out,'-','-l','eng+hrv','--psm','7'],capture_output=True,text=True).stdout.strip()
    res[c]=t; print(repr(c),'->',repr(t),flush=True)
json.dump(res,open(f'SCRATCH/res_{tag}.json','w'),ensure_ascii=False,indent=0)
