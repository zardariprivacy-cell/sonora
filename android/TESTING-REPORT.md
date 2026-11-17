# SONORA v3.0 — Testing Report

## Verification performed in the build environment (Nov 17, 2026)

This environment has **no Android emulator and no physical device**, so all
verification here is compile/static. Nothing below claims runtime behaviour
was observed.

| Check | Result |
|---|---|
| aapt2 resource compile (42 vector icons, 2 layouts, values) | ✅ pass |
| aapt2 link (manifest merge, R generation) | ✅ pass |
| javac — all 26 Java sources, `--release 8`, android-34 | ✅ pass (0 errors) |
| d8 dexing (min-api 24) | ✅ pass |
| zipalign + apksigner sign (debug.keystore, same key as v2.x) | ✅ pass |
| apksigner verify on the **delivery file** `~/workspace/your_files/sonora/Sonora.apk` | ✅ signature valid |
| aapt2 dump badging on the delivery file | ✅ `com.nawazishali.sonora`, versionCode 10, versionName 3.0, minSdk 24, targetSdk 34, launcher = SplashActivity |
| Permission audit on delivery file | ✅ READ_MEDIA_AUDIO, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, READ_EXTERNAL_STORAGE (maxSdk 32), WRITE_EXTERNAL_STORAGE (maxSdk 28), FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS |
| Static review pass (post-build) | ✅ fixed: foreground-service startForeground timing (Android 12+ crash), video singleTop onNewIntent, lock-mode unlock affordance, runtime POST_NOTIFICATIONS request |
| Vault crypto design check | ✅ code-level: AES-256-GCM, Keystore key, random per-file IV prefix, streamed Cipher streams, PBKDF2-HmacSHA256 (120k) salted PIN hash; `Vault.looksEncrypted()` self-check helper included. **Runtime ciphertext verification needs a device** (Android Keystore cannot execute in this VM). |

## Device checklist — PENDING on the user's phone

The user's own 20-point checklist from the spec. Each item is
**device-test-pending** until run on a real phone:

1. Fresh installation.
2. First-time permissions.
3. Permission denial.
4. Empty media library.
5. Large media libraries.
6. Hundreds of videos.
7. Audio playback.
8. Video playback.
9. Playback seeking.
10. Playback speed.
11. Subtitle functionality.
12. Background music.
13. Notification controls.
14. Picture-in-Picture.
15. Private vault.
16. SD card access.
17. App restart.
18. Playback resume.
19. Low-memory situations.
20. Different screen sizes.

Suggested order on the phone: install over the old Sonora (same signing key)
→ grant media permission → play one song (check notification + lock-screen
controls) → play one video (check gestures, PiP, resume) → set a vault PIN
and import one photo-video, confirm it disappears from the gallery flow and
plays inside the vault → toggle the light theme.

---

## v3.1 verification (Nov 17, 2026) — compile/static only, NOT device-tested

| Check | Result |
|---|---|
| aapt2 resource compile (46 vector icons incl. 4 new, 2 layouts, sheet anims, values) | ✅ pass |
| aapt2 link with ads-SDK manifest merge + --extra-packages (GMS ads 23.6.0) | ✅ pass |
| javac — all 38 Java sources (incl. Ads, MediaSheet, PressFx, History, Help), --release 8, android-34 + ads classpath | ✅ pass (0 errors) |
| d8 multidex (min-api 24) | ✅ pass (informational warnings only, same class as Ilmora's ads build) |
| zipalign + apksigner sign (debug.keystore, same key as v2.x/v3.0) | ✅ pass |
| apksigner verify on the **delivery file** | ✅ signature valid (CN=Android Debug) |
| aapt2 dump badging on the delivery file | ✅ com.nawazishali.sonora, versionCode 11, versionName 3.1, minSdk 24, targetSdk 34 |
| AdMob checks on delivery file | ✅ APPLICATION_ID meta-data = ca-app-pub-5229747293404900~3066756153 present in binary manifest; INTERNET + ACCESS_NETWORK_STATE among 22 permissions |
| Drive replacement | ✅ same file ID 17DexUZs_8GX_ODWNoGMEpmvU7QuFmMBt; Drive size 5,493,834 bytes == local delivery file |
| Static review of the timeline fix | ✅ seek scale is real ms in both players; duration chain player → retriever → MediaStore; max adopts player duration dynamically; position clamped |

**Still NOT device-tested** (no emulator/device in this environment). The
user's 20-point checklist from v3.0 still applies, plus for v3.1 on the
phone: (a) play a long video and confirm the seek bar now tracks to the
real end and drag-seeking lands at the dragged position; (b) long-press /
⋮ a few items in each tab and confirm the sheet opens, drags down to
dismiss, and each row works (PiP row especially); (c) confirm the banner
area shows nothing yet (account unverified) and the app behaves exactly as
before; (d) open several videos in a row and confirm no interstitial
appears more than once per 3 minutes (once ads serve).

---

## v3.3 (versionCode 13) — verification status

**Compile/static-verified only — NOT device-tested.** The build is green
via `build-v33.sh` and the DELIVERY file
(`~/workspace/your_files/sonora/Sonora.apk`) itself was verified:

- `aapt2 dump badging`: versionCode 13 / versionName 3.3.
- `aapt2 dump xmltree` on the binary manifest: ZERO literal
  `${applicationId}` occurrences; provider authorities are
  `com.nawazishali.sonora.mobileadsinitprovider` and
  `com.nawazishali.sonora.fileprovider`; AdMob APPLICATION_ID meta-data
  present and unchanged (ca-app-pub-5229747293404900~3066756153).
- `apksigner verify --print-certs`: SHA-256
  066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050 —
  unchanged, so v3.3 installs over v3.2.
- Size 5,506,342 bytes; MD5 16717bb9ee36e7fccac09573df215820.

Device checks still owed (first install on the user's Infinix Hot 40):
(a) the ⋮ / long-press popup opens CENTERED with the pop-in animation on
every list (Music, Videos, Home, Folders, playlists, search) and every
action in it works; (b) Edit Profile shows the dark 3D card with the
gradient Save button (not the old grey box) and name/photo save correctly;
(c) no song row anywhere shows a blank artwork square — placeholders
appear for songs without album art, and the same song always gets the
same placeholder; (d) the playing song's row shows the mini EQ bars,
animating while playing and frozen when paused, moving to the next song
on track change; (e) the SONORA header is gradient + centered on all
tabs; (f) the Vault PIN screen shows the full description text with no
"…"; (g) the Music tab no longer shows the chunky fast-scroll pill on
the right edge; (h) ads still fail silently (account unverified) and the
banner area collapses exactly as in v3.2.

## v3.4 (versionCode 14) — verification status

Compile/static-verified only — NOT device-tested (no emulator/KVM in the
build environment; the user's install on his Infinix Hot 40 is the device
test, as with every Sonora build).

- Build: `build-v34.sh` (derived from build-v33.sh; same manual pipeline,
  same `${applicationId}` sed-substitution + assert, same AdMob units),
  versionCode 14 / versionName 3.4. Build green.
- Delivery file verified at `~/workspace/your_files/sonora/Sonora.apk`
  (the exact file that ships, not just the build output): 5,510,438 bytes,
  MD5 `1157f5dd7031c04af8a2c3673f1475a5`; aapt2 badging
  `com.nawazishali.sonora` vc14 / 3.4; `aapt2 dump xmltree` on the merged
  manifest: zero literal `${applicationId}`; provider authorities
  `com.nawazishali.sonora.mobileadsinitprovider`,
  `com.nawazishali.sonora.androidx-startup`,
  `com.nawazishali.sonora.fileprovider`; apksigner cert SHA-256
  `066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050`
  (unchanged — installs over v3.3).
- Crash root cause established by code inspection: `AnimatedBarsView`
  lacked the `(Context, AttributeSet)` constructor LayoutInflater requires
  for the `<…AnimatedBarsView>` element v3.3 added to
  `item_media_list.xml` → `InflateException` (NoSuchMethodException) on the
  first row bind of the Music tab. Fixed at the root, plus defensive guards
  around all row decoration and a bitmask fix in `Ui.gradientFor`.
- Device checklist for the user's v3.4 install: (a) Music tab opens and
  lists songs with NO crash; (b) Search results, folder contents and the
  Videos tab list mode also open fine (same layout); (c) the playing
  song's row shows the animated EQ bars; (d) in the video player, Speed /
  Aspect / Audio / Subtitles / Repeat / Rotate each open the centered 3D
  box with the current choice highlighted, and each choice still does
  exactly what it did before; (e) Settings → About shows "Version 3.4";
  (f) vault rows open the vault menu on long-press (no ⋮ sheet);
  (g) Edit Profile → Choose photo no longer leaves the dialog floating;
  (h) Videos grid/list toggle icon matches the mode; (i) ads still fail
  silently (account unverified) exactly as before.

## v3.5 (versionCode 15) — verification status

Compile/static-verified only — NOT device-tested (no emulator/KVM in the
build environment; the user's install on his Infinix Hot 40 is the device
test, as with every Sonora build).

- Build: `build-v35.sh` (derived from build-v34.sh; same manual pipeline,
  same `${applicationId}` sed-substitution + assert, same AdMob units),
  versionCode 15 / versionName 3.5. Build green.
- Delivery file verified at `~/workspace/your_files/sonora/Sonora.apk`
  (the exact file that ships, not just the build output): 5,510,438 bytes,
  MD5 `4de55098bb168f7c2cc8af5bf09c4a78`; aapt2 badging
  `com.nawazishali.sonora` vc15 / 3.5; `aapt2 dump xmltree` on the merged
  manifest: zero literal `${applicationId}`; provider authorities
  `com.nawazishali.sonora.mobileadsinitprovider`,
  `com.nawazishali.sonora.androidx-startup`,
  `com.nawazishali.sonora.fileprovider`; apksigner cert SHA-256
  `066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050`
  (unchanged — installs over v3.4).
- What changed is presentation only, in `VideoPlayerActivity` (bottom
  options bar → six tinted 3D boxes; transport/top controls → circular 3D
  chips; play disc elevation) plus two new drawable helpers in `Ui`
  (`card3d(radius, int[])`, `circle3d(int[])`). No playback, gesture,
  seekbar or menu-action code was modified; the six box click handlers
  call the same v3.4 menu methods.
- Device checklist for the user's v3.5 install: (a) in the video player,
  the bottom bar shows six colourful 3D boxes (portrait: two rows of
  three; landscape: one row of six) — no bare text row; (b) every box
  opens its centered 3D menu and each choice works as before; (c) the
  Speed box label shows the current speed and updates after a change;
  (d) Repeat ON fills its box with the violet gradient, OFF returns to
  the tinted box; (e) rotating the phone re-flows the bar between the
  two-row and one-row layouts without restarting the video; (f) −10s/+10s,
  prev/next, PiP and Lock appear as round 3D chips and still work;
  (g) the bar still auto-hides after ~3.5 s and never covers the seekbar;
  (h) double-tap seek and brightness/volume swipes unchanged.

## v3.6 (versionCode 16) — verification status

Compile/static-verified only — NOT device-tested (no emulator/KVM in the
build environment; the user's install on his Infinix Hot 40 is the device
test, as with every Sonora build).

- Build: `build-v36.sh` (derived from build-v35.sh; same manual pipeline,
  same `${applicationId}` sed-substitution + assert, same AdMob units),
  versionCode 16 / versionName 3.6. Build green.
- Delivery file verified at `~/workspace/your_files/sonora/Sonora.apk`
  (the exact file that ships, not just the build output): 5,514,534 bytes,
  MD5 `48943a8fb850f8eac5de8c102eaa41fa`; aapt2 badging
  `com.nawazishali.sonora` vc16 / 3.6; `aapt2 dump xmltree` on the merged
  manifest: zero literal `${applicationId}`; provider authorities
  `com.nawazishali.sonora.mobileadsinitprovider`,
  `com.nawazishali.sonora.androidx-startup`,
  `com.nawazishali.sonora.fileprovider`; apksigner cert SHA-256
  `066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050`
  (unchanged — installs over v3.5).
- What changed: (1) `VaultActivity` presentation only — gate card +
  tinted PIN box + gradient buttons, header card with counts, big
  gradient Import box, vault-specific colourful rows, dashed empty-state
  card, Change PIN field styling. `Vault.java` untouched; every PIN /
  import / export / playback handler is byte-identical (verified by
  reading the diff). (2) `Ui` — `card3d(radius, int[])` and
  `circle3d(int[])` fills are now the pair gradient at full saturation
  (rim = pair darkened to 62%, top sheen white alpha 64 → 0);
  `card3d(radius)` decoupled with output identical to v3.5, so all
  app-wide cards are unchanged. (3) `VideoPlayerActivity` — layout-based
  aspect sizing (see FEATURES.md for the per-mode math), a new Stretch
  mode, an `OnVideoSizeChanged` listener, rotation re-fit, the touch
  listener moved to the root with texture-space translation, and the
  Repeat ON label treatment. (4) `SettingsTab` — About version string
  3.4 → 3.6.
- Device checklist for the user's v3.6 install:
  (a) **Player contrast first:** open any video — the six bottom boxes
  and the round chips (prev/next, −10s/+10s, PiP, Lock) must be vivid
  saturated colours with clearly readable white icons/labels, in BOTH
  the dark and the light app theme (v3.5 washed out under light).
  (b) **Fit:** a landscape video in portrait fills the width at its own
  ratio (black bars top/bottom, no squashing, no small band); rotate
  the phone and it re-fits to fill the landscape screen at the same
  ratio; a portrait video likewise stays portrait-shaped and large.
  (c) **Aspect menu:** Fit / Fill (crop) / Stretch / 16:9 / 4:3 /
  Original each resize immediately without restarting the video; Fill
  covers the whole screen with the ratio intact; Stretch fills exactly.
  (d) **Gestures:** double-tap left/right thirds of the VIDEO area seek
  ∓10s, centre toggles play; swipes on the left/right halves of the
  video adjust brightness/volume; single taps on the black bars still
  show/hide the controls.
  (e) **Repeat:** ON shows the bright gradient box with a bold
  "Repeat ✓" label; OFF returns to the violet→pink box.
  (f) **Vault gate:** centered 3D card, gradient shield badge, PIN
  field(s) in the tinted inner box; wrong PIN still toasts "Wrong PIN";
  device-lock unlock still works; create-PIN flow unchanged.
  (g) **Vault contents:** header card shows the real file count and
  total encrypted size; Import is the big gradient box; Change PIN
  opens the (already 3D) dialog with the new field style and works;
  every row is a colourful card whose colour matches its type; tap
  plays, long-press opens Play / Export / Delete; empty vault shows
  the dashed card.
  (h) Regression sweep: Music tab opens (v3.4 crash fix), Home/Settings/
  Folders cards look exactly as in v3.5 (the theme card was decoupled,
  not recoloured), seekbar/timeline still track correctly.
