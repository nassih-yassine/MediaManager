package dev.mediamanager.mobile

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.mediamanager.mobile.ui.AppShell

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "dev.mediamanager.mobile.EXTRA_TAB"
    }

    private val sharedLink = mutableStateOf<String?>(null)

    /** Held by the system splash until the first screen is actually ready. */
    private val appReady = mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !appReady.value }
        enableEdgeToEdge()
        maybeAskForNotifications()
        val container = (application as App).container
        sharedLink.value = extractLink(intent)
        val openPlayerOnStart = intent?.getBooleanExtra(
            dev.mediamanager.mobile.widget.NowPlayingWidget.EXTRA_OPEN_PLAYER,
            false,
        ) == true
        val startTab = intent?.getStringExtra(EXTRA_TAB)
        setContent {
            AppShell(
                startTab = startTab,
                container = container,
                player = container.player,
                sharedUrl = sharedLink.value,
                onSharedHandled = { sharedLink.value = null },
                onReady = { appReady.value = true },
                openPlayerOnStart = openPlayerOnStart,
            )
        }
    }

    override fun onStop() {
        (application as App).container.player.savePosition()
        super.onStop()
    }

    /** Home press while a video plays keeps it going in a PiP window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val now = (application as App).container.player.nowPlaying.value ?: return
        if (!now.isPlaying || !now.isVideo) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedLink.value = extractLink(intent)
        // A widget tap while the app is alive arrives here, not in onCreate.
        if (intent.getBooleanExtra(
                dev.mediamanager.mobile.widget.NowPlayingWidget.EXTRA_OPEN_PLAYER,
                false,
            )
        ) {
            (application as App).container.player.openPlayer()
        }
    }

    /** Pulls the first http(s) link out of a share payload. */
    private fun extractLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://\\S+").find(text)?.value
    }

    private fun maybeAskForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
