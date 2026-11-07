import subprocess, re
S='SCRATCH'
BAND={'sk':(1598,90,90),'ttt':(1352,86,250)}   # y, height, x start (skip ENG badge on TTT)
GAP={'sk':60,'ttt':40}
def strip(ph,png,lang='eng+hrv'):
    y,h,x0=BAND[ph]; out=png[:-4]+'_strip.png'
    subprocess.run(['convert',png,'-crop',f'{1080-x0}x{h}+{x0}+{y}','-negate','-resize','200%','-threshold','55%',out],check=True)
    t=subprocess.run(['tesseract',out,'-','-l',lang,'--psm','7','tsv'],capture_output=True,text=True).stdout
    toks=[]
    for line in t.splitlines()[1:]:
        f=line.split('\t')
        if len(f)<12 or f[0]!='5': continue
        w=f[11].strip(' |!l1I') if f[11].strip() in ('|','l','I','1','!') else f[11].strip(' |')
        if w: toks.append((int(f[6]),int(f[6])+int(f[8]),w))
    groups=[]; last=-999
    for a,b,w in toks:
        if groups and a-last < GAP[ph]*2: groups[-1]+=' '+w
        else: groups.append(w)
        last=b
    return groups
if __name__=='__main__':
    import sys; print(strip(sys.argv[1],sys.argv[2]))
