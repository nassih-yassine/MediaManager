package dev.mediamanager.mobile.ui.player

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
class ResumeDialogTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `resume and start over are distinct actions`() {
        var resumed = false
        var started = false
        compose.setContent {
            ResumeDialog(
                positionMs = 12 * 60_000L,
                onResume = { resumed = true },
                onStartOver = { started = true },
            )
        }

        compose.onNodeWithText("Resume").assertIsDisplayed().performClick()
        assertTrue(resumed)
        assertTrue(!started)
    }
}
