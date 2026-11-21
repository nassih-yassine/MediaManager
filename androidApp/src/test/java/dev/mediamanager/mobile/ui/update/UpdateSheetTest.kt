package dev.mediamanager.mobile.ui.update

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.mediamanager.mobile.data.update.UpdateInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class UpdateSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val info = UpdateInfo(
        versionTag = "android-v0.9.0",
        versionName = "0.9.0",
        notes = "What changed",
        apkUrl = "https://example.test/app.apk",
        releaseUrl = "https://example.test/release",
        publishedAt = null,
    )

    @Test
    fun `update sheet shows the version and starts the download`() {
        var downloaded = false
        compose.setContent {
            UpdateSheet(info = info, onDownload = { downloaded = true }, onLater = {})
        }

        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithText("Download update").performClick()

        assertTrue(downloaded)
    }

    @Test
    fun `later dismisses without downloading`() {
        var later = false
        compose.setContent { UpdateSheet(info = info, onDownload = {}, onLater = { later = true }) }

        compose.onNodeWithText("Later").performClick()

        assertTrue(later)
    }
}
