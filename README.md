# Sonora — Music Player

A modern, purple-neon music player web app, designed after a premium mobile music UI: dark theme, glowing violet gradients, and a full-screen "Now Playing" experience.

## Features

- **Home** — greeting, "Feel the Music" hero, Trending Now rail, your playlists, and a Made For You list
- **Real playback** — play/pause, next/previous, seek bar, volume, shuffle and repeat (off / all / one)
- **Background playback** — music keeps playing when you switch apps or lock the screen (keep the browser tab open), with lock-screen controls via the Media Session API
- **Playlists** — create your own playlists, add any song with the + button, play a whole playlist
- **Favorites** — tap the heart on any song; liked songs collect in one place
- **Synced lyrics** — tap "Lyrics" in the player and the current line lights up as the song plays
- **Queue** — see what plays next and jump to any song
- **Equalizer** — 5-band EQ (60Hz–14kHz) with Bass Boost, Virtualizer and presets (Normal, Rock, Pop, Jazz, Bass). The EQ processes songs added from your device through the Web Audio API; built-in demo streams always play directly so the sound never breaks
- **Your device songs** — add MP3s from your phone in the Library and play them inside Sonora
- **Search & genres** — live search by song or artist, plus genre chips
- **PWA** — installable, with a service worker that caches the app shell

## Run it

Open `index.html` on any static host (for example GitHub Pages) — no build step, no server code. The built-in library streams royalty-free demo tracks; add your own music from the Library tab.

## Tech

Plain HTML + CSS + JavaScript. Audio via the HTMLAudio element, Media Session API for background/lock-screen controls, Web Audio API for the equalizer, and localStorage for favorites, playlists and settings.

Made by Nawazish Ali.
