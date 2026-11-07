import json, math, sys
A='TTT/app/src/main/assets/ime/dict/'; S='SCRATCH/'
F=str.maketrans('čćšžđ','ccszd')
def loadb(p):
    b={}
    for line in open(p,encoding='utf8'):
        k,_,v=line.rstrip('\n').partition('\t')
        if v: b[k]=int(v)
    return b
D={l:json.load(open(A+l+'.json')) for l in ('en','hr')}
R={l:sorted(D[l],key=lambda w:-D[l][w]) for l in D}
def predict(lang,b,text,W=0.45,POOL=40,n=3):
    d,ranked=D[lang],R[lang]
    words=text.split(' '); word=words[-1]; prev=words[-2].lower() if len(words)>1 and words[-2] else None
    if word=='':
        if not prev: return []
        f=sorted(((k.split(' ',1)[1],v) for k,v in b.items() if k.startswith(prev+' ')),key=lambda x:-x[1])
        return [w for w,_ in f[:n]]
    t=word.translate(F).lower() if lang=='hr' else word.lower()
    pool=[w for w in ranked if (w[:len(word)].lower().translate(F)==t if lang=='hr' else w.lower().startswith(word.lower()))][:POOL]
    pr=lambda f:max(f-128,0)/127*4
    if prev: pool=sorted(pool,key=lambda w:-(pr(d[w])+W*math.log(b.get(f'{prev} {w.lower()}',0)+1)))
    return pool[:n]
import os
ref=json.load(open(S+os.environ.get('REF','res_skref.json')))
HR=set(list(ref)[int(os.environ.get('HRFROM','28')):])
def score(bs,W=0.45):
    hit=tot=0; miss=[]
    for c,s in ref.items():
        lang='hr' if c in HR else 'en'
        p=[x.lower() for x in predict(lang,bs[lang],c,W)]
        typed=c.split(' ')[-1].lower()
        for g in s:
            g=g.lower()
            if g==typed or ' ' in g or len(g)<1: continue
            tot+=1
            if g in p: hit+=1
            else: miss.append((c,g,p))
    return hit,tot,miss
old={l:loadb(A+l+'_bigrams.txt') for l in ('en','hr')}
new={l:loadb(S+'subs/'+l+'_bigrams.txt') for l in ('en','hr')}
if len(sys.argv)<2:
 for name,bs in (("old",old),("new",new)):
  for W in (0.3,0.45,0.7,1.0):
   h,t,m=score(bs,W); print(name,W,f'{h}/{t}')

def loadt(p,words,K=60000):
    t=[]
    for line in open(p,encoding='utf8'):
        c,_,k=line.strip().partition(' ')
        a=k.split(' ')
        if len(a)==3 and all(x in words for x in a): t.append((k,int(c)))
    t.sort(key=lambda x:-x[1]); return dict(t[:K])
EXTRA=int(os.environ.get('EXTRA','0'))
def predict3(lang,b,tr,text,W=0.45,W3=0.6,n=3):
    words=text.split(' '); word=words[-1]
    p1=words[-2].lower() if len(words)>1 and words[-2] else None
    p2=words[-3].lower() if len(words)>2 and words[-3] else None
    d,ranked=D[lang],R[lang]
    if word=='':
        if not p1: return []
        sc={}
        for k,v in b.items():
            if k.startswith(p1+' '): sc[k.split(' ',1)[1]]=W*math.log(v+1)
        if p2:
            for k,v in tr.items():
                if k.startswith(p2+' '+p1+' '): w=k.split(' ')[2]; sc[w]=sc.get(w,0)+W3*math.log(v+1)+2
        return [w for w,_ in sorted(sc.items(),key=lambda x:-x[1])[:n]]
    t=word.translate(F).lower() if lang=='hr' else word.lower()
    pool=[w for w in ranked if (w[:len(word)].lower().translate(F)==t if lang=='hr' else w.lower().startswith(word.lower()))][:40]
    if EXTRA and p1:
        canon={w.lower():w for w in d}
        for k in b:
            if k.startswith(p1+' '):
                f=k.split(' ',1)[1]
                if (f[:len(word)].translate(F)==t if lang=='hr' else f.startswith(word.lower())) and f in canon and canon[f] not in pool: pool.append(canon[f])
        if p2:
            for k in tr:
                if k.startswith(p2+' '+p1+' '):
                    f=k.split(' ')[2]
                    if (f[:len(word)].translate(F)==t if lang=='hr' else f.startswith(word.lower())) and f in canon and canon[f] not in pool: pool.append(canon[f])
    pr=lambda f:max(f-128,0)/127*4
    def s(w):
        x=pr(d[w])
        if p1: x+=W*math.log(b.get(f'{p1} {w.lower()}',0)+1)
        if p1 and p2: x+=W3*math.log(tr.get(f'{p2} {p1} {w.lower()}',0)+1)
        return x
    return sorted(pool,key=lambda w:-s(w))[:n]
if __name__=='__main__' and len(sys.argv)>1 and sys.argv[1]=='tri':
    langs=sys.argv[2].split(',')
    T={l:loadt(S+'subs/'+l+'.triples',{w.lower() for w in D[l]}) for l in langs}
    for W3 in (0.0,0.4,0.6,1.0):
        hit=tot=0
        for c,s in ref.items():
            lang='hr' if c in HR else 'en'
            if lang not in langs: continue
            p=[x.lower() for x in predict3(lang,new[lang],T[lang],c,W3=W3)]
            typed=c.split(' ')[-1].lower()
            for g in s:
                g=g.lower()
                if g==typed or ' ' in g: continue
                tot+=1; hit+=g in p
        print('W3',W3,f'{hit}/{tot}')
