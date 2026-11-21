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
class EmptyStateTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `empty state shows its copy and runs the action`() {
        var clicked = false
        compose.setContent {
            EmptyState(
                title = "Welcome to MediaManager",
                body = "Nothing here yet",
                actionLabel = "Paste a link",
                onAction = { clicked = true },
            )
        }

        compose.onNodeWithText("Welcome to MediaManager").assertIsDisplayed()
        compose.onNodeWithText("Paste a link").performClick()

        assertTrue(clicked)
    }
}
