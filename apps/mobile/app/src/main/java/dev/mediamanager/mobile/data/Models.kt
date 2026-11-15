package dev.mediamanager.mobile.data

data class Item(
    val id: String,
    val kind: String,
    val title: String,
    val sizeBytes: Long,
    val path: String,
    val thumbPath: String?,
    val available: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val mime: String,
    val sourceUrl: String?,
    val createdAt: Long,
) {
    val isAudio: Boolean get() = kind == "audio"
    val isVideo: Boolean get() = kind == "video"
}

fun ItemEntity.toItem(): Item = Item(
    id = id,
    kind = kind,
    title = title,
    sizeBytes = bytes,
    path = path,
    thumbPath = thumbPath,
    available = available,
    positionMs = positionMs,
    durationMs = durationMs,
    mime = mime,
    sourceUrl = sourceUrl,
    createdAt = createdAt,
)
