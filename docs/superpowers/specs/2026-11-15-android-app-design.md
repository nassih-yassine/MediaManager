# MediaManager Android — Design

Date: 2026-11-15
Status: awaiting approval
Related: `2026-11-14-media-manager-spec.md` (desktop/server app)

## 1. Intent

A pocket version of MediaManager. Paste a YouTube (or any yt-dlp) URL, choose
**Audio** or **Video**, and it lands in a library that lives entirely on the phone.
No accounts, no server, no sync, no login screen.

The second job is knowing where the storage went. The library is the only thing the
app stores, so the Storage screen must answer, precisely: how much this library
uses, how much the phone has left, and which items are the big ones.

## 2. Goals

- Paste-a-URL → Audio (mp3 + cover art) or Video (mp4), same behavior as the
  desktop worker.
- Offline library: browse, search, sort, play, queue, resume where you stopped.
- Organize with ordered playlists.
- Survive process death and reboots: the download queue is durable, nothing is
  half-visible in the library.
- Know the cost: device free space, library total, per-kind breakdown, per-item
  size ranked, delete frees space immediately.
- Feel like MediaManager: same quiet surfaces, same four theme families × light/dark.
- Keep it small and cheap to maintain: one module, no server, no DI framework.

## 3. Non-goals (v1)

- Accounts, login, sync, or any network call except the download itself.
- Indexing media already on the phone (MediaStore, camera roll, Downloads).
- Importing local files (file picker, share sheet). **Only media the app downloaded
  exists in the library.** Nothing else is visible in it and nothing leaves it.
- Pictures, galleries, books. Nothing produces them without local import, so v1 is
  `audio` + `video` only; `kind` stays a text column so they can return later.
- Notifications, reports, admin, announcements — they are server/account features.
- Chromecast, subtitles, playlists over DLNA, widgets.
- Exporting library files to `Downloads/` or the gallery (deferred; see §12).
- Widgets, Wear, tablet-specific layouts (phone portrait + landscape video only).
- Shuffle and repeat (the buttons are absent in v1, not just unbound).
- Downloading after a reboot without opening the app: no `RECEIVE_BOOT_COMPLETED`
  receiver. The queue resumes the next time the app is opened.

## 4. Anti-goals

- **A second backend.** No embedded HTTP server, no second SQLite schema shared with
  the Rust app. This is a standalone app with its own Room DB.
- **Losing a download to a swipe.** Killing the app mid-download must leave a
  resumable job, not a partial file in the library.
- **A storage screen that guesses.** Sizes come from the DB (`items.bytes`) and one
  `StatFs` call, not from walking the tree on every frame.
- **A 200 MB APK because it was easier.** ABI splits, arm64 only by default.

## 5. Constraints

| Constraint | Consequence |
|---|---|
| `minSdk 26`, `compileSdk/targetSdk 36` | Media3, foreground services, per-app language all fine. |
| Downloads happen in a foreground service | Android 14+ needs `foregroundServiceType="dataSync"`; Android 15+ caps that at **6 hours per 24h**. Long queues must be resumable, not restarted. |
| YouTube needs a JS runtime for signature solving | `youtubedl-android` 0.18.1 bundles QuickJS. Pass `--js-runtimes quickjs`; fall back to defaults on failure. This is the most fragile subsystem. |
| No Node/deno on the device | Desktop's `--js-runtimes node` flag must not be copied. |
| yt-dlp is GPL-3.0, bundled | The app is GPL-3.0. Ship the license text from in-app Settings → About. |
| App-private storage only | No `MANAGE_EXTERNAL_STORAGE`, no `READ_MEDIA_*`. Library under `filesDir`; invisible to the gallery by design. |
| Android auto-backup | Must be disabled for media, or a 4 GB library gets uploaded to Google. `allowBackup=false`. |
| Machine has no JDK/SDK | Nothing can be compiled until Temurin 21 + cmdline-tools are installed (~10 GB). See §13. |

## 6. Approaches considered

**Downloader.** Three options:

1. **`youtubedl-android` 0.18.1** — bundled yt-dlp + Python + ffmpeg, Maven Central,
   actively released. Same extractor and flags as desktop: `-x --audio-format mp3`,
   `--write-info-json`, sidecar thumbnail, separate ffmpeg cover pass. Any site yt-dlp
   supports works. Costs ~30–60 MB per ABI and GPL-3.0.
2. **NewPipe Extractor** — pure Kotlin, tiny, fast, no Python. YouTube only, no mp3
   without a second ffmpeg (ffmpeg-kit is archived), so audio would be `.m4a` or
   `.opus` and video capped at muxed streams/no 4K. Rejected: it is not "the same as
   desktop", and the user asked for exactly that.
3. **Delegate to the user's Rust server** — rejected: contradicts "everything local,
   no auth".

**Chosen: 1.** Parity of behavior beats APK size for a personal app, and one code path
handles every supported site, mp3 conversion and cover art.

**Queue engine.** Options: WorkManager, or Room rows + a foreground service.

WorkManager is the platform default, but a Python subprocess does not fit its retry /
cancellation model, progress reporting is awkward, and its 10-minute-per-run limit is
wrong for a 2 GB video. Chosen: **Room rows + one foreground service with a bounded
worker loop**, mirroring the desktop job table (claim, heartbeat, retry, lease
expiry). The platform's constraints are respected by keeping every stage idempotent
so a killed service resumes instead of restarting.

## 7. Architecture

Single Gradle module `:app`, packages by feature:

```
app/src/main/java/dev/mediamanager/mobile/
  MainActivity.kt            single activity, Compose
  data/    AppDatabase, ItemDao, JobDao, PlaylistDao, entities, converters
  media/   LibraryPaths, Thumbnailer, ItemRepository
  download/ DownloadService (FGS), DownloadWorker, YtDlpRunner, JobQueue,
            ErrorClassifier, ProgressParser
  playback/ PlaybackService (Media3 MediaSessionService), PlayerRepository
  storage/ StorageRepository (StatFs + aggregate queries)
  ui/      theme/ (tokens, Compose ColorScheme, type)
           library/ playlists/ add/ downloads/ storage/ settings/
           player/ (MiniPlayer, NowPlayingSheet, QueueSheet)
           components/ (Card, BarSlider, SelectMenu, dialogs)
```

- **One activity, Compose navigation**, bottom bar: Library · Playlists · Add ·
  Downloads · Storage; Settings in the top bar.
- **Room** is the only source of truth for items, jobs, playlists. Flow-based DAOs
  feed Compose; no ViewModel caching a second copy of the list.
- **Media3** `MediaSessionService` owns playback; `MiniPlayer` is a Compose overlay
  bound to `MediaController`. This is the Android equivalent of the desktop's
  "one media element only" rule: exactly one `ExoPlayer`, kept alive across screens.
- **Coil** for thumbnails from `files/thumbs/…`.
- No Hilt/Koin: a single `AppContainer` created in `Application`, passed explicitly.
  Fewer moving parts, faster builds.

## 8. Data model (Room)

```kotlin
@Entity(tableName = "items", indices = [Index("kind","createdAt"), Index("title")])
data class ItemEntity(
  @PrimaryKey val id: String,            // uuid v7, time-sortable
  val kind: String,                      // "audio" | "video"  (DB values, not labels)
  val title: String,
  val sourceUrl: String?,
  val path: String,                      // relative to library root
  val thumbPath: String?,
  val mime: String,
  val bytes: Long,
  val thumbBytes: Long = 0,              // thumb file size at finalize, for Storage
  val available: Boolean = true,         // false = file missing, set by Re-check
  val durationMs: Long?,
  val width: Int?, val height: Int?,
  val positionMs: Long = 0,
  val lastPlayedAt: Long? = null,
  val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "jobs", indices = [Index("status","priority","scheduledAt"), Index("dedupeKey")])
data class JobEntity(
  @PrimaryKey val id: String,
  val dedupeKey: String,                 // sha256(url|mode)
  val url: String,
  val mode: String,                      // "audio" | "video"
  val status: String,                    // queued|running|done|failed|cancelled
  val stage: String?,                    // fetching|converting|thumbnail|finalizing
  val progress: Float,
  val error: String?,
  val itemId: String?,
  val attempts: Int, val maxAttempts: Int = 3,
  val scheduledAt: Long,
  val claimedBy: String?, val heartbeatAt: Long?,
  val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey val id: String, val name: String,
                          val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId","itemId"],
        foreignKeys = [/* CASCADE both ways */], indices = [Index("playlistId","position")])
data class PlaylistItemEntity(val playlistId: String, val itemId: String,
                              val position: Int, val addedAt: Long)

@Entity(tableName = "queue_items", primaryKeys = ["position"],
        foreignKeys = [/* itemId CASCADE */])
data class QueueItemEntity(val position: Int, val itemId: String)
```

- Search is `LIKE '%q%'` on `title` with the index; a phone library is thousands of
  items, not 100k, so FTS is not worth the schema and trigger surface. (Reverse of
  the server's reasoning; recorded so nobody re-litigates it.)
- No `owner_id`, no `sync_changes`, no `notifications`. Deleted items cascade out of
  playlists **and out of `queue_items`**.
- **No `RETURNING`.** `minSdk 26` ships SQLite 3.18; `UPDATE … RETURNING` needs 3.35.
  Claim and reorder are `@Transaction` methods (select, then update), not one clever
  statement.
- Schema versioning: Room `Migration` objects from v1 onward. No destructive fallback:
  the library files outlive an uninstall only if the user keeps them, so a dropped DB
  is lost work.

## 9. Download pipeline

1. **Add** validates the URL (`https`, known-scheme), inserts a `jobs` row
   (`status=queued`), and starts `DownloadService` if it isn't running. A duplicate
   `dedupeKey` in `queued|running` warns instead of queueing twice.
2. **Claim**: `UPDATE jobs SET status='running', claimedBy=:w, heartbeatAt=:now,
   attempts=attempts+1 WHERE id=(SELECT ... ORDER BY priority DESC, createdAt LIMIT 1)`.
   Same statement shape as the desktop queue, one writer at a time.
3. **Execute** in `files/.incoming/<jobId>/`:
   `yt-dlp -o "%(id)s.%(ext)s" --write-info-json --write-thumbnail
   --convert-thumbnails jpg --no-playlist --js-runtimes quickjs`
   plus `-x --audio-format mp3 --audio-quality 0` for audio, or
   `-f "bv*[height<=1080]+ba/b[height<=1080]" --merge-output-format mp4` for video.
   Title/duration/dimensions come from the **info JSON**, never stdout. Progress
   comes from `--progress-template` only.
4. **Cover/thumb**: sidecar JPG to `files/thumbs/<prefix>/<id>.jpg`; audio cover
   embedding as a separate best-effort ffmpeg pass (desktop lesson: `--embed-thumbnail`
   is unreliable, and the pass changes file size → update `bytes` afterwards).
5. **Finalize** (order matters, as on desktop): move file into
   `files/library/<kind>/<id[0:2]>/<id>.<ext>` → insert `items` → mark job `done`.
   A `.incoming` dir is never served, so a crash mid-download shows nothing.
6. **Heartbeat** every 15 s from the worker; on service start, `running` rows with
   `heartbeatAt` older than 60 s are re-queued. Never blanket-requeue `running`
   (same regression the desktop app has a test for).
7. **Errors**: classify permanent (video unavailable, private, 404/403, unsupported
   URL) → fail immediately with a readable message; everything else retries 3× with
   exponential backoff (1 m, 4 m, 16 m). `cancelled` destroys the yt-dlp process and
   deletes `.incoming/<jobId>`.
8. **Foreground notification**: overall queue state + current item progress, with
   Cancel. The service stops itself when nothing is queued or running.

Concurrency: 2 downloads at once (configurable 1–4 in Settings). A phone on Wi-Fi
with a slow SD card does not benefit from 8. If parallel yt-dlp + QuickJS turns out
to be unsafe on a device, the worker drops to serial and the setting is capped at 1;
nothing else changes.

**Space check.** After the info JSON is read, compare `filesize_approx` (fallback
`filesize`) plus 200 MB headroom against `StatFs.availableBytes`. Too large → the job
fails at `fetching` with `Not enough space: needs ~1.2 GB, 410 MB free`. A mid-download
`ENOSPC` is classified **permanent**: retrying cannot make room.

**Network gating.** "Wi-Fi only" is evaluated at claim time through
`ConnectivityMonitor` (a `NetworkCallback` wrapper). No unmetered network → the job
stays `queued` with `stage = "waiting for Wi-Fi"` and the service stops itself rather
than holding a `dataSync` foreground service that transfers nothing. If the app is
alive when unmetered connectivity returns, the monitor starts the service again;
otherwise the next app open does. A Wi-Fi → cellular drop mid-job cancels the
subprocess, keeps `.incoming/<jobId>` (yt-dlp resumes `.part` where it can), and
re-queues **without** incrementing `attempts`. Failures that are genuinely the
network's (DNS, timeout, HTTP 5xx) count as retryable as usual.

**Boot.** Nothing resumes at boot; the queue is picked up when the app is opened.
Deliberate: a background FGS start from `BOOT_COMPLETED` is unreliable across OEMs
and the behavior is hard to test.

## 10. Screens

**Library** — segmented filter All · Audio · Video, search field, sort
(newest/oldest/title/largest/duration). Grid of cards: thumb, title, duration badge,
size only in "largest" sort. Long-press enters selection mode ("Add to playlist",
"Delete"). Empty state explains the Add flow.

**Player** — persistent mini bar (art, title, play/pause, next, queue). Tapping a card
plays it and **seeds the queue with the current filtered + sorted list**, starting at
the tapped item (desktop parity). The item menu also offers Play next and Add to
queue. The queue lives in `queue_items` plus the current index in DataStore, so it
survives process death and comes back **paused** on the next open — never auto-playing.
Tap opens `NowPlayingSheet`: large art (video gets a real surface), scrubber,
−10s/+10s, previous/next, speed, the queue sheet with drag reorder, and shuffle/repeat
buttons that do not exist in v1. Resume rule mirrors desktop: seek if `positionMs >
3 s`, reset if within 5 s of the end, saved every 5 s, on pause, and in `onStop`.
Deleting the item that is playing stops playback and removes it from the queue; the
player never holds a file handle to a deleted path.

**Playlists** — list of playlists with name, item count and a thumbnail stack; detail
is a reorderable list/grid with Play all, rename, delete, and remove-entry (long-press
or drag). Reorder writes the full ordered id list in one transaction. Playlist with
`kind = 'playlist'` only; galleries do not exist in v1.

**Add** — URL field with Paste from clipboard, `Audio | Video` segmented control,
optional playlist, Add button. Recent URLs list (last 10) so re-downloading a channel
video is two taps. Errors inline, never a raw yt-dlp dump.

**Downloads** — Active (progress bar, stage, cancel) and Finished (done/failed, error
text, retry, remove). "Clear finished" and "Clear all" behind a confirm.

**Storage** (the requested feature)

```
Library 4.21 GB
[==========|------------]  38.4 GB used of 128 GB · 82.7 GB free

Audio        2.11 GB   612 items   50%
Video        2.06 GB    48 items   49%
Thumbnails     38 MB   660 files    1%
Temp            0 B

Largest items                      size   share
[art] Long mix — 3:42:11         410 MB   9.5%   ⋮
[art] Concert 4K                 1.2 GB  28.3%   ⋮
...
```

- Top number = `SUM(items.bytes)` + thumbs + `.incoming`, i.e. what MediaManager uses.
- Bar = `StatFs` on the library volume: used / total, free in words.
- Breakdown by kind, then thumbnails and temp, each with count and share.
- Item list sorted by `bytes` desc (toggle newest/largest), each row shows size and
  % of library; tap → play, ⋮ → delete (deletes the file, the row and its playlist
  memberships; confirms with the exact MB freed), add to playlist.
- All values are `Flow`s off the DB plus one `StatFs` read on resume; no tree walk.
- `Temp` is the one measured number: the sum of `filesDir/.incoming/*` file sizes, read
  on screen open on `Dispatchers.IO` (one directory level, bounded by queue length)
  and shown as "measuring…" until it lands. Thumbnails come from
  `SUM(items.thumbBytes)`, written at finalize.
- A "Re-check" action recomputes sizes from disk for items whose file is missing or
  whose bytes differ (reads `File.length()` per row, off the main thread), and flags
  orphans in `.incoming` / `thumbs` with a "Clean up" button.

**Settings** — Appearance (theme family, light/dark/system), Downloads (concurrency,
max video height 720/1080/1440/best, Wi-Fi only, auto-retry), yt-dlp version +
"Update yt-dlp" (the library's update channel), Storage defaults, About (version,
GPL-3.0 notice, yt-dlp/ffmpeg attribution).

## 11. Theme

Port `apps/web/src/app/globals.css` tokens 1:1 into a Compose `ColorScheme` plus the
non-Material extras (`canvas`, `panel`, `panel2`, `line`, `lineStrong`, `ink`,
`muted`, `accent`, `accentInk`, `danger`). Four families (`slate`, `paper`, `ocean`,
`moss`) × light/dark, stored in DataStore (`mm.appearance`), read
before first composition so there is no flash. Space Grotesk + JetBrains Mono bundled
as `.ttf` in `res/font`; Noto Sans Arabic UI too, so Arabic titles don't fall back to
a poor system font. No webfont download at runtime.

## 12. Left to the builder

- Exact Gradle/AGP versions, version catalog contents, and dependency pins.
- Whether `DownloadService` drives `YtDlpRunner` on `Dispatchers.IO` with one
  subprocess at a time per slot, or a small process pool — either satisfies §9.
- Whether the queue sheet and Now Playing share one sheet or two.
- Icons (Material Symbols set, no custom art beyond the existing app mark).
- Whether to expose a `share`/export action later and how (MediaStore insert into
  `Downloads/`); explicitly out of v1.

## 13. Verification

- **Unit (JVM, no device)**: `LibraryPaths` sharding + traversal guard, dedupe key,
  error classification, progress parsing, resume-threshold rule, storage share math,
  job lease expiry.
- **Room**: in-memory DB tests via Robolectric for DAO queries: newest cursor, largest
  sort, aggregate sizes per kind, playlist reorder, cascade on delete.
- **Compose UI**: Robolectric/Robolectric-free screenshot-free tests for the Add form
  validation, Storage numbers rendered, selection mode.
- **Build gate**: `./gradlew assembleDebug testDebugUnitTest lint` must pass. This
  machine has no toolchain yet; installing Temurin 21 + Android cmdline-tools +
  `platforms;android-36` + `build-tools` is the first step (~10 GB, 20–30 min). No
  emulator is required for the gate; a physical device is needed for the downloader
  and playback smoke test.
- **Device smoke** (manual, documented in `apps/mobile/README.md`): paste a URL →
  audio appears with cover; video appears and seeks; kill the app mid-download →
  job resumes; Airplane mode → job fails with a readable message and retries; delete
  the largest item → Storage total drops by exactly its size.

## 14. Risks

| Risk | Mitigation |
|---|---|
| YouTube extraction breaks (JS runtime, signature changes) | In-app yt-dlp update, QuickJS runtime, error text that names the cause, and a Settings page showing the extractor version. |
| 6-hour `dataSync` FGS cap on Android 15+ | Jobs are rows; a killed service re-claims and resumes. Warn in Downloads when a queue is very long. |
| APK size | `abiFilters` + `splits` arm64-v8a only for personal installs; no universal APK unless asked. |
| GPL-3.0 obligations | License + attribution in About; repo stays GPL-3.0-compatible. |
| Phone storage pressure | Storage screen + per-item delete is the answer; no silent eviction in v1. |
