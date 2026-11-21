package dev.mediamanager.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.AppContainer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** End-to-end shell: real container, real Room database, every tab reachable. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppShellTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container =
        AppContainer(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = container.db.close()

    @Test
    fun `every tab opens from the shell`() {
        compose.setContent {
            AppShell(container = container, player = container.player)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("MediaManager")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Search titles").assertIsDisplayed()

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Paste a link. YouTube or anything yt-dlp knows.").assertIsDisplayed()

        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithText("No playlists yet").assertExists()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Check for updates").assertExists()

        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Welcome to MediaManager").assertIsDisplayed()
    }
}
