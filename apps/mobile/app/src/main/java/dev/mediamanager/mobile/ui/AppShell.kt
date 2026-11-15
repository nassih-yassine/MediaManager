package dev.mediamanager.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Appearance
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.ui.add.AddScreen
import dev.mediamanager.mobile.ui.downloads.DownloadsScreen
import dev.mediamanager.mobile.ui.library.LibraryScreen
import dev.mediamanager.mobile.ui.player.MiniPlayer
import dev.mediamanager.mobile.ui.player.NowPlayingSheet
import dev.mediamanager.mobile.ui.playlists.PlaylistsScreen
import dev.mediamanager.mobile.ui.settings.SettingsScreen
import dev.mediamanager.mobile.ui.storage.StorageScreen
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import dev.mediamanager.mobile.ui.theme.MmTheme
import dev.mediamanager.mobile.ui.theme.ThemeFamily
import dev.mediamanager.mobile.ui.theme.ThemeMode

private enum class Tab(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Default.LibraryMusic),
    PLAYLISTS("Playlists", Icons.Default.PlaylistPlay),
    ADD("Add", Icons.Default.AddCircle),
    DOWNLOADS("Queue", Icons.Default.Download),
    STORAGE("Storage", Icons.Default.Storage),
}

@Composable
fun AppShell(container: AppContainer, player: PlayerRepository) {
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(
        initialValue = Appearance(ThemeFamily.SLATE, ThemeMode.SYSTEM),
    )
    MmTheme(appearance.family, appearance.mode) {
        var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY.name) }
        var settingsOpen by remember { mutableStateOf(false) }
        var nowPlayingOpen by remember { mutableStateOf(false) }
        val colors = LocalAppColors.current
        val current = Tab.valueOf(tab)
        val filesDir = container.appContext.filesDir

        Scaffold(
            containerColor = colors.canvas,
            topBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.canvas)
                        .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "MediaManager",
                        color = colors.ink,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.muted)
                    }
                }
            },
            bottomBar = {
                Column {
                    MiniPlayer(player = player, filesDir = filesDir, onOpen = { nowPlayingOpen = true })
                    NavigationBar(containerColor = colors.panel, contentColor = colors.ink) {
                        Tab.entries.forEach { entry ->
                            NavigationBarItem(
                                selected = current == entry,
                                onClick = { tab = entry.name },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = {
                                    Text(
                                        entry.label,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.accentInk,
                                    selectedTextColor = colors.accent,
                                    indicatorColor = colors.accent,
                                    unselectedIconColor = colors.muted,
                                    unselectedTextColor = colors.muted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (current) {
                    Tab.LIBRARY -> LibraryScreen(container, player)
                    Tab.PLAYLISTS -> PlaylistsScreen(container, player)
                    Tab.ADD -> AddScreen(container)
                    Tab.DOWNLOADS -> DownloadsScreen(container)
                    Tab.STORAGE -> StorageScreen(container, player)
                }
            }
        }

        if (settingsOpen) {
            SettingsScreen(container, onClose = { settingsOpen = false })
        }
        if (nowPlayingOpen) {
            NowPlayingSheet(player = player, filesDir = filesDir, onClose = { nowPlayingOpen = false })
        }
    }
}
