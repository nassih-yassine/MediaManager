package dev.mediamanager.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mediamanager.mobile.ui.theme.ThemeFamily
import dev.mediamanager.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mm.settings")

data class Appearance(val family: ThemeFamily, val mode: ThemeMode)

class SettingsStore(private val context: Context) {

    private object Keys {
        val family = stringPreferencesKey("theme_family")
        val mode = stringPreferencesKey("theme_mode")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val maxHeight = intPreferencesKey("max_height")
        val recentUrls = stringPreferencesKey("recent_urls")
    }

    val appearance: Flow<Appearance> = context.dataStore.data.map { prefs ->
        Appearance(
            family = ThemeFamily.fromKey(prefs[Keys.family]),
            mode = ThemeMode.fromKey(prefs[Keys.mode]),
        )
    }

    suspend fun setAppearance(family: ThemeFamily, mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.family] = family.key
            prefs[Keys.mode] = mode.key
        }
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.wifiOnly] ?: false }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.wifiOnly] = value }
    }

    val maxHeight: Flow<Int> = context.dataStore.data.map { it[Keys.maxHeight] ?: 1080 }

    suspend fun setMaxHeight(value: Int) {
        context.dataStore.edit { it[Keys.maxHeight] = value }
    }

    val recentUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.recentUrls]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addRecentUrl(url: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.recentUrls]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            val next = (listOf(url) + current.filterNot { it == url }).take(10)
            prefs[Keys.recentUrls] = next.joinToString("\n")
        }
    }
}
