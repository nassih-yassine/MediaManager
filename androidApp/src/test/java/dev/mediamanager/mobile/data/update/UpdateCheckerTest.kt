package dev.mediamanager.mobile.data.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {

    private val payload = """
        [{
          "tag_name": "android-v0.2.0",
          "html_url": "https://example.test/release",
          "body": "Fixes",
          "published_at": "2026-11-20T10:00:00Z",
          "draft": false,
          "prerelease": false,
          "assets": [{"name": "MediaManager-arm64.apk", "browser_download_url": "https://example.test/app.apk"}]
        }]
    """.trimIndent()

    @Test
    fun `newer release is available`() = runTest {
        val checker = UpdateChecker(currentVersion = "0.1.1.1803", fetch = { payload })

        val result = checker.check()

        assertTrue(result is UpdateChecker.Result.Available)
        assertEquals("0.2.0", (result as UpdateChecker.Result.Available).version.display())
    }

    @Test
    fun `same tag is up to date even when the local build is ahead`() = runTest {
        val sameTag = payload.replace("android-v0.2.0", "android-v0.1.1")
        val checker = UpdateChecker(currentVersion = "0.1.1.1803", fetch = { sameTag })

        assertTrue(checker.check() is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `network loss fails quietly`() = runTest {
        val checker = UpdateChecker(currentVersion = "0.1.1", fetch = { throw IOException("offline") })

        val result = checker.check()

        assertEquals(UpdateChecker.Reason.OFFLINE, (result as UpdateChecker.Result.Failed).reason)
    }

    @Test
    fun `garbage payload is an error, not a crash`() = runTest {
        val checker = UpdateChecker(currentVersion = "0.1.1", fetch = { "not json" })

        assertEquals(UpdateChecker.Reason.ERROR, (checker.check() as UpdateChecker.Result.Failed).reason)
    }
}
