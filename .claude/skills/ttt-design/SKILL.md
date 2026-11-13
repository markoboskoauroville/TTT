---
name: ttt-design
description: TTT visual design rules (the host look). Load before writing ANY TTT screen, panel, pop-up, dialog, activity, chat, settings block or key: colours, key shape, rings, recorder strip, panels. Use for every new TTT module and every UI change.
---

# TTT design: follow the host, never a new look

Marko, 13.11.2026: "everything you make recently looks like it came from a different mindset ... follow the visual design of the host application and apply it to all its future modules." Build 635's RE chat (Messenger blue, gradient avatar, title bar, Material pills, Material dropdown on the RE key) was rejected: `rejected-re-chat-build-635.png`. The reference is the keyboard itself: `reference-keyboard-recorder.png`.

## 1. Reuse before drawing
1. A recorder anywhere = the keyboard strip: language badge (ENG/HR), trash, red lamp + bold clock (30 sp) + MB + hairline meter, send. Use `MaRecorderReadout` (ui/MaRecordMeter.kt) or `MaScopeCanvas`. Never a round red FAB.
2. A key anywhere = `MaHostKey` (ui/MaHost.kt), the keyboard key: face, 16 dp corners, 1.5 dp ring.
3. A long-press choice on a key = `MaKeyPanel` (ui/MaKeyPanel.kt), the H/E engine panel look: dark panel growing UP from the key, header in the key colour + x, chosen row lit, one tap picks and closes. Never `DropdownMenu`.
4. Spoken text = the Reader's highlight (MaReader.currentIndex, gold word) and `MaSubtitleRow` when Reader display is subtitles.

## 2. Colours (MaHost)
| role | colour |
|---|---|
| background | `MaHost.Bg` #11151C (the keyboard background) |
| key face / lit | `MaHost.Key` #1C2129 / `MaHost.KeyLit` #262C36 |
| ink / warm label / muted | #E6EDF3 / #F2DDB4 / #8B949E |
| accent (numbers, highlight, spoken word, drafts) | gold #E8B15C |
| ring: records voice | dark red #9B3B33, light red #E8776C while recording |
| ring: switched on | green #6FA85A |
| ring: changes the view | sand #F2DDB4 at 55 % |
| destructive | light red ink #E8776C, red ring |
Colour means STATE, never decoration. No blue, no purple, no gradients, no new reds.

## 3. Layout
- No title bars, no app names, no avatars in TTT screens. Content first; controls at the bottom as a row of keys, like the feature row.
- Bottom row of keys with weights, 44 dp minimum height, 3 dp gaps.
- Text: 17 sp body, bold labels, 13 sp secondary. Numbers in gold.
- Chat: iPhone Messages layout (his balloons right, RE's left, 18 dp corners with a 4 dp tail corner), in host colours; the keys for each answer under the balloon.
- Dialogs: key-face background, keys as buttons (`HostDialog` in MaReChatActivity.kt).
- Pressable things highlight on press; nothing breathes or blinks.

## 4. Never
- Key or token text fields (keys only through "Import keys from file").
- Material default components in their default colours (TextButton, FilledTonalButton, DropdownMenu, FAB) on TTT screens.
- A feature's own recorder, menu or button style when the keyboard already has one.

## 5. Check before push
Screenshot the new screen next to the keyboard (Pixel 7). Same colours? Same key shape? Same recorder? If something new had to be invented, write it into this file.
