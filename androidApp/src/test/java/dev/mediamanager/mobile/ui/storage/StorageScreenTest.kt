package dev.mediamanager.mobile.ui.storage

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class StorageScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container = AppContainer(ApplicationProvider.getApplicationContext())

    private fun item(id: String, kind: String, title: String, bytes: Long) = ItemEntity(
        id = id,
        kind = kind,
        title = title,
        path = "library/$kind/${id.take(2)}/$id.bin",
        mime = "application/octet-stream",
        bytes = bytes,
        thumbBytes = 0,
        available = true,
        positionMs = 0,
        createdAt = 1,
        updatedAt = 0,
    )

    @Before
    fun seed() = runBlocking {
        container.db.itemDao().insert(item("a1", "audio", "Zebra recitation", 2_000_000))
        container.db.itemDao().insert(item("v1", "video", "Beta lecture", 5_000_000))
    }

    @After
    fun tearDown() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        container.db.clearAllTables()
    }

    @Test
    fun `storage shows the breakdown and the largest items`() {
        compose.setContent { StorageScreen(container, container.player) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("WHAT IS USING IT")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("WHAT IS USING IT").assertIsDisplayed()
        compose.onNodeWithText("LARGEST ITEMS").assertExists()
        compose.onNodeWithText("Re-check").assertExists()
    }
}
