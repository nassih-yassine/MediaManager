# MediaManager Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A standalone Kotlin/Compose Android app that downloads YouTube (yt-dlp) URLs as audio or video into an on-device library, plays them, and reports exactly how much phone storage the library uses.

**Architecture:** Single Gradle module. Room is the only source of truth (items, jobs, playlists, queue). A foreground service runs a durable job queue over the bundled yt-dlp; Media3 owns playback; Compose screens read Room `Flow`s. No server, no accounts, no sync.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), Room, Media3/ExoPlayer + MediaSessionService, Coil, DataStore Preferences, `io.github.junkfood02.youtubedl-android:library` + `:ffmpeg` 0.18.1, Robolectric + JUnit4 for JVM tests.

**Spec:** `docs/superpowers/specs/2026-11-15-android-app-design.md`

## Global Constraints

- `applicationId "dev.mediamanager.mobile"`, `minSdk 26`, `compileSdk 36`, `targetSdk 36`, Java/Kotlin target 17.
- DB kind values are `"audio"` and `"video"`; UI labels are Audio and Video. No other kinds in v1.
- Library root is `context.filesDir`; media at `library/<kind>/<id[0:2]>/<id>.<ext>`, thumbs at `thumbs/<id[0:2]>/<id>.jpg`, temp at `.incoming/<jobId>/`. All paths built only through `LibraryPaths`.
- `android:allowBackup="false"`, no `MANAGE_EXTERNAL_STORAGE`, no `READ_MEDIA_*`. Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`.
- No `RETURNING` in SQL (SQLite 3.18 on API 26). Use `@Transaction` methods.
- Job finalize order is fixed: move file → insert `items` → mark job `done`.
- No Hilt, no Koin, no Retrofit, no WorkManager. One `AppContainer` in `Application`.
- Every screen is a Compose screen reading a `StateFlow` from a plain ViewModel with a factory; no fragment layer.
- All user-facing sizes come from `formatBytes`; no ad-hoc formatting.
- App is GPL-3.0; license text ships in `res/raw/gpl3.txt` and is linked from Settings → About.

## Review Focus

1. App killed mid-download (swipe away, reboot): the library shows nothing partial and the job resumes on next open.
2. A URL yt-dlp cannot take (private, unavailable, typo, unsupported site): one readable message, no crash, no infinite retry.
3. Adding the same URL twice: at most one active job; the second attempt says so instead of downloading twice.
4. Deleting the item that is currently playing: playback stops, it leaves the queue and playlists, the file is gone, Storage's total drops by exactly its size.
5. Storage after a file disappears underneath the app: Re-check flags it, totals never go negative, deleting it does not throw.

---

### Task 1: Build skeleton, toolchain, byte formatting

**Files:**
- Create: `apps/mobile/settings.gradle.kts`, `apps/mobile/build.gradle.kts`, `apps/mobile/gradle.properties`, `apps/mobile/gradle/libs.versions.toml`, `apps/mobile/app/build.gradle.kts`, `apps/mobile/local.properties` (git-ignored), `apps/mobile/.gitignore`
- Create: `apps/mobile/app/src/main/AndroidManifest.xml`
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/App.kt`, `MainActivity.kt`, `AppContainer.kt`
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/format/ByteFormat.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/format/ByteFormatTest.kt`

**Interfaces:**
- Produces: `fun formatBytes(bytes: Long): String` — 1024-based, `"412 KB"`, `"4.2 MB"`, `"412 MB"`, `"4.21 GB"`; `AppContainer(appContext: Context)` with lazily created `db`, `paths`, `settings`.
- Consumes: nothing.

- [ ] **Step 1: Scaffold the Gradle project**

`settings.gradle.kts` includes `:app` and `dependencyResolutionManagement { repositories { google(); mavenCentral() } }`.
`app/build.gradle.kts`: `compileSdk 36`, `minSdk 26`, `targetSdk 36`, `buildFeatures { compose = true }`, Compose Compiler Gradle plugin, `testOptions { unitTests.isIncludeAndroidResources = true }`.
`local.properties` contains `sdk.dir=/home/iomi/Android/Sdk` and is added to `.gitignore`.
`gradle.properties` contains `org.gradle.java.home=/home/iomi/.local/opt/jdk-21.0.13+9` and `android.useAndroidX=true`.
`libs.versions.toml` pins: Kotlin, AGP, Compose BOM, Room (+KSP), Media3, Coil, DataStore, Robolectric, coroutines-test. Take the versions AGP/Kotlin resolve cleanly on first build and record them in this file; do not leave `+` ranges.
Manifest: single `MainActivity`, `allowBackup="false"`, `theme` from `res/values/themes.xml`, permissions listed in Global Constraints.

- [ ] **Step 2: Write the failing test**

```kotlin
class ByteFormatTest {
    @Test fun `bytes under a megabyte stay in KB`() =
        assertEquals("412 KB", formatBytes(421_888))
    @Test fun `megabytes below ten keep one decimal`() =
        assertEquals("4.2 MB", formatBytes(4_404_019))
    @Test fun `megabytes above ten are whole`() =
        assertEquals("412 MB", formatBytes(432_013_312))
    @Test fun `gigabytes keep two decimals`() =
        assertEquals("4.21 GB", formatBytes(4_520_710_144))
    @Test fun `zero is zero`() = assertEquals("0 B", formatBytes(0))
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*ByteFormatTest*'`
Expected: FAIL — `formatBytes` unresolved.

- [ ] **Step 4: Implement `formatBytes` and the empty `MainActivity`**

`MainActivity` sets Compose content to a centered `Text("MediaManager")`. `App` creates `AppContainer` once. No theme yet.

- [ ] **Step 5: Run the test, then the build gate**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (universal before splits are added in Task 9).

- [ ] **Step 6: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): Android project skeleton with byte formatting"
```

---

### Task 2: Theme tokens and appearance storage

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/theme/Tokens.kt`, `Theme.kt`, `Type.kt`
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/data/SettingsStore.kt`
- Create: `apps/mobile/app/src/main/res/font/space_grotesk.ttf`, `jetbrains_mono.ttf`, `noto_sans_arabic_ui.ttf` (copied from `apps/web/public/fonts` where available)
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/theme/TokensTest.kt`, `apps/mobile/app/src/test/java/dev/mediamanager/mobile/data/SettingsStoreTest.kt`

**Interfaces:**
- Produces: `enum class ThemeFamily { SLATE, PAPER, OCEAN, MOSS }`, `enum class ThemeMode { LIGHT, DARK, SYSTEM }`, `data class AppColors(canvas, panel, panel2, line, lineStrong, ink, muted, accent, accentInk, danger)`, `fun colorsFor(family: ThemeFamily, dark: Boolean): AppColors`, `@Composable fun MmTheme(colors: AppColors, content: @Composable () -> Unit)` exposing `AppColors` via a `LocalAppColors` CompositionLocal.
- Produces: `class SettingsStore(context: Context)` with `val appearance: Flow<Pair<ThemeFamily, ThemeMode>>` and `suspend fun setAppearance(family, mode)`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing tests**

```kotlin
class TokensTest {
    @Test fun `slate dark accent matches web tokens`() =
        assertEquals(0xFF9AE6B4, colorsFor(ThemeFamily.SLATE, true).accent)
    @Test fun `slate light accent matches web tokens`() =
        assertEquals(0xFF0F7A4A, colorsFor(ThemeFamily.SLATE, false).accent)
    @Test fun `every family has a distinct canvas in dark mode`() =
        assertEquals(4, ThemeFamily.entries.map { colorsFor(it, true).canvas }.toSet().size)
    @Test fun `ink contrasts with canvas in all eight combinations`() { /* luminance delta > 0.4 */ }
}

class SettingsStoreTest {
    @Test fun `appearance defaults to slate and system`() = runTest { /* first read */ }
    @Test fun `setAppearance round-trips`() = runTest { /* moss + light */ }
}
```

Copy the remaining token values from `apps/web/src/app/globals.css` (`--panel`, `--panel-2`, `--line`, `--line-strong`, `--muted`, `--accent-ink`, `--danger`) for all four families × both modes. `panel2` is the Compose name for `--panel-2`.

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*TokensTest*' --tests '*SettingsStoreTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement tokens, `MmTheme`, `SettingsStore`**

`MmTheme` builds a Material 3 `darkColorScheme`/`lightColorScheme` from `AppColors` (surface = panel, background = canvas, primary = accent, onPrimary = accentInk, error = danger) and provides `LocalAppColors`. `SettingsStore` uses Preferences DataStore named `mm.settings`, keys `theme_family`, `theme_mode`, default `slate`/`system`. `Type.kt` declares `MmTypography` with Space Grotesk for body/title and JetBrains Mono for numeric styles.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*TokensTest*' --tests '*SettingsStoreTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): port theme tokens and appearance storage"
```

---

### Task 3: Library paths and traversal guard

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/media/LibraryPaths.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/media/LibraryPathsTest.kt`

**Interfaces:**
- Produces: `class LibraryPaths(private val root: File)` with `val libraryDir: File`, `fun itemFile(kind: String, id: String, ext: String): File`, `fun itemRelativePath(kind: String, id: String, ext: String): String`, `fun thumbFile(id: String): File`, `fun thumbRelativePath(id: String): String`, `fun incomingDir(jobId: String): File`, `fun resolve(relative: String): File?` (null when the path escapes `root` or is absolute).
- Consumes: nothing.

- [ ] **Step 1: Write the failing tests**

```kotlin
class LibraryPathsTest {
    private val paths = LibraryPaths(tempDir("mm"))

    @Test fun `items are sharded by id prefix`() {
        val f = paths.itemFile("audio", "ab12cd34", "mp3")
        assertEquals("audio/ab/ab12cd34.mp3", paths.itemRelativePath("audio", "ab12cd34", "mp3"))
        assertTrue(f.path.endsWith("library/audio/ab/ab12cd34.mp3"))
    }

    @Test fun `thumbs use the same shard`() {
        assertTrue(paths.thumbFile("ab12").path.endsWith("thumbs/ab/ab12.jpg"))
    }

    @Test fun `resolve rejects traversal and absolute paths`() {
        assertNull(paths.resolve("../../etc/passwd"))
        assertNull(paths.resolve("/etc/passwd"))
        assertNull(paths.resolve("audio/../../x.mp3"))
        assertNotNull(paths.resolve("audio/ab/ab12.mp3"))
    }

    @Test fun `resolve canonicalises a sibling prefix`() {
        // root=/tmp/x, path=/tmp/xy/… must not pass
        assertNull(paths.resolve("audio/../../xy/a.mp3"))
    }

    @Test fun `incoming dir is per job`() {
        assertTrue(paths.incomingDir("job-1").path.endsWith(".incoming/job-1"))
    }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*LibraryPathsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `LibraryPaths`**

`resolve` does `File(root, relative).canonicalFile` and requires `path.startsWith(root.canonicalPath + File.separator)` — the separator matters, it is the `x` vs `xy` bug. `itemRelativePath` returns `library/<kind>/<id.take(2)>/<id>.<ext>`, thumbs `thumbs/<id.take(2)>/<id>.jpg`, and both reject an `id` shorter than 2 chars with `require`.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*LibraryPathsTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): sharded library paths with traversal guard"
```

---

### Task 4: Room schema and DAOs

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/data/Entities.kt`, `AppDatabase.kt`, `ItemDao.kt`, `JobDao.kt`, `PlaylistDao.kt`, `QueueDao.kt`, `Converters.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/data/DaoTest.kt`

**Interfaces:**
- Produces: entities exactly as in spec §8 (`ItemEntity` with `thumbBytes`, `available`; `JobEntity`; `PlaylistEntity`; `PlaylistItemEntity`; `QueueItemEntity`).
- Produces `ItemDao`: `fun observeAll(kind: String?): Flow<List<ItemEntity>>`, `fun observeLargest(limit: Int): Flow<List<ItemEntity>>`, `suspend fun search(q: String): List<ItemEntity>`, `suspend fun get(id: String): ItemEntity?`, `suspend fun insert(item: ItemEntity)`, `suspend fun updatePosition(id, positionMs, playedAt)`, `suspend fun delete(id: String)`, `fun observeTotalsByKind(): Flow<List<KindTotal>>` (`data class KindTotal(kind: String, bytes: Long, count: Int)`), `fun observeTotalBytes(): Flow<Long>`, `fun observeThumbBytes(): Flow<Long>`, `suspend fun setAvailable(id, available)`.
- Produces `PlaylistDao`: create/rename/delete, `observePlaylists()`, `observeItems(playlistId)`, `addItems(playlistId, itemIds)`, `removeItem`, `reorder(playlistId, orderedIds)`, `playlistsContaining(itemId)`.
- Produces `QueueDao`: `observeQueue()`, `replaceAll(itemIds)`, `removeAt(position)`, `move(from, to)`.
- Consumes: Task 1 (`AppContainer`), Task 3 (no).

- [ ] **Step 1: Write the failing tests**

```kotlin
@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }

    @Test fun `newest first and kind filter`() = runTest { /* insert 3, assert order + audio-only */ }
    @Test fun `largest sort`() = runTest { /* bytes desc */ }
    @Test fun `search is case-insensitive contains`() = runTest { /* "mix" finds "Long MIX" */ }
    @Test fun `totals group by kind`() = runTest { /* audio 2 items, video 1 — exact byte sums */ }
    @Test fun `deleting an item drops playlist and queue rows`() = runTest { /* cascade */ }
    @Test fun `reorder persists the given order`() = runTest { /* 3 ids reversed */ }
    @Test fun `adding the same item to a playlist twice is a no-op`() = runTest { /* count stays 1 */ }
}
```

Fill each with concrete ids, byte values and assertions (`assertEquals(listOf("c","b","a"), ids)`), not comments alone.

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DaoTest*'`
Expected: FAIL — `AppDatabase` unresolved.

- [ ] **Step 3: Implement entities, DAOs, database**

Version 1, `exportSchema = true` with schema dir `app/schemas`, `fallbackToDestructiveMigration` **off**. Search query: `SELECT * FROM items WHERE title LIKE '%' || :q || '%' ORDER BY createdAt DESC`. Mappers in `Converters.kt` convert entity → `Item` UI model (`data class Item(id, kind, title, sizeBytes, durationMs, thumbPath, available, positionMs)`).

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DaoTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): Room schema, DAOs and aggregates"
```

---

### Task 5: Item deletion (row, file, memberships)

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/media/ItemRepository.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/media/ItemRepositoryTest.kt`

**Interfaces:**
- Produces: `class ItemRepository(db: AppDatabase, paths: LibraryPaths)` with `suspend fun delete(id: String): DeletedItem` (`data class DeletedItem(id, freedBytes, wasPlaying: Boolean)`), `suspend fun recheck(): RecheckResult` (`data class RecheckResult(missing: List<String>, resized: List<Pair<String, Long>>)`).
- Consumes: Task 3 `paths`, Task 4 DAOs.

- [ ] **Step 1: Write the failing tests**

```kotlin
class ItemRepositoryTest {
    @Test fun `delete removes row, file, thumb and memberships`() = runTest {
        // 1.2 MB file + thumb exist; playlist and queue contain the item
        val result = repo.delete("id01")
        assertEquals(1_200_000L, result.freedBytes)
        assertFalse(paths.itemFile("audio", "id01", "mp3").exists())
        assertFalse(paths.thumbFile("id01").exists())
        assertNull(db.itemDao().get("id01"))
        assertTrue(db.playlistDao().playlistsContaining("id01").isEmpty())
    }

    @Test fun `delete of an already missing file still removes the row`() = runTest { /* freedBytes == 0 */ }

    @Test fun `recheck flags missing files and wrong sizes`() = runTest {
        // one item with a file, one deleted underneath, one with a grown file
        assertEquals(listOf("gone"), result.missing)
        assertEquals(listOf("grown" to 900L), result.resized)
        assertFalse(db.itemDao().get("gone")!!.available)
    }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*ItemRepositoryTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Delete order: read row → delete DB row (cascades) → delete files → return freed bytes = file length + thumb length measured before deletion. Missing files are not an error. `recheck` iterates rows on `Dispatchers.IO`, calls `File.length()`, sets `available=false` for missing, and updates `bytes` for size drift.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*ItemRepositoryTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): item deletion and storage re-check"
```

---

### Task 6: Download domain rules (pure)

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/download/Dedupe.kt`, `ErrorClassifier.kt`, `ProgressParser.kt`, `SpaceCheck.kt`, `ResumeTarget.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/download/DownloadRulesTest.kt`

**Interfaces:**
- Produces: `fun dedupeKey(url: String, mode: DownloadMode): String` where `enum class DownloadMode { AUDIO, VIDEO }`; `sealed interface Failure { data class Permanent(val message: String) : Failure; data class Retryable(val message: String) : Failure }`; `fun classify(output: String): Failure`; `fun parseProgress(line: String): Float?`; `sealed interface SpaceVerdict { object Fits; data class TooLarge(neededBytes: Long, freeBytes: Long) }`; `fun checkSpace(needed: Long?, free: Long, headroom: Long = 200L * 1024 * 1024): SpaceVerdict`; `fun resumeTarget(positionMs: Long, durationMs: Long?): Long`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing tests**

```kotlin
class DownloadRulesTest {
    @Test fun `dedupe key ignores whitespace and host case`() {
        assertEquals(dedupeKey("https://youtu.be/x", AUDIO), dedupeKey(" https://youtu.be/x ", AUDIO))
        assertNotEquals(dedupeKey("https://youtu.be/x", AUDIO), dedupeKey("https://youtu.be/x", VIDEO))
    }

    @Test fun `unavailable and private are permanent`() {
        assertIs<Permanent>(classify("ERROR: [youtube] abc: Video unavailable"))
        assertIs<Permanent>(classify("ERROR: Private video. Sign in if you've been granted access"))
        assertIs<Permanent>(classify("ERROR: Unsupported URL: https://example.com/x"))
        assertIs<Permanent>(classify("ERROR: HTTP Error 404: Not Found"))
    }

    @Test fun `network trouble is retryable`() {
        assertIs<Retryable>(classify("ERROR: Unable to download webpage: <urlopen error timed out>"))
        assertIs<Retryable>(classify("ERROR: HTTP Error 503: Service Unavailable"))
    }

    @Test fun `no space left on device is permanent`() =
        assertIs<Permanent>(classify("OSError: [Errno 28] No space left on device"))

    @Test fun `progress template parses percent`() {
        assertEquals(0.42f, parseProgress("[download]  42.3% of 10.00MiB at 1.2MiB/s ETA 00:05")!!, 0.001f)
        assertNull(parseProgress("[download] Destination: /x/a.mp4"))
    }

    @Test fun `space check keeps 200 MB headroom`() {
        assertIs<Fits>(checkSpace(needed = 1_000, free = 1_000_000_000))
        assertIs<TooLarge>(checkSpace(needed = 5_000_000_000, free = 4_000_000_000))
        assertIs<Fits>(checkSpace(needed = null, free = 1_000))   // unknown size → try anyway
    }

    @Test fun `resume seeks only past three seconds and away from the end`() {
        assertEquals(0L, resumeTarget(1_000, 100_000))
        assertEquals(30_000L, resumeTarget(30_000, 100_000))
        assertEquals(0L, resumeTarget(98_000, 100_000))
        assertEquals(30_000L, resumeTarget(30_000, null))
    }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DownloadRulesTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`classify` matches lowercased substrings in spec order: `no space left` → permanent; `video unavailable`, `private video`, `http error 404/403`, `unsupported url`, `member-only`, `sign in to confirm` → permanent; `timed out`, `temporary failure`, `http error 5`, `connection reset`, `unable to download webpage` → retryable; default retryable with the last `ERROR:` line as the message.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DownloadRulesTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): download classification, progress and space rules"
```

---

### Task 7: Durable job queue (claim, heartbeat, reap)

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/download/JobQueue.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/download/JobQueueTest.kt`

**Interfaces:**
- Produces: `class JobQueue(private val dao: JobDao, private val clock: () -> Long = System::currentTimeMillis)` with `suspend fun enqueue(url: String, mode: DownloadMode): EnqueueResult` (`data class EnqueueResult(val job: JobEntity, val duplicate: Boolean)`), `suspend fun claim(workerId: String): JobEntity?`, `suspend fun heartbeat(id: String)`, `suspend fun finish(id: String, itemId: String)`, `suspend fun fail(id: String, error: String, permanent: Boolean): Boolean` (true = will retry), `suspend fun requeue(id: String, keepAttempts: Boolean = true)`, `suspend fun cancel(id: String)`, `suspend fun reapExpired(): Int`.
- Consumes: Task 4 `JobDao`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class JobQueueTest {
    @Test fun `claim takes the oldest queued job and increments attempts`() = runTest { /* a then b */ }
    @Test fun `claim skips jobs scheduled in the future`() = runTest { /* backoff row */ }
    @Test fun `duplicate active url returns the existing job flagged`() = runTest {
        val first = queue.enqueue("https://youtu.be/x", AUDIO)
        val second = queue.enqueue("https://youtu.be/x", AUDIO)
        assertTrue(second.duplicate); assertEquals(first.job.id, second.job.id)
    }
    @Test fun `same url different mode is a new job`() = runTest { /* VIDEO not duplicate */ }
    @Test fun `retryable failure backs off and stays queued`() = runTest {
        // attempts 1 → scheduledAt = now + 60s, status queued
    }
    @Test fun `permanent failure fails immediately`() = runTest { /* status failed, error kept */ }
    @Test fun `third retryable failure becomes failed`() = runTest { /* attempts == maxAttempts */ }
    @Test fun `reap only requeues stale running jobs`() = runTest {
        // fresh heartbeat untouched, 61s-old heartbeat requeued with same attempt count
    }
    @Test fun `cancel marks cancelled and a cancelled job is never claimed`() = runTest { }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*JobQueueTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`claim` runs in a `@Transaction` on the DAO: select the first `queued` row with `scheduledAt <= now` ordered by `priority DESC, createdAt`, then update it to `running`, `claimedBy=workerId`, `attempts+1`, `heartbeatAt=now`. No `RETURNING`. Backoff = `60_000L shl (attempts - 1)` (1 m, 2 m, 4 m). `reapExpired` requeues `running` rows with `heartbeatAt < now - 60_000` **without** changing `attempts`.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*JobQueueTest*'`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): durable job queue with claim, backoff and lease reaping"
```

---

### Task 8: yt-dlp arguments and info-JSON parsing

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/download/YtDlpArgs.kt`, `MediaInfo.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/download/YtDlpArgsTest.kt`, `MediaInfoTest.kt`

**Interfaces:**
- Produces: `object YtDlpArgs` with `fun audio(url: String, outDir: File, jsRuntime: String? = "quickjs"): List<String>` and `fun video(url: String, outDir: File, maxHeight: Int, jsRuntime: String? = "quickjs"): List<String>`; `fun parseInfo(json: String): MediaInfo?` where `data class MediaInfo(title: String, durationMs: Long?, ext: String, width: Int?, height: Int?, approxBytes: Long?)`; `fun findMediaFile(dir: File, ext: String): File?`, `fun findThumbFile(dir: File): File?`.
- Consumes: Task 6 `DownloadMode` (not directly).

- [ ] **Step 1: Write the failing tests**

```kotlin
class YtDlpArgsTest {
    private val dir = File("/tmp/job1")

    @Test fun `audio extracts mp3 with cover and info json`() {
        val a = YtDlpArgs.audio("https://youtu.be/x", dir)
        assertTrue(a.containsAll(listOf("-x", "--audio-format", "mp3", "--audio-quality", "0")))
        assertTrue(a.containsAll(listOf("--write-info-json", "--write-thumbnail", "--convert-thumbnails", "jpg")))
        assertTrue(a.containsAll(listOf("--no-playlist", "--js-runtimes", "quickjs")))
        assertTrue(a.containsAll(listOf("-o", "/tmp/job1/%(id)s.%(ext)s")))
        assertEquals("https://youtu.be/x", a.last())
    }

    @Test fun `video caps height and merges to mp4`() {
        val a = YtDlpArgs.video("https://youtu.be/x", dir, maxHeight = 1080)
        assertTrue(a.containsAll(listOf("-f", "bv*[height<=1080]+ba/b[height<=1080]")))
        assertTrue(a.containsAll(listOf("--merge-output-format", "mp4")))
        assertFalse(a.contains("-x"))
    }

    @Test fun `no js runtime flag when null`() { assertFalse(YtDlpArgs.audio("u", dir, null).contains("--js-runtimes")) }
}

class MediaInfoTest {
    @Test fun `parses title, duration, dimensions and estimate`() { /* real yt-dlp info json fixture */ }
    @Test fun `returns null for junk`() { assertNull(parseInfo("not json")) }
    @Test fun `picks the media and thumb file by extension`() { /* dir with x.mp3, x.jpg, x.info.json */ }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*YtDlpArgsTest*' --tests '*MediaInfoTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Args always include `--no-playlist`, `--newline`, `--no-warnings`, `--socket-timeout 20`, `--retries 2`, `--write-info-json`, the output template, and the JS runtime flag only when non-null. Parse with `org.json.JSONObject`; `duration` seconds → ms; `filesize_approx` else `filesize`.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*YtDlpArgsTest*' --tests '*MediaInfoTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): yt-dlp argument builder and info parsing"
```

---

### Task 9: Extractor wrapper and pipeline finalize

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/download/Extractor.kt`, `YtDlpExtractor.kt`, `DownloadPipeline.kt`, `Thumbnailer.kt`
- Modify: `apps/mobile/app/build.gradle.kts` (add `youtubedl-android` library + ffmpeg, `extractNativeLibs`, abi splits arm64-v8a)
- Modify: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/App.kt` (init `YoutubeDL` and `FFmpeg` once)
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/download/DownloadPipelineTest.kt`

**Interfaces:**
- Produces: `interface Extractor { suspend fun fetch(job: JobEntity, onProgress: (Float) -> Unit): MediaInfo }` (downloads into `paths.incomingDir(job.id)`).
- Produces: `class DownloadPipeline(extractor, paths, db, thumbnailer, clock)` with `suspend fun run(job: JobEntity, onProgress: (Float) -> Unit = {}): PipelineResult` (`sealed interface PipelineResult { data class Done(val itemId: String) : ...; data class Failed(val message: String, val retryable: Boolean) : ...; object Cancelled }`).
- Produces: `class Thumbnailer(paths)` with `fun saveThumb(itemId: String, sourceJpeg: File): Long` returning thumb bytes.
- Consumes: Tasks 3, 4, 6, 8.

- [ ] **Step 1: Write the failing tests**

```kotlin
class DownloadPipelineTest {
    private val fake = FakeExtractor()      // writes a 1.2 MB fake mp3 + 40 KB jpg + info.json

    @Test fun `success moves media into the library, inserts the item and reports bytes`() = runTest {
        val result = pipeline.run(job(id = "j1", mode = AUDIO))
        assertIs<Done>(result)
        val item = db.itemDao().get(result.itemId)!!
        assertEquals("audio/ab/ab12.mp3", item.path)
        assertEquals(1_200_000L, item.bytes)
        assertTrue(item.thumbBytes > 0)
        assertTrue(File(root, item.path).exists())
        assertFalse(paths.incomingDir("j1").exists())
    }

    @Test fun `permanent extractor error leaves no item and fails the job`() = runTest {
        fake.failWith = "ERROR: Video unavailable"
        assertIs<Failed>(pipeline.run(job(id = "j2")))
        assertTrue(db.itemDao().observeAll(null).first().isEmpty())
        assertFalse(File(root, "library/audio").walkTopDown().any { it.isFile })
    }

    @Test fun `cancelled job deletes its temp dir`() = runTest { /* cancel → incoming gone, no item */ }

    @Test fun `missing thumbnail still finalizes with null thumbPath`() = runTest { /* fake writes no jpg */ }

    @Test fun `insert failure removes the moved file`() = runTest {
        // duplicate path forces the UNIQUE constraint; library must not keep an orphan
    }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DownloadPipelineTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`YtDlpExtractor` calls `YoutubeDL.getInstance().execute(YoutubeDLRequest(url).apply { addOption(...) }, id) { progress, _, line -> }`, using `parseProgress` and the job id as the process id so cancel can destroy it. `run` order: fetch into incoming → `checkSpace(approxBytes, free)` before the copy → move media into `library/...` → `thumbnailer.saveThumb` → insert item with `bytes = File.length()` (measured **after** any cover pass) → delete incoming dir. Wrap the insert in `runCatching`; on failure delete the moved media and return `Failed(retryable = false)`.
Also add `splits { abi { isEnable = true; include("arm64-v8a"); isUniversalApk = true } }` so the debug APK stays installable on x86 emulators too.

- [ ] **Step 4: Run tests to see them pass, then build**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DownloadPipelineTest*' && ./gradlew assembleDebug`
Expected: PASS and BUILD SUCCESSFUL. Confirm APK size is recorded in `apps/mobile/README.md` (expected tens of MB because of bundled Python + ffmpeg).

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): yt-dlp extractor and finalize pipeline"
```

---

### Task 10: Download service and notifications

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/download/DownloadWorker.kt`, `DownloadService.kt`, `QueueNotifications.kt`, `ConnectivityMonitor.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/download/WorkerControlTest.kt`

**Interfaces:**
- Produces: `class DownloadWorker(queue, pipeline, settings, scope)` with `fun start(workerId: String)` / `fun stop()`, exposing `val state: StateFlow<WorkerState>` (`data class WorkerState(running: Boolean, activeJobId: String?, activeProgress: Float, waitingForWifi: Boolean)`).
- Produces: `object QueueNotifications` with `fun build(context, state, activeTitle): Notification` and a pure `fun summary(state, activeTitle): String`.
- Produces: `class ConnectivityMonitor(context)` with `val unmetered: Flow<Boolean>`.
- Consumes: Tasks 7, 9.

- [ ] **Step 1: Write the failing tests**

```kotlin
class WorkerControlTest {
    @Test fun `serial worker takes jobs until the queue is empty`() = runTest { /* 3 jobs → 3 done, state.running false */ }
    @Test fun `wifi-only with no network waits instead of failing`() = runTest {
        // metro=false, settings.wifiOnly=true → job stays queued with stage "waiting for Wi-Fi"
        // state.waitingForWifi == true and no attempt consumed
    }
    @Test fun `retryable failure schedules the next attempt`() = runTest { /* attempts 1, still queued */ }
    @Test fun `stop cancels the active process id`() = runTest { /* extractor saw cancel("j1") */ }
    @Test fun `notification summary names the item and percent`() =
        assertEquals("Downloading · Long mix · 42%", QueueNotifications.summary(state, "Long mix"))
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*WorkerControlTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`DownloadService` is a `LifecycleService` with `foregroundServiceType="dataSync"`, `START_NOT_STICKY`. On create: `reapExpired()`, then loop `claim` → `pipeline.run` → `finish`/`fail` → repeat while a job was found. It stops itself and removes the notification when `claim()` returns null. Claim gate: if `settings.wifiOnly` and `!monitor.unmetered.first()` → set `stage = "waiting for Wi-Fi"`, stop the service, and start it again from `ConnectivityMonitor` when unmetered returns (app process alive only).
Notification channel `downloads`, `IMPORTANCE_LOW`, ongoing, cancel action. Concurrency is 1 worker in v1; the Settings value is stored but only 1 is honoured until parallel yt-dlp is proven safe (spec §9).

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*WorkerControlTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): download foreground service, retries and Wi-Fi gating"
```

---

### Task 11: Navigation shell and Add screen

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/AppShell.kt`, `ui/add/AddScreen.kt`, `AddViewModel.kt`, `ui/components/SelectMenu.kt`, `Segmented.kt`, `MmButton.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/add/AddFormTest.kt`

**Interfaces:**
- Produces: `data class AddFormState(url: String, mode: DownloadMode, playlistId: String?, error: String?, submitting: Boolean)` with `fun validate(): AddFormState`, `fun submit(queue: JobQueue): SubmitResult` (`data class SubmitResult(val jobId: String?, val duplicate: Boolean, val error: String?)`).
- Produces: `@Composable fun AppShell(container: AppContainer)` with bottom bar Library · Playlists · Add · Downloads · Storage and Settings in the top bar.
- Consumes: Tasks 2, 7.

- [ ] **Step 1: Write the failing tests**

```kotlin
class AddFormTest {
    @Test fun `blank url is invalid`() = assertEquals("Paste a link first", AddFormState(url = "   ").validate().error)
    @Test fun `non-http url is rejected`() = assertEquals("That is not a link", AddFormState(url = "youtube.com/x").validate().error)
    @Test fun `http and https are accepted`() = assertNull(AddFormState(url = "https://youtu.be/x").validate().error)
    @Test fun `trimmed url is submitted`() = runTest { /* queue.enqueue received https://youtu.be/x */ }
    @Test fun `duplicate reports itself without a second job`() = runTest { /* second submit duplicate == true */ }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*AddFormTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Add screen: large URL field with Paste button (`ClipboardManager`), `Audio | Video` segmented control, optional playlist `SelectMenu`, Add button, "Recent" list of the last 10 submitted URLs from DataStore (tap fills the field). On submit: validate → `JobQueue.enqueue` → start `DownloadService` → clear the field → snackbar "Queued" or "Already in the queue". Buttons use accent tokens; no Material default purple anywhere.

- [ ] **Step 4: Run tests to see them pass, then build**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*AddFormTest*' assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): app shell and add-by-url screen"
```

---

### Task 12: Library screen

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/library/LibraryScreen.kt`, `LibraryViewModel.kt`, `ItemCard.kt`, `ItemActionsSheet.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/library/LibraryQueryTest.kt`

**Interfaces:**
- Produces: `enum class LibraryFilter { ALL, AUDIO, VIDEO }`, `enum class LibrarySort { NEWEST, OLDEST, TITLE, LARGEST, DURATION }`, `fun kindFor(filter): String?`, `fun sortItems(items: List<Item>, sort: LibrarySort): List<Item>`.
- Consumes: Tasks 4, 5.

- [ ] **Step 1: Write the failing tests**

```kotlin
class LibraryQueryTest {
    @Test fun `filter maps to kinds`() { assertEquals(null, kindFor(ALL)); assertEquals("audio", kindFor(AUDIO)) }
    @Test fun `title sort is case-insensitive`() { /* "apple", "Banana" → apple first */ }
    @Test fun `largest sort puts the big file first`() { /* 1.2 GB before 4 MB */ }
    @Test fun `duration sort tolerates null durations`() { /* nulls last */ }
    @Test fun `missing items render with an unavailable flag`() { /* available=false survives mapping */ }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*LibraryQueryTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Grid, 2 columns portrait, thumb 16:9 with duration badge, title two lines, unavailable badge "File missing". Segmented filter + search field (debounced 250 ms) + sort `SelectMenu` in the header. Long-press enters selection mode with a bottom bar: Add to playlist, Delete (confirm dialog quoting exact freed size). Item menu: Play next, Add to queue, Add to playlist, Delete. Empty state: "Nothing here yet. Paste a link in Add."

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*LibraryQueryTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): library grid with filter, search and selection"
```

---

### Task 13: Playback, queue and resume

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/playback/PlaybackService.kt`, `PlayerRepository.kt`, `QueueStore.kt`
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/player/MiniPlayer.kt`, `NowPlayingSheet.kt`, `QueueSheet.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/playback/QueueStoreTest.kt`, `PlayerRepositoryTest.kt`

**Interfaces:**
- Produces: `class QueueStore(db, dataStore)` with `suspend fun seed(itemIds: List<String>, startAt: Int)`, `fun observe(): Flow<List<QueueEntry>>`, `suspend fun addNext(id)`, `suspend fun addLast(id)`, `suspend fun removeAt(position)`, `suspend fun move(from, to)`, `val currentIndex: Flow<Int>`, `suspend fun restore(): List<QueueEntry>`.
- Produces: `class PlayerRepository(context, queueStore, itemDao)` with `fun playItem(item: Item, queueSeed: List<Item>, index: Int)`, `fun toggle()`, `fun next()`, `fun previous()`, `fun seekTo(ms)`, `fun stop()`, `suspend fun onItemDeleted(id)`, `val nowPlaying: StateFlow<NowPlaying?>`.
- Consumes: Tasks 4, 6 (`resumeTarget`).

- [ ] **Step 1: Write the failing tests**

```kotlin
class QueueStoreTest {
    @Test fun `seed starts at the tapped index`() = runTest { /* 5 items, startAt 2 → current index 2 */ }
    @Test fun `addNext inserts after the current index`() = runTest { /* order a,x,b,c */ }
    @Test fun `removeAt keeps the current item when it is after the removed one`() = runTest { }
    @Test fun `move renumbers positions without gaps`() = runTest { /* 0..n-1 */ }
    @Test fun `restore returns the saved queue paused at the saved index`() = runTest { /* isPlaying false */ }
    @Test fun `deleting the current item stops and drops it`() = runTest { /* nowPlaying null */ }
}

class PlayerRepositoryTest {
    @Test fun `playback seeks to the resume point when it is past three seconds`() = runTest { }
    @Test fun `position is saved every five seconds of playback`() = runTest { /* fake clock */ }
    @Test fun `audio and video both use the single ExoPlayer instance`() = runTest { /* one controller */ }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*QueueStoreTest*' --tests '*PlayerRepositoryTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`PlaybackService` extends `MediaSessionService` and owns exactly one `ExoPlayer` (audio attributes = music; `handleAudioBecomingNoisy`; media session for the notification and lockscreen). `PlayerRepository` binds a `MediaController` and maps `MediaItem`s built from `items` (`uri = File(root, item.path)`). Save position on pause, every 5 s while playing, `onTaskRemoved`, and on service destroy. MiniPlayer is rendered once in `AppShell` above the bottom bar; tapping opens the sheet. Queue operations persist before touching the controller.

- [ ] **Step 4: Run tests to see them pass, then build**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*QueueStoreTest*' --tests '*PlayerRepositoryTest*' assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): Media3 playback, queue persistence and resume"
```

---

### Task 14: Playlists

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/playlists/PlaylistsScreen.kt`, `PlaylistDetailScreen.kt`, `PlaylistsViewModel.kt`, `AddToPlaylistDialog.kt`, `DragReorder.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/playlists/PlaylistsViewModelTest.kt`

**Interfaces:**
- Produces: `class PlaylistsViewModel(dao: PlaylistDao, queue: QueueStore)` with `fun create(name)`, `fun rename(id, name)`, `fun delete(id)`, `fun removeItem(playlistId, itemId)`, `fun reorder(playlistId, orderedIds)`, `fun playAll(playlistId)`, `val error: StateFlow<String?>`.
- Consumes: Tasks 4, 13.

- [ ] **Step 1: Write the failing tests**

```kotlin
class PlaylistsViewModelTest {
    @Test fun `blank name is rejected`() = runTest { /* error == "Name cannot be empty" */ }
    @Test fun `reorder writes the full ordered list`() = runTest { /* positions 0,1,2 */ }
    @Test fun `playAll seeds the queue with the playlist order`() = runTest { /* first item index 0 */ }
    @Test fun `adding the same item twice is a no-op`() = runTest { /* count unchanged, no error */ }
    @Test fun `deleting a playlist keeps its items`() = runTest { /* item rows intact */ }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*PlaylistsViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Playlists list (name, count, 2×2 thumb stack, ⋮ rename/delete). Detail: reorderable list with drag handle, Play all, remove entry, tap to play that item seeded from the playlist. `AddToPlaylistDialog` lists playlists with checkmarks and a "New playlist" row; it fires `mm:playlists-changed`-equivalent by `Flow` invalidation (no event bus needed).

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*PlaylistsViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): playlists with drag reorder and play-all"
```

---

### Task 15: Downloads screen

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/downloads/DownloadsScreen.kt`, `DownloadsViewModel.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/downloads/DownloadsViewModelTest.kt`

**Interfaces:**
- Produces: `data class DownloadRow(id, title, url, status, stage, progress, error, sizeText?)`, `fun groupJobs(jobs: List<JobEntity>): Pair<List<DownloadRow>, List<DownloadRow>>` (active, finished).
- Consumes: Tasks 4, 7, 10.

- [ ] **Step 1: Write the failing tests**

```kotlin
class DownloadsViewModelTest {
    @Test fun `active jobs come first newest-first and finished below`() = runTest { }
    @Test fun `waiting for wifi is shown as a stage, not an error`() = runTest { }
    @Test fun `permanent failure offers retry`() = runTest { /* row.canRetry */ }
    @Test fun `cancel marks the job cancelled`() = runTest { }
    @Test fun `clear finished deletes only done and failed`() = runTest { }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DownloadsViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Two sections: Active (progress bar, stage label, cancel) and Finished (done → "Open", failed → error text + Retry, cancelled). "Clear finished" with a confirm dialog. Show the yt-dlp line only inside an expandable "Details" on failed rows; the primary text is the classified message.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*DownloadsViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): downloads screen with progress and retry"
```

---

### Task 16: Storage screen

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/storage/StorageRepository.kt`, `StorageSnapshot.kt`
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/storage/StorageScreen.kt`, `StorageViewModel.kt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/storage/StorageRepositoryTest.kt`, `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/storage/StorageMathTest.kt`

**Interfaces:**
- Produces: `data class StorageSnapshot(libraryBytes: Long, audioBytes: Long, videoBytes: Long, thumbBytes: Long, tempBytes: Long, itemCount: Int, audioCount: Int, videoCount: Int, deviceTotal: Long, deviceFree: Long)`, `data class SizeRow(val item: Item, val share: Float)`.
- Produces: `class StorageRepository(db, paths, statFs: () -> FileStats)` with `fun observeLibrary(): Flow<StorageSnapshot>`, `suspend fun measureTemp(): Long`, `fun largestItems(limit: Int): Flow<List<SizeRow>>`.
- Produces: `fun share(part: Long, whole: Long): Float` and `fun usedFraction(snapshot): Float`.
- Consumes: Tasks 3, 4, 5.

- [ ] **Step 1: Write the failing tests**

```kotlin
class StorageRepositoryTest {
    @Test fun `snapshot sums media, thumbs and temp`() = runTest {
        // audio 2.11 GB / video 2.00 GB / thumbs 38 MB / temp 12 MB
        assertEquals(4_144_000_000L, snap.libraryBytes)   // exact fixture sum
        assertEquals(38_000_000L, snap.thumbBytes)
    }
    @Test fun `empty library is all zero and the bar still renders`() = runTest { }
    @Test fun `temp is the sum of incoming job dirs`() = runTest { /* 2 dirs */ }
    @Test fun `largest items are ranked by bytes with share`() = runTest {
        // 1.2 GB of 4 GB → 0.30 share
    }
    @Test fun `device free space comes from StatFs`() = runTest { }
    @Test fun `a negative share is impossible even if bytes are stale`() = runTest { /* part > whole → 0 */ }
}

class StorageMathTest {
    @Test fun `share formats as one decimal percent`() { assertEquals("28.3%", formatShare(0.2834f)) }
    @Test fun `used fraction clamps to 0..1`() { assertEquals(1f, usedFraction(total = 100, free = -5), 0.001f) }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*StorageRepositoryTest*' --tests '*StorageMathTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Header: `Library {formatBytes(libraryBytes)}`; device bar with `used = total - free`, and the line `X used of Y · Z free`. Breakdown rows Audio/Video/Thumbnails/Temp with bytes, count and share. Then "Largest items" list (limit 50, toggle Largest/Newest) with thumb, title, `formatBytes(bytes)`, share, and a ⋮ menu (Play, Add to playlist, Delete). Delete confirms with the exact freed amount and refreshes from the DB flow. `Re-check` button calls `ItemRepository.recheck()` and shows "3 files missing" with a list; deleting a missing item is allowed and frees 0 B. `Clean up` removes leftover `.incoming` dirs and orphan thumbs.

- [ ] **Step 4: Run tests to see them pass, then build**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*StorageRepositoryTest*' --tests '*StorageMathTest*' assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): storage usage screen with per-item breakdown"
```

---

### Task 17: Settings and About

**Files:**
- Create: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Create: `apps/mobile/app/src/main/res/raw/gpl3.txt`
- Test: `apps/mobile/app/src/test/java/dev/mediamanager/mobile/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces: `data class DownloadSettings(wifiOnly: Boolean, maxHeight: Int)` persisted in SettingsStore; `fun sanitizeMaxHeight(input: Int): Int` (allowed 720, 1080, 1440, 2160, else 1080).
- Consumes: Tasks 2, 10.

- [ ] **Step 1: Write the failing tests**

```kotlin
class SettingsViewModelTest {
    @Test fun `unknown height falls back to 1080`() = assertEquals(1080, sanitizeMaxHeight(999))
    @Test fun `wifi only round-trips through settings`() = runTest { /* set true, read true */ }
    @Test fun `theme selection persists and is applied`() = runTest { /* moss/light */ }
    @Test fun `about exposes the GPL notice and extractor version`() = runTest { /* non-empty strings */ }
}
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Sections: Appearance (family `SelectMenu`, mode segmented Light/Dark/System), Downloads (Wi-Fi only switch, max height `SelectMenu`, "yt-dlp version" with an "Update extractor" action calling `YoutubeDL.updateYoutubeDL` and reporting success/failure), Storage (open Storage), About (version, GPL-3.0 text, yt-dlp + ffmpeg attribution). Theme changes apply immediately through `AuthProvider`-equivalent `AppearanceState` at the root.

- [ ] **Step 4: Run tests to see them pass**

Run: `cd apps/mobile && ./gradlew testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile
git commit -m "feat(mobile): settings, theme picker and extractor update"
```

---

### Task 18: Wiring, icon, docs, final gate

**Files:**
- Modify: `apps/mobile/app/src/main/java/dev/mediamanager/mobile/App.kt`, `MainActivity.kt`, `ui/AppShell.kt`
- Create: `apps/mobile/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, adaptive icon from `apps/web/public/logo.svg`
- Create: `apps/mobile/README.md`
- Modify: `docs/architecture.md` (mobile section: Kotlin standalone, not Expo, not a server client)
- Modify: `AGENTS.md` (add mobile commands + the "no auth, no server, local only" rule)

**Interfaces:**
- Consumes: every prior task.

- [ ] **Step 1: Wire the root**

`App` initialises `YoutubeDL`/`FFmpeg`, builds `AppContainer`, applies the stored theme before first composition (`collectAsState` with an initial read from DataStore done in `runBlocking` on a background splash read, or render nothing for one frame — no theme flash), reaps expired jobs and starts `DownloadService` if any job is queued/running on app start.

- [ ] **Step 2: Add the icon and verify the build gate**

Run: `cd apps/mobile && ./gradlew clean testDebugUnitTest lint assembleDebug`
Expected: BUILD SUCCESSFUL, no lint errors (warnings allowed), APK produced. Record APK size and the exact commands in `apps/mobile/README.md`.

- [ ] **Step 3: Write the README**

Cover: prerequisites (JDK 21 path, SDK path), build/install commands (`./gradlew installDebug`, `adb install -r app/build/outputs/apk/debug/app-universal-debug.apk`), what works, known limits (yt-dlp breakage, 6-hour FGS cap, no boot auto-resume), and the device smoke script from the spec §13.

- [ ] **Step 4: Update the two docs**

`docs/architecture.md` Mobile section becomes: standalone Kotlin app, own Room DB, no server, no auth; the server remains source of truth only for the web/desktop clients. `AGENTS.md`: add `make mobile`-equivalent commands and a bullet that the Android app deliberately has no auth and never talks to the API.

- [ ] **Step 5: Commit**

```bash
git add apps/mobile docs/architecture.md AGENTS.md
git commit -m "docs(mobile): Android app README, architecture and agent notes"
```

---

### Task 19: Device smoke test (manual, physical phone)

**Files:**
- Modify: `apps/mobile/README.md` (record the run)

- [ ] **Step 1: Install and run through the list**

Run: `adb install -r apps/mobile/app/build/outputs/apk/debug/app-universal-debug.apk`
Then, on the device:
1. Add a short YouTube URL as Audio → item appears with cover art; `adb shell run-as dev.mediamanager.mobile ls files/library/audio` shows `<id[0:2]>/<id>.mp3`.
2. Add the same URL as Video → a second item appears, plays, and seeking works.
3. Add the audio URL again → "Already in the queue" or an existing item, no duplicate file.
4. Start a long download and swipe the app away → reopen: job continues, nothing partial in the library.
5. Turn on Airplane mode and add a URL → readable failure, retries once connectivity returns.
6. Delete the currently playing item → playback stops, Storage total drops by exactly its size.
7. Open Storage → numbers add up: Audio + Video + Thumbnails + Temp ≈ Library total; the item you deleted is gone from the list.

- [ ] **Step 2: Record results**

Paste the checklist with pass/fail and any deviation into `apps/mobile/README.md`, then commit:

```bash
git add apps/mobile/README.md
git commit -m "test(mobile): device smoke test results"
```
