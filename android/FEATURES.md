# SONORA v3.0 — Feature List (honest)

Legend: ✅ implemented in code and compile-verified · ⚠️ implemented with a
documented limitation · ❌ not implemented (reason given).
Nothing here is claimed as device-tested — see TESTING-REPORT.md.

## Identity & theme
- ✅ Original Sonora branding, tagline "Your Complete Media Experience" (no PLAYit assets)
- ✅ Midnight Violet palette, exact spec hexes, rounded cards, gradient accents (sparing)
- ✅ Light theme variant (Settings toggle, persisted; derived from the same palette)
- ✅ Animated splash (animated equalizer bars), animated favorite toggle, play/pause
  transition animation, mini-player, bottom-nav transitions
- ⚠️ Animations are native (View animations + custom views). Lottie/Rive were
  evaluated and rejected by the binding plan: their AARs cannot be built with
  this project's manual javac/aapt2 pipeline.

## Navigation & home
- ✅ 5 bottom tabs — Home / Videos / Music / Folders / Settings — all functional
- ✅ Global search (title/artist/album/folder, audio + video)
- ✅ Home dashboard: welcome header, search, recently played, Continue Watching
  (real saved positions), recently added videos/music, most played, favorites,
  quick access, storage overview — all real device data, no sample content

## Video library & player
- ✅ MediaStore discovery; All / Recent / Favorites; sort name/date/size/duration;
  grid & list layouts; real thumbnails; duration/size/date shown; refresh
- ✅ Full-screen player: play/pause, seek bar, ±10 s buttons, double-tap seek
  (left/right thirds), swipe brightness (left half) / volume (right half) with
  on-screen indicator, speed 0.25x–2x, rotation toggle, aspect modes
  (fit/fill/16:9/4:3/original), screen lock with standalone unlock button
- ✅ Embedded audio-track and subtitle-track selection (MediaPlayer TrackInfo)
- ✅ External .srt via SAF picker, built-in SRT parser, caption overlay synced
  to playback position
- ✅ Resume playback, repeat-one, prev/next, auto-advance, playback history,
  Continue Watching writes, Picture-in-Picture (button + onUserLeaveHint)
- ⚠️ Formats: whatever the device's MediaPlayer supports (MP4/3GP/WebM broadly;
  MKV/AVI/MOV depend on the device's codecs — Android framework limitation,
  not an app limitation). No software decoder is bundled (Media3/FFmpeg are
  incompatible with the manual build pipeline).
- ⚠️ Embedded subtitles render via MediaPlayer timed-text callbacks; support
  varies by device/file. External .srt always works through the built-in parser.

## Music player
- ✅ MediaStore scanning; All Songs / Artists / Albums / Playlists / Favorites;
  queue; shuffle; repeat off/all/one; background playback via foreground
  service; notification + lock-screen controls (framework MediaSession);
  headset controls via media session; headphone-unplug pause; audio focus
  (loss/duck/transient handling); album artwork; metadata; favorites;
  playlists (create/rename/delete/add/remove); history; resume
- ✅ Real 5-band Equalizer + BassBoost on the player's audio session, platform
  presets + custom bands, persisted across launches
- ✅ Spectrum visualizer (Visualizer FFT) with graceful fallback animation when
  RECORD_AUDIO is denied
- ✅ Sleep timer: 15/30/45/60 min + custom
- ⚠️ Lyrics: synced `.lrc` sidecar files (same name next to the song) are
  displayed with line highlighting/auto-scroll. Android's framework metadata
  exposes no standard embedded-lyrics field, so embedded lyrics inside tags
  cannot be read reliably — documented rather than faked.

## Advanced
- ✅ Floating video player = Picture-in-Picture (above)
- ✅ Background audio playback (service)
- ✅ Subtitle manager (embedded selection + external SRT load/off)
- ✅ Audio track selection (video player)
- ✅ Video → audio extraction: audio track copied without re-encode into the
  Music library. Android has no built-in MP3 encoder, so output is **M4A**
  and the UI labels it honestly "Extract Audio (M4A)".
- ✅ Media sharing (content URIs / built-in ShareProvider), playback history,
  Continue Watching, favorites for both media types

## Private vault
- ✅ PIN setup/verify/change — PBKDF2-HmacSHA256, 120k iterations, random salt;
  only the salted hash is stored
- ✅ AES-256-GCM encryption, key in Android Keystore, per-file random IV,
  streamed (low memory); files stored in app-private storage so they never
  appear in library scans
- ✅ Import flow: pick media → encrypt → verify decrypted length matches →
  delete original (SAF delete; MediaStore system consent dialog on Android 11+).
  If original deletion fails, the import is kept and the flow reports it via
  the consent result/rescan — the file may then exist in both places until
  the user approves deletion.
- ✅ In-vault playback: decrypts into the app-private cache only (wiped on
  vault exit/app destroy); plaintext is never written to shared storage
- ✅ Export/restore after authentication (MediaStore insert into
  Movies/Music "SonoraVault")
- ⚠️ Biometric unlock: shipped as **device-credential unlock**
  (KeyguardManager confirm-credential intent — accepts fingerprint/PIN/
  pattern, whatever the device lock uses). A direct fingerprint-only prompt
  needs the AndroidX BiometricPrompt, unavailable to the framework-only
  build; the device-lock route covers the same hardware biometrics.

## File access
- ✅ MediaStore + ContentResolver throughout; SAF for user folders/SD card
- ✅ Android 13+ READ_MEDIA_AUDIO / READ_MEDIA_VIDEO; Android 14 partial
  access via READ_MEDIA_VISUAL_USER_SELECTED (video side); ≤12
  READ_EXTERNAL_STORAGE (maxSdk 32); POST_NOTIFICATIONS requested on 13+
- ✅ No MANAGE_EXTERNAL_STORAGE requested
- ✅ Permission denial never crashes: every tab shows a permission state with
  a Grant button
- ✅ SQLite database (spec's "Room or equivalent"): favorites, playlists,
  playlist items, history, positions, settings, vault metadata; indexed
  history/positions
- ⚠️ Image loading is a built-in async loader + LruCache (plan's "Coil or
  compatible" — Coil cannot be built in this pipeline)

## Performance
- ✅ All scanning/decoding/encryption off the main thread; thumbnail LruCache;
  streamed vault crypto (no whole-file memory loads); no network use at all;
  no analytics; partial wake lock only during music playback
