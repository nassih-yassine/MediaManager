# AGENTS.md

A standalone Kotlin/Compose app: own Room DB, own library in app-private storage, no
server, no account, no sync. Everything below is a rule that has cost time.

## Commands (verified on this machine)

```sh
make build      # assembleDebug
make test       # testDebugUnitTest (unit + Robolectric)
make check      # test + lint
make fmt        # ktlint format
make hooks      # install the pre-push hook (ktlint + Android lint)
make coverage   # unit-test coverage (JaCoCo), HTML under androidApp/build/reports/coverage
make install    # installs the debug APK if the phone has room
make release    # signed release APK
```

Single test from the repo root:

```sh
./gradlew testDebugUnitTest --tests "dev.mediamanager.mobile.download.JobQueueTest"
```

Build env: JDK 21 (`JAVA_HOME`), Android SDK (`ANDROID_HOME` or `local.properties`).
`gradle.properties` must not pin a machine-specific `org.gradle.java.home`; CI and
other machines cannot resolve it.

## Architecture rules that are not obvious

- **Kotlin style is enforced.** ktlint 1.8 via `.editorconfig` (`make fmt`); the
  pre-push hook (`.githooks/pre-push`, installed automatically by `settings.gradle.kts`)
runs `ktlintCheck` and `lintDebug`. The
  opinionated `function-signature`/`class-signature`/chain rules are disabled because
  they fight Compose. Do not re-enable them without reformatting the whole app.

- **Room is the only source of truth.** The UI reads flows; nothing caches state as
  the authority. `RETURNING` is banned — `minSdk 26` ships SQLite 3.18, so claim and
  reorder are `@Transaction` select-then-update.
- **Jobs are rows.** `DownloadService` claims with a lease, heartbeats every 15 s, and
  `reapExpired` re-queues rows whose `heartbeat_at` is stale. Startup recovery must
  only requeue **expired leases**; a blanket `running -> queued` steals jobs from live
  workers, and there is a regression test.
- **Finalize order:** `.incoming/<jobId>/` → rename into the library → INSERT items →
  mark the job done. Deleting the cancel path here leaves orphan files.
- **Storage is sharded:** `<kind>/<id[0:2]>/<id>.<ext>`, thumbs in `thumbs/<prefix>/`.
- **One media element only.** `PlayerProvider` keeps a single `<video>`/ExoPlayer alive
  across navigation. A second instance causes double playback.
- **Playback state lives in the browser… or rather, on disk.** Resume points are in
  `items.position_ms`, saved every ~8 s, on pause, on end (reset to 0) and on
  `onStop`. The play queue lives in `queue_items`.
- **Resume is a long-form affordance.** Under ten minutes an item always starts at 0;
  at or above it, `playItems` holds on the first frame and asks (Resume / Start over).
- **The widget never binds a service.** A `BroadcastReceiver` cannot bind; transport is
  forwarded to `PlaybackService` as a start command.
- **Gallery-style collections are playlists** (`kind='playlist'`); there is no second
  table. The API/UI reuse `PlaylistMenu` everywhere.

## Downloads

- **YouTube uses NewPipe Extractor**, everything else the bundled yt-dlp. yt-dlp 403s
  on YouTube anonymously; NewPipe does not need a JS runtime or PO token.
- **Only `deno` is enabled by default in yt-dlp and deno is not installed here.** Every
  invocation passes `--no-js-runtimes --js-runtimes node` or YouTube extraction fails.
- **Title/duration/dimensions come from `--write-info-json`**, not stdout parsing. Only
  progress comes from `--progress-template`.
- **Most YouTube videos have no muxed stream.** Download video-only + audio in
  parallel, then remux with `MediaMuxer` (MP4), falling back to WebM and finally the
  bundled ffmpeg binary.
- **Parallel ranged downloads.** Files over 1.2 MB are split across 2–8 connections
  (`length / 1.5 MB`). A single connection to googlevideo is throttled to a few
  hundred KB/s, which is why short tracks must not fall back to the serial path.
- **The sidecar JPG is the required cover artifact.** `--embed-thumbnail` is not
  reliable; the best-effort `ffmpeg` pass changes the file size, so update `bytes`
  afterwards. `ffmpeg` writing a single image needs `-update 1`.
- **Permanent errors fail immediately** (video unavailable, 404/403, private);
  everything else retries up to 5 attempts with exponential backoff. Network drops
  give the attempt back and retry in 15 s.
- **Deleting an item with a job** needs `jobs.item_id ON DELETE SET NULL`; there is a
  regression test (`deletes_an_item_that_has_a_job`).
- **A URL already in the library is refused**, and the dedupe check plus insert share
  one transaction so two taps cannot queue twice.

## Android specifics

- Compose + Room + Media3 + DataStore. One `AppContainer` built in `App`; no Hilt.
  Single activity, screens are composables over DAO flows.
- `DownloadService` is a `dataSync` foreground service; `PlaybackService` is a
  `MediaSessionService` holding the only ExoPlayer. Opening the app restarts the
  download service when work is pending (OEM battery managers kill it).
- Android 15+ caps `dataSync` foreground services at 6 h/day, and there is no boot
  receiver: the queue resumes when the app is opened. Both are deliberate.
- Storage tab is the feature people ask for: library total, `StatFs` device bar,
  breakdown by kind, thumbnails, measured temp, items ranked by size. Sizes come from
  the DB, not from walking the tree.
- Bundled yt-dlp makes the app GPL-3.0. Keep the licence notice in Settings → About.
- `Modifier.blur` requires API 31+. Compose cannot blur content behind a view, so the
  mini player uses translucency, a shadow and a hairline instead.

## UI

- Themes are tokens in `ui/theme/Tokens.kt`: `canvas`, `panel`, `panel-2`, `line`,
  `ink`, `muted`, `accent`, `accent-ink`, `danger`, six families × light/dark.
- Typography: Space Grotesk with the bundled Arabic face as a fallback in the same
  family, JetBrains Mono for numbers. No runtime font download, so builds work offline.
- Popovers must be portaled: `PlaylistMenu`, `ThemePicker`, `SelectMenu` render into
  `document`/`Dialog` with fixed coordinates. Anything inside a card or a scrolling
  column gets clipped.
- Confirmation is a themed sheet/card with one full-width action, not Material's footer
  buttons. The confirm tint follows the accent.
- Progress is a track-and-fill bar; Material's `LinearProgressIndicator` draws a stop
  dot beside the fill.
- Keyboard/gesture affordances stay: tap a row to play, drag the player down to the
  mini player, × removes a queue entry.
- **Width chooses navigation, height chooses density.** A landscape phone is ~891×411 dp
  (expanded by width, shorter than any portrait phone), so the rail applies to every
  window wider than COMPACT, and the player goes side-by-side on any wide window.
  Stacking artwork above the panel in landscape leaves the transport below the fold;
  `WindowInfoTest` guards the breakpoints.

## Gotchas that already cost time

- **Never re-add a second player surface.** Video and audio share one big player; video
  fills the artwork slot with a `PlayerView` keyed on the `exo` instance, so a service
  restart rebinds it.
- **`StateFlow` replays its current value.** A collector that opens the player on
  `openRequests` must `drop(1)`, or the player opens on every cold start and hides the
  mini player.
- **A `BroadcastReceiver` cannot bind a service.** `ReceiverCallNotAllowedException`
  kills the process; use a start command instead.
- **`pkill -f target/debug/mm` is gone; this is Android now.** For the phone use
  `pgrep -x mm`, or `adb shell am force-stop dev.mediamanager.mobile`.
- **`make_jpeg_thumb` must copy JPEGs, never rename.** Renaming moved the source into
  `thumbs/`, so covers worked and the full image 404'd. Regression test:
  `jpeg_thumbnail_keeps_the_source`.
- If a screen sits on "Loading…", check for 404 chunks (a stale web build) — no longer
  applicable; the Android equivalent is a DB open failure, which the error banner shows.
- Port 3000/3001 belong to the old web app and are irrelevant here.

## Tests

- Unit tests are `app/src/test/**` with Robolectric where DB/context is needed. Bug
  fixes go test-first; a fix without a reproducing test is incomplete.
- Tests that matter: `DownloadRulesTest` (error classification, resume threshold,
  space check), `JobQueueTest` (backoff, lease reaping, dedupe, cancel), 
  `DownloadPipelineTest` (finalize order, no orphan file on insert failure),
  `StorageRepositoryTest` (aggregates, share math), `DaoTest` (cascades), 
  `ResumeRulesTest` (short media always restarts).

## Release

- `scripts/install-apk.sh` refuses an install that will not fit: APK size + 20 % +
  150 MB headroom, and it names the shortfall.
- Release signing reads `MM_STORE_FILE`, `MM_STORE_PASSWORD`, `MM_KEY_ALIAS`,
  `MM_KEY_PASSWORD`. Keep the keystore out of the repo; without it the build falls back
  to the debug key.
- `versionName` is `<tag>.<commit count>` from git; `versionCode` is `10000 + commits`.
  Never reset either — installed builds must be upgradeable.
- Tag `android-v*` builds a release APK in CI and attaches it to a draft release.
- The app is arm64-only (`splits.abi.include("arm64-v8a")`); 32-bit devices cannot
  install it.
