package dev.mediamanager.mobile.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class HomeScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container = AppContainer(ApplicationProvider.getApplicationContext())

    private fun item(id: String, title: String) = ItemEntity(
        id = id,
        kind = "audio",
        title = title,
        path = "library/audio/${id.take(2)}/$id.mp3",
        mime = "audio/mpeg",
        bytes = 1_000,
        thumbBytes = 0,
        available = true,
        positionMs = 0,
        createdAt = id.hashCode().toLong(),
        updatedAt = 0,
    )

    @Before
    fun seed() = runBlocking {
        container.db.itemDao().insert(item("a1", "Zebra recitation"))
        container.db.itemDao().insert(item("a2", "Alpha recitation"))
    }

    @After
    fun tearDown() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        container.db.clearAllTables()
    }

    @Test
    fun `home lists the newest items and rows are tappable`() {
        compose.setContent { HomeScreen(container, container.player, onAdd = {}) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Recently added")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Recently added").assertIsDisplayed()
        compose.onNodeWithText("2 items").assertIsDisplayed()
        compose.onNodeWithText("Zebra recitation").assertIsDisplayed()
        compose.onNodeWithText("Zebra recitation").performClick()
    }
}
