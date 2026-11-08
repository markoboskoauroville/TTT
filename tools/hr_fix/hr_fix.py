#!/usr/bin/env python3
"""
hr_fix.py — the Python twin of TTT's MaCroatianFix + MaCyrillic (8.11.2026), for other apps
(the Mac's Voice Transcribe) that run Parakeet TDT 0.6B v3 and want its Croatian corrected the same way.

    python3 -I tools/hr_fix/hr_fix.py "kad stignes kuci, posalji mi poruku"
    from hr_fix import fix; fix(text)

Data: app/src/main/assets/ime/dict/{hr.json,hr_bigrams.txt,hr_trigrams.txt,en.json} (HR_FIX_DICT=dir).
Kotlin is the reference (app/.../dictate/nlp/MaCroatianFix.kt); keep both in step.
"""
import json,re,math,sys,os
D=os.environ.get('HR_FIX_DICT', os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','app','src','main','assets','ime','dict'))+'/'
hr={k.lower():v for k,v in json.load(open(D+'hr.json')).items()}
en={k.lower():v for k,v in json.load(open(D+'en.json')).items()} if __import__('os').path.exists(D+'en.json') else {}
bi={}
for l in open(D+'hr_bigrams.txt'):
    a,c=l.rstrip('\n').split('\t'); bi[a]=int(c)
tri={}
for l in open(D+'hr_trigrams.txt'):
    a,c=l.rstrip('\n').split('\t'); tri[a]=int(c)
F=str.maketrans('čćšžđ','ccszd')
fold={}
for w,f in hr.items():
    k=w.translate(F)
    if f>fold.get(k,('',0))[1]: fold[k]=(w,f)
def edits(w):
    out=set()
    for i in range(len(w)+1):
        if i<len(w): out.add(w[:i]+w[i+1:])
        if i+1<len(w): out.add(w[:i]+w[i+1]+w[i]+w[i+2:])
        for c in 'abcdefghijklmnoprstuvzj':
            if i<len(w): out.add(w[:i]+c+w[i+1:])
            out.add(w[:i]+c+w[i:])
    return out
def ctx(prev,nxt,cand,prev2=None):
    L=math.log10
    s=0
    if prev: s+=40*L(1+bi.get(prev+' '+cand,0))
    if nxt: s+=15*L(1+bi.get(cand+' '+nxt,0))
    if prev2 and prev: s+=40*L(1+tri.get(prev2+' '+prev+' '+cand,0))
    return s
def fix_word(w,prev,nxt,prev2=None):
    l=w.lower(); f=l.translate(F)
    if l in hr and hr[l]<140:
        # a rare known word with a far commoner twin one letter away ('žal mi je' -> 'žao mi je')
        best=None
        for c in edits(f):
            if len(c)!=len(f) or c not in fold: continue
            d,df=fold[c]
            if df<hr[l]+60: continue
            sc=df+ctx(prev,nxt,d,prev2)
            if ctx(prev,nxt,d,prev2)>ctx(prev,nxt,l,prev2) and (best is None or sc>best[1]): best=(d,sc)
        if best: return best[0]
    if l in hr:
        # plain form known but a diacritic form is far commoner and the context agrees
        d,df=fold.get(f,(l,0))
        cd,cl=ctx(prev,nxt,d,prev2),ctx(prev,nxt,l,prev2)
        if d!=l and df>=hr[l]+30 and (cd>cl or (cd==cl and hr[l]<200)): return d
        return l
    if f in fold: return fold[f][0]
    if len(f)<4 or (en and l in en): return l
    best=None
    cands=edits(f)
    # ikavian / shortened 'ije' ('mliko' -> 'mlijeko', 'lipo' -> 'lijepo')
    for i,ch in enumerate(f):
        if ch=='i':
            cands.add(f[:i]+'ije'+f[i+1:]); cands.add(f[:i]+'je'+f[i+1:])
    for c in cands:
        if c in fold:
            d,df=fold[c]
            if df<(150 if len(c)==len(f) else 170): continue
            sc=df+ctx(prev,nxt,d,prev2)-(0 if len(c)==len(f) else 30)
            if best is None or sc>best[1]: best=(d,sc)
    return best[0] if best else l
def merge(text):
    # 'adres u restorana': an unknown word and the next short piece make one known word
    def rep(m):
        a,b=m.group(1),m.group(2); j=(a+b).lower()
        if a.lower() not in hr and j.translate(F) in fold and len(b)<=3: return a+b
        return m.group(0)
    return re.sub(r"\b(\w{3,}) (\w{1,3})\b",rep,text)
SINGLE=set('iuaskov')
def segment(letters):
    # best split of letters into 1..3 known words (folded), by summed frequency
    n=len(letters); best={0:(0,[])}
    for i in range(1,n+1):
        for j in range(max(0,i-14),i):
            if j not in best: continue
            piece=letters[j:i]
            if len(piece)==1 and piece not in SINGLE: continue
            if piece not in fold: continue
            d,f=fold[piece]
            if f<150: continue
            sc=best[j][0]+f-40   # each extra word costs
            if len(best[j][1])<3 and (i not in best or sc>best[i][0]): best[i]=(sc,best[j][1]+[d])
    return best.get(n)
def resegment(text):
    toks=text.split(' ')
    i=0
    while i<len(toks):
        core=re.sub(r'\W','',toks[i]).lower()
        if core and core.isalpha() and core not in hr and core.translate(F) not in fold:
            done=False
            for a,b in ((i-2,i),(i-1,i),(i,i+1),(i-1,i+1)):
                if a<0 or b>=len(toks): continue
                win=toks[a:b+1]
                if any(re.search(r'[^\w]',w[:-1]) or not re.sub(r'\W','',w).isalpha() for w in win): continue
                letters=''.join(re.sub(r'\W','',w).lower().translate(F) for w in win)
                seg=segment(letters)
                if seg and len(seg[1])<len(win):
                    trail=re.sub(r'^\w+','',win[-1])
                    new=' '.join(seg[1])
                    if win[0][0].isupper(): new=new[0].upper()+new[1:]
                    toks[a:b+1]=[new+trail]; i=a; done=True; break
            if done: i+=1; continue
        i+=1
    return ' '.join(toks)
def fix(text):
    text=resegment(merge(text))
    toks=re.findall(r"\w+|\W+",text)
    words=[i for i,t in enumerate(toks) if re.match(r"\w",t) and not t.isdigit()]
    out=list(toks)
    for n,i in enumerate(words):
        t=toks[i]
        if t[0].isupper() and n>0 and not re.search(r"[.!?]\s*$", ''.join(toks[:i])): continue
        prev=toks[words[n-1]].lower() if n>0 else None
        nxt=toks[words[n+1]].lower() if n+1<len(words) else None
        prev2=toks[words[n-2]].lower() if n>1 else None
        r=fix_word(t,prev,nxt,prev2)
        if t[0].isupper(): r=r[0].upper()+r[1:]
        out[i]=r
    return ''.join(out)
CYR={'а':'a','б':'b','в':'v','г':'g','д':'d','ђ':'đ','е':'e','ж':'ž','з':'z','и':'i','ј':'j','к':'k','л':'l','љ':'lj','м':'m','н':'n','њ':'nj','о':'o','п':'p','р':'r','с':'s','т':'t','ћ':'ć','у':'u','ф':'f','х':'h','ц':'c','ч':'č','џ':'dž','ш':'š','я':'ja','ю':'ju','ё':'jo','й':'j','щ':'šč','ы':'i','э':'e','ь':'','ъ':''}
def to_latin(t):
    out=[]
    for i,c in enumerate(t):
        l=c.lower(); r=CYR.get(l)
        if r is None: out.append(c); continue
        if c==l or not r: out.append(r); continue
        nx=t[i+1] if i+1<len(t) else ''
        out.append(r.upper() if nx.isalpha() and nx.isupper() else r[:1].upper()+r[1:])
    return ''.join(out)
if __name__=='__main__':
    for t in sys.argv[1:]:
        print(fix(to_latin(t)))
