# Sonora — Android app

The Android version of Sonora: a lightweight native shell (one WebView activity) that
bundles the whole Sonora web player (the `index.html` / `styles.css` / `app.js` in this
repository's root) inside the APK, so the app works without the website.

- Package: `com.nawazishali.sonora` — min Android 7 (API 24), target API 34
- The Library "Add songs from device" button works through a native file chooser.
- The WebView is deliberately never paused, so songs keep playing in the background
  while the app process is alive.
- Launcher icons are PNG files (not in git — regenerate them with
  `tools/generate_icons.py`, which draws the purple headphones icon with PIL).
- `build-sonora.sh` documents the manual build (aapt2 + javac + d8 + apksigner);
  the same project also opens in Android Studio for a signed Play Store build.
