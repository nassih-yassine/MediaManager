package dev.mediamanager.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.download.YtDlpExtractor
import dev.mediamanager.mobile.ui.components.KeyValueRow
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.components.Segmented
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import dev.mediamanager.mobile.ui.theme.ThemeFamily
import dev.mediamanager.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private val heights = listOf(720, 1080, 1440, 2160)

@Composable
fun SettingsScreen(container: AppContainer, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(
        initialValue = dev.mediamanager.mobile.data.Appearance(ThemeFamily.SLATE, ThemeMode.SYSTEM),
    )
    val wifiOnly by container.settings.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val maxHeight by container.settings.maxHeight.collectAsStateWithLifecycle(initialValue = 1080)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = colors.ink, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = colors.accent) }
        }

        SectionTitle("Appearance")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Theme", color = colors.ink)
            Spacer(Modifier.weight(1f))
            SelectMenu(
                label = "Theme",
                options = ThemeFamily.entries.map { family ->
                    family to family.key.replaceFirstChar { c -> c.uppercase() }
                },
                selected = appearance.family,
                onSelect = { family -> scope.launch { container.settings.setAppearance(family, appearance.mode) } },
            )
        }
        Spacer(Modifier.height(12.dp))
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
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Downloads")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi only", color = colors.ink)
                Text(
                    "Jobs wait, without spending an attempt, until Wi-Fi is back.",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
            Switch(
                checked = wifiOnly,
                onCheckedChange = { scope.launch { container.settings.setWifiOnly(it) } },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Max video height", color = colors.ink)
            Spacer(Modifier.weight(1f))
            SelectMenu(
                label = "Height",
                options = heights.map { it to "${it}p" },
                selected = maxHeight,
                onSelect = { scope.launch { container.settings.setMaxHeight(it) } },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Storage")
        Text(
            "The library reports its own size. Re-check lives on the Storage tab.",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("About")
        KeyValueRow("Version", "0.1.0")
        KeyValueRow("Extractor", YtDlpExtractor.version() ?: "bundled")
        Spacer(Modifier.height(8.dp))
        Text(
            "MediaManager is local only: no account, no server, no sync. " +
                "Downloads use yt-dlp and ffmpeg (GPL-3.0).",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Licensed under GPL-3.0. Source: github.com/iomi/MediaManager",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}
