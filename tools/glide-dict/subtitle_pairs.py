# One line of text in, its lowercase word pairs out. Feed: OpenSubtitles v2018 mono <lang>.txt.gz, first 12M lines;
# then: LC_ALL=C sort | uniq -c | awk '$1>=8' > <lang>.pairs (see merge_bigrams.py).
import sys, re
W=re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)?")
out=sys.stdout
for line in sys.stdin:
    line=line.strip().lstrip('-').strip()
    if not line: continue
    ws=W.findall(line.lower())
    for a,b in zip(ws,ws[1:]): out.write(a+' '+b+'\n')
