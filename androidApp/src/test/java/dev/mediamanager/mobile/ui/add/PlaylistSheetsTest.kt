package dev.mediamanager.mobile.ui.add

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.PlaylistEntry
import dev.mediamanager.mobile.download.summarizePlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PlaylistSheetsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun summary(count: Int = 3) = summarizePlaylist(
        listId = "PL1",
        name = "Mix",
        uploader = "Channel",
        coverUrl = null,
        entries = (1..count).map {
            PlaylistEntry("https://www.youtube.com/watch?v=v$it", "Video $it", 60_000)
        },
    )

    @Test
    fun `picker shows the count and a fixed download footer`() {
        var confirmed = false
        compose.setContent {
            PlaylistSelectSheet(
                summary = summary(),
                onConfirm = { confirmed = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("3 of 3 selected").assertIsDisplayed()
        compose.onNodeWithText("Download 3").assertIsDisplayed().performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `search filters titles and a row toggles the footer count`() {
        compose.setContent {
            PlaylistSelectSheet(summary = summary(), onConfirm = {}, onDismiss = {})
        }

        compose.onNodeWithText("Search titles").performTextInput("2")

        compose.onNodeWithText("Video 2").assertExists()
        compose.onNodeWithText("Video 1").assertDoesNotExist()
        compose.onNodeWithText("Video 2").performScrollTo().performClick()
        compose.onNodeWithText("Download 2").assertIsDisplayed()
    }

    @Test
    fun `import sheet shows the playlist and the media choices`() {
        var mode = DownloadMode.AUDIO
        var chose = false
        compose.setContent {
            PlaylistImportSheet(
                summary = summary(),
                mode = mode,
                onMode = { mode = it },
                onAll = {},
                onChoose = { chose = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("Mix").assertIsDisplayed()
        compose.onNodeWithText("3 items", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download all").assertIsDisplayed()
        compose.onNodeWithText("Choose media").performClick()

        assertTrue(chose)
        compose.onNodeWithText("Video").performClick()
        assertEquals(DownloadMode.VIDEO, mode)
    }
}
