package app.lody.android.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import app.lody.android.R
import app.lody.android.designsystem.*
import app.lody.android.runtime.FileChange

@Composable
internal fun ChangedFilesCard(files: List<FileChange>, expanded: Boolean, joinedTop: Boolean = false, onToggle: (Boolean) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val state = stringResource(if (expanded) R.string.home_tree_expanded else R.string.home_tree_collapsed)
    val rowHeight = LodySize.touchTarget
    Column(Modifier.padding(horizontal = TranscriptMargin).fillMaxWidth()
        .clip(RoundedCornerShape(topStart = if (joinedTop) 0.dp else LodySpacing.md, topEnd = if (joinedTop) 0.dp else LodySpacing.md, bottomStart = LodySpacing.md, bottomEnd = LodySpacing.md)).background(colors.fillTertiary)) {
        Row(Modifier.fillMaxWidth().height(rowHeight).clickable(role = Role.Button) { onToggle(!expanded) }
            .semantics { stateDescription = state }.padding(horizontal = LodySpacing.md),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
            Text(pluralStringResource(R.plurals.chat_changed_files, files.size, files.size),
                Modifier.weight(1f), style = type.subhead, color = colors.textPrimary)
            FileCounts(files.sumOf { it.added }, files.sumOf { it.removed })
            Icon(LodyIcons.Expand, null,
                Modifier.size(LodyIconSize.sm).rotate(if (expanded) 0f else -90f), tint = colors.iconSecondary)
        }
        if (expanded) {
            LazyColumn(Modifier.fillMaxWidth().height(rowHeight * minOf(files.size, 6)), userScrollEnabled = files.size > 6) {
                itemsIndexed(files) { _, file ->
                    Row(Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = LodySpacing.md),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                        Text(file.path, Modifier.weight(1f), style = type.footnote.copy(fontFamily = type.code.fontFamily),
                            color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FileCounts(file.added, file.removed)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCounts(added: Int, removed: Int) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val digits = type.footnote.copy(fontFamily = type.code.fontFamily)
    Text("+$added", style = digits, color = colors.diffAddedText)
    Text("−$removed", style = digits, color = colors.diffRemovedText)
}
