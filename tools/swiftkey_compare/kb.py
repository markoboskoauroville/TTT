import subprocess, sys, time, json, os
S='SCRATCH'
R1='qwertyuiop'; R2='asdfghjkl'; R3='zxcvbnm'
def keymap(xs1,y1,xs2,y2,xs3,y3,space):
    m={}
    for r,xs,y in ((R1,xs1,y1),(R2,xs2,y2),(R3,xs3,y3)):
        for c,x in zip(r,xs): m[c]=(x,y)
    m[' ']=space; return m
SK=keymap([54,162,268,376,484,592,700,808,916,1022],1766,[108,216,322,430,538,646,754,862,970],1910,[216,324,432,538,646,754,862],2052,(540,2194))
TT=keymap([60,166,272,380,486,592,698,806,912,1020],1526,[110,216,324,430,538,644,752,860,968],1674,[222,328,434,540,646,752,860],1824,(592,1974))
PH={'sk':('127.0.0.1:5555',SK),'ttt':('127.0.0.1:5556',TT)}
def sh(dev,cmd): return subprocess.run(['adb','-s',dev,'shell',cmd],capture_output=True,text=True).stdout
def tap_text(ph,text):
    dev,m=PH[ph]
    cmds=[f'input tap {m[c][0]} {m[c][1]}' for c in text.lower() if c in m]
    # one shell call, small pause so the keyboard keeps up
    sh(dev,' && sleep 0.12 && '.join(cmds))
def clear(ph):
    dev,_=PH[ph]; sh(dev,'input tap 540 700; sleep 0.3; input keycombination 113 29; input keyevent 67')
def shot(ph,name):
    dev,_=PH[ph]; p=f'{S}/shots/{name}.png'; os.makedirs(f'{S}/shots',exist_ok=True)
    with open(p,'wb') as f: f.write(subprocess.run(['adb','-s',dev,'exec-out','screencap -p'],capture_output=True).stdout)
    return p
if __name__=='__main__':
    ph,text,name=sys.argv[1:4]
    clear(ph); time.sleep(0.4); tap_text(ph,text); time.sleep(1.2); print(shot(ph,name))
