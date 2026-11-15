# MediaManager for Android

A standalone Android app for the same job as the desktop MediaManager: paste a
YouTube (or any yt-dlp) link, pick **Audio** or **Video**, and it lands in a library
that lives entirely on the phone. No account, no server, no sync.

The second screen answers the phone question: **how much space is this using, and on
what**. Library total, device free/used, a breakdown by kind, and every item ranked
by size with its share and a delete that frees exactly that much.

## Build

Prerequisites on this machine:

- JDK 21 at `~/.local/opt/jdk-21.0.13+9` (pinned in `gradle.properties`)
- Android SDK at `~/Android/Sdk` (pinned in `local.properties`)

```sh
cd apps/mobile
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit + Robolectric tests
./gradlew installDebug           # install on a connected phone
```

`./gradlew lint` is part of the gate before a release build.

If the wrapper is missing, regenerate it once:

```sh
~/.local/opt/gradle-9.9.1/bin/gradle wrapper --gradle-version 9.9.1
```

## What works

- Paste a URL, choose Audio (mp3 + cover art) or Video (mp4, height capped in
  Settings). Downloads run in a foreground service and survive leaving the app.
- Library: All / Audio / Video, search, sort by newest, oldest, title, largest,
  duration. Play, play next, add to queue, add to playlist, delete.
- Player bar that stays across screens, queue with reorder, resume points saved in
  the database.
- Playlists with reorder, play all, rename-free v1 (create and delete).
- Downloads screen: progress, stage, cancel, retry, clear finished.
- Storage screen: library total, device bar, kind breakdown, thumbnails, temp,
  largest items with share, Re-check, delete.
- Four theme families × light/dark, stored on device.
- Settings: Wi-Fi-only downloads, max video height, extractor version, about.

## Known limits

- **yt-dlp breaks sometimes.** YouTube changes signature handling; the extractor is
  the most fragile part. Update the app when downloads start failing.
- **Android 15+ caps `dataSync` foreground services at 6 hours per day.** Jobs are
  rows: a cancelled service resumes on the next app open. Very long queues may need
  two sessions.
- **No boot auto-resume.** The queue starts when the app is opened.
- **The library is private to the app.** Files live under `files/library/` and are
  not visible in the gallery or the Files app. Deleting the app deletes the media.
- **GPL-3.0.** Bundled yt-dlp + ffmpeg make the app GPL-3.0.
- Playlists cannot be renamed yet; the API exists, the button does not.
- Queue and playlist reordering use ↑/↓ buttons, not drag.
- Video plays inside the Now Playing sheet; there is no separate full-screen player.
- No in-app extractor update button yet: update the app to pick up a newer yt-dlp.
- Orphan `.incoming` directories are not swept automatically; a killed download resumes
  into the same directory, so they are reused rather than leaked.

## Smoke test on a phone

1. Add a short link as Audio → item appears with cover art.
2. Add the same link as Video → second item, plays, seeking works.
3. Add the Audio link again → "Already in the queue", no duplicate file.
4. Start a long download, swipe the app away, reopen → the job continues.
5. Airplane mode + a new link → readable failure; retries when the network returns.
6. Delete the playing item → playback stops, Storage total drops by its size.
7. Storage → Audio + Video + Thumbnails + Temp ≈ Library total.
