package dev.mediamanager.mobile.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.ItemEntity
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
class LibraryScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container = AppContainer(ApplicationProvider.getApplicationContext())

    private fun item(id: String, kind: String, title: String) = ItemEntity(
        id = id,
        kind = kind,
        title = title,
        path = "library/$kind/${id.take(2)}/$id.bin",
        mime = if (kind == "audio") "audio/mpeg" else "video/mp4",
        bytes = 1_000,
        thumbBytes = 0,
        available = true,
        positionMs = 0,
        createdAt = id.hashCode().toLong(),
        updatedAt = 0,
    )

    @Before
    fun seed() = runBlocking {
        container.db.itemDao().insert(item("a1", "audio", "Alpha recitation"))
        container.db.itemDao().insert(item("v1", "video", "Beta lecture"))
    }

    @After
    fun tearDown() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        container.db.clearAllTables()
    }

    @Test
    fun `the kind filter and the search narrow the list`() {
        compose.setContent { LibraryScreen(container, container.player) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Alpha recitation")).fetchSemanticsNodes().isNotEmpty()
        }

        // Audio is the default filter.
        compose.onNodeWithText("Alpha recitation").assertIsDisplayed()
        compose.onNodeWithText("Beta lecture").assertDoesNotExist()

        compose.onNodeWithText("Video").performClick()
        compose.onNodeWithText("Beta lecture").assertIsDisplayed()

        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("Search titles").performTextInput("Alpha")
        compose.onNodeWithText("Alpha recitation").assertExists()
        compose.onNodeWithText("Beta lecture").assertDoesNotExist()
    }
}
