package dev.mediamanager.mobile.data.update

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.data.SettingsStore
import dev.mediamanager.mobile.ui.update.UpdateCenter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateCenterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings = SettingsStore(context)

    private val payload = """
        [{
          "tag_name": "android-v0.9.0",
          "html_url": "https://example.test/release",
          "body": "Notes",
          "published_at": "2026-11-20T10:00:00Z",
          "draft": false,
          "prerelease": false,
          "assets": [{"name": "MediaManager-arm64.apk", "browser_download_url": "https://example.test/app.apk"}]
        }]
    """.trimIndent()

    /** DataStore is a process singleton; each test starts from "never checked". */
    @Before
    fun reset() = runBlocking {
        settings.setLastUpdateCheckAt(0)
        settings.setDismissedUpdateTag(null)
    }

    private fun center(now: Long) = UpdateCenter(
        settings = settings,
        checker = UpdateChecker(currentVersion = "0.1.1.1803", fetch = { payload }),
        clock = { now },
    )

    @Test
    fun `automatic checks are throttled to one a day`() = runTest {
        val now = 1_700_000_000_000
        assertEquals("android-v0.9.0", center(now).autoCheck()?.versionTag)
        assertNull(center(now + 60_000).autoCheck())
        assertEquals("android-v0.9.0", center(now + UpdateCenter.THROTTLE_MS + 1).autoCheck()?.versionTag)
    }

    @Test
    fun `a dismissed tag is not shown automatically again`() = runTest {
        val now = 1_700_000_000_000
        val first = center(now)!!
        assertEquals("android-v0.9.0", first.autoCheck()?.versionTag)

        first.dismiss("android-v0.9.0")

        assertNull(center(now + UpdateCenter.THROTTLE_MS + 1).autoCheck())
    }

    @Test
    fun `manual checks ignore the throttle`() = runTest {
        val now = 1_700_000_000_000
        center(now).autoCheck()

        val result = center(now + 60_000).manualCheck()

        assert(result is UpdateChecker.Result.Available)
    }
}
