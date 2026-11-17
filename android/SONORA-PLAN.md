# SONORA v3.0 — Native Rebuild Plan (Nov 17, 2026)

## What exists today (inspected)
- Android: ONE 289-line `MainActivity.java` — a WebView shell. It loads the web UI,
  bridges MediaStore AUDIO only (title/artist/duration) and streams songs to the
  WebView via `https://songs.local/<id>`. No video, no native UI, no database,
  no playback service, no vault.
- Web app (`~/workspace/sonora/`, GitHub repo `sonora`): the current player UI.
  It stays live and untouched.
- Conclusion: a PLAYit-class player cannot be built inside a WebView shell.
  SONORA Android becomes a **native Java app**, same package
  `com.nawazishali.sonora`, same signing keystore (installs over the old app).

## Engineering decisions (with reasons)
1. **Playback: framework MediaPlayer** (not Media3/ExoPlayer). This project's
   build pipeline is manual (javac/aapt2/d8 — Gradle cannot run in this
   environment) and Media3's dependency graph cannot be built or verified here.
   The spec allows Media3 "if compatible with the existing project" — it is not.
   MediaPlayer gives: speed 0.25–2x (PlaybackParams, API 23+), embedded
   subtitle/audio track selection (getTrackInfo/selectTrack), PiP, audiofx.
2. **Database: SQLiteOpenHelper** — the spec allows "Room or an equivalent
   local database". Tables: favorites, playlists, playlist_items, history,
   positions (resume), settings, vault_meta.
3. **Images: built-in async loader + LruCache** ("Coil or compatible").
   Video thumbs via MediaMetadataRetriever/MediaStore; album art via
   MediaMetadataRetriever embedded picture + album-art content URIs.
4. **Video→audio: extract audio track to M4A** (MediaExtractor/MediaMuxer).
   Android has no built-in MP3 encoder; the feature is labelled honestly
   "Extract Audio (M4A)".
5. **Animations: native only** (no Lottie AAR). Animated splash, custom
   equalizer-bars view, spectrum visualizer from Visualizer FFT data,
   animated favorite toggle, mini-player expand/collapse transitions.
6. **Vault biometrics:** PIN (PBKDF2 hash) is primary. Device-credential /
   fingerprint unlock via framework APIs where available; if a piece cannot
   be compile-verified it ships PIN-only and is listed as incomplete — never faked.

## Theme — Midnight Violet (exact spec palette)
Background #100719 · Card #1C1030 · Elevated #29183C · Primary #813CFF ·
Secondary #C45BFF · Accent #E84BCB · Text #FFFFFF · Secondary text #B8AEC5 ·
Border #382849. Rounded corners, gradient accents used sparingly.
Light theme: derived light variant of the same palette (Settings toggle +
system default). Tagline: "Your Complete Media Experience".

## Stages
- **S0** Skeleton: manifest (READ_MEDIA_AUDIO/VIDEO 33+, READ_EXTERNAL_STORAGE
  ≤32, partial-access handling, FOREGROUND_SERVICE + media playback type,
  RECORD_AUDIO for visualizer, PiP activity config), theme, DB, plan docs.
- **S1** Library: async MediaStore scan (audio + video), 5 tabs
  (Home/Videos/Music/Folders/Settings), Home dashboard with REAL data
  (recently played, continue watching, recently added, most played,
  favorites, storage overview), global search, sort (name/date/size/duration),
  grid/list toggle, refresh. No fake content anywhere.
- **S2** Music engine: foreground playback Service + framework MediaSession,
  notification + lock-screen controls, queue, shuffle, repeat (off/all/one),
  audio focus, headset controls via MediaSession, equalizer (5-band) +
  bass boost + presets, visualizer, sleep timer, lyrics from embedded metadata
  where present, favorites/playlists/history/resume persisted.
- **S3** Video player: full-screen activity, play/pause, seek, ±10 s,
  double-tap seek, swipe brightness (left) / volume (right), speed 0.25–2x,
  rotation, aspect-ratio modes, screen lock, embedded subtitle + audio track
  selection, external .srt via SAF picker (own SRT parser → caption overlay),
  resume position, repeat, prev/next, PiP (floating player), continue-watching.
- **S4** Folders + storage: bucket/folder browser from MediaStore (+ SAF
  folder grant), counts per folder; storage dashboard (StatFs totals,
  music/video library sizes, large files, recently added); rename,
  delete-with-confirmation (MediaStore delete request on API 30+),
  share, open-with, refresh.
- **S5** Private vault: Android Keystore AES-256-GCM per-file encryption into
  app-private storage, PIN setup/verify, import (encrypt → verify → delete
  original via MediaStore consent flow), in-vault playback (decrypt to pipe/
  temp stream — never plaintext on shared storage), restore/export after auth.
  Vault files never appear in the normal library.
- **S6** Build + verify + document: manual pipeline build, aapt2 badging +
  apksigner verify, README update, FEATURES.md (complete vs incomplete with
  reasons), TESTING-REPORT.md (what was compile/static-verified here vs the
  20-point device checklist for the user's phone — nothing claimed as
  device-tested that was not), publish source to GitHub repo `sonora`
  under `android/`, APK to Drive + local copy.

## Testing boundary (honest)
No emulator/device exists in this build environment. Verification here =
compilation, resource linking, signing, static checks. All runtime behaviour
must be confirmed on the user's phone; the report says exactly that.
