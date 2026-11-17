# SONORA Android — v3.0 (Native)

**Sonora — Your Complete Media Experience.** A premium, fully offline video +
music player for Android, rebuilt natively (Java, Android framework only — no
AndroidX, no Gradle dependency graph) in the Midnight Violet identity.

The web app lives at the root of the GitHub repo `sonora` and is untouched;
this native app lives under `android/` in the same repo and in
`~/workspace/apps/sonora-android/` locally.

## What it does

- **Home** dashboard built only from real device data: recently played,
  Continue Watching (with resume points), recently added, most played,
  favorites, quick access, storage overview.
- **Videos**: MediaStore discovery, real thumbnails, All/Recent/Favorites,
  sort (name/date/size/duration), grid/list toggle, refresh.
- **Video player**: play/pause, seek, ±10 s buttons + double-tap seek, swipe
  brightness (left half) / volume (right half), speed 0.25x–2x, aspect ratio
  (fit/fill/16:9/4:3/original), screen lock, embedded audio & subtitle track
  selection, external `.srt` via file picker (built-in SRT parser), resume,
  repeat-one, prev/next, Picture-in-Picture.
- **Music**: foreground playback service with media session, notification and
  lock-screen controls, queue, shuffle, repeat (off/all/one), audio focus,
  headset-unplug pause, 5-band equalizer + bass boost + presets, spectrum
  visualizer, sleep timer, synced `.lrc` lyrics, playlists, favorites,
  history, resume.
- **Folders**: MediaStore folder browser with counts/sizes, plus SAF folder
  grants (SD card / custom locations).
- **Storage** dashboard: device totals, library sizes, largest files,
  recently added; rename / delete (with confirmation + system consent on
  Android 11+) / share / open-with; video → audio extraction (M4A).
- **Private Vault**: PIN (PBKDF2-HmacSHA256) or device-credential unlock;
  AES-256-GCM encryption (Android Keystore) into app-private storage;
  import → verify → delete original; playback decrypts only into the private
  cache; export back to the library after authentication.

## Build (manual pipeline — Gradle is not used)

Requirements: JDK 17, Android SDK platform 34 + build-tools 34.0.0.

```bash
export PATH=~/workspace/tools/jdk/bin:$PATH
bash build-v3.sh
# output: build-v3/Sonora.apk  (signed with ~/workspace/apps/debug.keystore,
# alias androiddebugkey — same key as previous Sonora releases, installs over them)
```

Verify the delivery file:

```bash
aapt2 dump badging build-v3/Sonora.apk
apksigner verify build-v3/Sonora.apk
```

## Identity

- Package: `com.nawazishali.sonora`
- Version: 3.0 (versionCode 10), min SDK 24, target SDK 34
- Theme: Midnight Violet — bg `#100719`, card `#1C1030`, elevated `#29183C`,
  primary `#813CFF`, secondary `#C45BFF`, accent `#E84BCB`, text `#FFFFFF`,
  secondary text `#B8AEC5`, border `#382849`; plus a derived light variant
  (Settings toggle).

See `FEATURES.md` for the honest completed/incomplete feature list and
`TESTING-REPORT.md` for what was verified here vs. the on-device checklist.
