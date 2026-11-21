package dev.mediamanager.mobile.ui.downloads

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.JobEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DownloadsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container = AppContainer(ApplicationProvider.getApplicationContext())

    @Before
    fun seed() = runBlocking {
        container.db.jobDao().insert(
            JobEntity(
                id = "failed1",
                dedupeKey = "k",
                url = "https://youtu.be/failed",
                mode = "audio",
                status = "failed",
                error = "Video unavailable",
                progress = 0f,
                attempts = 3,
                scheduledAt = 1,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    @After
    fun tearDown() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        container.db.clearAllTables()
    }

    @Test
    fun `a failed job shows its reason and a retry action`() {
        compose.setContent { DownloadsScreen(container, container.player, onClose = {}) }
        val rows = kotlinx.coroutines.runBlocking {
            container.db.jobDao().observeFinished().first()
        }
        org.junit.Assert.assertEquals(1, rows.size)
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("FAILED")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Video unavailable", substring = true).assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Actions")).performClick()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }
}
