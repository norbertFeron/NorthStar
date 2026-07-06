# Northstar — Master Context (for any agent)

> **READ THIS FIRST.** This is the single orientation doc for anyone (human or agent)
> picking up work on Northstar. It records *where everything lives*, the hard
> constraints, the dash protocol, the capture/decompile assets, the build & release
> machinery, and the current state. Pair it with `CLAUDE.md` (project charter) and the
> auto-memory index at
> `~/.claude/projects/-home-aditya-Work-repos-Northstar-Android/memory/MEMORY.md`.
>
> **This file is LOCAL-ONLY.** Do not push it (see the no-push policy below). It
> references the author's private reverse-engineering assets and signing keys.

Last updated: 2026-06-29 · App version at time of writing: **1.3.2 (versionCode 17)**.

---

## 0. What Northstar is (one paragraph)

Personal Android companion app for a **Royal Enfield Himalayan 450** (now also pairs
with **Guerrilla 450** and **Bear 650**) that streams **navigation to the bike's
Tripper dash** (a small round TFT) over the dash's Wi-Fi. The whole point: Northstar
renders the map **off-screen**, hardware-encodes **H.264**, and sends it as **RTP over
UDP/5000** to the dash, so **the phone screen stays OFF** during the ride (no overheating
— the official RE app screen-mirrors and cooks the phone). In-app branding is **"Sherpa"**;
the project/package shell is **"Northstar"**. Independent project, not affiliated with RE.

---

## 1. HARD CONSTRAINTS — read before doing anything

1. **NEVER `git push` to `origin`.** The public repo `github.com/adityadasika21/NorthStar`
   is a user-base funnel; a competitor fork (`subtlesayak/open-dash`) is built on it and
   pulls the author's riders. Pushing feeds them. **Hard stop — do not push, do not offer to
   push, do not nag about unpushed commits.** Committing **locally** (on `play-release` or any
   branch) is fine and encouraged. Deliver via Firebase test-build push, direct APK, Play, or
   **binary-only GitHub release** (attach APK asset, anchor tag at existing remote HEAD — no
   source pushed).
2. **Do not commit RE's decompiled internals** or capture pcaps to the repo. They live OUTSIDE
   the tree (`~/ns_reverse`, `~/ns_captures`) — keep them there.
3. **Android only.** No iOS in *this* repo (separate repo exists — see §9).
4. **The dash-streaming core needs hardware-in-the-loop validation on the bike.** It can't be
   fully verified from code. Anything touching the protocol/encoder is "compiles, needs a ride."
5. **Tone:** the author wants to be addressed **JARVIS-style** — call him **sir**, dry/witty/
   concise, British-butler composure, but keep technical accuracy underneath. He is direct:
   *"do not assume shit — ask me what you need."* Ask upfront rather than guess.

---

## 2. Repository layout

Working dir: `/home/aditya/Work/repos/Northstar Android` · git branch `play-release` (release
line) · main branch `main`. Package `com.example.northstar` (namespace), applicationId
`com.northstar.app`. minSdk **30** (Android 11), targetSdk 36, compileSdk 36.x, AGP 9.x,
Kotlin + Jetpack Compose + Material3, Navigation Compose (string routes).

### Source tree — `app/src/main/java/com/example/northstar/`

- **`dash/`** — dash link layer
  - `DashSocket.kt` — UDP sockets (control plane broadcast + RTP). `DashAuth.kt` — RSA auth
    handshake. `DashSession.kt` — session orchestration / keep-alive loops. `DashConfig.kt` —
    per-rider SSID/password (multi-SSID, no hardcoded SSID). `DashWifiManager.kt` — Wi-Fi
    join via `WifiNetworkSpecifier` (prefix `RE_*`). `DashKeepAliveService.kt` — **foreground
    service** (type connectedDevice) holding PARTIAL_WAKE_LOCK + WifiLock so the stream survives
    Doze with the screen off (the whole point).
  - **`dash/map/`** — `TileProvider.kt` (raster tile mem+disk cache; live source = Google proxy
    `https://mt1.google.com/vt/lyrs=m&hl=en&z=%d&x=%d&y=%d`), `OfflineMaps.kt` (persistent
    route-corridor tile download into `filesDir/offline_tiles`), `MapRenderer.kt` (off-screen
    Canvas render, slippy-map best-effort scaling), `Mercator.kt`, `LocationTracker.kt`.
  - **`dash/nav/`** — `Router.kt` (OSRM demo server — flagged liability, to be replaced),
    `PolylineCodec.kt`, `Route.kt`, `GeoPoint.kt`, `VoiceManager.kt` (TTS).
  - **`dash/protocol/`** — `K1GPacket.kt` (K1G TLV framing), `DashCommands.kt` (nav/media/call TLV builders).
  - **`dash/video/`** — `DashEncoder.kt` (MediaCodec H.264 Baseline, `requestKeyFrame()`),
    `NalProcessor.kt`, `RtpPacketizer.kt` (RFC 6184 single-NAL + FU-A).
- **`data/`** — `NorthstarDb.kt` (SQLite source of truth), `SyncRepository.kt` (Firestore sync),
  `FirebaseGate.kt`/`FirebaseFeatures.kt` (BYO-Firebase, all no-op if no `google-services.json`),
  `SettingsStore.kt` (autoConnect + useMiles), `Units.kt`, `GarageModels.kt`, `RideRecorder.kt`,
  `RideDiagnostics.kt`/`DiagnosticsUploader.kt` (debug-only remote diag), `UpdateChecker.kt`
  (in-app OTA via GitHub releases), `TestBuildChecker.kt`, `AuthPrefs.kt`, `SharedLocation.kt`,
  `MaintenanceNotifier.kt`, `NorthstarMessagingService.kt` (FCM).
- **`media/`** — `MediaInfoProvider.kt` (now-playing via MediaSessionManager),
  `CallInfoProvider.kt`, `NorthstarNotificationListener.kt` (notification access + caller name),
  `MediaModels.kt`. (CallController for joystick answer/reject lives here too when present.)
- **`ui/`** — `NorthstarIcons.kt` (icon aliases + custom path vectors).
  - **`ui/theme/`** — `Color.kt`, `Theme.kt`, `Type.kt` (Geist via Downloadable Fonts).
    **Dark-only in 1.3.2 release** (theming reverted — see §8).
  - **`ui/components/`** — `NorthstarComponents.kt`, `NorthstarMap.kt` (in-app MapLibre map),
    `CircularDash.kt`, `Joystick.kt`, `BarChart.kt`, `UpdatePrompt.kt`.
  - **`ui/navigation/AppNavigation.kt`** — NavHost + bottom nav + update prompt root.
  - **`ui/screens/`** — Login, Home, Route, Dash, Garage, Rides, Settings.
- **`viewmodel/`** — `DashViewModel.kt` (the big one: frame loop, motion-adaptive fps,
  dead-reckoning predictor, joystick routing, media forwarding), `RouteViewModel.kt`,
  `GarageViewModel.kt`, `RidesViewModel.kt`, `AuthViewModel.kt`, `AppViewModel.kt`.
- **`util/`** — `CrashGuard.kt`, `Dbg.kt`, `Telemetry.kt`, `DeviceId.kt`, `BuildId.kt`,
  `DeviceReadiness.kt`, `ExitInfoCollector.kt`, `LocationParser.kt`.

### Docs & tooling in-repo

- `CLAUDE.md` — project charter (authoritative on goals/phasing/non-goals).
- `docs/HLD-LLD.md` — architecture. `docs/design/` — UI specs (incl. `THEMES.md`).
- `docs/ROADMAP.md` — prioritized plan (1.3.3: joystick controls §0 top priority).
- `docs/NAVIGATION-PLAN.md` — the navigation moat plan (§7 here).
- `docs/RELEASE-1.3.2.md`, `docs/RELEASE-internal-testing.md`, `docs/PLAY-CONSOLE.md`,
  `docs/PLAY-LISTING.md`, `docs/playstore/`.
- `version 1.3/` — changelogs (`CHANGELOG-1.3.2.md` = current release notes), reddit post, old APKs.
- `tools/firebase/` — remote build/diag loop (see §6). `tools/dash-emulator/` — pure-Python
  Tripper dash emulator for desk testing (no bike needed).

---

## 3. The dash protocol (hardware-verified, firmware 11.63)

Target dash firmware = **11.63**. Reference implementation = **better-dash**
(github.com/norbertFeron/better-dash, Apache-2.0). "K1G" is a dash **hardware-model code**
(part RAC00398→K1G), not packet magic — but the wire header does carry the literal `"K1G "`.

### Network layout
- Dash AP: SSID `RE_xxxx_yymmdd` (author's was `RE_P0RP_260525`), factory password **`12345678`**
  (universal). Dash IP **192.168.1.1** (BSSID `a8:40:0b:1f:41:55`), first client **192.168.1.2**.
  Dash is **2.4 GHz, single-client** (a second client kicks the first), auto-selects channel per
  power-cycle, sleeps when no client connected.
- **Video:** RTP/UDP to **192.168.1.1:5000** (payload type 96).
- **Control plane is UDP BROADCAST**, not unicast: TX binds `:2000`, sets `SO_BROADCAST`, sends
  to **192.168.1.255:2000**. **RX on `:2002` must be bound BEFORE the first TX** (else ICMP
  port-unreachable wedges the dash state machine).

### Packet framing
- **Incoming (dash→app):** 8-byte header — `[0:2] outer_len [2:4] seg_count [4:8] ignored [8:]
  TLVs` (TLV: type@8 sub@9 len@10 val@12).
- **Outgoing (app→dash):** full K1G header — `[len:2][0002][seq:4][0201][00]["K1G "@12-15][00]
  [TLV type@17 sub@18 len@19-20 value…]`. A **rolling seq byte** (after `"K1G "`) is patched at
  send time, shared counter across all control packets.

### Auth (stateful)
Dash sends RSA **modulus (07 00)** and **exponent (07 03)**, possibly in separate packets —
accumulate, then send `q3c.d` once. Reject = `07 01` != 01 (retry up to 5×); confirm = `07 01 01`.

### Nav-entry order (matters)
route-card `0x007E` ×4 → projectionFrame → z2 (once) → route-card. **Route card must precede z2**
or the dash never allocates its decoder surface. Then keep-alive: projectionFrame at ~4 Hz +
route-card at ~1 Hz, or the destination watchdog tears the decoder down after ~15-20s.

### Video profile (matches RE app, validated on the wire)
- H.264 **Baseline**, **IDR every ~1.0s**, **SPS/PPS prepended to every keyframe** (dash resyncs
  on any keyframe). Keyframes **front-loaded** at stream start (several IDRs ~0.25s apart in the
  first second so the dash locks on fast).
- RE uses two profiles: High = **4 fps / 200 kbps**, Low = **2 fps / 100 kbps**; 30-frame
  drop-oldest queue. Northstar runs **motion-adaptive** (250ms/200kbps moving, 500ms/100kbps
  static, MOTION_HOLD_MS debounce) — gated on **motion**, NOT battery (deliberate, fits screen-off).
- **Dash decoder is the smoothness ceiling (~8-12 fps; blinks above).** Smoothness comes from the
  **dead-reckoning predictor** interpolating between GPS fixes, not high stream fps.
- **Video is intermittent in RE** — it stops streaming when the dash shows its own native
  music/call/menu screens. Northstar streams continuously (a future power win to copy RE's
  off-map stop).

### Joystick codes (verified on bike, fw 11.63) — TLV type `0x09` sub `0x00`, last byte = code
`UP=0x06` `DOWN=0x07` `LEFT=0x0A` `RIGHT=0x09` `IN/select=0x18` `BACK=0x12`. In MAP mode the dash
also forwards `0x13/0x14` (zoom out/in direct). **Mode-aware:** joystick is only forwarded after
**IN (0x18)** enters the dash's media-control mode; **NO map panning** — map joystick is zoom ±
only; in media mode up/down=volume, left/right=prev/next track. Unmapped seen: `01 05 0b 15`.
**Exit→menu gap:** RE on exit tells the dash to close the map and return to its Favorites menu;
Northstar doesn't yet → map hangs / dash non-interactable after our session. (1.3.3 top fix.)

### Media / call TLVs (cracked from capture)
- **`05 0d` now-playing:** three NUL-separated text fields ≤~19-20 chars (likely
  `album\0title\0artist`), no state/position byte; followed by `05 58` (0x55) OK-ack.
- **`05 22` call:** `name\0`, re-sent ~1 Hz, display-only.
- **`05 40` album art:** each TLV value = `[flag:1][b1:1][b2:1]` + ≤190 bytes JPEG (≤193 total).
  flag `00`=first `01`=mid `ff`=last; reassemble by concatenating bytes after the 3-byte prefix
  across a `00 (01*) ff` run. **Residual unknown:** the 2-byte `b1b2` is content-dependent /
  per-fragment (rolling CRC or transfer id — ruled out: checksum/index/length). Resolve on
  hardware. Northstar renders its own art so this is low priority.
- **`05 01`** turn-by-turn instruction text. **`06 08`** projection keepalive. **`06 0b`** device name.
- **`0x0C` telemetry (dash→app):** ~27 subfields (sub 0x01-0x26) but **all zero even with ignition
  on** — the Tripper does NOT forward instrument data to a projection client. Telemetry-into-app is
  **not feasible** over this link (would need OBD/CAN). Don't chase it.

### Dash-side reliability
The dash Wi-Fi AP **hangs intermittently mid-ride** (dash-side bug, needs power-cycle). Northstar
has reconnect auto-recovery + a rider alert. Android-13 first-pairing auth fails if **Location
services master toggle is OFF** — app prompts "Turn on Location".

---

## 4. Reverse-engineering assets (LOCAL ONLY — never commit)

All outside the repo, in the author's home dir. The Nothing Phone 3 (model **A024**, Android 16)
is **rooted (Magisk)** as of 2026-06-20, so capture is **on-device tcpdump over USB adb** (no
monitor mode needed). `/system/bin/tcpdump` (4.99.4) is built into Nothing OS.

### Captures — `~/ns_captures/`
- `re_session_20260620-101351.pcap` — 239s real official-app dash session (fw 11.63); the
  primary source for everything in §3. (snaplen 256 → album-art JPEG body truncated; text TLVs intact.)
- Capture scripts in `~`:
  - `ns_capture_phone.sh [name]` — start/stop on-device tcpdump + auto-pull to `~/ns_captures`.
  - `ns_cap_start.sh [name]` / `ns_cap_stop.sh` — **untethered** LMK-proof ride capture (start at
    home over USB, "SAFE TO UNPLUG", ride, stop at home). Filter `net 192.168.1.0/24`.
  - `ns_analyze_capture.sh <pcap>` — fps/bitrate/IDR/static-suppression + joystick + return channel.
  - `ns_capture_dash.sh` (monitor-mode, legacy/dead-end on this laptop), `ns_capture_ctrl.sh`.
  - **SELinux gotchas** (scripts encode them): tcpdump must be exec'd directly by Magisk-su
    (`nohup`/`setsid` → permission denied; use `trap '' HUP` then background); run pushed scripts
    as `su -c "sh /path"`; stop with explicit `kill -INT <pid>` (pid in
    `/data/local/tmp/ns_tcpdump.pid`). While on dash Wi-Fi the phone leaves the LAN → wireless adb
    dies → **drive capture over USB cable**.
  - **CAPTURE_PLAN** (one-process-per-pcap labeled sessions: enter_nav, exit_nav, zoom, media,
    nowplaying, albumart, joystick_full, fav_write) at `~/ns_reverse/CAPTURE_PLAN.md`.

### Decompiled official RE app — `~/ns_reverse/`
- Package **`com.royalenfield.reprime`**. APKs at `~/ns_reverse/reprime_apk/` (base.apk, 13-dex
  multidex + `split_config.arm64_v8a.apk`). Sources at `~/ns_reverse/reprime_src/` (jadx 1.5.1).
- Writeups (LOCAL): **`~/ns_reverse/FINDINGS.md`** (static analysis), **`~/ns_reverse/MEDIA_PROTOCOL.md`**
  (media/call TLV teardown). Parsers in `/tmp/k1g_*.py`, `/tmp/art_*.py`, `~/ns_reverse/decode_k1g.py`.
- Key findings: dash projection code is **JVM** (obfuscated `bluconnect` pkg +
  `…ui.home.navigation`), NOT in native `libBluConnect-Lib.so`. Encoder = `bluconnect.n2g`
  ("MediaEncoder") → MediaCodec input Surface → `j6g` sends RTP to 192.168.1.1:5000. Profiles
  `q2g(framerate, iFrameInterval, bitrate, frameToSend)`. Native libs present: `libBluConnect-Lib.so`,
  `libgmm-jni.so` (Google Maps Mobile renderer), libsqlcipher, librdpdf, libbarhopper.

### Desk testing without the bike
`tools/dash-emulator/` — pure-Python Tripper dash emulator (separate path from the build loop).

---

## 5. Build & signing

### JDK gotcha (critical)
This machine only has a JRE system-wide; Gradle's toolchain auto-detect picks a JRE without
`jlink` → AGP `JdkImageTransform` fails. **Always point Gradle at the Adoptium JDK 21:**
```bash
cd "/home/aditya/Work/repos/Northstar Android"
JDK=/home/aditya/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH" ./gradlew --no-daemon \
  -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.home="$JDK" \
  :app:assembleRelease
```
Use `--no-daemon`. No device/emulator attached here → only builds/lint/unit tests run locally.
Build tools (apksigner/aapt): `~/Android/Sdk/build-tools/37.0.0`. adb: `~/Android/Sdk/platform-tools/adb`.

### Two signing keys (both gitignored, both in `~/keystores/`)
1. **Sideload key** — `northstar-release.jks`, alias `androiddebugkey`, creds in `signing.properties`
   (repo root). Cert is **byte-identical to `~/.android/debug.keystore`**, signer SHA-256
   `ff42ba2e6d64e0c36e819aed298025002321b5dcd8aca75c7aee2f0a1dbdb00d` (SHA-1
   `80:30:BD:71:02:F2:9C:52:4D:C2:5E:0C:87:54:84:0D:2A:C9:2D:C8`). Used for the **GitHub/Obtainium/
   sideload channel** — every public APK shares one signature so updates install in place. **Play
   rejects this** (debug-signed). **GUARD THIS KEY** — losing it forces all users to reinstall.
2. **Play upload key** — `northstar-upload.jks`, alias `northstar-upload`, creds in
   `signing-upload.properties` (strong random pw), cert SHA-256 `37:38:49:D5…`, DN `CN=NorthStar,
   O=NorthStar, C=IN`. For Play App Signing.

`app/build.gradle.kts` selects: pass **`-PplayUpload`** → upload key (`:app:bundleRelease` for the
AAB); without the flag → sideload key (default). The two channels have different signatures → users
can't cross-grade in place (they pick a channel). Release build is R8-minified, arm64-v8a only,
`DIAG_UPLOAD=false`. Full runbook: `docs/RELEASE-internal-testing.md`.

**OAuth note:** Google Sign-In requires the `(com.northstar.app + signing SHA-1)` pair registered as
an Android OAuth 2.0 client in GCP/Firebase console (SHA-1 `80:30:BD:71…`). `UNREGISTERED_ON_API_CONSOLE`
= that registration missing.

### Release process (binary-only GitHub release — preserves no-push)
```bash
gh release create vX.Y.Z --title "Northstar vX.Y.Z" \
  --notes-file "version 1.3/CHANGELOG-X.Y.Z.md" \
  --target main \
  /path/to/northstar-vX.Y.Z.apk
```
`--target main` anchors the tag at the **existing remote HEAD** → **no source pushed**, only the APK
asset ships. (gh network calls may need per-command sandbox approval.) Auto-update for users =
**Obtainium** (repo URL) + the in-app `UpdateChecker` (compares dotted version unless the changelog
contains a `sha256:` line → then checksum compare). Bump `versionCode` every release.

---

## 6. Remote test/diagnostics loop (no adb needed)

Firebase project **`northstar-c2625`** (note: diag/sync project also referenced as the app's
Firebase). Service account at `tools/firebase/serviceAccount.json` (gitignored).

- **Push a test build:** `cd tools/firebase && node push-build.mjs <apk-path> "notes"` → uploads to
  Storage `builds/northstar-test.apk` (fixed permalink) + writes Firestore `meta/test_build`
  (buildId + sha256). Phone installs via in-app "New test build" prompt. **Test builds are DEBUG**
  (`DIAG_UPLOAD=true` → ride logs upload; same debug key so they install over each other).
- **Pull ride logs:** `node pull-diag.mjs [--since 1d]` → writes `tools/firebase/diag/<device>__ride-*.log`
  and **auto-labels each session CURRENT vs OLD BUILD** by matching the app-logged APK sha
  (`[build] apk=<sha12>`) against `meta/test_build.sha256`. **Always check that label first.**
- Firestore rules in `tools/firebase/firestore.rules`, deployed via `node deploy-rules.mjs`:
  `users/{uid}` owner-only, `diagnostics` create-only, `meta` read-only. App upload + in-app prompt
  break without these.
- Diagnostics upload is **debug-only** (`BuildConfig.DIAG_UPLOAD`, false in release), fires on
  app-open + on disconnect. 0 km blips upload as diagnostics but don't save as rides.
- **Default delivery = remote Firebase push** (not adb). Push every new test build so the user
  installs from the app. If using adb: **always** `adb install -r --user 0` (personal profile;
  the app otherwise lands in Private space / user 10 and looks like a "work profile"). Wireless adb
  device A024 may show **two transports** — target by `-t <transport_id>` (changes per reconnect),
  not `-s <serial>` (ambiguous).

---

## 7. Navigation plan (the moat — 1.3.3+, branch `feature/navigation`)

Goal: **Google-level clarity on Indian roads** (flyovers, U-turns, service roads) on the round dash
+ an offline/adventure edge Google lacks. Full plan: `docs/NAVIGATION-PLAN.md`. Locked decisions:
- **Buy the routing brain, own rendering + offline + feedback loop.**
- **Online brain = Mapbox** (Directions + traffic, drawn in our own dash style — forced by ToS:
  Google's terms forbid drawing Google routing data on a non-Google map). **Bring-your-own Mapbox key.**
- **Offline brain = Valhalla** (self-host VPS + on-device tiles): fun/curvy costing + offline + Compass.
- **Kill the OSRM demo server** (current liability in `dash/nav/Router.kt`).
- **Flyover detection is SENSOR-FREE** (no barometer — not all phones have one): HMM map-matcher over
  the GPS sequence + planned route prior + lateral-offset/heading/speed heuristics + hysteresis.
- **Custom dash cartography** (vector, labels off, fat route ribbon in bike accent, day/night) — raster
  OSM looks bad shrunk to 526×300. NOT Material 3 Expressive.
- Build on `feature/navigation` (off `play-release`); starts after 1.3.2 ships. Acceptance-gated by an
  Indian-roads test set (10 routes, ≥3 flyovers ridden on+under, ≥5 U-turn/service-road junctions).

**Strategy context:** moat = navigation robustness + remote diagnostics (the fork `open-dash` has
neither — it chased a wallpaper fad on a crash-prone 1.3 with no Firebase). Adopt open-dash's
data-entry depth (multi-vehicle, full expense categories, CSV export, editable odometer/auto-mileage,
document expiry reminders), go international, migrate fork users via an importer. Public repo stays
frozen.

---

## 8. Current state (1.3.2) & theming status

**1.3.2 (vc17) is built and ready to publish** as a **binary-only GitHub release** (sideload key,
`ff42ba2e…`, R8-minified ~22MB, arm64, `DIAG_UPLOAD=false`). Changelog =
`version 1.3/CHANGELOG-1.3.2.md`. Contents shipped:
- **Offline route maps** (`OfflineMaps.kt` corridor download into `filesDir/offline_tiles`, read
  before the evictable cache; tile source re-pointed to the same Google proxy as the live map).
- **Two ride fixes** in `DashViewModel.kt`: (1) **startup-warmup** — hold 4 fps for
  `STREAM_WARMUP_MS=6s` and pulse an IDR every 600ms (`STREAM_WARMUP_KEY_MS`) so the dash locks on at
  ~3-4s not ~8s (root cause: parked start idled to 2 fps and starved keyframes); (2) **speed-aware
  dead-reckoning** — fix-derived velocity when the GPS chip omits speed/bearing + `conf =
  max(accConf, speedConf=fixSpeed/8)` so prediction isn't scaled down by the worse accuracy normal at
  speed (root cause: 1 Hz GPS + under-projection → lag-then-snap).
- Garage odometer card, miles/km units (`SettingsStore.useMiles` + `Units.kt`), sign-in-from-guest,
  honest settings (dead toggles removed), and a **teardown crash fix**: `DashKeepAliveService.stop()`
  uses `stopService(...)` not `startService(ACTION_STOP)` (the latter crashes "Not allowed to start
  service" when backgrounded on API 26+).

**Theming: REVERTED for 1.3.2.** A full per-bike colourway + light/dark/system system was built then
removed for this release on the author's call ("drop only themes, ship offline maps"). The app is
**dark-only** in 1.3.2. The complete theming work is preserved in a local **stash** (`stash@{0}:
session-full-backup`) and documented in memory `theming_architecture.md`. The mechanism (for when it
returns in 1.3.3): `ui/theme/Color.kt` tokens become `@Composable @ReadOnlyComposable get()`
properties reading `LocalNeutrals`/`LocalAccent` CompositionLocals → ~267 call sites became
theme-aware with zero edits. **Gotcha:** a composable-getter token can only be read in @Composable
scope — inside a `Canvas{}`/`drawBehind{}` lambda you must capture it into a local first. Catalog =
3 bikes × ~5 real RE factory colourways × light/dark; defaults (himalayan450 / Adventure Gold / DARK)
preserve the old look.

**1.3.3 locked scope** (`docs/ROADMAP.md`): **joystick controls = §0 NON-NEGOTIABLE top priority**
(turn-by-turn is a co-priority, not a replacement) + per-bike themes (un-stash) + international +
rider QOL + open-dash garage data-entry parity. The exit→menu joystick gap (§3) is a top fix.

---

## 9. iOS port (separate repo — context only)

Started 2026-06-26 at **`/home/aditya/Work/repos/northstar-ios`** (NOT in this tree; local git, no
remote). Parity target = Android 1.3.2 + RE per-bike themes. SwiftUI + a Foundation core. **Needs
macOS + Xcode to build the UI** — Linux box can't compile that. The portable core (`NorthstarCore`:
`K1GPacket.swift` + `RTPPacketizer.swift`) is **verified green on Linux** (5 tests pass, Swift 6.0.3,
wire-compatible with the dash emulator) via `podman run --rm -v "$PWD":/src:Z -w /src swift:6.0 swift
test`. Two hardware gates to prove before building out: (1) no-internet Wi-Fi auto-join to `RE_*` via
NEHotspotConfiguration; (2) background H.264 encode with screen OFF (iOS suspends on lock — needs an
approved background mode). Map: `northstar-ios/docs/STATUS.md`.

---

## 10. Known reliability fixes already in code (don't re-introduce the bugs)

- **Recycled-bitmap crashes:** never `recycle()` a Bitmap shown in a Compose `Image`
  (`asImageBitmap`); clear the UI reference first and let GC reclaim it. Hit the wallpaper preview +
  dash frame (vc16 fix).
- **MapLibre page-switch crash:** native SIGSEGV from an async map-style callback firing after
  teardown — guarded with a `destroyed` flag (code-13 hotfix); plus a connect-time SIGABRT variant
  (disposing the map while connecting). Keep the guards.
- **LOW_MEMORY_KILL mid-ride:** the OS reclaimed the streaming process (→ dash "Timeout!"). Tile
  memory cache lowered to ~1/8 heap (24-72MB clamp); disk cache still holds every tile.
- **Screen-off Doze:** handled by `DashKeepAliveService` (foreground, wakelock + wifilock). Tiles
  must come via cellular while bound to the internet-less dash Wi-Fi → `TileProvider.prefetch`
  warms around GPS at stream start + periodically; `OfflineMaps` covers dead zones.

---

## 11. Quick reference — paths

| What | Where |
|---|---|
| Repo | `/home/aditya/Work/repos/Northstar Android` (branch `play-release`) |
| Adoptium JDK 21 | `/home/aditya/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2` |
| Build tools | `~/Android/Sdk/build-tools/37.0.0` · adb `~/Android/Sdk/platform-tools/adb` |
| Sideload key | `~/keystores/northstar-release.jks` (creds `signing.properties`) |
| Play upload key | `~/keystores/northstar-upload.jks` (creds `signing-upload.properties`) |
| Remote loop | `tools/firebase/{push-build,pull-diag,deploy-rules}.mjs` · SA `serviceAccount.json` |
| Dash emulator | `tools/dash-emulator/` |
| Capture pcaps | `~/ns_captures/` · scripts `~/ns_cap_*.sh`, `~/ns_capture_*.sh`, `~/ns_analyze_capture.sh` |
| RE decompile | `~/ns_reverse/` (`FINDINGS.md`, `MEDIA_PROTOCOL.md`, `CAPTURE_PLAN.md`, `reprime_apk/`, `reprime_src/`) |
| iOS port | `/home/aditya/Work/repos/northstar-ios` |
| Auto-memory | `~/.claude/projects/-home-aditya-Work-repos-Northstar-Android/memory/MEMORY.md` |

---

*Maintenance: when a fact here changes, update both this file and the relevant memory in
`…/memory/`. This file is the human/agent-readable mirror of the auto-memory — keep them in sync,
and keep this file LOCAL (never pushed to origin).*
