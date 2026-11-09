#!/usr/bin/env python3
"""STT bake-off: python3 run_bakeoff.py [engines...]  (clips/*.webm -> 16k wav -> each engine -> results.json -> report table)
Keys are read from ~/Downloads/API and never printed. Engines: assemblyai groq gemini soniox speechmatics deepgram.
Each runs plain and with the vocabulary in names.txt (where supported). Never OpenAI."""
import json,os,re,subprocess,sys,time,unicodedata,urllib.request,glob
D=os.path.dirname(os.path.abspath(__file__));API=os.path.expanduser("~/Downloads/API/")
S=json.load(open(D+"/sentences.json",encoding="utf-8"));NAMES=[l.strip() for l in open(D+"/names.txt",encoding="utf-8") if l.strip()]
def key(fn,rx=None):
    p=glob.glob(API+fn+"*")
    if not p:return None
    t=open(p[0],errors="ignore").read()
    m=re.findall(rx,t) if rx else [t.strip()]
    return m[0] if m else None
def http(url,data=None,h=None,m=None):
    r=urllib.request.Request(url,data=data,headers=h or {},method=m)
    return json.loads(urllib.request.urlopen(r,timeout=120).read())
def multipart(fields,fname,fbytes):
    b="----x9";o=b""
    for k,v in fields.items():o+=f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
    o+=f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="{fname}"\r\nContent-Type: audio/wav\r\n\r\n'.encode()+fbytes+f"\r\n--{b}--\r\n".encode()
    return o,{"Content-Type":"multipart/form-data; boundary="+b}
def groq(w,g,vocab):
    k=key("groq-api",r"gsk_\w+");d,h=multipart({"model":"whisper-large-v3","response_format":"json","temperature":"0",
      **({"prompt":", ".join(NAMES)} if vocab else {})},"a.wav",open(w,"rb").read());h["Authorization"]="Bearer "+k;h["User-Agent"]="curl/8"
    return http("https://api.groq.com/openai/v1/audio/transcriptions",d,h)["text"]
def assemblyai(w,g,vocab):
    k=key("AssemblyAI-api",r"\b[0-9a-f]{32}\b");H={"authorization":k}
    up=http("https://api.assemblyai.com/v2/upload",open(w,"rb").read(),{**H,"Content-Type":"application/octet-stream"})["upload_url"]
    b={"audio_url":up,"language_detection":True,"speech_model":"universal"}
    if vocab:b["word_boost"]=NAMES
    i=http("https://api.assemblyai.com/v2/transcript",json.dumps(b).encode(),{**H,"Content-Type":"application/json"})["id"]
    while True:
        r=http("https://api.assemblyai.com/v2/transcript/"+i,None,H)
        if r["status"]=="completed":return r["text"]
        if r["status"]=="error":raise Exception(r["error"])
        time.sleep(1)
def gemini(w,g,vocab):
    import base64;k=key("gemini_key")
    p="Transcribe this speech exactly, verbatim, no commentary. Languages: Croatian and English, possibly mixed."+(" Names that may occur: "+", ".join(NAMES)+"." if vocab else "")
    b={"contents":[{"parts":[{"text":p},{"inline_data":{"mime_type":"audio/wav","data":base64.b64encode(open(w,"rb").read()).decode()}}]}]}
    r=http("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="+k,json.dumps(b).encode(),{"Content-Type":"application/json"})
    return r["candidates"][0]["content"]["parts"][0]["text"]
def deepgram(w,g,vocab):
    k=key("deepgram");q="model=nova-3&language=multi" if g!="hr" else "model=nova-3&language=hr"
    if vocab:q+="".join("&keyterm="+urllib.parse.quote(n) for n in NAMES)
    return http("https://api.deepgram.com/v1/listen?"+q,open(w,"rb").read(),{"Authorization":"Token "+k,"Content-Type":"audio/wav"})["results"]["channels"][0]["alternatives"][0]["transcript"]
import urllib.parse
_PK=[]
def parakeet(w,g,vocab):
    if vocab:raise Exception("no vocab support")
    import sherpa_onnx,wave,numpy as np
    if not _PK:
        m=os.path.expanduser("~/Downloads/parakeet-from-pixel/parakeet-tdt-0.6b-v3/")
        _PK.append(sherpa_onnx.OfflineRecognizer.from_transducer(encoder=m+"encoder.onnx",decoder=m+"decoder.onnx",joiner=m+"joiner.onnx",tokens=m+"tokens.txt",model_type="nemo_transducer",num_threads=4))
    f=wave.open(w);a=np.frombuffer(f.readframes(f.getnframes()),dtype=np.int16).astype(np.float32)/32768
    st=_PK[0].create_stream();st.accept_waveform(16000,a);_PK[0].decode_stream(st);return st.result.text
ENG={"parakeet":parakeet,"groq":groq,"assemblyai":assemblyai,"gemini":gemini,"deepgram":deepgram}  # soniox, speechmatics: add when Marko has put the key in the file
def norm(t):
    t=unicodedata.normalize("NFC",t.lower());t=re.sub(r"[^\w\s]"," ",t);return t.split()
def wer(r,h):
    d=list(range(len(h)+1))
    for i,a in enumerate(r,1):
        p,d[0]=d[0],i
        for j,b in enumerate(h,1):p,d[j]=d[j],min(d[j]+1,d[j-1]+1,p+(a!=b))
    return d[-1],len(r)
def strip(t):return "".join(c for c in unicodedata.normalize("NFD",t) if unicodedata.category(c)!="Mn")
def score(ref,hyp):
    e,n=wer(norm(ref),norm(hyp));rn=norm(ref);hn=norm(hyp)
    dia=sum(1 for a in rn if a not in hn and strip(a) in [strip(x) for x in hn] and a!=strip(a))
    nm=[x for N in NAMES for x in [norm(N)] if " ".join(x) in " ".join(rn)];nw=sum(1 for x in nm if " ".join(x) not in " ".join(hn))
    return e,n,dia,nw,len(nm)
if __name__=="__main__":
    es=sys.argv[1:] or list(ENG);R={}
    rf=D+"/results.json"
    if os.path.exists(rf):R=json.load(open(rf))
    for s in S:
        wv=f"{D}/clips/{s['id']}.wav";src=f"{D}/clips/{s['id']}.webm"
        if not os.path.exists(src):continue
        if not os.path.exists(wv):subprocess.run(["ffmpeg","-y","-loglevel","error","-i",src,"-ar","16000","-ac","1",wv],check=True)
        for e in es:
            for v in (0,1):
                k=f"{e}|{v}|{s['id']}"
                if k in R:continue
                t0=time.time()
                try:R[k]={"hyp":ENG[e](wv,s["g"],v),"sec":time.time()-t0}
                except Exception as x:R[k]={"err":str(x)[:200]}
                json.dump(R,open(rf,"w"),ensure_ascii=False,indent=1)
    T={}
    for k,r in R.items():
        if "hyp" not in r:continue
        e,v,i=k.split("|");s=next(x for x in S if x["id"]==i);sc=score(s["t"],r["hyp"])
        for grp in (s["g"],"all"):
            a=T.setdefault((e,v,grp),[0]*6+[[]]);[a.__setitem__(j,a[j]+sc[j]) for j in range(5)];a[5]+=1;a[6].append(r["sec"])
    print("engine|vocab|group|WER%|name err|diacritic err|avg sec|clips")
    for (e,v,g),a in sorted(T.items()):print(f"{e}|{'yes' if v=='1' else 'no'}|{g}|{100*a[0]/max(a[1],1):.1f}|{a[3]}/{a[4]}|{a[2]}|{sum(a[6])/len(a[6]):.1f}|{a[5]}")
