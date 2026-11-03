package app.lody.android.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.lody.android.R
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme
import app.lody.android.designsystem.LodyWorkingGrid
import app.lody.android.runtime.TurnItem

/**
 * Lazy rows for [blocks] of one agent turn, web-style: prose renders as Markdown blocks;
 * an activity group is a disclosure row ("Ran 3 commands · Read 2 files ›") whose steps follow
 * as sibling rows when open — never a nested scroller. Rows animate in and out with the list
 * ([animateItem]) so a toggle reads as one native motion.
 */
internal fun LazyListScope.turnBlocks(
    entry: ChatEntry.Assistant,
    blocks: List<TurnLayout.Block>,
    open: Map<String, Boolean>,
    toggle: (String, Boolean) -> Unit,
) {
    val active = if (entry.working) entry.layout.blocks.lastOrNull()?.key else null
    for (block in blocks) {
        when (block) {
            is TurnLayout.Content -> content(entry, block)
            is TurnLayout.Activity -> {
                val expanded = open[block.key] ?: block.opensByDefault
                val running = block.key == active
                item(key = block.key, contentType = "disclosure") {
                    Disclosure(activityLabel(block, running), expanded, Modifier.animateItem(), shimmer = running) { toggle(block.key, it) }
                }
                if (expanded) {
                    for (step in block.items) {
                        val key = "${block.key}:${step.id}"
                        when (step) {
                            is TurnItem.Thought -> prose(key, entry.prose[step.id], secondary = true)
                            is TurnItem.Tool -> item(key = key, contentType = "step") {
                                ToolStep(step, running && step == block.items.last(), Modifier.animateItem())
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.content(entry: ChatEntry.Assistant, block: TurnLayout.Content) {
    when (val value = block.item) {
        is TurnItem.Text, is TurnItem.ProposedPlan -> prose(block.key, entry.prose[value.id], secondary = false)
        is TurnItem.Checklist -> item(key = block.key, contentType = "checklist") { Checklist(value, Modifier.animateItem()) }
        is TurnItem.Notice -> item(key = block.key, contentType = "notice") { Notice(value, Modifier.animateItem()) }
        is TurnItem.Tool -> item(key = block.key, contentType = "step") { ToolStep(value, entry.working && value.status == "in_progress", Modifier.animateItem()) }
        is TurnItem.Thought -> prose(block.key, entry.prose[value.id], secondary = true)
    }
}

/** Each top-level Markdown block is its own lazy row, so streaming re-renders only the tail. */
private fun LazyListScope.prose(key: String, markdown: MarkdownBlocks?, secondary: Boolean) {
    val blocks = markdown?.blocks ?: return
    blocks.forEachIndexed { index, text ->
        item(key = "$key#$index", contentType = "markdown") {
            MarkdownBlock(
                text,
                Modifier
                    .animateItem()
                    .padding(horizontal = TranscriptMargin)
                    .padding(bottom = LodySpacing.md),
                secondary = secondary,
            )
        }
    }
}

/**
 * Web's process disclosure: the label leads, the chevron trails and turns down when open. Compact
 * secondary type, no fill; the whole row is the target and announces its state.
 */
@Composable
internal fun Disclosure(label: String, expanded: Boolean, modifier: Modifier = Modifier, shimmer: Boolean = false, onToggle: (Boolean) -> Unit) {
    val colors = LodyTheme.colors
    val state = stringResource(if (expanded) R.string.home_tree_expanded else R.string.home_tree_collapsed)
    val angle by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(role = Role.Button) { onToggle(!expanded) }
            .semantics { stateDescription = state }
            .padding(horizontal = TranscriptMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs),
    ) {
        Text(
            label,
            style = LodyTheme.typography.footnote,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).then(if (shimmer) Modifier.shimmer() else Modifier),
        )
        Icon(LodyIcons.Expand, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.xs).rotate(angle))
    }
}

/** Web's live status at the end of a running turn: the working grid and what it is doing. */
@Composable
internal fun LiveStatus(thinking: Boolean, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = TranscriptMargin, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        LodyWorkingGrid()
        Text(
            stringResource(if (thinking) R.string.chat_activity_thinking else R.string.chat_working),
            style = LodyTheme.typography.footnote,
            color = colors.textTertiary,
            modifier = Modifier.shimmer(),
        )
    }
}

/**
 * One tool step as a sentence: the verb in its tense ("Ran" / "Running"), then its target — a
 * file name for file steps, the command in code type for commands, else the agent's title. Edits
 * add their ±lines; a failure or a pending approval says so in its tone.
 */
@Composable
internal fun ToolStep(item: TurnItem.Tool, running: Boolean, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val inProgress = running || item.status == "pending" || item.status == "in_progress"
    val verb = stepVerb(item.kind, inProgress)
    val file = item.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() && item.kind in FileKinds }
    // A title already opening with a capitalized word is an authored label and keeps it (web).
    val authored = item.title.matches(Regex("^[A-Z][a-z]+[\\s:(].*", RegexOption.DOT_MATCHES_ALL))
    val text = buildAnnotatedString {
        when {
            file != null -> {
                verb?.let { withStyle(SpanStyle(color = colors.textSecondary)) { append(it); append(' ') } }
                withStyle(SpanStyle(color = colors.textTertiary)) { append(file) }
            }
            verb != null && !authored && item.title.isNotBlank() -> {
                withStyle(SpanStyle(color = colors.textSecondary)) { append(verb); append(' ') }
                val command = item.kind == "execute" || item.kind == "bash"
                withStyle(SpanStyle(color = colors.textTertiary, fontFamily = if (command) type.code.fontFamily else null)) { append(item.title) }
            }
            else -> withStyle(SpanStyle(color = colors.textSecondary)) { append(item.title.ifBlank { verb.orEmpty() }) }
        }
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = TranscriptMargin, vertical = LodySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Text(
            text,
            style = type.footnote,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).then(if (inProgress) Modifier.shimmer() else Modifier),
        )
        val added = item.added ?: 0
        val removed = item.removed ?: 0
        if (added + removed > 0) {
            val digits = type.footnote.copy(fontFeatureSettings = "tnum")
            Text("+$added", style = digits, color = colors.diffAddedText)
            Text("−$removed", style = digits, color = colors.diffRemovedText)
        }
        when {
            item.awaitingPermission -> Text(stringResource(R.string.chat_step_awaiting), style = type.footnote, color = colors.warningDefault)
            item.status == "failed" -> Text(stringResource(R.string.chat_step_failed), style = type.footnote, color = colors.dangerDefault)
        }
    }
}

private val FileKinds = setOf("read", "edit", "write", "delete", "move")

@Composable
private fun stepVerb(kind: String, running: Boolean): String? {
    val (done, now) = when (kind) {
        "execute", "bash" -> R.string.chat_step_ran to R.string.chat_step_running
        "read" -> R.string.chat_step_read to R.string.chat_step_reading
        "edit", "write" -> R.string.chat_step_edited to R.string.chat_step_editing
        "delete" -> R.string.chat_step_deleted to R.string.chat_step_deleting
        "move" -> R.string.chat_step_moved to R.string.chat_step_moving
        "search" -> R.string.chat_step_searched to R.string.chat_step_searching
        "fetch" -> R.string.chat_step_fetched to R.string.chat_step_fetching
        else -> return null
    }
    return stringResource(if (running) now else done)
}

/** Web's group header copy; a thought-only group reads "Thought" / "Thinking…". */
@Composable
private fun activityLabel(block: TurnLayout.Activity, running: Boolean): String {
    val summary = block.summary
    val parts = buildList {
        if (summary.commands > 0) add(pluralStringResource(R.plurals.chat_activity_commands, summary.commands, summary.commands))
        if (summary.reads > 0) add(pluralStringResource(R.plurals.chat_activity_reads, summary.reads, summary.reads))
        if (summary.edits > 0) add(pluralStringResource(R.plurals.chat_activity_edits, summary.edits, summary.edits))
        if (summary.searches > 0) add(pluralStringResource(R.plurals.chat_activity_searches, summary.searches, summary.searches))
        if (summary.fetches > 0) add(pluralStringResource(R.plurals.chat_activity_fetches, summary.fetches, summary.fetches))
        if (summary.others > 0) add(pluralStringResource(R.plurals.chat_activity_tools, summary.others, summary.others))
    }
    if (parts.isNotEmpty()) return parts.joinToString(" · ")
    return stringResource(if (running) R.string.chat_activity_thinking else R.string.chat_activity_thought)
}

/** The agent's checklist: done items checked and dimmed, the current one emphasized. */
@Composable
private fun Checklist(item: TurnItem.Checklist, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().padding(horizontal = TranscriptMargin).padding(bottom = LodySpacing.md),
        verticalArrangement = Arrangement.spacedBy(LodySpacing.xs),
    ) {
        for ((content, status) in item.entries) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                val done = status == "completed"
                Icon(
                    if (done) LodyIcons.Check else LodyIcons.StateWaiting,
                    contentDescription = null,
                    tint = if (done) colors.successDefault else colors.iconTertiary,
                    modifier = Modifier.padding(top = 2.dp).size(LodyIconSize.sm),
                )
                Text(
                    content,
                    style = LodyTheme.typography.subhead,
                    color = when {
                        done -> colors.textTertiary
                        status == "in_progress" -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                )
            }
        }
    }
}

/** A failure the turn ended with, in the danger tone; other notices stay quiet. */
@Composable
private fun Notice(item: TurnItem.Notice, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = TranscriptMargin).padding(bottom = LodySpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Icon(LodyIcons.Alert, contentDescription = null, tint = if (item.failure) colors.dangerDefault else colors.iconTertiary, modifier = Modifier.padding(top = 2.dp).size(LodyIconSize.sm))
        Text(item.text, style = LodyTheme.typography.subhead, color = if (item.failure) colors.dangerDefault else colors.textSecondary)
    }
}

/** Web's agent shimmer, as a soft pulse: only text that is still happening moves. */
@Composable
private fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer-alpha",
    )
    return alpha(alpha)
}
