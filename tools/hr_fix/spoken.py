#!/usr/bin/env python3
"""
spoken.py — Python twins of TTT's MaNameSpelling, MaSpokenTime and MaSpokenEmail (8.11.2026), for the
Mac's ears. Kotlin is the reference (app/.../dictate/nlp/); keep both in step.

    from spoken import finish
    finish(raw_text, saved_emails=["marko.bosko@gmail.com"], croatian=None)

finish(): Cyrillic to Latin, Croatian fix (hr_fix) when croatian is True, or None and most words are
Croatian, his name with k, spoken email joined and snapped to saved_emails, "at 330" -> "at 3:30".
"""
import re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hr_fix

NAMES=[("marco","marko"),("bosco","bosko")]
def names(text):
    for a,b in NAMES:
        def rep(m,b=b):
            w=m.group(0)
            if w.isupper(): return b.upper()
            if w[0].isupper(): return b[0].upper()+b[1:]
            return b
        text=re.sub(r"(?i)(?<![\w])"+a+r"(?![\w])",rep,text)
    return text

TIME=re.compile(r"(?i)\b(at|by|until|till|from|before|after|around|about|since)\s+(\d{1,2})(\d{2})\b(?![:.,]\d)")
def times(text):
    def rep(m):
        h,mm=int(m.group(2)),int(m.group(3)); digits=m.group(2)+m.group(3)
        if 1<=h<=23 and 0<=mm<=55 and mm%5==0 and not (len(digits)==4 and digits.startswith("0")):
            return f"{m.group(1)} {h}:{m.group(3)}"
        return m.group(0)
    return TIME.sub(rep,text)

def looks_croatian(text):
    ws=[w for w in re.findall(r"[^\W\d_]+",text.lower()) if len(w)>=2]
    if len(ws)<2: return False
    hr_only=sum(1 for w in ws if (w in hr_fix.hr or w.translate(hr_fix.F) in hr_fix.fold) and w not in hr_fix.en)
    en_only=sum(1 for w in ws if w in hr_fix.en and w not in hr_fix.hr)
    return hr_only>en_only

def finish(text, saved_emails=(), croatian=None):
    latin=hr_fix.to_latin(text)
    if croatian is None: croatian = latin!=text or looks_croatian(latin)
    if croatian: latin=hr_fix.fix(latin)
    return times(emails(names(latin), saved_emails))

import re
AT={"at","et","@","majmun","monkey","affe"}; DOT={"dot","točka","tocka","punkt","period"}
SEP={"dot":".","točka":".","tocka":".","period":".","underscore":"_","podvlaka":"_","dash":"-","hyphen":"-","crtica":"-","minus":"-"}
MAIL={"gmail.com","googlemail.com","yahoo.com","outlook.com","hotmail.com","live.com","icloud.com","me.com","proton.me","protonmail.com","gmx.com","gmx.net","aol.com","mail.com","yandex.com","net.hr","t-com.hr","gmail.hr","inet.hr","zg.t-com.hr","auroville.community","auroville.org.in"}
PRON={"me","us","you","him","her","them","it","i","we","mi","nas","ja","ti","te","vas","email","mail","home","work"}
TLD=re.compile(r"^[a-z]{2,12}$"); DP=re.compile(r"^[a-z0-9-]+$"); NP=re.compile(r"^[\w.%+-]+$")
WR=re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")
LE=" \t\n\"'(“‘"; TR=" \t\n.,!?;:\"')”’"
def toks(t):
    out=[]
    for m in re.finditer(r"\S+",t):
        s=m.group(); a=0;b=len(s)
        while a<b and s[a] in LE:a+=1
        while b>a and s[b-1] in TR:b-=1
        out.append(dict(start=m.start(),end=m.end(),core=s[a:b],cs=m.start()+a,ce=m.start()+b))
    return out
def letters(s): return "".join({'č':'c','ć':'c','š':'s','ž':'z','đ':'d'}.get(c,c) for c in s.lower() if c.isalnum())
def dist(a,b):
    p=list(range(len(b)+1))
    for i in range(1,len(a)+1):
        c=[i]+[0]*len(b)
        for j in range(1,len(b)+1): c[j]=min(p[j]+1,c[j-1]+1,p[j-1]+(a[i-1]!=b[j-1]))
        p=c
    return p[len(b)]
def score(cand,saved):
    cl=letters(cand.split('@')[0]); cd=cand.split('@',1)[1].lower()
    if not cl: return None
    best=None
    for s in saved:
        sd=s.split('@',1)[1].lower()
        if sd!=cd and dist(sd,cd)>1: continue
        sl=letters(s.split('@')[0]); d=dist(cl,sl)
        if d>len(sl)//4: continue
        rel=d/len(sl)+(0 if sd==cd else .1)
        if best is None or rel<best[1]: best=(s,rel)
    return best
def snap(text,saved):
    T=toks(text)
    if len(T)<3: return text
    sb="";cp=0;i=1
    while i<len(T)-1:
        at=T[i]
        if at['core'].lower() not in AT or (at['ce']!=at['end'] and at['core']!="@"): i+=1;continue
        dp=[];j=i+1;last=-1;f=T[j]['core'].lower()
        if '.' in f:
            ps=f.split('.')
            if all(DP.match(x) for x in ps): dp+=ps; last=j
        elif DP.match(f):
            dp.append(f); last=j
            while T[last]['ce']==T[last]['end'] and last+2<len(T) and T[last+1]['core'].lower() in DOT and DP.match(T[last+2]['core'].lower()):
                dp.append(T[last+2]['core'].lower()); last+=2
        if len(dp)<2 or not TLD.match(dp[-1]) or last<0: i+=1;continue
        dom=".".join(dp); k=i-1
        if not NP.match(T[k]['core']) or T[k]['ce']!=T[k]['end']: i+=1;continue
        loc=T[k]['core'].lower()
        while k-2>=0 and T[k-1]['core'].lower() in SEP and T[k-1]['ce']==T[k-1]['end'] and T[k-2]['ce']==T[k-2]['end'] and NP.match(T[k-2]['core']):
            loc=T[k-2]['core'].lower()+SEP[T[k-1]['core'].lower()]+loc; k-=2
        if not loc or cp>T[k]['cs']: i+=1;continue
        ex=[];e=k-1;x=loc
        while e>=0 and len(ex)<2 and T[e]['ce']==T[e]['end'] and NP.match(T[e]['core']) and T[e]['start']>=cp:
            x=T[e]['core'].lower()+x; ex.append((x,e)); e-=1
        sn=None
        if saved:
            best=None
            for cand,tk in [(loc+"@"+dom,None)]+[(n+"@"+dom,t) for n,t in ex]:
                h=score(cand,saved)
                if h and (best is None or h[1]<best[2]): best=(h[0],tk,h[1])
            if best: sn=(best[0],best[1])
        if sn: rep=sn[0]; st=sn[1] if sn[1] is not None else k
        else:
            known=dom in MAIL or any(s.split('@',1)[1].lower()==dom for s in saved)
            if not known or loc in PRON or len(loc)<2: i+=1;continue
            rep=loc+"@"+dom; st=k
        sb+=text[cp:T[st]['cs']]+rep; cp=T[last]['ce']; i=last+1
    if cp==0: return text
    return sb+text[cp:]
def emails(t,saved=()):
    saved=list(saved)
    o=snap(t,saved)
    if saved: o=WR.sub(lambda m:(score(m.group().lower(),saved) or (m.group(),))[0],o)
    return o

if __name__=="__main__":
    for t in sys.argv[1:]: print(finish(t, ["marko.bosko@gmail.com"]))
