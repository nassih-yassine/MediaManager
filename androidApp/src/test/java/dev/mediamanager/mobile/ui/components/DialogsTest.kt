package dev.mediamanager.mobile.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DialogsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `confirm dialog shows its copy and confirms`() {
        var confirmed = false
        compose.setContent {
            ConfirmDialog(
                title = "Delete this item?",
                body = "The file leaves the phone.",
                confirmLabel = "Delete",
                onConfirm = { confirmed = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("Delete this item?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `select menu opens and returns the chosen option`() {
        var chosen = 0
        compose.setContent {
            SelectMenu(
                label = "Speed",
                options = listOf(1 to "1×", 2 to "2×"),
                selected = 1,
                onSelect = { chosen = it },
            )
        }

        compose.onNodeWithText("1×").performClick()
        compose.onNodeWithText("2×").performClick()

        assertTrue(chosen == 2)
    }
}
