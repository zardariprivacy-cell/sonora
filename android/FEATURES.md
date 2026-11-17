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

---

## v3.3 (versionCode 13, Nov 17 2026) — "Everything 3D, colourful, centered"

A polish pass driven by the user's v3.2 phone screenshots and his words:
"3D boxes banao, middle main rakho, colourful boxes banao, singing wali
vibe aani chahye… sara kuch 3D hona chahye" + "audio waly songs hain unka
logo to lagao" (songs need artwork). No feature was removed; everything
from v3.2 (theme/profile, timeline fix, AdMob, vault, extraction, PiP,
sheet actions) is preserved with the same ad unit IDs.

- **Centered 3D popup (MediaSheet):** the ⋮ / long-press sheet is no longer
  a bottom sheet — it opens as a CENTERED popup card (gravity center,
  62% dim backdrop, tap-outside to dismiss) with a layered 3D background
  (Primary→Accent gradient border + top-lit violet fill + white sheen),
  24dp elevation, a pop-in animation (scale 0.88→1 + fade, 190ms), a
  colourful gradient header (artwork/logo + title + metadata) and action
  rows whose icons sit in colourful gradient badges. Every existing action
  (Play, Play Next, Add to Queue, favorites, playlists, Share, Details,
  Rename, vault, delete, …) works exactly as before.
- **All dialogs themed (new `Dialogs` helper):** every dialog in the app —
  Edit Profile, delete confirmations, Details, playlist pickers, sleep
  timer, rename, sort pickers, video speed/aspect/audio/subtitle menus,
  queue, clear-history, vault PIN change/delete — now renders as the same
  centered 3D card with gradient primary buttons. Edit Profile specifically
  is a dark #211A35-family card, rounded, with a themed input and a
  gradient SAVE button (the v3.2 screenshot showed a plain grey stock
  dialog). A `SonoraDialog` alert-dialog theme in themes.xml backstops any
  remaining stock dialog so the grey look cannot come back.
- **Colourful 3D everywhere:** `Ui.cardBg()` is now a layered 3D drawable
  (gradient edge #9B6BFF→#C084FC, top highlight, card fill) so every list
  card, grid cell and section card in the app lifted at once, plus real
  elevation shadows on rows/cards. Icon chips in grids, menus, settings
  rows, folder rows and sheet rows sit in colourful gradient badges that
  rotate through six harmonious gradients (violet, pink, magenta, indigo,
  teal-blue) picked deterministically per label.
- **Centered gradient brand header:** the toolbar on every tab now shows
  "SONORA" in a Primary→Accent gradient, CENTERED at the top, with the
  tagline centered under it and search floating at the right edge.
- **Folders tab:** the storage card is a colourful 3D box with a gradient
  storage badge; "Open a folder" and "Last granted folder" are 3D card
  rows with gradient folder badges; folder rows use gradient folder badges
  + elevation; the section header carries the EQ-bars decoration.
  FolderMediaActivity inherits the card/artwork treatment automatically.
- **Song artwork / logo (new `Artwork`):** audio rows never show a blank
  square again. Order: real album art → generated colourful placeholder —
  a gradient from the violet/pink/indigo/blue family chosen by the song
  title's hash (same song, same art, every time), a white music-note
  glyph and a subtle EQ-bars motif, drawn once and cached in the existing
  LruCache. Videos without a thumbnail get the analogous gradient + play
  glyph. Applies everywhere art appears: Music list, Home rows, search,
  queue, mini-player, popup header, playlists, Now Playing.
- **Music vibe:** the currently-playing song's row shows small animated
  EQ bars (the existing AnimatedBarsView in miniature; frozen frame when
  paused) in both the Music list and programmatic rows; Home and Folders
  section headers get a tiny gradient EQ-bars decoration.
- **Small fixes:** the Vault PIN screen description now wraps in full (it
  was truncated with "…" because the label forced one line); the same
  wrap fix applied to the vault header, Home permission card, empty-state
  messages and History. The Music tab's stray right-edge pill (the OEM
  fast-scroll thumb in the screenshot) is gone — fast scroll disabled,
  thin fading scrollbar instead.
- Build: `build-v33.sh` (derived from build-v32.sh; keeps the
  `${applicationId}` substitution + assert and the AdMob integration).
  Delivery APK verified: vc13 / 3.3, no literal `${applicationId}` in the
  binary manifest, providers carry the `com.nawazishali.sonora` prefix,
  signing cert unchanged (SHA-256
  066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050), so it
  installs over v3.2. Size 5,506,342 bytes, MD5
  16717bb9ee36e7fccac09573df215820. Compile/static-verified only — NOT
  device-tested (see TESTING-REPORT.md).

## v3.4 (versionCode 14, Nov 17 2026) — Music-tab crash fix + 3D video option menus + full button audit

- **Music-tab crash FIXED at the root.** v3.3 added an `AnimatedBarsView`
  (the animated EQ bars) into the shared media-list row layout
  (`item_media_list.xml`), but the view only declared a `(Context)`
  constructor. LayoutInflater requires a `(Context, AttributeSet)`
  constructor, so inflating the FIRST row threw `InflateException` and the
  app died the moment the Music tab's list bound — every time, for every
  user. The full constructor chain is now implemented. The same layout is
  used by Search results, folder contents and the Videos tab list mode, so
  all four surfaces are fixed by the same change.
- **Second latent crash removed:** `Ui.gradientFor(int)` used
  `Math.abs(index)`, which stays negative for `Integer.MIN_VALUE` (a
  possible song-key hash) and would have indexed the gradient table at −2.
  Now bitmask-based, safe for every int.
- **Row binding can never crash on decoration again:** the EQ-bars /
  favourite-heart decoration in both row binders (`MediaAdapter`,
  `Rows.mediaRow`) is wrapped so any failure degrades silently — artwork
  and playback are unaffected.
- **Video player option menus are 3D boxes now:** Speed, Aspect ratio,
  Audio track, Subtitles, Repeat and Rotate all open the same centered 3D
  popup as the media sheet — gradient header naming the group, colourful
  icon rows, and the current selection filled with the Primary→Accent
  gradient with a check mark. Repeat and Rotate changed from one-tap
  toggles into menus (Off / Repeat one; Portrait / Landscape) with the
  same underlying actions; every other selection performs byte-identically
  the action it performed before.
- **Full-app button audit** (every clickable element in every screen traced
  to its handler in code; table in `~/workspace/sonora-v3/report-v34.md`).
  Fixes from the audit: the About dialog said "Version 3.3" (now 3.4);
  vault rows no longer show the standard ⋮ sheet (its rows can't work on
  an encrypted item — the vault's own Play/Export/Delete menu stays on
  long-press); the Edit Profile dialog now dismisses before the photo
  picker opens and after a photo removal instead of floating stale over
  the rebuilt tab; the Equalizer preset spinner no longer reapplies the
  already-active preset (which rebuilt the screen); the Videos grid/list
  toggle icon now swaps with the mode. Everything else traced OK as-is —
  no dead buttons, no stub handlers, no fake toasts found; every intent
  target exists in the manifest.
- Delivery APK verified at `~/workspace/your_files/sonora/Sonora.apk`:
  5,510,438 bytes, MD5 1157f5dd7031c04af8a2c3673f1475a5, vc14 / 3.4, zero
  literal `${applicationId}` in the merged manifest, signing cert unchanged
  (SHA-256 066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050),
  so it installs over v3.3. Compile/static-verified only — NOT
  device-tested (see TESTING-REPORT.md).

## v3.5 (versionCode 15, Nov 17 2026) — Video player's bottom options BAR is now colourful 3D boxes

- **The bar itself, not just its menus.** v3.4 converted the option MENUS
  to centered 3D popups but left the bottom bar that opens them as a row
  of bare white text labels on black ("1.0x  Aspect  Subtitles  Audio
  Repeat  Rotate") — the user's screenshot called this out directly
  ("Bottom waly option 3D box main nahi kiya"). Each of the six options is
  now its own **colourful 3D box**: the shared layered 3D treatment
  (gradient border + top-lit fill + real elevation shadow), a white glyph
  on top and a small label under it.
- **Each box has its own tint.** The border gradient rotates through the
  same gradient-badge family used for icon chips (`Ui.gradientFor`):
  Speed violet→lavender, Aspect pink→violet, Subtitles blue→violet,
  Audio teal→blue, Repeat violet→pink, Rotate indigo→cyan. New
  `Ui.card3d(radius, int[])` overload supplies the per-box tint; the
  original `card3d(radius)` delegates to it with the brand pair.
- **Layout choice:** PORTRAIT = two rows of three boxes (each box wide
  enough for the "Subtitles" label at full size); LANDSCAPE = one row of
  six. The activity handles rotation itself (`configChanges`), so the bar
  re-flows in `onConfigurationChanged` without recreating the player.
- **Behaviour is byte-identical to v3.4:** tapping a box opens the same
  centered 3D option menu (Speed / Aspect / Subtitles / Audio / Repeat /
  Rotate). The Speed box label still shows the live speed ("1.0x") and
  updates on change. Repeat's state now shows on the box itself: ON =
  full Primary→Accent gradient fill, OFF = its tinted 3D box (the toast
  feedback is unchanged).
- **Matching chrome, lightly:** the −10s / +10s and prev / next controls
  are now circular 3D chips (new `Ui.circle3d` — gradient rim + top-lit
  fill + elevation); the center play/pause disc keeps its gradient and
  gains a real elevation shadow; the top-right PiP and Lock icons sit in
  small circular 3D chips. Playback behaviour, double-tap seek,
  brightness/volume swipes, the seekbar logic and the v3.4 menus are
  untouched. The bar still auto-hides/shows with the existing controls
  fade and lives inside the bottom bar, below the seekbar.
- Delivery APK verified at `~/workspace/your_files/sonora/Sonora.apk`:
  5,510,438 bytes, MD5 4de55098bb168f7c2cc8af5bf09c4a78, vc15 / 3.5, zero
  literal `${applicationId}` in the merged manifest, signing cert unchanged
  (SHA-256 066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050),
  so it installs over v3.4. Compile/static-verified only — NOT
  device-tested (see TESTING-REPORT.md).

## v3.6 (versionCode 16, Nov 17 2026) — Vault 3D treatment + player contrast fix + aspect-fit sizing

- **Private Vault — full colourful 3D treatment (`VaultActivity`,
  presentation only; `Vault.java`, the PIN verification flow and all
  import/export/file logic are untouched).**
  - **Gate (setup / unlock):** the whole panel is now a centered 3D card
    (the shared layered `cardBg` treatment + elevation) with a gradient
    shield badge on top (`Ui.iconBadge`), the PIN field(s) inside a
    tinted inner box, and each field itself restyled as a tinted inset
    box. "Create PIN & open vault" / "Unlock" are full-width gradient 3D
    buttons; "Use device lock (fingerprint / PIN)" is a full-width
    saturated 3D box (indigo→cyan). All handlers, validation messages and
    the device-credential intent are byte-identical to v3.5. Honest note:
    the vault has **no custom number pad** — PIN entry is the system
    password field, whose dots are what sits in the tinted box; no keypad
    was invented, because that would change the PIN entry flow.
  - **Contents:** the header is a 3D card with the gradient shield badge,
    the title and a live counts/usage line ("N files • X encrypted on
    this device", computed from the vault cursor). **Import media** is a
    big gradient 3D box; **Change PIN** a saturated indigo→cyan 3D box
    beside it. Every item row is now a vault-specific colourful 3D card
    (saturated gradient fill from the badge family — blue = video,
    violet = audio, pink = photo, indigo→cyan = file) with a matching
    gradient type badge, white title, and a "size • type • Encrypted"
    line with a small lock glyph. Click still plays, long-press still
    opens the vault's own menu, and rows still have no ⋮ (v3.4). The
    empty state sits in a dashed-border card with a gradient badge.
  - **Change PIN dialog:** its card was already the 3D dialog card
    (`Dialogs` uses `Ui.dialogCardBg`); its fields now carry the same
    tinted inset-box style via the shared `pinField()` helper. Flow and
    validation unchanged.
  - Settings → About now says "Version 3.6" (the string had lagged at
    3.4 through the v3.5 build).

- **URGENT contrast fix — the v3.5 player boxes/chips were invisible.**
  The user's v3.5 screenshot showed the six bottom-bar boxes and the
  circular chips as near-white / pale-lavender boxes with white icons and
  labels ("kuch nazar bhi nahi aa raha"). Root cause, in code: the v3.5
  `Ui.card3d(radius, int[])` and `Ui.circle3d(int[])` used the tint pair
  ONLY in the 2dp border ring (at alpha 185/120 dark, 165/110 light);
  the fill was the pale theme gradient — **light theme: #FFFFFF →
  #EAE3F8**; dark theme: lighten(#2C2247, .16) ≈ #4A3F6E → #211A35 — and
  the player draws white glyphs + labels, so under the light theme the
  content vanished. **Fix:** in both helpers the fill is now the pair
  gradient itself at full saturation (TL_BR, full alpha — the same
  values as the proven `Ui.iconBadge` fills, e.g. #9B6BFF→#C084FC,
  #F15BB5→#9B6BFF, #5B8CFF→#9B6BFF, #00C9A7→#5B8CFF), the rim is the
  pair darkened to 62%, and a thin top sheen (white alpha 64 → 0) keeps
  the lit-from-top 3D feel without washing the colour out. Elevation and
  PressFx are unchanged. `Ui.card3d(radius)` (the app-wide theme card)
  was **decoupled** from the tint overload and reproduces its v3.5
  output exactly, so no other screen changed. Audit: the tint overload
  and `circle3d` had exactly one consumer — `VideoPlayerActivity` (six
  tool boxes, Repeat OFF state, prev/next and ±10s chips, PiP/Lock
  chips) — plus the new v3.6 vault rows, which follow the same rule:
  white content only on deep/saturated fills. **Repeat ON** is now
  distinguished by the brighter Primary→Accent gradient fill PLUS a
  bold "Repeat ✓" label (OFF = the saturated violet→pink tint box).

- **Video sizing — "jesa jis format main ho, usy wesy rehne do."** The
  TextureView is now SIZED to the video's display rect instead of being
  a permanent full-window view with the picture squeezed by a matrix.
  Old mechanism (v3.0–v3.5): `updateAspect()` only set a centre-pivot
  `setTransform` computed from `getVideoWidth()/getVideoHeight()`
  captured once at `onPrepared` — the stream's coded dimensions; for
  files whose display ratio differs (rotation / sample-aspect metadata,
  common in phone and WhatsApp videos) the picture shrank into a small
  centred band, the size was never refreshed, and rotation re-ran the
  same stale numbers. New math, recomputed on prepared /
  onVideoSizeChanged / surface-size-changed / configuration-changed
  against the current screen size: **Fit (default)** = largest rect of
  the video's own ratio inside the screen (letterbox/pillarbox on the
  black root, zero distortion, zero crop); **16:9 / 4:3** = same fit
  math with the forced ratio; **Original** = the video's pixel size,
  centred; **Fill** = full-screen view + cover matrix (ratio preserved,
  overflow cropped); **Stretch (new)** = full-screen view, identity
  matrix, ratio ignored. Mode switches resize immediately without
  restarting playback; the mode persists for the session as before. The
  touch listener moved from the TextureView to the root with coordinates
  translated into texture space, so taps/swipes on the letterbox bars
  still work and the double-tap thirds + brightness/volume halves track
  the actual video area; buttons and the seek bar still consume their
  own touches first. PiP, auto-hide chrome, the v3.4 menus and the v3.5
  bar are untouched.
- Delivery APK verified at `~/workspace/your_files/sonora/Sonora.apk`:
  5,514,534 bytes, MD5 48943a8fb850f8eac5de8c102eaa41fa, vc16 / 3.6, zero
  literal `${applicationId}` in the merged manifest, signing cert unchanged
  (SHA-256 066d5a8fc005469a587e90d44a8ffd6a35db79897fd67cc7127c8c59a3392050),
  so it installs over v3.5. Compile/static-verified only — NOT
  device-tested (see TESTING-REPORT.md).
