package dev.mediamanager.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import java.io.File

@Composable
fun MmSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun Thumb(item: Item, filesDir: File, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val file = item.thumbPath?.let { File(filesDir, it) }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null && file.exists()) {
            coil.compose.AsyncImage(
                model = file,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (item.isAudio) Icons.Default.MusicNote else Icons.Default.Videocam,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onAction,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentInk,
                ),
            ) { Text(actionLabel) }
        }
    }
}

@Composable
fun <T> SelectMenu(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: label
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selectedLabel, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            color = if (value == selected) colors.accent else colors.ink,
                            fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(value)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, text) ->
            val active = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) colors.accent else colors.panel2)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text,
                    color = if (active) colors.accentInk else colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Confirmation sheet. Same anatomy as the playlist sheets: title, one line of
 * context, then a ghost Cancel beside a filled primary action.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.DeleteOutline,
) {
    val colors = LocalAppColors.current
    // Theme accent, not a fixed red: every other primary action in the app follows it.
    val tint = colors.accent
    val onTint = colors.accentInk

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        // A landscape phone would stretch this sheet edge to edge.
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    title,
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                body,
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("Cancel", color = colors.muted)
                }
                androidx.compose.material3.Button(
                    onClick = { onConfirm(); onDismiss() },
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = tint,
                        contentColor = onTint,
                    ),
                ) {
                    Text(confirmLabel)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A text action with a leading glyph, at the 18 dp size the rest of the app uses. */
@Composable
fun MmTextButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: androidx.compose.ui.graphics.Color = LocalAppColors.current.accent,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) color else LocalAppColors.current.muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text, color = if (enabled) color else LocalAppColors.current.muted)
    }
}

@Composable
fun SectionTitle(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LocalAppColors.current.muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text.uppercase(),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.muted,
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.muted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(value, color = colors.ink, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ScreenHeader(title: String, actions: @Composable () -> Unit = {}) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}
