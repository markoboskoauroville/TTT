#!/usr/bin/env python3
"""MaSettingsSync (13.11.2026): settings between phones through his private repository; off by default; no secrets."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = os.path.join(R, "app/src/main/kotlin/dev/patrickgold/florisboard")
def read(p): return open(os.path.join(K, p), encoding="utf-8").read()
fail = []
def check(ok, msg):
    if not ok: fail.append(msg)

s = read("dictate/MaSettingsSync.kt")
check('getBoolean("on", false)' in s, "off by default")
m = re.search(r'private val PRIVATE = Regex\(\s*(.*?)\n    \)', s, re.S)
check(m is not None, "PRIVATE pattern found")
if m:
    pat = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)))
    rx = re.compile(pat)
    keys = re.findall(r'key = "([a-z0-9_]+)"', read("app/AppPrefs.kt"))
    for k in keys:
        if re.search(r"api_key|password|token|secret|proxy|provider_accounts|last_dictation", k):
            check(rx.search(k) is not None, f"secret-like pref {k} must never be synced")
    for k in ("dictate__ma_rows", "dictate__ma_key_widths", "dictate__ma_key_colors", "dictate__ma_settings_order"):
        if k in keys:
            check(rx.search(k) is None, f"layout pref {k} must be synced")
check("ImportStrategy.Merge" in s, "applied with Merge (keys and per-phone state stay)")
check("firstTime" in s, "a phone syncing for the first time takes the repository's settings")
check("MaSettingsSync.maybePull()" in read("FlorisImeService.kt") and "MaSettingsSync.syncSoon()" in read("FlorisImeService.kt"),
      "keyboard open pulls, keyboard close pushes")
check("MaSettingsSync.syncSoon()" in read("app/FlorisAppActivity.kt"), "leaving Settings pushes")
check("MaSettingsSync.offerTokens(toks)" in read("dictate/engines/KeyImporter.kt"), "token only through the key file picker")
check("OutlinedTextField" in read("app/settings/dictate/MaSettingsSyncScreen.kt") and "token" not in
      re.sub(r'"[^"]*"', "", read("app/settings/dictate/MaSettingsSyncScreen.kt")).lower().split("outlinedtextfield")[1][:300],
      "the only text field is the repository name, never a token box")
check("SETTINGS_SYNC" in read("app/settings/MaSettingsOrderScreen.kt"), "entry has a route")
if fail:
    print("\n".join("FAIL " + f for f in fail)); sys.exit(1)
print("settings sync ok")
