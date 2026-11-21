package dev.mediamanager.mobile.ui.update

import dev.mediamanager.mobile.data.SettingsStore
import dev.mediamanager.mobile.data.update.UpdateChecker
import dev.mediamanager.mobile.data.update.UpdateInfo
import kotlinx.coroutines.flow.first

/**
 * Decides when the app may go to the network. Automatic checks are throttled to once a
 * day and stay quiet; a tag the user said "Later" to is not shown again automatically.
 */
class UpdateCenter(
    private val settings: SettingsStore,
    private val checker: UpdateChecker,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun autoCheck(): UpdateInfo? {
        val now = clock()
        if (now - settings.lastUpdateCheckAt.first() < THROTTLE_MS) return null
        settings.setLastUpdateCheckAt(now)

        val result = checker.check()
        if (result !is UpdateChecker.Result.Available) return null
        if (settings.dismissedUpdateTag.first() == result.info.versionTag) return null
        return result.info
    }

    suspend fun manualCheck(): UpdateChecker.Result {
        settings.setLastUpdateCheckAt(clock())
        return checker.check()
    }

    suspend fun dismiss(tag: String) {
        settings.setDismissedUpdateTag(tag)
    }

    companion object {
        const val THROTTLE_MS = 24L * 60 * 60 * 1000
    }
}
