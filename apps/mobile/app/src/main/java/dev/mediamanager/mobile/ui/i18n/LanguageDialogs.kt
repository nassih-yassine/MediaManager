package dev.mediamanager.mobile.ui.i18n

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.mediamanager.mobile.ui.theme.LocalAppColors

/**
 * Asked once on a fresh install. English is preselected; the answer is stored in
 * DataStore and can be changed later from Settings.
 */
@Composable
fun LanguageFirstRunDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current

    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.panel)
                .border(1.dp, colors.line, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text(
                strings.languageTitle,
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                strings.languageBody,
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            LanguageOption(
                label = strings.languageEnglish,
                active = selected == AppLanguage.ENGLISH,
                onClick = { onSelect(AppLanguage.ENGLISH) },
            )
            Spacer(Modifier.height(8.dp))
            LanguageOption(
                label = strings.languageArabic,
                active = selected == AppLanguage.ARABIC,
                onClick = { onSelect(AppLanguage.ARABIC) },
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentInk,
                ),
            ) {
                Text(strings.continueLabel)
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) colors.accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) colors.accent else colors.line,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (active) colors.ink else colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (active) colors.accent else colors.line),
        )
    }
}

/** Settings row: two chips, applied instantly. */
@Composable
fun LanguagePickerRow(current: AppLanguage, onPick: (AppLanguage) -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(strings.languageTitle, color = colors.ink)
            Text(
                strings.languageBody,
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("EN", current == AppLanguage.ENGLISH) { onPick(AppLanguage.ENGLISH) }
            Chip("ع", current == AppLanguage.ARABIC) { onPick(AppLanguage.ARABIC) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .width(44.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.accent else colors.panel2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) colors.accentInk else colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}
