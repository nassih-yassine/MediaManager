# MediaManager for Android

The app source for MediaManager for Android: paste a YouTube (or any yt-dlp) link,
pick **Audio** or **Video**, and it lands in a library that lives entirely on the
phone. No account, no server, no sync.

The Storage screen answers the phone question: **how much space is this using, and on
what** — library total, device free/used, a breakdown by kind, and every item ranked
by size with its share and a delete that frees exactly that much.

## Build

Prerequisites:

- JDK 21 (`JAVA_HOME`, or `org.gradle.java.home` in an untracked override)
- Android SDK with platform 36 (`ANDROID_HOME` or a root `local.properties`)

From the repository root:

```sh
./gradlew assembleDebug          # androidApp/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit + Robolectric tests
./gradlew lint
./gradlew installDebug           # or: make install
```

`make build`, `make test`, `make check`, `make install` and `make release` wrap the
same commands. The release build reads `MM_STORE_FILE`, `MM_STORE_PASSWORD`,
`MM_KEY_ALIAS` and `MM_KEY_PASSWORD`.

If the wrapper is missing, regenerate it once:

```sh
~/.local/opt/gradle-9.9.1/bin/gradle wrapper --gradle-version 9.9.1
```

## What works

- Paste a URL, choose Audio or Video. **YouTube goes through NewPipe Extractor**
  (anonymous, no JS runtime), so audio arrives as m4a/opus with its cover; every
  other site stays on the bundled yt-dlp.
- Downloads run in a `dataSync` foreground service, split over several ranged
  connections, two jobs at a time, and survive leaving the app.
- **Network loss resumes by itself.** A waiting job keeps the service alive, the
  notification says "Waiting for the network", and the queue continues on the next
  connectivity change.
- Library: Audio / Video, search, sort by newest, oldest, title, largest, duration.
  Play, play next, add to queue, add to playlist, delete.
- Player shared across screens: queue with reorder and remove, resume points in the
  database, a prompt before restarting long media, immersive video sized to the
  stream, a home-screen widget with play/pause/next/previous/repeat.
- Playlists with reorder, play all and shuffle.
- Downloads screen: progress, stage, cancel, retry, clear finished.
- Storage screen: library total, device bar, kind breakdown, thumbnails, temp,
  largest items with share, Re-check, delete.
- Six theme families × light/dark, stored on device.

## Known limits

- **YouTube extraction is the fragile part.** NewPipe occasionally needs an update
  when YouTube changes; update the app when YouTube downloads start failing.
- YouTube audio is m4a/opus, not mp3.
- **Android 15+ caps `dataSync` foreground services at 6 hours per day.** Jobs are
  rows: a cancelled service resumes on the next app open.
- Arm64 only; there is no 32-bit APK.
- No iOS target yet — see [`../iosApp/README.md`](../iosApp/README.md).
