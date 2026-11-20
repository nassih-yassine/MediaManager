package dev.mediamanager.mobile.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.mediamanager.mobile.data.PlaylistDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Playlist covers live at `covers/<playlistId>.jpg` so they survive the source item
 * being deleted. A cover is either copied from an item's thumbnail or decoded from a
 * picked image.
 */
class PlaylistCoverStore(
    private val context: Context,
    private val paths: LibraryPaths,
    private val dao: PlaylistDao,
) {

    suspend fun fromItemThumb(playlistId: String, thumbPath: String?): String? =
        withContext(Dispatchers.IO) {
            val source = thumbPath?.let { paths.resolve(it) }?.takeIf { it.exists() } ?: return@withContext null
            val target = paths.coverFile(playlistId)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            target.takeIf { it.length() > 0 }?.let { apply(playlistId, paths.coverRelativePath(playlistId)) }
        }

    suspend fun fromUri(playlistId: String, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            // A 48 MP photo decodes to ~190 MB at full size; sample to ~1024 px first.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null
            val target = paths.coverFile(playlistId)
            target.parentFile?.mkdirs()
            val scaled = scaleSquare(bitmap, 512)
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (!scaled.isRecycled) scaled.recycle()
            if (!bitmap.isRecycled && bitmap !== scaled) bitmap.recycle()
            apply(playlistId, paths.coverRelativePath(playlistId))
        }

    suspend fun clear(playlistId: String) {
        withContext(Dispatchers.IO) { paths.coverFile(playlistId).delete() }
        apply(playlistId, null)
    }

    private suspend fun apply(playlistId: String, coverPath: String?): String? {
        dao.setCover(playlistId, coverPath, System.currentTimeMillis())
        return coverPath
    }

    private fun scaleSquare(bitmap: Bitmap, size: Int): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, side, side)
        return Bitmap.createScaledBitmap(square, size, size, true).also {
            if (it !== bitmap && square !== bitmap) square.recycle()
        }
    }

    fun coverFile(playlistId: String): File = paths.coverFile(playlistId)
}
