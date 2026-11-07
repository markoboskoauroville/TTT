"""Merges conversational word pairs from OpenSubtitles (subtitle_pairs.py output, `uniq -c` format) with the
written-text bigrams into ime/dict/<lang>_bigrams.txt: 80k pairs, both words in the dictionary, subtitles
weighted 3:1 (7.11.2026, tuned against SwiftKey: 35 -> 53 of 100 SwiftKey words in the top three).
Paths A (assets) and D (pair counts) are set below."""
import json, sys
A='/home/ubuntu/TTT/app/src/main/assets/ime/dict/'; D='/tmp/claude-1001/-/84ca29d5-423c-4a24-a964-25e47bbd9109/scratchpad/subs/'
K=80000; SUBS_W=0.75   # conversational pairs weigh 3x the written ones
for l in ('en','hr'):
    words={w.lower() for w in json.load(open(A+l+'.json'))}
    old={}
    for line in open(A+l+'_bigrams.txt',encoding='utf8'):
        k,_,v=line.rstrip('\n').partition('\t')
        if v: old[k]=int(v)
    sub={}
    for line in open(D+l+'.pairs',encoding='utf8'):
        c,_,k=line.strip().partition(' ')
        a,_,b=k.partition(' ')
        if a in words and b in words: sub[k]=int(c)
    to,ts=sum(old.values()),sum(sub.values())
    scale=ts/1e6
    merged={}
    for k in set(old)|set(sub):
        p=(1-SUBS_W)*old.get(k,0)/to*1e6+SUBS_W*sub.get(k,0)/ts*1e6
        merged[k]=max(1,round(p*scale))
    top=sorted(merged.items(),key=lambda x:-x[1])[:K]
    with open(D+l+'_bigrams.txt','w',encoding='utf8') as f:
        for k,v in top: f.write(f'{k}\t{v}\n')
    print(l,'old',len(old),'subs kept',len(sub),'written',len(top))
