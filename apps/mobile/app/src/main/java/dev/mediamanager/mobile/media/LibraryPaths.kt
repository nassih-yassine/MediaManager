package dev.mediamanager.mobile.media

import java.io.File

/**
 * Every library path in the app is built here. Media lives at
 * `library/<kind>/<id[0:2]>/<id>.<ext>`, thumbnails at `thumbs/<id[0:2]>/<id>.jpg`,
 * and partial work at `.incoming/<jobId>/`.
 */
class LibraryPaths(private val root: File) {

    val libraryDir: File get() = File(root, "library")
    val thumbsDir: File get() = File(root, "thumbs")
    val incomingRoot: File get() = File(root, ".incoming")

    fun itemRelativePath(kind: String, id: String, ext: String): String {
        require(id.length >= 2) { "id must have at least two characters" }
        require(kind.matches(Regex("[a-z]+"))) { "kind must be lowercase letters" }
        val cleanExt = ext.removePrefix(".").lowercase()
        require(cleanExt.matches(Regex("[a-z0-9]+"))) { "extension must be alphanumeric" }
        return "library/$kind/${id.take(2)}/$id.$cleanExt"
    }

    fun itemFile(kind: String, id: String, ext: String): File =
        File(root, itemRelativePath(kind, id, ext))

    fun thumbRelativePath(id: String): String {
        require(id.length >= 2) { "id must have at least two characters" }
        return "thumbs/${id.take(2)}/$id.jpg"
    }

    fun thumbFile(id: String): File = File(root, thumbRelativePath(id))

    fun incomingDir(jobId: String): File = File(incomingRoot, jobId)

    /** Returns the canonical file when it stays under [root], null for traversal or absolute paths. */
    fun resolve(relative: String): File? {
        if (relative.isBlank()) return null
        if (relative.startsWith("/") || relative.startsWith("\\")) return null
        val base = root.canonicalFile
        val target = File(base, relative).canonicalFile
        val basePath = base.path
        return when {
            target.path == basePath -> null
            target.path.startsWith(basePath + File.separator) -> target
            else -> null
        }
    }

    fun deleteRecursively(dir: File): Boolean = dir.exists() && dir.deleteRecursively()

    fun usableBytes(): Long = try {
        android.os.StatFs(root.absolutePath).availableBytes
    } catch (e: Exception) {
        0L
    }
}
