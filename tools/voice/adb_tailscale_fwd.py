# Local TCP port -> SOCKS5 (tailscale userspace on 127.0.0.1:1055) -> target. For adb over Tailscale.
import socket, struct, sys, threading
LPORT, HOST, PORT = int(sys.argv[1]), sys.argv[2], int(sys.argv[3])
def socks():
    s = socket.create_connection(("127.0.0.1", 1055), timeout=15)
    s.sendall(b"\x05\x01\x00"); s.recv(2)
    h = HOST.encode()
    s.sendall(b"\x05\x01\x00\x03" + bytes([len(h)]) + h + struct.pack(">H", PORT))
    r = s.recv(10)
    if len(r) < 2 or r[1] != 0: raise OSError("socks connect failed %r" % r)
    s.settimeout(None); return s
def pipe(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d: break
            b.sendall(d)
    except OSError: pass
    finally:
        for x in (a, b):
            try: x.shutdown(socket.SHUT_RDWR)
            except OSError: pass
srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("127.0.0.1", LPORT)); srv.listen(8)
while True:
    c, _ = srv.accept()
    try: u = socks()
    except OSError as e: print(e, flush=True); c.close(); continue
    threading.Thread(target=pipe, args=(c, u), daemon=True).start()
    threading.Thread(target=pipe, args=(u, c), daemon=True).start()
