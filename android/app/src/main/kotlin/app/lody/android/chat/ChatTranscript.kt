package app.lody.android.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lody.android.R
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodySize
import app.lody.android.home.Avatar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodyRadius
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme

/** Who wrote a turn and when, for its header: avatar (or agent mark), name, time. */
data class TurnIdentity(val name: String, val image: String? = null, val time: String = "")

/** A user turn's delivery as web's `UserMessageSendStatus` shows it. */
enum class Delivery { Sending, Sent, Queued, Seen, Failed }

/** A transcript entry. Assistant replies render as Markdown blocks; user turns as plain text. */
sealed interface ChatEntry {
    val id: String

    data class User(override val id: String, val text: String, val who: TurnIdentity, val delivery: Delivery = Delivery.Sent) : ChatEntry
    /** [worked] is "Worked for 8s" once the turn finished; empty while streaming. */
    data class Assistant(override val id: String, val markdown: MarkdownBlocks, val who: TurnIdentity, val worked: String = "") : ChatEntry
}

/**
 * The chat transcript. Every Markdown block is its own lazy item keyed by message and
 * block index, so a streaming reply only re-renders its tail block. The list follows new
 * content while the reader is at the bottom and stops when they scroll up.
 */
@Composable
fun ChatTranscript(
    entries: List<ChatEntry>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyListState = rememberLazyListState(),
) {
    // Follow mode changes only through the reader's own scrolling: dragging up leaves it,
    // reaching the bottom again re-enters it. Growing content alone never turns it off.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.canScrollForward }
            .collect { (scrolling, more) -> if (scrolling) following = !more }
    }
    val tail = entries.lastOrNull()
    val tailSize = when (tail) {
        is ChatEntry.Assistant -> tail.markdown.blocks.size to (tail.markdown.blocks.lastOrNull()?.length ?: 0)
        is ChatEntry.User -> 1 to tail.text.length + tail.delivery.ordinal
        null -> 0 to 0
    }
    LaunchedEffect(entries.size, tailSize) {
        val last = state.layoutInfo.totalItemsCount - 1
        if (following && last >= 0) state.scrollToItem(last, Int.MAX_VALUE)
    }
    LazyColumn(modifier, state = state, contentPadding = contentPadding) {
        entries.forEach { entry ->
            when (entry) {
                is ChatEntry.User -> item(key = entry.id, contentType = "user") { UserTurn(entry) }
                is ChatEntry.Assistant -> {
                    itemsIndexed(
                        entry.markdown.blocks,
                        key = { index, _ -> "${entry.id}#$index" },
                        contentType = { _, _ -> "markdown" },
                    ) { _, block ->
                        MarkdownBlock(
                            block,
                            Modifier
                                .padding(horizontal = TranscriptMargin)
                                .padding(bottom = LodySpacing.md),
                        )
                    }
                    // The agent's identity closes its answer, as iOS's meta row: mark, name · model,
                    // time, and "Worked for …" once finished.
                    item(key = "${entry.id}#who", contentType = "agent-footer") { AgentFooter(entry.who, entry.worked) }
                }
            }
        }
    }
}

/** Narrower than the screen inset (web's mobile conversation runs close to the edges). */
val TranscriptMargin = LodySpacing.md

/**
 * Web's mobile user turn: name, time and delivery above a right-aligned bubble, with the
 * sender's avatar beside it.
 */
@Composable
private fun UserTurn(entry: ChatEntry.User) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TranscriptMargin, vertical = LodySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm, Alignment.End),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(LodySpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs)) {
                DeliveryMark(entry.delivery)
                Text(
                    listOf(entry.who.name, entry.who.time).filter { it.isNotEmpty() }.joinToString("  ·  "),
                    style = LodyTheme.typography.caption,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
            Text(
                entry.text,
                style = LodyTheme.typography.reading,
                color = colors.textPrimary,
                modifier = Modifier
                    .widthIn(max = LodyLayout.readableMaxWidth)
                    .background(colors.fillSecondary, RoundedCornerShape(LodyRadius.xl))
                    .padding(horizontal = LodySpacing.lg, vertical = LodySpacing.md),
            )
        }
        Avatar(entry.who.image, entry.who.name, LodySize.avatarMd, Modifier.padding(top = LodySpacing.lg))
    }
}

/** Web's delivery mark: a check once the agent picked the turn up, an open circle before. */
@Composable
private fun DeliveryMark(delivery: Delivery) {
    val colors = LodyTheme.colors
    val size = LodyIconSize.sm
    val label = stringResource(
        when (delivery) {
            Delivery.Sending -> R.string.chat_delivery_sending
            Delivery.Sent -> R.string.chat_delivery_sent
            Delivery.Queued -> R.string.chat_delivery_queued
            Delivery.Seen -> R.string.chat_delivery_seen
            Delivery.Failed -> R.string.chat_delivery_failed
        },
    )
    val mark = Modifier.size(size).semantics { contentDescription = label }
    when (delivery) {
        Delivery.Sending -> CircularProgressIndicator(mark.padding(LodySpacing.xxs), color = colors.iconTertiary, strokeWidth = 1.5.dp)
        Delivery.Seen -> Icon(LodyIcons.StateDone, contentDescription = label, tint = colors.accentDefault, modifier = Modifier.size(size))
        Delivery.Failed -> Icon(LodyIcons.Alert, contentDescription = label, tint = colors.dangerDefault, modifier = Modifier.size(size))
        Delivery.Queued -> Icon(LodyIcons.StateWaiting, contentDescription = label, tint = colors.iconTertiary, modifier = Modifier.size(size))
        Delivery.Sent -> Box(mark.padding(LodySpacing.xxs).border(1.dp, colors.iconTertiary, CircleShape))
    }
}

/** The agent's identity after its answer: mark, name (· model), time, and how long it worked. */
@Composable
private fun AgentFooter(who: TurnIdentity, worked: String) {
    val colors = LodyTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TranscriptMargin).padding(bottom = LodySpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Box(Modifier.size(LodySize.avatarMd).background(colors.fillSecondary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(LodyIcons.Agent, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
        }
        Text(who.name, style = LodyTheme.typography.subhead, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        val meta = listOf(who.time, worked).filter { it.isNotEmpty() }.joinToString("  ·  ")
        if (meta.isNotEmpty()) Text(meta, style = LodyTheme.typography.caption, color = colors.textTertiary, maxLines = 1)
    }
}

/**
 * One top-level Markdown block on design-system tokens. `retainState` keeps the previous
 * render while the renderer re-parses a growing block, so streaming text never flashes
 * back to the loading state.
 */
@Composable
fun MarkdownBlock(text: String, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val state = rememberMarkdownState(content = text, retainState = true)
    Markdown(
        markdownState = state,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = colors.textPrimary,
            codeBackground = colors.codeBg,
            inlineCodeBackground = colors.codeInlineBg,
            dividerColor = colors.borderSubtle,
            tableBackground = colors.fillTertiary,
        ),
        typography = markdownTypography(
            h1 = type.title,
            h2 = type.headline,
            h3 = type.bodyEmphasis,
            h4 = type.bodyEmphasis,
            h5 = type.subhead,
            h6 = type.subhead,
            text = type.reading,
            code = type.code,
            inlineCode = type.reading.copy(fontFamily = type.code.fontFamily),
            quote = type.reading.copy(color = colors.textSecondary, fontStyle = FontStyle.Italic),
            paragraph = type.reading,
            ordered = type.reading,
            bullet = type.reading,
            list = type.reading,
            textLink = TextLinkStyles(style = SpanStyle(color = colors.textLink)),
            table = type.bodySmall,
        ),
        components = markdownComponents(codeBlock = highlightedCodeBlock, codeFence = highlightedCodeFence),
    )
}
