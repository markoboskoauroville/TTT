import json, sys, unicodedata
S='SCRATCH'
sk=json.load(open(f'{S}/res_{sys.argv[1]}.json')); tt=json.load(open(f'{S}/res_{sys.argv[2]}.json'))
def norm(w): return w.strip().lower().strip('.,"\'')
def near(a,b):  # OCR slack: bold first word loses a letter
    a=a.lower(); b=b.lower()
    return a==b or (len(a)>=3 and (a in b or b in a) and abs(len(a)-len(b))<=1) or (len(b)>=2 and a.endswith(b) and len(a)-len(b)<=2)
top1=any3=anyN=n=0; cov=tot=0
for case,s in sk.items():
    t=[norm(x) for x in tt.get(case,[])]; typed=norm(case.split(' ')[-1])
    targets=[norm(x) for x in s if norm(x) and norm(x)!=typed and ' ' not in norm(x)]
    if not targets: continue
    n+=1; goal=targets[0]
    h1=bool(t) and near(goal,t[0]); h3=any(near(goal,x) for x in t[:3]); hN=any(near(goal,x) for x in t)
    top1+=h1; any3+=h3; anyN+=hN
    for g in targets: tot+=1; cov+=any(near(g,x) for x in t)
    print(f"{'OK ' if h3 else ('~  ' if hN else 'MISS')} {case!r:24} SK={s}  TTT={tt.get(case,[])[:6]}")
print(f'\n(coverage: {cov}/{tot} SwiftKey words appear in the TTT strip)')
print(f"coverage: {cov}/{tot} SwiftKey words appear in the TTT strip")
print(f'\n{n} cases: SwiftKey top word = TTT first {top1}, in TTT top 3 {any3}, anywhere in TTT strip {anyN}')
