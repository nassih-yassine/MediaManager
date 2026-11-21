package dev.mediamanager.mobile.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.mediamanager.mobile.ui.theme.colorsFor
import kotlinx.coroutines.launch

private val heights = listOf(720, 1080, 1440, 2160)

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit = {}, onOpenStorage: () -> Unit = {}) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(
        initialValue = Appearance(ThemeFamily.SLATE, ThemeMode.SYSTEM),
    )
    val wifiOnly by container.settings.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val maxHeight by container.settings.maxHeight.collectAsStateWithLifecycle(initialValue = 1080)
    val simultaneous by container.settings.concurrentDownloads.collectAsStateWithLifecycle(initialValue = 2)
    val libraryBytes by container.db.itemDao().observeTotalBytes().collectAsStateWithLifecycle(initialValue = 0L)

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsCard("Appearance", Icons.Default.Palette) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mode", color = colors.ink, modifier = Modifier.weight(1f))
                SelectMenu(
                    label = "Mode",
                    options = listOf(
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark",
                        ThemeMode.SYSTEM to "System",
                    ),
                    selected = appearance.mode,
                    onSelect = { mode -> scope.launch { container.settings.setAppearance(appearance.family, mode) } },
                )
            }
            HorizontalDivider(color = colors.line)
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "Theme",
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val dark = when (appearance.mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                ThemeFamily.entries.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { family ->
                            ThemeOption(
                                family = family,
                                dark = dark,
                                selected = appearance.family == family,
                                modifier = Modifier.weight(1f),
                                onSelect = {
                                    scope.launch { container.settings.setAppearance(family, appearance.mode) }
                                },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
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
            HorizontalDivider(color = colors.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Simultaneous downloads", color = colors.ink)
                    Text(
                        "Each download already uses four connections; two jobs overlap cleanly.",
                        color = colors.muted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                SelectMenu(
                    label = "Jobs",
                    options = listOf(1 to "1", 2 to "2", 3 to "3"),
                    selected = simultaneous,
                    onSelect = { scope.launch { container.settings.setConcurrentDownloads(it) } },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Storage", Icons.Default.Storage) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenStorage)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library storage", color = colors.ink)
                    Text(
                        "Total, breakdown and the largest items",
                        color = colors.muted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    dev.mediamanager.mobile.ui.format.formatBytes(libraryBytes),
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("About", Icons.Default.Info) {
            KeyValueRow("Version", dev.mediamanager.mobile.BuildConfig.VERSION_NAME, Modifier.padding(vertical = 12.dp))
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

/** A theme you can see before you pick it: its own panel, ink and accent. */
@Composable
private fun ThemeOption(family: ThemeFamily, dark: Boolean, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val palette = colorsFor(family, dark)
    val active = LocalAppColors.current.accent
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.canvas)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) active else palette.line,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
    ) {
        Column {
            Text(
                family.key.replaceFirstChar { it.uppercase() },
                color = palette.ink,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(palette.accent, palette.panel2, palette.line, palette.muted).forEach { dot ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(dot))
                }
            }
        }
    }
}
