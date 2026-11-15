package dev.mediamanager.mobile.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Appearance
import dev.mediamanager.mobile.download.YtDlpExtractor
import dev.mediamanager.mobile.ui.components.KeyValueRow
import dev.mediamanager.mobile.ui.components.MmSurface
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.components.Segmented
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import dev.mediamanager.mobile.ui.theme.ThemeFamily
import dev.mediamanager.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private val heights = listOf(720, 1080, 1440, 2160)

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(
        initialValue = Appearance(ThemeFamily.SLATE, ThemeMode.SYSTEM),
    )
    val wifiOnly by container.settings.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val maxHeight by container.settings.maxHeight.collectAsStateWithLifecycle(initialValue = 1080)

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsCard("Appearance", Icons.Default.Palette) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", color = colors.ink, modifier = Modifier.weight(1f))
                SelectMenu(
                    label = "Theme",
                    options = ThemeFamily.entries.map { family ->
                        family to family.key.replaceFirstChar { it.uppercase() }
                    },
                    selected = appearance.family,
                    onSelect = { family -> scope.launch { container.settings.setAppearance(family, appearance.mode) } },
                )
            }
            HorizontalDivider(color = colors.line)
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "Mode",
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Segmented(
                    options = listOf(
                        ThemeMode.LIGHT.name to "Light",
                        ThemeMode.DARK.name to "Dark",
                        ThemeMode.SYSTEM.name to "System",
                    ),
                    selected = appearance.mode.name,
                    onSelect = { mode ->
                        scope.launch { container.settings.setAppearance(appearance.family, ThemeMode.valueOf(mode)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Downloads", Icons.Default.Download) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wi-Fi only", color = colors.ink)
                    Text(
                        "Jobs wait, without spending an attempt, until Wi-Fi is back.",
                        color = colors.muted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = { scope.launch { container.settings.setWifiOnly(it) } },
                )
            }
            HorizontalDivider(color = colors.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Max video height", color = colors.ink)
                    Text(
                        "Anything taller is scaled down when downloading.",
                        color = colors.muted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                SelectMenu(
                    label = "Height",
                    options = heights.map { it to "${it}p" },
                    selected = maxHeight,
                    onSelect = { scope.launch { container.settings.setMaxHeight(it) } },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Storage", Icons.Default.Storage) {
            Text(
                "The library reports its own size on the Storage tab, including thumbnails, playlist covers and temporary files.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("About", Icons.Default.Info) {
            KeyValueRow("Version", "0.1.0")
            KeyValueRow("Extractor", YtDlpExtractor.version() ?: "bundled")
            Text(
                "Local only: no account, no server, no sync. YouTube goes through NewPipe, other sites through the bundled yt-dlp and ffmpeg.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "GPL-3.0 · github.com/nassih-yassine/MediaManager",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title, icon)
        MmSurface(Modifier.fillMaxWidth()) {
            content()
        }
    }
}
