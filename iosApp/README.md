# iosApp (placeholder)

There is no iOS app yet. This directory exists so the repository has a home for it,
and so the decision below is written down before someone starts guessing.

## Why it is a separate app, not a shared UI

The Android app is built on four things that have no iOS equivalent:

| Android | iOS |
|---|---|
| Room over SQLite | Core Data / GRDB |
| Media3 ExoPlayer + MediaSessionService | AVPlayer + MPRemoteCommandCenter |
| `youtubedl-android` (bundled Python + ffmpeg) | no equivalent; needs its own extractor |
| NewPipe Extractor (JVM) | not available |

A Kotlin Multiplatform `shared/` module could hold pure logic — models, formatting,
the resume-threshold rule, playlist ordering — but the downloader, player, storage and
UI would all be Swift. Treat iOS as a native port that borrows the *rules* in
`AGENTS.md`, not the code.

## What a first iOS slice would need

1. A local library directory + SQLite schema mirroring `items`, `playlists`,
   `playlist_items`, `jobs`, `queue_items` so a future sync is a data question, not a
   redesign.
2. A downloader path: yt-dlp cannot ship on iOS, so either a server-side helper or a
   native extractor. This is the decision that gates everything else.
3. AVPlayer + background audio entitlement.
4. SwiftUI screens that follow the same tokens (canvas/panel/ink/accent) and the same
   interaction rules.

Do not start here until the downloader question has an answer.
