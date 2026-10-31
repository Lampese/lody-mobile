// The chat list: the app's home. Mirrors ios/App/SessionListScreen.swift.
//
// Simplicity: one line per chat, no separators, subtitles or timestamps; chats
// are indented so their titles align with the group title. Efficiency: the
// indent gutter shows a glyph only when a chat needs attention (running,
// waiting, failed, unread); collapsed groups surface a count and their most
// urgent state; search filters every group; long-press acts in place.

package app.lody.designpreview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.lody.android.designsystem.LodyElevations
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme
import app.lody.android.designsystem.shadow

enum class PreviewDestination { Components, Tokens }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    appearance: Int,
    onAppearanceChange: (Int) -> Unit,
    onNavigate: (PreviewDestination) -> Unit,
) {
    val colors = LodyTheme.colors
    var collapsed by rememberSaveable { mutableStateOf(SampleData.initiallyCollapsed) }
    var showingAll by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val groups = if (!searching) {
        SampleData.groups
    } else {
        SampleData.groups.mapNotNull { group ->
            group.chats.filter { it.title.contains(query.trim(), ignoreCase = true) }
                .takeIf { it.isNotEmpty() }
                ?.let { group.copy(chats = it) }
        }
    }

    Scaffold(
        containerColor = colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Account and workspaces" }) {
                        Box(
                            modifier = Modifier
                                .size(LodySize.avatarMd)
                                .background(colors.fillSecondary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(SampleData.workspaceInitial, style = LodyTheme.typography.subhead, color = colors.textPrimary)
                        }
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(SampleData.workspaceName, style = LodyTheme.typography.headline, color = colors.textPrimary)
                        Text(SampleData.organization, style = LodyTheme.typography.footnote, color = colors.textSecondary)
                    }
                },
                actions = { MoreMenu(appearance, onAppearanceChange, onNavigate) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase, scrolledContainerColor = colors.bgBase),
            )
        },
        bottomBar = {
            BottomBar(query = query, onQueryChange = { query = it })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            groups.forEachIndexed { index, group ->
                val isCollapsed = !searching && group.id in collapsed
                val visible = if (searching || group.id in showingAll) group.chats else group.chats.take(SampleData.visibleChatLimit)
                item(key = "header-${group.id}") {
                    GroupHeaderRow(
                        group = group,
                        collapsed = isCollapsed,
                        modifier = Modifier.padding(top = if (index == 0) LodySpacing.none else LodySpacing.sm),
                    ) {
                        collapsed = if (group.id in collapsed) collapsed - group.id else collapsed + group.id
                    }
                }
                if (!isCollapsed) {
                    items(visible, key = { "${group.id}/${it.title}" }) { chat -> ChatRow(chat) }
                    val hidden = group.chats.size - visible.size
                    if (hidden > 0) {
                        item(key = "more-${group.id}") { ShowMoreRow(hidden) { showingAll = showingAll + group.id } }
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        "No chats match “$query”",
                        style = LodyTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LodySpacing.xxxl)
                            .padding(horizontal = LodyLayout.screenPaddingX),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenu(appearance: Int, onAppearanceChange: (Int) -> Unit, onNavigate: (PreviewDestination) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = LodyTheme.colors
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "More" }) {
            Glyph(LodyIcons.More, colors.iconPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bgOverlay) {
            listOf("System", "Light", "Dark").forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label, style = LodyTheme.typography.body) },
                    trailingIcon = { if (appearance == index) Glyph(LodyIcons.Check, colors.accentFg, size = LodyIconSize.sm) },
                    onClick = { onAppearanceChange(index); open = false },
                )
            }
            HorizontalDivider(color = colors.borderSubtle)
            DropdownMenuItem(
                text = { Text("Components", style = LodyTheme.typography.body) },
                leadingIcon = { Glyph(LodyIcons.Sparkles, colors.iconSecondary) },
                onClick = { open = false; onNavigate(PreviewDestination.Components) },
            )
            DropdownMenuItem(
                text = { Text("Tokens", style = LodyTheme.typography.body) },
                leadingIcon = { Glyph(LodyIcons.Settings, colors.iconSecondary) },
                onClick = { open = false; onNavigate(PreviewDestination.Tokens) },
            )
        }
    }
}

/** Search pill plus the new-chat button, floating above the list and the keyboard. */
@Composable
private fun BottomBar(query: String, onQueryChange: (String) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(LodySize.controlLg)
                .dropShadow(CircleShape, LodyElevations.sm.shadow())
                .clip(CircleShape)
                .background(colors.bgOverlay)
                .padding(horizontal = LodySpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
        ) {
            Glyph(LodyIcons.Search, colors.iconSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentDefault),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search chats", style = type.body, color = colors.textPlaceholder)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(Modifier.clip(CircleShape).clickable { onQueryChange("") }.semantics { contentDescription = "Clear search" }) {
                    Glyph(LodyIcons.Close, colors.iconSecondary, size = LodyIconSize.sm)
                }
            }
        }
        FloatingActionButton(
            onClick = {},
            shape = CircleShape,
            containerColor = colors.bgOverlay,
            contentColor = colors.iconPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = LodySpacing.none),
            modifier = Modifier
                .size(LodySize.controlLg)
                .dropShadow(CircleShape, LodyElevations.sm.shadow())
                .semantics { contentDescription = "New chat" },
        ) {
            Glyph(LodyIcons.Compose, colors.iconPrimary)
        }
    }
}

/** The most urgent thing a chat (or a collapsed group) wants from the user, if anything. */
enum class ChatAttention {
    Running, Waiting, Failed, Unread;

    companion object {
        fun of(chat: SampleChat): ChatAttention? = when (chat.state) {
            SessionState.Running -> Running
            SessionState.Waiting -> Waiting
            SessionState.Failed -> Failed
            else -> if (chat.unread) Unread else null
        }

        /** Waiting and failed need a decision, so they outrank work in progress. */
        fun mostUrgent(chats: List<SampleChat>): ChatAttention? {
            val all = chats.mapNotNull(::of).toSet()
            return listOf(Waiting, Failed, Running, Unread).firstOrNull { it in all }
        }
    }
}

@Composable
private fun AttentionGlyph(attention: ChatAttention) {
    when (attention) {
        ChatAttention.Running -> StateGlyph(SessionState.Running, LodyIconSize.sm)
        ChatAttention.Waiting -> StateGlyph(SessionState.Waiting, LodyIconSize.sm)
        ChatAttention.Failed -> StateGlyph(SessionState.Failed, LodyIconSize.sm)
        ChatAttention.Unread -> Box(Modifier.size(LodySize.dot).background(LodyTheme.colors.accentDefault, CircleShape))
    }
}

@Composable
private fun GroupHeaderRow(group: SampleGroup, collapsed: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val colors = LodyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (collapsed) "${group.name}, collapsed, ${group.chats.size} chats" else "${group.name}, expanded"
            }
            .padding(start = LodyLayout.screenPaddingX, end = LodySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodyLayout.listRowGap),
    ) {
        Glyph(group.icon, colors.iconPrimary)
        Text(
            group.name,
            style = LodyTheme.typography.bodyEmphasis,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (collapsed) {
            ChatAttention.mostUrgent(group.chats)?.let { AttentionGlyph(it) }
            Text("${group.chats.size}", style = LodyTheme.typography.footnote, color = colors.textTertiary)
        }
        if (group.kind == SampleGroup.Kind.Project) {
            IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "New chat in ${group.name}" }) {
                Glyph(LodyIcons.Compose, colors.iconSecondary)
            }
        } else {
            Spacer(Modifier.width(LodySpacing.md))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: SampleChat) {
    val colors = LodyTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LodySize.listRow)
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                .padding(start = LodyLayout.screenPaddingX, end = LodyLayout.screenPaddingX),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The gutter under the group icon: empty unless the chat needs attention.
            Box(Modifier.width(LodyLayout.childIndent - LodyLayout.screenPaddingX), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.size(LodyIconSize.md), contentAlignment = Alignment.Center) {
                    ChatAttention.of(chat)?.let { AttentionGlyph(it) }
                }
            }
            Text(
                chat.title,
                style = if (chat.unread) LodyTheme.typography.bodyEmphasis else LodyTheme.typography.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.bgOverlay) {
            listOf(
                Triple("Rename", LodyIcons.Edit, false),
                Triple("Copy link", LodyIcons.Link, false),
                Triple("Archive", LodyIcons.Inbox, false),
                Triple("Delete", LodyIcons.Trash, true),
            ).forEach { (label, icon, destructive) ->
                val tint = if (destructive) colors.dangerDefault else colors.textPrimary
                DropdownMenuItem(
                    text = { Text(label, style = LodyTheme.typography.body, color = tint) },
                    leadingIcon = { Glyph(icon, if (destructive) colors.dangerDefault else colors.iconSecondary) },
                    onClick = { menuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun ShowMoreRow(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(onClick = onClick)
            .padding(start = LodyLayout.childIndent),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("Show $count more", style = LodyTheme.typography.body, color = LodyTheme.colors.textTertiary)
    }
}
