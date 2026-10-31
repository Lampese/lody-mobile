package app.lody.android.chat

import androidx.compose.foundation.background
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

/** A transcript entry. Assistant replies render as Markdown blocks; user turns as plain text. */
sealed interface ChatEntry {
    val id: String

    data class User(override val id: String, val text: String) : ChatEntry
    data class Assistant(override val id: String, val markdown: MarkdownBlocks) : ChatEntry
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
        is ChatEntry.User -> 1 to tail.text.length
        null -> 0 to 0
    }
    LaunchedEffect(entries.size, tailSize) {
        val last = state.layoutInfo.totalItemsCount - 1
        if (following && last >= 0) state.scrollToItem(last, Int.MAX_VALUE)
    }
    LazyColumn(modifier, state = state, contentPadding = contentPadding) {
        entries.forEach { entry ->
            when (entry) {
                is ChatEntry.User -> item(key = entry.id, contentType = "user") { UserTurn(entry.text) }
                is ChatEntry.Assistant -> itemsIndexed(
                    entry.markdown.blocks,
                    key = { index, _ -> "${entry.id}#$index" },
                    contentType = { _, _ -> "markdown" },
                ) { _, block ->
                    MarkdownBlock(
                        block,
                        Modifier
                            .padding(horizontal = LodyLayout.screenPaddingX)
                            .padding(bottom = LodySpacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserTurn(text: String) {
    val colors = LodyTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.md),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text,
            style = LodyTheme.typography.reading,
            color = colors.textPrimary,
            modifier = Modifier
                .widthIn(max = LodyLayout.readableMaxWidth)
                .background(colors.fillSecondary, RoundedCornerShape(LodyRadius.lg))
                .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm),
        )
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
