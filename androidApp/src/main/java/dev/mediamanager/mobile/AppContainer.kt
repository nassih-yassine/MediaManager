package dev.mediamanager.mobile

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.MIGRATION_1_2
import dev.mediamanager.mobile.data.MIGRATION_2_3
import dev.mediamanager.mobile.data.SettingsStore
import dev.mediamanager.mobile.download.CompositeExtractor
import dev.mediamanager.mobile.download.ConnectivityMonitor
import dev.mediamanager.mobile.download.DownloadPipeline
import dev.mediamanager.mobile.download.DownloadSettings
import dev.mediamanager.mobile.download.JobQueue
import dev.mediamanager.mobile.download.Thumbnailer
import dev.mediamanager.mobile.download.YoutubeNewPipeExtractor
import dev.mediamanager.mobile.download.YtDlpExtractor
import dev.mediamanager.mobile.media.AudioOutputMonitor
import dev.mediamanager.mobile.media.ItemRepository
import dev.mediamanager.mobile.media.PlaylistCoverStore
import dev.mediamanager.mobile.media.LibraryPaths
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.storage.StorageRepository
import dev.mediamanager.mobile.media.VolumeControl

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val paths: LibraryPaths = LibraryPaths(appContext.filesDir)

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "mm.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    val settings: SettingsStore = SettingsStore(appContext)

    val connectivity: ConnectivityMonitor = ConnectivityMonitor(appContext)

    val jobQueue: JobQueue = JobQueue(
        transaction = { block -> db.withTransaction { block() } },
        dao = db.jobDao(),
        existsInLibrary = { url -> db.itemDao().existsBySourceUrl(url) },
    )


    val itemRepository: ItemRepository = ItemRepository(db, paths)

    val storage: StorageRepository = StorageRepository(db, paths)

    val playlistCovers: PlaylistCoverStore = PlaylistCoverStore(appContext, paths, db.playlistDao())

    val audioOutput: AudioOutputMonitor = AudioOutputMonitor(appContext)
    val volume: VolumeControl = VolumeControl(appContext)

    val player: PlayerRepository = PlayerRepository(appContext, db, paths, settings)

    private val thumbnailer = Thumbnailer(paths)

    /** Kept in memory and refreshed by [App]; the extractor runs on an IO thread. */
    @Volatile
    var downloadSettings: DownloadSettings = DownloadSettings()

    private val ytDlpExtractor: YtDlpExtractor =
        YtDlpExtractor(appContext, paths) { downloadSettings }

    val extractor: CompositeExtractor = CompositeExtractor(
        newPipe = YoutubeNewPipeExtractor(paths) { downloadSettings.maxHeight },
        ytDlp = ytDlpExtractor,
    )

    val pipeline: DownloadPipeline = DownloadPipeline(
        extractor = extractor,
        paths = paths,
        db = db,
        thumbnailer = thumbnailer,
        freeBytes = { paths.usableBytes() },
    )
}
