"""Writes ime/dict/<lang>_trigrams.txt from OpenSubtitles three-word counts (`uniq -c` format): the 60k most
frequent sequences whose three words are all in the dictionary (7.11.2026, SwiftKey tuning)."""
import json, sys
A='/home/ubuntu/TTT/app/src/main/assets/ime/dict/'; D='/tmp/claude-1001/-/84ca29d5-423c-4a24-a964-25e47bbd9109/scratchpad/subs/'
l=sys.argv[1]; K=60000
words={w.lower() for w in json.load(open(A+l+'.json'))}
t=[]
for line in open(D+l+'.triples',encoding='utf8'):
    c,_,k=line.strip().partition(' ')
    a=k.split(' ')
    if len(a)==3 and all(x in words for x in a): t.append((k,int(c)))
t.sort(key=lambda x:-x[1])
with open(A+l+'_trigrams.txt','w',encoding='utf8') as f:
    for k,v in t[:K]: f.write(f'{k}\t{v}\n')
print(l,len(t),'->',min(K,len(t)))
