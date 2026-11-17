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
