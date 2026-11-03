package app.lody.android.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import app.lody.android.runtime.StepBlock
import app.lody.android.runtime.StepDetail
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
import androidx.compose.runtime.setValue
import androidx.compose.material3.SegmentedButton
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
    details: StepDetails,
) {
    val active = if (entry.working) entry.layout.blocks.lastOrNull()?.key else null
    for (block in blocks) {
        when (block) {
            is TurnLayout.Content -> content(entry, block, open, toggle, details)
            is TurnLayout.Activity -> {
                // A running turn shows its work open; once it settles, groups rest closed (web).
                val expanded = open[block.key] ?: (entry.working || block.opensByDefault)
                val running = block.key == active
                item(key = block.key, contentType = "disclosure") {
                    Disclosure(activityLabel(block, running), expanded, Modifier.animateItem(), shimmer = running) { toggle(block.key, it) }
                }
                if (expanded) {
                    for (step in block.items) {
                        val key = "${block.key}:${step.id}"
                        when (step) {
                            is TurnItem.Thought -> prose(key, entry.prose[step.id], secondary = true)
                            is TurnItem.Tool -> step(entry, step, key, running && step == block.items.last(), open, toggle, details)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.content(
    entry: ChatEntry.Assistant,
    block: TurnLayout.Content,
    open: Map<String, Boolean>,
    toggle: (String, Boolean) -> Unit,
    details: StepDetails,
) {
    when (val value = block.item) {
        is TurnItem.Text, is TurnItem.ProposedPlan -> prose(block.key, entry.prose[value.id], secondary = false)
        is TurnItem.Checklist -> item(key = block.key, contentType = "checklist") { Checklist(value, Modifier.animateItem()) }
        is TurnItem.Notice -> item(key = block.key, contentType = "notice") { Notice(value, Modifier.animateItem()) }
        is TurnItem.Tool -> step(entry, value, block.key, entry.working && value.status == "in_progress", open, toggle, details)
        is TurnItem.Thought -> prose(block.key, entry.prose[value.id], secondary = true)
        is TurnItem.Task -> Unit
    }
}

/**
 * A step row, and when it is open its detail card as the next sibling row (web's ONE
 * `ToolDetailSheet`): the step's sentence is the disclosure, the card loads on first open.
 */
private fun LazyListScope.step(
    entry: ChatEntry.Assistant,
    tool: TurnItem.Tool,
    key: String,
    running: Boolean,
    open: Map<String, Boolean>,
    toggle: (String, Boolean) -> Unit,
    details: StepDetails,
) {
    val detailKey = "$key#detail"
    val expandable = tool.hasDetail && details.available
    val expanded = expandable && open[detailKey] == true
    item(key = key, contentType = "step") {
        ToolStep(
            tool, running, Modifier.animateItem(),
            expanded = if (expandable) expanded else null,
            onToggle = { toggle(detailKey, it) },
        )
    }
    if (expanded) {
        item(key = detailKey, contentType = "step-detail") { StepDetailCard(entry.id, tool, details, Modifier.animateItem()) }
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
internal fun ToolStep(
    item: TurnItem.Tool,
    running: Boolean,
    modifier: Modifier = Modifier,
    /** Non-null when the step opens a detail card: whether it is open. */
    expanded: Boolean? = null,
    onToggle: (Boolean) -> Unit = {},
) {
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
    val state = expanded?.let { stringResource(if (it) R.string.home_tree_expanded else R.string.home_tree_collapsed) }
    val angle by animateFloatAsState(if (expanded == true) 0f else -90f, label = "step-chevron")
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (expanded != null) Modifier
                    .heightIn(min = 32.dp)
                    .clickable(role = Role.Button) { onToggle(!expanded) }
                    .semantics { stateDescription = state.orEmpty() }
                else Modifier,
            )
            .padding(horizontal = TranscriptMargin, vertical = LodySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Text(
            text,
            style = type.footnote,
            maxLines = if (expanded == true) 6 else 2,
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
        // Disclosure label leads, chevron trails (web).
        if (expanded != null) Icon(LodyIcons.Expand, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.xs).rotate(angle))
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

/**
 * Loads and keeps step details for the transcript's life, keyed by step and status (a running
 * step's detail is fetched again once it settles). [available] is false where the screen has no
 * runtime to ask (previews, the stream probe).
 */
class StepDetails(private val load: (suspend (entryId: String, itemId: String) -> StepDetail)?) {
    val available: Boolean get() = load != null
    internal val cache = androidx.compose.runtime.mutableStateMapOf<String, StepDetail>()
    internal suspend fun fetch(entryId: String, itemId: String): StepDetail = load!!(entryId, itemId)
}

/**
 * Web's `ToolDetailSheet`: ONE raised card under the step, sections in content order — the
 * command in code type, its output, a diff's removed and added lines — with native text
 * selection, no header restating the step.
 */
@Composable
private fun StepDetailCard(entryId: String, tool: TurnItem.Tool, details: StepDetails, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val key = "${tool.id}:${tool.status}"
    var failed by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    val detail = details.cache[key]
    androidx.compose.runtime.LaunchedEffect(key) {
        if (detail != null) return@LaunchedEffect
        try { details.cache[key] = details.fetch(entryId, tool.id) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(LodySpacing.md)
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = TranscriptMargin)
            .padding(top = LodySpacing.xxs, bottom = LodySpacing.sm)
            .shadow(LodySpacing.xxs, shape, clip = false)
            .background(colors.bgRaised, shape)
            .border(0.5.dp, colors.borderSubtle, shape)
            .padding(LodySpacing.md),
        verticalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        when {
            failed -> Text(stringResource(R.string.chat_step_detail_failed), style = type.footnote, color = colors.textTertiary)
            detail == null -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(LodyIconSize.sm), color = colors.iconTertiary, strokeWidth = 1.5.dp)
            detail.blocks.isEmpty() -> Text(stringResource(R.string.chat_step_detail_empty), style = type.footnote, color = colors.textTertiary)
            else -> androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                    for (block in detail.blocks) DetailBlock(block)
                    if (detail.truncated) Text(stringResource(R.string.chat_step_detail_truncated), style = type.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(block: StepBlock) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val code = type.footnote.copy(fontFamily = type.code.fontFamily)
    when (block) {
        is StepBlock.Command -> Text("$ ${block.command}", style = code, color = colors.textPrimary)
        is StepBlock.Output -> {
            // Long output keeps its tail, where the result usually is.
            val lines = block.text.trimEnd().lines()
            val shown = if (lines.size > OutputLines) listOf("…") + lines.takeLast(OutputLines) else lines
            Text(shown.joinToString("\n"), style = code, color = colors.textSecondary)
            block.exitCode?.takeIf { it != 0 }?.let {
                Text(stringResource(R.string.chat_step_exit_code, it), style = type.caption, color = colors.dangerDefault)
            }
        }
        is StepBlock.Diff -> androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (block.path.isNotBlank()) Text(block.path.substringAfterLast('/'), style = type.caption, color = colors.textTertiary)
            val removed = block.oldText.lines().filter { it.isNotEmpty() }.take(DiffLines)
            val added = block.newText.lines().filter { it.isNotEmpty() }.take(DiffLines)
            for (line in removed) DiffLine("− $line", colors.diffRemovedText, colors.diffRemovedBg, code)
            for (line in added) DiffLine("+ $line", colors.diffAddedText, colors.diffAddedBg, code)
        }
        is StepBlock.Text -> Text(block.text, style = type.footnote, color = colors.textSecondary)
    }
}

@Composable
private fun DiffLine(text: String, color: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text, style = style, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = LodySpacing.xs),
    )
}

private const val OutputLines = 40
private const val DiffLines = 60

/**
 * The turn's output card: one raised card with a row per kind of output — its background tasks and
 * sub-agents (live spinner or stack glyph, "Waiting on N tasks" / "N tasks", the newest running
 * task's latest step) and its changed files ("3 files +135 −157"). Each row opens its own near
 * full-screen task sheet; changed files expand inline to at most six rows. Neutral tones; iOS
 * draws the same card.
 */
internal fun LazyListScope.turnOutput(entry: ChatEntry.Assistant, onOpenTasks: (String) -> Unit, onOpenFiles: (String) -> Unit, open: Map<String, Boolean>, toggle: (String, Boolean) -> Unit) {
    val tasks = entry.layout.tasks
    val files = if (entry.working) emptyList() else entry.files
    if (tasks.isEmpty() && files.isEmpty()) return
    val key = "${entry.id}#output"
    if (tasks.isNotEmpty()) {
        item(key = "$key:tasks", contentType = "output-row") {
            TaskCard(tasks, Modifier.animateItem().cardSegment(if (files.isEmpty()) Segment.Only else Segment.First)) { onOpenTasks(entry.id) }
        }
    }
    if (files.isNotEmpty()) {
        item(key = "$key:files", contentType = "output-row") {
            ChangedFilesCard(files, open["$key:files"] ?: false, joinedTop = tasks.isNotEmpty()) { toggle("$key:files", it) }
        }
    }
    item(key = "$key:end", contentType = "output-end") { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = LodySpacing.md)) }
}

/**
 * The line that closes a finished turn, always last: the agent mark, model and finish time (web's
 * footer shows config, not author identity).
 */
internal fun LazyListScope.turnFooter(entry: ChatEntry.Assistant) {
    if (entry.working) return
    val text = listOf(entry.who.name, entry.who.time).filter { it.isNotBlank() }.joinToString(" · ")
    if (text.isEmpty()) return
    item(key = "${entry.id}#footer", contentType = "footer") {
        Row(
            Modifier.animateItem().fillMaxWidth().padding(horizontal = TranscriptMargin, vertical = LodySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs),
        ) {
            Icon(LodyIcons.Agent, contentDescription = null, tint = LodyTheme.colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
            Text(text, style = LodyTheme.typography.footnote, color = LodyTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FilesRow(files: List<app.lody.android.runtime.FileChange>, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val digits = type.footnote.copy(fontFeatureSettings = "tnum")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Icon(LodyIcons.Diff, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
        Text(pluralStringResource(R.plurals.chat_files_count, files.size, files.size), style = type.subhead, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text("+${files.sumOf { it.added }}", style = digits, color = colors.diffAddedText)
        Text("−${files.sumOf { it.removed }}", style = digits, color = colors.diffRemovedText)
        Icon(LodyIcons.Forward, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
    }
}

/**
 * A turn's changed files in a near full-screen native sheet: one card of ruled rows, each the
 * file's name in code type, its folder, and its +/− counts.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun FilesSheet(files: List<app.lody.android.runtime.FileChange>, onDismiss: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.bgBase) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LodySpacing.xl),
        ) {
            item(key = "title") {
                Row(
                    Modifier.padding(horizontal = TranscriptMargin).padding(bottom = LodySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                ) {
                    Text(pluralStringResource(R.plurals.chat_files_count, files.size, files.size), style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    val digits = type.subhead.copy(fontFeatureSettings = "tnum")
                    Text("+${files.sumOf { it.added }}", style = digits, color = colors.diffAddedText)
                    Text("−${files.sumOf { it.removed }}", style = digits, color = colors.diffRemovedText)
                }
            }
            files.forEachIndexed { index, file ->
                item(key = file.path) {
                    val segment = when {
                        files.size == 1 -> Segment.Only
                        index == 0 -> Segment.First
                        index == files.lastIndex -> Segment.Last
                        else -> Segment.Middle
                    }
                    Row(
                        Modifier.animateItem().cardSegment(segment).fillMaxWidth().heightIn(min = 52.dp)
                            .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                    ) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(file.path.substringAfterLast('/'), style = type.subhead.copy(fontFamily = type.code.fontFamily), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            val folder = file.path.substringBeforeLast('/', "")
                            if (folder.isNotEmpty()) Text(folder, style = type.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                        }
                        val digits = type.footnote.copy(fontFeatureSettings = "tnum")
                        Text("+${file.added}", style = digits, color = colors.diffAddedText)
                        Text("−${file.removed}", style = digits, color = colors.diffRemovedText)
                    }
                }
            }
        }
    }
}

private enum class Segment { Only, First, Middle, Last }

/** One row's slice of the task card: rounded only where the card starts or ends, ruled between rows. */
@Composable
private fun Modifier.cardSegment(segment: Segment): Modifier {
    val colors = LodyTheme.colors
    val radius = LodySpacing.md
    val shape = when (segment) {
        Segment.Only -> androidx.compose.foundation.shape.RoundedCornerShape(radius)
        Segment.First -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = radius, topEnd = radius)
        Segment.Middle -> androidx.compose.ui.graphics.RectangleShape
        Segment.Last -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    }
    val rule = colors.borderSubtle
    return this
        .padding(horizontal = TranscriptMargin)
        .background(colors.fillTertiary, shape)
        .then(
            if (segment == Segment.Middle || segment == Segment.Last) Modifier.drawBehind {
                drawLine(rule, androidx.compose.ui.geometry.Offset(LodySpacing.md.toPx(), 0f), androidx.compose.ui.geometry.Offset(size.width - LodySpacing.md.toPx(), 0f), 0.5.dp.toPx())
            } else Modifier,
        )
        .clip(shape)
}

@Composable
private fun TaskCard(tasks: List<TurnItem.Task>, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val running = tasks.count { it.running }
    val latest = tasks.lastOrNull { it.running && it.latest.isNotBlank() }?.latest
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        // The mark and the copy cross-fade as tasks start and settle.
        androidx.compose.animation.Crossfade(running > 0, label = "tasks-mark") { live ->
            if (live) androidx.compose.material3.CircularProgressIndicator(Modifier.size(LodyIconSize.sm).padding(1.dp), color = colors.iconSecondary, strokeWidth = 1.5.dp)
            else Icon(LodyIcons.Sessions, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).animateContentSize()) {
            Text(tasksLabel(tasks), style = type.subhead, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            latest?.let { Text(it, style = type.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.shimmer()) }
        }
        Icon(LodyIcons.Forward, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
    }
}

@Composable
private fun tasksLabel(tasks: List<TurnItem.Task>): String {
    val running = tasks.count { it.running }
    val background = tasks.all { it.background }
    return when {
        running > 0 && background -> pluralStringResource(R.plurals.chat_tasks_waiting_background, running, running)
        running > 0 -> pluralStringResource(R.plurals.chat_tasks_waiting, running, running)
        background -> pluralStringResource(R.plurals.chat_tasks_count_background, tasks.size, tasks.size)
        else -> pluralStringResource(R.plurals.chat_tasks_count, tasks.size, tasks.size)
    }
}

@Composable
private fun TaskRow(task: TurnItem.Task, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val actor = task.actor.ifBlank { stringResource(R.string.chat_task_actor) }
    val meta = when (task.state) {
        "failed" -> stringResource(R.string.chat_step_failed)
        "cancelled" -> stringResource(R.string.chat_task_cancelled)
        "unknown" -> stringResource(R.string.chat_task_unknown)
        else -> ""
    }
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LodySpacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
            TaskMark(task.state)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textPrimary)) { append(actor) }
                    if (task.description.isNotBlank() && task.description != actor) {
                        withStyle(SpanStyle(color = colors.textSecondary)) { append(" · ") ; append(task.description) }
                    }
                },
                style = type.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (meta.isNotEmpty()) Text(meta, style = type.footnote, color = if (task.state == "failed") colors.dangerDefault else colors.textTertiary)
            Icon(LodyIcons.Forward, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.xs))
        }
        if (task.running && task.latest.isNotBlank()) {
            Text(
                task.latest, style = type.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = LodyIconSize.sm + LodySpacing.sm).shimmer(),
            )
        }
    }
}

/** A task's state as a mark: live, done, failed, stopped, lost, or not started (web `TaskMark`). */
@Composable
private fun TaskMark(state: String) {
    val colors = LodyTheme.colors
    val size = Modifier.size(LodyIconSize.sm)
    when (state) {
        "running" -> androidx.compose.material3.CircularProgressIndicator(size.padding(2.dp), color = colors.iconSecondary, strokeWidth = 1.5.dp)
        "completed" -> Icon(LodyIcons.Check, contentDescription = null, tint = colors.successDefault, modifier = size)
        "failed" -> Icon(LodyIcons.Alert, contentDescription = null, tint = colors.dangerDefault, modifier = size)
        else -> Icon(LodyIcons.StateWaiting, contentDescription = null, tint = colors.iconTertiary, modifier = size)
    }
}

/**
 * A turn's tasks in a near full-screen native sheet, split like web's tabs: a segmented control
 * between Subagents and Tasks when the turn has both, then the task list; a row slides to that
 * task's run inside the sheet and Back (or the arrow) slides back. Tasks are read live from
 * [tasks], so a running task keeps updating in either view.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TasksSheet(tasks: List<TurnItem.Task>, onDismiss: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val both = tasks.any { it.group == "tasks" } && tasks.any { it.group != "tasks" }
    var group by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(if (tasks.all { it.group == "tasks" }) "tasks" else "subagents") }
    var shown by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = shown != null) { shown = null }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.bgBase) {
        androidx.compose.animation.AnimatedContent(
            targetState = shown,
            transitionSpec = {
                val forward = targetState != null
                (androidx.compose.animation.slideInHorizontally { width -> if (forward) width / 3 else -width / 3 } + androidx.compose.animation.fadeIn())
                    .togetherWith(androidx.compose.animation.slideOutHorizontally { width -> if (forward) -width / 3 else width / 3 } + androidx.compose.animation.fadeOut())
            },
            label = "tasks-sheet",
        ) { id ->
            val task = id?.let { wanted -> tasks.firstOrNull { it.id == wanted } }
            if (task != null) {
                TaskRun(task) { shown = null }
            } else androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                Text(
                    tasksLabel(tasks), style = type.headline, color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = TranscriptMargin).padding(bottom = LodySpacing.md),
                )
                if (both) {
                    androidx.compose.material3.SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(horizontal = TranscriptMargin).padding(bottom = LodySpacing.sm),
                    ) {
                        listOf("subagents" to R.string.chat_tasks_subagents, "tasks" to R.string.chat_tasks_others).forEachIndexed { index, (value, label) ->
                            val live = tasks.any { (it.group == "tasks") == (value == "tasks") && it.running }
                            SegmentedButton(
                                selected = group == value,
                                onClick = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                    group = value
                                },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 2),
                                icon = {
                                    // A live group carries a spinner, like web's tab; no count.
                                    if (live) androidx.compose.material3.CircularProgressIndicator(Modifier.size(LodyIconSize.xs), strokeWidth = 1.5.dp, color = colors.iconSecondary)
                                },
                            ) { Text(stringResource(label), style = type.subhead) }
                        }
                    }
                }
                val visible = tasks.filter { if (group == "tasks") it.group == "tasks" else it.group != "tasks" }
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = LodySpacing.xs, bottom = LodySpacing.xl),
                ) {
                    visible.forEachIndexed { index, task ->
                        item(key = task.id) {
                            TaskRow(
                                task,
                                Modifier.animateItem().cardSegment(
                                    when {
                                        visible.size == 1 -> Segment.Only
                                        index == 0 -> Segment.First
                                        index == visible.lastIndex -> Segment.Last
                                        else -> Segment.Middle
                                    },
                                ),
                            ) { shown = task.id }
                        }
                    }
                }
            }
        }
    }
}

/** One task's own run: its brief, then its steps as sentences and its text as prose; its result or error last. */
@Composable
private fun TaskRun(task: TurnItem.Task, onBack: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth().fillMaxHeight(0.92f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LodySpacing.xl),
    ) {
        item(key = "head") {
            androidx.compose.foundation.layout.Column(
                Modifier.padding(end = TranscriptMargin).padding(bottom = LodySpacing.md),
                verticalArrangement = Arrangement.spacedBy(LodySpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(LodyIcons.Back, contentDescription = stringResource(R.string.chat_tasks_back), tint = colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md))
                    }
                    TaskMark(task.state)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(LodySpacing.sm))
                    Text(task.actor.ifBlank { stringResource(R.string.chat_task_actor) }, style = type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (task.description.isNotBlank()) Text(task.description, style = type.subhead, color = colors.textSecondary, modifier = Modifier.padding(start = TranscriptMargin))
            }
        }
        if (task.steps.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(if (task.running) R.string.chat_task_waiting_first else R.string.chat_task_no_steps),
                    style = type.footnote, color = colors.textTertiary, modifier = Modifier.padding(horizontal = TranscriptMargin),
                )
            }
        }
        for (step in task.steps) {
            item(key = step.id) {
                when (step) {
                    is TurnItem.Tool -> ToolStep(step, task.running && step == task.steps.last(), Modifier.animateItem())
                    is TurnItem.Text -> Text(step.text, style = type.subhead, color = colors.textPrimary, modifier = Modifier.animateItem().padding(horizontal = TranscriptMargin, vertical = LodySpacing.xs))
                    is TurnItem.Thought -> Text(step.text, style = type.subhead, color = colors.textSecondary, modifier = Modifier.animateItem().padding(horizontal = TranscriptMargin, vertical = LodySpacing.xs))
                    else -> Unit
                }
            }
        }
        if (!task.running && task.result.isNotBlank()) {
            item(key = "result") {
                Text(
                    task.result, style = type.subhead,
                    color = if (task.state == "failed") colors.dangerDefault else colors.textPrimary,
                    modifier = Modifier.animateItem().padding(horizontal = TranscriptMargin).padding(top = LodySpacing.md),
                )
            }
        }
    }
}
