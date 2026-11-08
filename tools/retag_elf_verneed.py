#!/usr/bin/env python3
"""Retag one ELF version requirement (e.g. VERS_1.24.3 -> VERS_1.28.2) in a shared library.

Used by tools/fetch-sherpa-onnx.sh: the ONNX Runtime JNI bridge from Maven (onnxruntime4j_jni,
1.24.3) asks for `OrtGetApiBase@VERS_1.24.3`, while sherpa-onnx 2.x ships libonnxruntime.so
1.28.2, whose symbols are tagged VERS_1.28.2. The C API is backward compatible (the bridge asks
for API level 24, which 1.28 serves), so only the version tag has to match. The two names must
have the same length; the string in .dynstr and the hash in .gnu.version_r are both rewritten.

Usage: retag_elf_verneed.py <lib.so> <OLD> <NEW>
"""
import struct
import sys


def elf_hash(name: bytes) -> int:
    h = 0
    for c in name:
        h = ((h << 4) + c) & 0xFFFFFFFF
        g = h & 0xF0000000
        if g:
            h ^= g >> 24
        h &= ~g & 0xFFFFFFFF
    return h


def main(path: str, old: str, new: str) -> None:
    old_b, new_b = old.encode(), new.encode()
    if len(old_b) != len(new_b):
        sys.exit("names must have the same length")
    data = bytearray(open(path, "rb").read())
    if data[:4] != b"\x7fELF":
        sys.exit("not an ELF file")
    is64 = data[4] == 2
    end = "<" if data[5] == 1 else ">"
    if is64:
        shoff, = struct.unpack_from(end + "Q", data, 0x28)
        shentsize, shnum, shstrndx = struct.unpack_from(end + "HHH", data, 0x3A)
    else:
        shoff, = struct.unpack_from(end + "I", data, 0x20)
        shentsize, shnum, shstrndx = struct.unpack_from(end + "HHH", data, 0x2E)

    def section(i):
        base = shoff + i * shentsize
        if is64:
            name, typ, _flags, _addr, off, size, link = struct.unpack_from(end + "IIQQQQI", data, base)
        else:
            name, typ, _flags, _addr, off, size, link = struct.unpack_from(end + "IIIIIII", data, base)
        return name, typ, off, size, link

    SHT_GNU_VERNEED = 0x6FFFFFFE
    verneed = [section(i) for i in range(shnum) if section(i)[1] == SHT_GNU_VERNEED]
    if len(verneed) != 1:
        sys.exit("no single .gnu.version_r section")
    _, _, vr_off, _vr_size, strtab_idx = verneed[0]
    _, _, str_off, str_size, _ = section(strtab_idx)

    changed = 0
    off = vr_off
    while True:
        _ver, cnt, _file, aux, nxt = struct.unpack_from(end + "HHIII", data, off)
        a = off + aux
        for _ in range(cnt):
            _h, flags, other, name, anext = struct.unpack_from(end + "IHHII", data, a)
            s = str_off + name
            if data[s:s + len(old_b) + 1] == old_b + b"\0":
                data[s:s + len(new_b)] = new_b
                struct.pack_into(end + "IHHII", data, a, elf_hash(new_b), flags, other, name, anext)
                changed += 1
            if anext == 0:
                break
            a += anext
        if nxt == 0:
            break
        off += nxt
    if changed == 0:
        already = new_b + b"\0" in bytes(data[str_off:str_off + str_size])
        if already:
            print(f"  {path}: already {new}")
            return
        sys.exit(f"{old} not found in {path}")
    open(path, "wb").write(bytes(data))
    print(f"  {path}: {old} -> {new} ({changed} entr{'y' if changed == 1 else 'ies'})")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
