package dev.mediamanager.mobile.data.update

/** The newest published release that carries an installable APK. */
data class UpdateInfo(
    val versionTag: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String?,
    val releaseUrl: String,
    val publishedAt: String?,
)
