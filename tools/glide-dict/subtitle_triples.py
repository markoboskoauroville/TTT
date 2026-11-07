import sys, re
W=re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)?")
out=sys.stdout
for line in sys.stdin:
    ws=W.findall(line.strip().lower())
    for a,b,c in zip(ws,ws[1:],ws[2:]): out.write(a+' '+b+' '+c+'\n')
