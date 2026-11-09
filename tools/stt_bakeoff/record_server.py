#!/usr/bin/env python3
"""Local recorder for the STT bake-off: python3 record_server.py -> http://127.0.0.1:8851
Saves clips to ./clips/<id>.webm. Marko reads each sentence once."""
import http.server, json, os
D=os.path.dirname(os.path.abspath(__file__)); os.makedirs(D+"/clips",exist_ok=True)
S=open(D+"/sentences.json",encoding="utf-8").read()
PAGE="""<!doctype html><meta charset=utf-8><title>Read these</title><style>body{font:20px system-ui;max-width:760px;margin:20px auto;padding:0 16px}
.s{border-bottom:1px solid #ccc;padding:10px 0}button{font-size:18px;margin-right:8px}.ok{color:green}</style>
<h2>Read each sentence once, at your normal dictation pace</h2><div id=l></div>
<script>const S=%s;const l=document.getElementById('l');let st=null;
async function init(){st=await navigator.mediaDevices.getUserMedia({audio:true});
for(const s of S){const d=document.createElement('div');d.className='s';d.innerHTML='<b>'+s.id+'</b> '+s.t+'<br><button>Record</button><button disabled>Stop</button><span></span>';
const [b,p,sp]=d.children.length?[d.querySelector('button'),d.querySelectorAll('button')[1],d.querySelector('span')]:[];
let r,ch;b.onclick=()=>{ch=[];r=new MediaRecorder(st);r.ondataavailable=e=>ch.push(e.data);
r.onstop=async()=>{await fetch('/up/'+s.id,{method:'POST',body:new Blob(ch)});sp.textContent=' saved';sp.className='ok'};r.start();b.disabled=true;p.disabled=false;sp.textContent=' recording...'};
p.onclick=()=>{r.stop();b.disabled=false;p.disabled=true};l.appendChild(d)}}init()</script>"""%S
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(s):
        s.send_response(200);s.send_header("Content-Type","text/html;charset=utf-8");s.end_headers();s.wfile.write(PAGE.encode())
    def do_POST(s):
        i=s.path.split("/")[-1]; n=int(s.headers["Content-Length"])
        if i.isalnum(): open(f"{D}/clips/{i}.webm","wb").write(s.rfile.read(n))
        s.send_response(200);s.end_headers()
    def log_message(*a):pass
http.server.HTTPServer(("127.0.0.1",8851),H).serve_forever()
