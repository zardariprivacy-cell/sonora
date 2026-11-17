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

---

# v3.1 additions (Nov 17, 2026)

## Timeline bug fix
- ✅ Root cause found in the v3.0 code: both players computed seek progress on
  a 0..1000 scale but never called `seek.setMax(1000)`; a SeekBar's default
  max is 100, so the bar saturated at 10% of the file while playback
  continued. v3.1 uses real milliseconds end-to-end (max = duration,
  progress = position), duration resolved player-first
  (MediaPlayer.getDuration() → MediaMetadataRetriever → MediaStore), the max
  updates dynamically if the player's duration differs, and position is
  clamped ≤ duration. Same fix in the music player (identical defect).
- ✅ Unit audit: MediaStore DURATION (ms) and SIZE (bytes) were already read
  and formatted correctly everywhere (list/grid/folders/storage/details).

## Home & Settings redesign (reference element style)
- ✅ Settings tab rebuilt in the user's reference style: a 3×3 grid of large
  line icons with labels (Private Vault, Extract Audio, Storage, Folders,
  Theme, History, Share App, Large Files, Refresh Library) over a flat icon
  list with thin dividers (Theme, Sleep Timer, Equalizer, Private Vault,
  Storage Dashboard, Playback History, Help, About) — Midnight Violet, own
  vector icons, no emoji, no copied artwork, and NO coins/VIP/ad placeholders
  (those PLAYit monetization elements were deliberately not copied).
- ✅ Every grid cell and row performs a real action (video-picker + M4A
  extraction for Extract Audio; StorageActivity scrolled to largest files
  for Large Files; real share intent for Share App). No dead elements.
- ✅ Home tab keeps all real-data sections and gains the same-style
  quick-access icon grid (Videos, Music, Folders, Private Vault, History,
  Storage) under the welcome header and search bar.
- ✅ New screens: Playback History (history table, most recent first, with
  Clear) and Help (real usage notes for gestures, subtitles, vault, files).

## "3D box" popup interactions
- ✅ MediaSheet: tapping a media item's overflow (⋮) button or long-pressing
  it anywhere (Videos, Music, Folders, Home sections, search results,
  history) opens a raised bottom sheet — 18dp elevation shadow, rounded top
  corners, gradient header with artwork + title + real metadata (duration,
  size, format, path), then all actions as icon rows. Slides up over a
  dimmed background; dismisses on outside tap or drag-down on the header.
- ✅ Video rows: Play, Play in Picture-in-Picture (player opens and enters
  PiP once prepared), Add/Remove Favorites, Extract Audio (M4A), Share,
  Open with…, Rename, Details (now incl. format + real resolution), Delete.
- ✅ Song rows: Play, Play Next, Add to Queue (new MusicService queue
  operations), Add to Playlist, Add/Remove Favorites, Share, Open with…,
  Rename, Details, Delete.
- ✅ Folder long-press opens the folder sheet: Open + Details (song/video
  counts and sizes).
- ✅ PressFx 3D press feedback: buttons and grid cells scale down slightly
  with an elevation drop on touch and spring back on release (native
  ViewPropertyAnimator). Applied to the Home/Settings grids and lists,
  bottom-nav items, mini-player buttons, media rows' overflow, sheet rows,
  and both players' control buttons (touch listeners never consume events,
  so clicks, long-presses, scrolling and player gestures are unaffected).

## AdMob (owner's real account)
- ✅ Integrated with the same proven manual pipeline as Ilmora (Google
  Mobile Ads SDK 23.6.0 bundled + dexed without Gradle, manifests merged).
  App ID ca-app-pub-5229747293404900~3066756153 (manifest meta-data,
  verified in the delivery APK); INTERNET + ACCESS_NETWORK_STATE added.
- ✅ Banner unit ca-app-pub-5229747293404900/4996795821: anchored-adaptive
  banner in MainActivity's layout flow between the tab content and the
  mini-player (directly above the bottom navigation when the mini-player
  is hidden), hidden on the Settings tab. The container stays GONE until
  an ad actually loads — no empty box.
- ✅ Interstitial unit ca-app-pub-5229747293404900/4613652442: preloaded;
  shown only when the user opens a video, at most once every 3 minutes,
  never on app start and never during playback (in-player next/previous
  does not pass the gate). If no ad is loaded, the video opens instantly.
- ⚠️ The AdMob ACCOUNT is still "being verified", so real ads do NOT serve
  yet. The integration fails silently by design (no dialogs, no crashes,
  banner space collapses). Ads will start serving once Google approves the
  account — no app update needed. Never click your own ads.

## Install-conflict fix (Nov 17, 2026) — v3.1 re-shipped

- 🐛→✅ v3.1 as first shipped would not install on a phone that also has
  Ilmora installed: "App not installed as package conflicts with an
  existing package." Root cause: the manual ads pipeline merged GMS AAR
  manifests without substituting the `${applicationId}` placeholder, so
  the APK declared provider authorities literally named
  `${applicationId}.mobileadsinitprovider` and
  `${applicationId}.androidx-startup` — identical to Ilmora's, and Android
  allows only one app per provider authority
  (INSTALL_FAILED_CONFLICTING_PROVIDER).
- ✅ Fix: `build-v31.sh` now substitutes `${applicationId}` →
  `com.nawazishali.sonora` in the merged manifest before linking.
  Verified on the delivery APK (aapt2 xmltree): authorities are
  `com.nawazishali.sonora.mobileadsinitprovider` and
  `com.nawazishali.sonora.androidx-startup`; no literal placeholder
  remains. Signing cert unchanged (SHA-256
  066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050), so it
  installs over v3.0. Drive file replaced in place, size/MD5 verified.
