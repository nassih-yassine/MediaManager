package dev.mediamanager.mobile.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** UI language. Stored per device; the app has no account to sync it to. */
enum class AppLanguage(val key: String) {
    ENGLISH("en"),
    ARABIC("ar");

    val isRtl: Boolean get() = this == ARABIC

    companion object {
        fun fromKey(value: String?): AppLanguage =
            entries.firstOrNull { it.key == value } ?: ENGLISH
    }
}

/** English is the reference; Arabic must cover the same fields by construction. */
data class Strings(
    val appName: String,
    val tabHome: String,
    val tabLibrary: String,
    val tabAdd: String,
    val tabPlaylists: String,
    val tabSettings: String,
    val items: (Int) -> String,
    val recentlyAdded: String,
    val addTitle: String,
    val addHint: String,
    val formatAudio: String,
    val formatVideo: String,
    val downloadAudio: String,
    val downloadVideo: String,
    val settingsTitle: String,
    val appearance: String,
    val downloads: String,
    val storage: String,
    val about: String,
    val languageTitle: String,
    val languageBody: String,
    val languageEnglish: String,
    val languageArabic: String,
    val continueLabel: String,
)

val EnglishStrings = Strings(
    appName = "MediaManager",
    tabHome = "Home",
    tabLibrary = "Library",
    tabAdd = "Add",
    tabPlaylists = "Playlists",
    tabSettings = "Settings",
    items = { count -> if (count == 1) "1 item" else "$count items" },
    recentlyAdded = "Recently added",
    addTitle = "Add",
    addHint = "Paste a link. YouTube or anything yt-dlp knows.",
    formatAudio = "Audio",
    formatVideo = "Video",
    downloadAudio = "Download audio",
    downloadVideo = "Download video",
    settingsTitle = "Settings",
    appearance = "Appearance",
    downloads = "Downloads",
    storage = "Storage",
    about = "About",
    languageTitle = "Language",
    languageBody = "English is the default. You can change it later in Settings.",
    languageEnglish = "English",
    languageArabic = "العربية",
    continueLabel = "Continue",
)

val ArabicStrings = Strings(
    appName = "MediaManager",
    tabHome = "الرئيسية",
    tabLibrary = "المكتبة",
    tabAdd = "إضافة",
    tabPlaylists = "قوائم التشغيل",
    tabSettings = "الإعدادات",
    items = { count ->
        when (count) {
            0 -> "لا عناصر"
            1 -> "عنصر واحد"
            2 -> "عنصران"
            in 3..10 -> "$count عناصر"
            else -> "$count عنصرًا"
        }
    },
    recentlyAdded = "أُضيف حديثًا",
    addTitle = "إضافة",
    addHint = "الصق رابطًا. يوتيوب أو أي موقع يعرفه yt-dlp.",
    formatAudio = "صوت",
    formatVideo = "فيديو",
    downloadAudio = "تنزيل الصوت",
    downloadVideo = "تنزيل الفيديو",
    settingsTitle = "الإعدادات",
    appearance = "المظهر",
    downloads = "التنزيلات",
    storage = "التخزين",
    about = "حول",
    languageTitle = "اللغة",
    languageBody = "الإنجليزية هي الافتراضية. يمكنك تغييرها لاحقًا من الإعدادات.",
    languageEnglish = "English",
    languageArabic = "العربية",
    continueLabel = "متابعة",
)

val LocalStrings = staticCompositionLocalOf { EnglishStrings }
