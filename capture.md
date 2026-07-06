# Dash Captures

Local capture directory: `/home/aditya/ns_captures/`

These pcaps are intentionally not committed to the repo. Keep them on the workstation and use this file as the index for the protocol work.

## 2026-06-30 Captures

- `/home/aditya/ns_captures/exit_nav_20260630-101745.pcap`
- `/home/aditya/ns_captures/zoom_20260630-102038.pcap`
- `/home/aditya/ns_captures/media_20260630-102513.pcap`
- `/home/aditya/ns_captures/media_from_nav_20260630-105837.pcap`
- `/home/aditya/ns_captures/call_20260630-110620.pcap`

Older reference:

- `/home/aditya/ns_captures/re_session_20260620-101351.pcap`

Reverse notes outside the repo:

- `/home/aditya/ns_reverse/FINDINGS.md`
- `/home/aditya/ns_reverse/MEDIA_PROTOCOL.md`
- `/home/aditya/ns_reverse/CAPTURE_PLAN.md`

## Findings Used In Code

- Map zoom joystick codes from the `zoom` capture: `0x13` is zoom in and `0x14` is zoom out.
- Media-from-navigation controls: `0x05` is play/pause, `0x06` and `0x07` are volume, and `0x09`/`0x0A` are next/previous after entering media controls.
- Standalone media: `0x18` enters/exposes the dash media-control overlay.
- Now-playing `05 0d` carries three NUL-separated text fields in captured RE order: album, title, artist.
- Album art `05 40` in the full-snaplen captures uses large chunks. Observed transfers had value lengths `1001` and `300`, with the first value byte as a transfer flag (`0x01` for non-final chunks, `0xFF` for final chunks).

## Open Gaps

- Exact dash state TLVs that make the native `+/-` chrome appear as separate icons are not fully isolated yet.
- Exit-navigation close sequence is only partly isolated. The button code was observed, but the complete app-to-dash teardown sequence still needs a focused decode.
- Call answer/reject/end is not a current target because the RE app itself did not answer or reject calls from the dash during capture.

## Decoded 2026-06-30 (Claude cross-check of the pcaps)

Parsed all five pcaps directly (parsers: `/tmp/parse_k1g.py`, `/tmp/parse_vals.py`, `/tmp/pin.py`).
Wire facts: phone `192.168.1.2`, dash `192.168.1.1`, control broadcast `.255:2000`; **return
channel = dash → phone:`2002`** (filter `udp.dstport==2002 && ip.src==192.168.1.1`, field
`udp.payload`, TLV length is **2 bytes**). Joystick = `09 00`, code = value's last byte.

### ★ EXIT-NAV teardown — FULLY ISOLATED (closes the "Open Gaps" item)
From `exit_nav` (map up t78–107s; exit at ~107s). The complete app→dash close sequence the RE app
sends, which Northstar omits (hence our dash hangs):
1. **`06 80` = `12`** — mode-set to back/exit (0x12 = the BACK code). Entry uses `06 80` = `0b`.
2. **`06 05` = `aa` ×4** — projection-state heartbeat flips active→idle. (`06 05` runs ~1 Hz the whole
   session: value **`55` = actively projecting**, **`aa` = idle/transition**.)
3. **`05 17` = `aa` ×2** — session-boundary marker (also sent ×2 at *entry*; same `aa` value both ways).
4. All nav TLVs stop; stream reverts to the 1 Hz idle keepalive group only.

Entry sequence (for symmetry): `05 17`=`aa`×2 → `06 0c`=`30` → `06 05`=`aa`(→`55`) → `06 0a`=`0000`
(video start) → `06 80`=`0b`. **App fix:** add this exit to `DashCommands.kt` + call on teardown.

### Confirms your "Findings Used In Code"
- MEDIA: `0x18` enter, `0x06/0x07` volume, `0x09/0x0A` track next/prev, `0x05` play/pause — matches
  (`media` seq `18×5 06 06 07 07 0a 06 07 09 0a 05 05`).
- `09 04`/`09 06` (value `aa`) = per-frame decode-acks, not buttons.

### ⚠ Two conflicts to settle with one labelled press each
1. **Zoom direction.** Your note: `0x13`=in, `0x14`=out. Northstar's current code + the old decompile
   memory say the **opposite** (`0x14`=zoomIn, `0x13`=zoomOut). My `zoom` capture (`0b 13 13 14 14`)
   confirms 13 & 14 are the zoom pair but can't prove direction. **Do one labelled "zoom IN ×2" press
   to settle** before trusting it in code.
2. **Album-art `05 40` chunking.** Your full-snaplen note (chunks ~`1001`/`300`, flag `0x01` non-final
   / `0xFF` final) **contradicts** the old snaplen-256 inference (≤190B/frag). The full capture is more
   trustworthy → Northstar's existing ~1000-byte chunk may actually be **correct**. Reconcile in
   `dash_media_tlv_protocol` memory + `DashCommands.albumArtFrames` rather than "fixing" it to 190B.

### CALL — RE has NO answer/reject; that's a Northstar-only feature
**The official RE app cannot answer / reject / end calls — it is display-only.** So a RE capture will
NEVER show "answer/reject codes": there is nothing to reverse-engineer here. Answering/ending is
Northstar's own capability (`TelecomManager.acceptRingingCall` / `endCall` behind `ANSWER_PHONE_CALLS`).
What the `call` capture DOES show is only what the dash forwards while a call card is up: `09 00` codes
`01/03/04` + a new `09 01` state sub-channel (`aaaa/aa55/0055` = dash ring/state). **We pick which
forwarded code maps to answer vs reject ourselves and verify it live during a real Northstar-handled
call** — do NOT wait for a "labelled RE answer/reject capture"; it can't exist. (No labelled capture
target for call control.)
