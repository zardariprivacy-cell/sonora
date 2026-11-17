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
