#!/usr/bin/env python3
"""
TTT, the launcher icon, in the Mantra Shruti Box style (Marko, 21.10.2026: "change this icon of
this app into the same style ... instead of buttons of the Shruti box, there will be buttons of this
keyboard with this specific feature that each button has different color ... glowing nice style").

The keyboard as it looks: dark keycaps on a black ground, the LEGEND of each key lit in its own colour
(the G815 look of Settings -> Key colours), each in a halo as the Shruti valves glow. A, B and C
(Marko: "don't put ttt, put abc") over a microphone key and the space bar. The letters are drawn as
lit tubes: a coloured stroke with a pale core.

Writes, from the one list of shapes below:
  app/src/main/icon/ic_app_icon_foreground.xml   template, Gradle draws the build number under it
  app/src/main/icon/ic_app_icon_monochrome.xml   the themed (one-tint) twin, same template rule
  design/app_icon.svg                            the same picture, to look at anywhere

Measured to app-icon.md: everything solid inside the 42-unit box 33...75; the keycaps span 42 of the
72 mask (0.583). The halos fade out before the mask.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

CAP_FILL = "#1A1F27"
CAP_EDGE = "#2C323E"

# x, y, w, h, glyph, colour
KEYS = [
    (33.3, 34.3, 12.4, 14.4, "A", "#FF4D5E"),   # red, as the number keys in his screenshot
    (48.8, 34.3, 12.4, 14.4, "B", "#FFC24D"),   # gold
    (64.3, 34.3, 12.4, 14.4, "C", "#4DD6E8"),   # cyan, the TTT colour
    (33.3, 51.0, 12.4, 10.0, "mic", "#B98CFF"), # violet
    (48.8, 51.0, 27.9, 10.0, "space", "#5CE68A"),  # green
]


def f(v: float) -> str:
    return f"{v:.2f}".rstrip("0").rstrip(".")


def rrect(x, y, w, h, r):
    return (f"M{f(x + r)},{f(y)} H{f(x + w - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w)},{f(y + r)} "
            f"V{f(y + h - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w - r)},{f(y + h)} H{f(x + r)} "
            f"A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y + h - r)} V{f(y + r)} A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)} Z")


def glyph(kind, x, y, w, h):
    """(path, filled) for the legend; stroked legends are drawn at stroke width 1.6."""
    cx, cy = x + w / 2, y + h / 2
    top, bottom = y + 3.3, y + h - 3.3
    mid = (top + bottom) / 2
    if kind == "A":
        return (f"M{f(cx - 3.1)},{f(bottom)} L{f(cx)},{f(top)} L{f(cx + 3.1)},{f(bottom)} "
                f"M{f(cx - 1.9)},{f(mid + 1.2)} H{f(cx + 1.9)}"), "neon"
    if kind == "B":
        x0 = cx - 2.6
        r1, r2 = (mid - top) / 2, (bottom - mid) / 2
        return (f"M{f(x0)},{f(top)} V{f(bottom)} H{f(x0 + 2.9)} A{f(r2)},{f(r2)} 0 0 0 {f(x0 + 2.9)},{f(mid)} H{f(x0)} "
                f"M{f(x0)},{f(top)} H{f(x0 + 2.7)} A{f(r1)},{f(r1)} 0 0 1 {f(x0 + 2.7)},{f(mid)}"), "neon"
    if kind == "C":
        rx, ry, dx = 3.2, (bottom - top) / 2, 2.7
        dy = ry * (1 - (dx / rx) ** 2) ** 0.5
        return (f"M{f(cx + dx)},{f(mid - dy)} A{f(rx)},{f(ry)} 0 1 0 {f(cx + dx)},{f(mid + dy)}"), "neon"
    if kind == "mic":
        # the capsule filled, the cradle and stand stroked: two paths
        cap = rrect(cx - 1.4, y + 1.8, 2.8, 4.4, 1.4)
        stand = (f"M{f(cx - 2.6)},{f(y + 4.4)} A2.6,2.6 0 0 0 {f(cx + 2.6)},{f(y + 4.4)} "
                 f"M{f(cx)},{f(y + 7.0)} V{f(y + 8.4)}")
        return [(cap, True), (stand, False)], None
    if kind == "space":
        return (f"M{f(cx - 7)},{f(cy - 1.2)} V{f(cy + 1.4)} H{f(cx + 7)} V{f(cy - 1.2)}"), False
    raise ValueError(kind)


def parts(kind, x, y, w, h):
    g, filled = glyph(kind, x, y, w, h)
    return g if filled is None else [(g, filled)]


def lighten(hex_colour, k):
    c = [int(hex_colour[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(v + (255 - v) * k):02X}" for v in c)


# ------------------------------------------------------------------ Android vector drawable

def vd_foreground():
    out = []
    for x, y, w, h, kind, col in KEYS:
        out.append(f'    <path android:pathData="{rrect(x, y, w, h, 2.2)}" android:fillColor="{CAP_FILL}" '
                   f'android:strokeColor="{CAP_EDGE}" android:strokeWidth="0.6" />')
    for x, y, w, h, kind, col in KEYS:
        cx, cy = x + w / 2, y + h / 2
        r = min(w, 16) * 0.62
        sy = h / min(w, 16) * 1.05 if kind in ("A", "B", "C") else 0.8
        sx = 1.0 if kind != "space" else 2.0
        c = col[1:]
        out.append(f'    <group android:pivotX="{f(cx)}" android:pivotY="{f(cy)}" android:scaleX="{f(sx)}" android:scaleY="{f(sy)}">')
        out.append(f'        <path android:pathData="M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1 0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1 0 {f(-2 * r)},0 Z">')
        out.append('            <aapt:attr name="android:fillColor">')
        out.append(f'                <gradient android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" android:gradientRadius="{f(r)}">')
        out.append(f'                    <item android:offset="0" android:color="#B3{c}" />')
        out.append(f'                    <item android:offset="0.45" android:color="#59{c}" />')
        out.append(f'                    <item android:offset="1" android:color="#00{c}" />')
        out.append('                </gradient>')
        out.append('            </aapt:attr>')
        out.append('        </path>')
        out.append('    </group>')
    for x, y, w, h, kind, col in KEYS:
        light = lighten(col, 0.75)
        for d, filled in parts(kind, x, y, w, h):
            if filled == "neon":
                out.append(f'    <path android:pathData="{d}" android:strokeColor="{col}" android:strokeWidth="2.3" '
                           f'android:strokeLineCap="round" android:strokeLineJoin="round" />')
                out.append(f'    <path android:pathData="{d}" android:strokeColor="{lighten(col, 0.85)}" android:strokeWidth="0.9" '
                           f'android:strokeLineCap="round" android:strokeLineJoin="round" />')
            elif filled:
                out.append(f'    <path android:pathData="{d}" android:strokeColor="{col}" android:strokeWidth="0.5">')
                out.append('        <aapt:attr name="android:fillColor">')
                out.append(f'            <gradient android:type="linear" android:startX="{f(x)}" android:startY="0" android:endX="{f(x + w)}" android:endY="0">')
                out.append(f'                <item android:offset="0.2" android:color="{col}" />')
                out.append(f'                <item android:offset="0.5" android:color="{light}" />')
                out.append(f'                <item android:offset="0.8" android:color="{col}" />')
                out.append('            </gradient>')
                out.append('        </aapt:attr>')
                out.append('    </path>')
            else:
                out.append(f'    <path android:pathData="{d}" android:strokeColor="{light}" android:strokeWidth="1.6" '
                           f'android:strokeLineCap="round" android:strokeLineJoin="round" />')
    return "\n".join(out)


def vd_monochrome():
    out = []
    for x, y, w, h, kind, col in KEYS:
        out.append(f'    <path android:pathData="{rrect(x, y, w, h, 2.2)}" android:fillColor="#00000000" '
                   f'android:strokeColor="#FFFFFF" android:strokeWidth="0.6" />')
        for d, filled in parts(kind, x, y, w, h):
            if filled == "neon":
                out.append(f'    <path android:pathData="{d}" android:strokeColor="#FFFFFF" android:strokeWidth="2.1" '
                           f'android:strokeLineCap="round" android:strokeLineJoin="round" />')
            elif filled:
                out.append(f'    <path android:pathData="{d}" android:fillColor="#FFFFFF" />')
            else:
                out.append(f'    <path android:pathData="{d}" android:strokeColor="#FFFFFF" android:strokeWidth="1.6" '
                           f'android:strokeLineCap="round" android:strokeLineJoin="round" />')
    return "\n".join(out)


HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
     {what} GENERATED by design/make_app_icon.py; change it there. THIS IS A TEMPLATE: app/build.gradle.kts
     writes the real drawable into build/generated/versionIcon with the build number drawn under the keys
     (version-visible.md), at the <!--VERSION- -> line.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
"""


def write_vd(name, what, body, aapt):
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    text = HEADER.replace("<!--VERSION- ->", "VERSION").format(what=what, ns=ns) + body + "\n    <!--VERSION-->\n</vector>\n"
    p = ROOT / "app/src/main/icon" / f"{name}.xml"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


# ------------------------------------------------------------------ SVG, the same picture

def svg(version="v403", background="#000000", mono=False):
    defs, body = [], []
    body.append(f'<rect width="108" height="108" fill="{background}"/>')
    for i, (x, y, w, h, kind, col) in enumerate(KEYS):
        if mono:
            body.append(f'<path d="{rrect(x, y, w, h, 2.2)}" fill="none" stroke="#fff" stroke-width="0.6"/>')
        else:
            body.append(f'<path d="{rrect(x, y, w, h, 2.2)}" fill="{CAP_FILL}" stroke="{CAP_EDGE}" stroke-width="0.6"/>')
    if not mono:
        for i, (x, y, w, h, kind, col) in enumerate(KEYS):
            cx, cy = x + w / 2, y + h / 2
            r = min(w, 16) * 0.62
            sy = h / min(w, 16) * 1.05 if kind in ("A", "B", "C") else 0.8
            sx = 1.0 if kind != "space" else 2.0
            defs.append(f'<radialGradient id="h{i}" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" gradientUnits="userSpaceOnUse">'
                        f'<stop offset="0" stop-color="{col}" stop-opacity="0.7"/><stop offset="0.45" stop-color="{col}" stop-opacity="0.35"/>'
                        f'<stop offset="1" stop-color="{col}" stop-opacity="0"/></radialGradient>')
            body.append(f'<g transform="translate({f(cx)} {f(cy)}) scale({f(sx)} {f(sy)}) translate({f(-cx)} {f(-cy)})">'
                        f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="url(#h{i})"/></g>')
    for i, (x, y, w, h, kind, col) in enumerate(KEYS):
        light = lighten(col, 0.75)
        if not mono:
            defs.append(f'<linearGradient id="g{i}" x1="{f(x)}" y1="0" x2="{f(x + w)}" y2="0" gradientUnits="userSpaceOnUse">'
                        f'<stop offset="0.2" stop-color="{col}"/><stop offset="0.5" stop-color="{light}"/><stop offset="0.8" stop-color="{col}"/></linearGradient>')
        for d, filled in parts(kind, x, y, w, h):
            if filled == "neon":
                rl = 'stroke-linecap="round" stroke-linejoin="round" fill="none"'
                if mono:
                    body.append(f'<path d="{d}" stroke="#fff" stroke-width="2.1" {rl}/>')
                else:
                    body.append(f'<path d="{d}" stroke="{col}" stroke-width="2.3" {rl}/>')
                    body.append(f'<path d="{d}" stroke="{lighten(col, 0.85)}" stroke-width="0.9" {rl}/>')
            elif filled:
                body.append(f'<path d="{d}" fill="#fff"/>' if mono else
                            f'<path d="{d}" fill="url(#g{i})" stroke="{col}" stroke-width="0.5"/>')
            else:
                body.append(f'<path d="{d}" fill="none" stroke="{"#fff" if mono else light}" stroke-width="1.6" '
                            f'stroke-linecap="round" stroke-linejoin="round"/>')
    body.append(version_svg(version, "#fff" if mono else VERSION_COLOUR))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">'
            f'<defs>{"".join(defs)}</defs>{"".join(body)}</svg>')


# The build number, as app/build.gradle.kts draws it (seven segments).
VERSION_COLOUR = "#C9D1D9"
VERSION_TOP = 64.6
SEG = {'a': "M0,0 H5", 'b': "M5,0 V5", 'c': "M5,5 V10", 'd': "M0,10 H5", 'e': "M0,5 V10", 'f': "M0,0 V5", 'g': "M0,5 H5"}
DIG = {'0': "abcdef", '1': "bc", '2': "abged", '3': "abgcd", '4': "fgbc", '5': "afgcd", '6': "afgedc",
       '7': "abc", '8': "abcdefg", '9': "abcdfg"}


def version_svg(text, colour):
    cell, gap, scale = 5.0, 2.2, 0.85
    width = (len(text) * cell + (len(text) - 1) * gap) * scale
    left = 54 - width / 2
    out = []
    for i, ch in enumerate(text):
        d = "M0,4 L2.5,10 L5,4" if ch == "v" else " ".join(SEG[s] for s in DIG[ch])
        out.append(f'<path transform="translate({f(left + i * (cell + gap) * scale)} {VERSION_TOP}) scale({scale})" d="{d}" '
                   f'fill="none" stroke="{colour}" stroke-width="{f(1.5 / scale)}" stroke-linecap="round" stroke-linejoin="round"/>')
    return "".join(out)


if __name__ == "__main__":
    write_vd("ic_app_icon_foreground", "TTT, the launcher icon: lit legends on dark keys.", vd_foreground(), True)
    write_vd("ic_app_icon_monochrome", "TTT, the themed icon (one tint, by alpha alone).", vd_monochrome(), False)
    (ROOT / "design").mkdir(exist_ok=True)
    (ROOT / "design/app_icon.svg").write_text(svg())
    (ROOT / "design/app_icon_monochrome.svg").write_text(svg(background="#3A3F47", mono=True))
    print("written")
