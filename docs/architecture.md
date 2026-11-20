# Architecture

One Android app, no backend. Everything below the UI is on the device.

```
androidApp/app/src/main/java/dev/mediamanager/mobile/
  App.kt, AppContainer.kt     process entry, manual dependency graph
  data/                       Room entities, DAOs, the database and its migrations
  download/                   extractor interface, NewPipe + yt-dlp, pipeline, queue,
                              foreground service, notifications, muxer
  media/                      library paths, thumbnails, covers, audio route, volume
  playback/                   PlayerRepository over a MediaController, PlaybackService
  storage/                    size aggregation and the device bar
  ui/                         Compose screens, theme tokens, components
  widget/                     the now-playing RemoteViews widget
```

## Layers

- **Data.** Room is the single source of truth. `items` holds the library, `jobs` the
  download queue, `playlists` + `playlist_items` the collections, `queue_items` the
  play queue. DAOs expose flows; the UI never caches a copy as the authority.
- **Download.** `DownloadService` drains `jobs` with a lease-based claim. An
  `Extractor` turns a URL into a file (`YoutubeNewPipeExtractor` for YouTube,
  `YtDlpExtractor` for everything else). `DownloadPipeline` owns the finalize order:
  write into `.incoming/<job>`, rename into the library, insert the `items` row, mark
  the job done.
- **Playback.** `PlaybackService` is a `MediaSessionService` and owns the only
  ExoPlayer. `PlayerRepository` talks to it through a `MediaController`, publishes a
  `NowPlaying` snapshot, and persists resume points and the queue.
- **UI.** One activity, Compose screens over DAO flows, an `AppContainer` handed down
  explicitly. Overlays (player, downloads, storage) live in the shell so the player
  survives navigation.
- **Widget.** RemoteViews reading a snapshot the repository pushes, sending transport
  as service start commands (a receiver may not bind a service).

## Why there is no server

The product is a phone library: the phone holds the bytes, the database and the
account-free experience. A server would add sync, auth and a second copy of every rule
without making the app work better offline. If sync ever arrives, it should be
server-authoritative and additive, not a rewrite of these layers.
