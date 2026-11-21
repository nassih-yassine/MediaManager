package dev.mediamanager.mobile.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.ui.theme.ThemeFamily
import dev.mediamanager.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = SettingsStore(context)

    @Test
    fun `appearance defaults to slate and system`() = runTest {
        val appearance = store.appearance.first()
        assertEquals(ThemeFamily.SLATE, appearance.family)
        assertEquals(ThemeMode.SYSTEM, appearance.mode)
    }

    @Test
    fun `appearance round-trips`() = runTest {
        store.setAppearance(ThemeFamily.MOSS, ThemeMode.LIGHT)
        val appearance = store.appearance.first()
        assertEquals(ThemeFamily.MOSS, appearance.family)
        assertEquals(ThemeMode.LIGHT, appearance.mode)
    }

    @Test
    fun `download settings round-trip with defaults`() = runTest {
        assertFalse(store.wifiOnly.first())
        assertEquals(1080, store.maxHeight.first())
        store.setWifiOnly(true)
        store.setMaxHeight(720)
        assertTrue(store.wifiOnly.first())
        assertEquals(720, store.maxHeight.first())
    }

    @Test
    fun `update check bookkeeping defaults to never and nothing dismissed`() = runTest {
        assertEquals(0L, store.lastUpdateCheckAt.first())
        assertNull(store.dismissedUpdateTag.first())
    }

    @Test
    fun `update check time and dismissed tag round-trip`() = runTest {
        store.setLastUpdateCheckAt(1_700_000_000_000)
        store.setDismissedUpdateTag("android-v0.9.0")

        assertEquals(1_700_000_000_000, store.lastUpdateCheckAt.first())
        assertEquals("android-v0.9.0", store.dismissedUpdateTag.first())

        store.setDismissedUpdateTag(null)
        assertNull(store.dismissedUpdateTag.first())
        // DataStore is shared across tests in this class; leave it at the defaults.
        store.setLastUpdateCheckAt(0)
    }

    @Test
    fun `recent urls are newest first, deduped and capped`() = runTest {
        repeat(12) { store.addRecentUrl("https://youtu.be/$it") }
        store.addRecentUrl("https://youtu.be/3")
        val urls = store.recentUrls.first()
        assertEquals(10, urls.size)
        assertEquals("https://youtu.be/3", urls.first())
        assertEquals(urls.size, urls.toSet().size)
    }
}
