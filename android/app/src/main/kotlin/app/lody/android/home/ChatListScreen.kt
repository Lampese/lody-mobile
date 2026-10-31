// The Home chat list in the mobile design language. Reference design:
// design-system/examples/android/.../SessionListScreen.kt; iOS counterpart: LodyKit ChatListView.
//
// One line per chat, no separators or metadata; titles align with their group title.
// The indent gutter shows a glyph only when a chat needs attention; collapsed groups
// show their count and most urgent state; search filters every group. This phase is
// read-only, so there is no new-chat button or row menu yet.

package app.lody.android.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.lody.android.R
import app.lody.android.auth.Workspace
import app.lody.android.designsystem.LodyElevations
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme
import app.lody.android.designsystem.shadow

/** Everything the list renders; HomeActivity derives it from account and catalog state. */
data class ChatListUiState(
    val title: String,
    val subtitle: String,
    val initial: String,
    val groups: List<ChatGroup>,
    val placeholder: String,
    val workspaces: List<Workspace>,
    val workspaceId: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(state: ChatListUiState, onSelectWorkspace: (Workspace) -> Unit, onSignOut: () -> Unit) {
    val colors = LodyTheme.colors
    var collapsed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showingAll by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val groups = ChatListModel.search(state.groups, query)

    Scaffold(
        containerColor = colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { WorkspaceMenu(state, onSelectWorkspace) },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.title, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1)
                        if (state.subtitle.isNotEmpty()) {
                            Text(state.subtitle, style = LodyTheme.typography.footnote, color = colors.textSecondary, maxLines = 1)
                        }
                    }
                },
                actions = { MoreMenu(onSignOut) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase, scrolledContainerColor = colors.bgBase),
            )
        },
        bottomBar = { SearchBar(query) { query = it } },
    ) { padding ->
        val empty = when {
            groups.isNotEmpty() -> null
            searching -> stringResource(R.string.home_search_empty, query.trim())
            else -> state.placeholder
        }
        if (empty != null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = LodyLayout.screenPaddingX), contentAlignment = Alignment.Center) {
                Text(empty, style = LodyTheme.typography.body, color = colors.textSecondary)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            groups.forEachIndexed { index, group ->
                val isCollapsed = !searching && group.id in collapsed
                val visible = if (searching || group.id in showingAll) group.chats else group.chats.take(ChatListModel.VISIBLE_CHATS)
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
                    items(visible, key = { "${group.id}/${it.id}" }) { chat -> ChatRow(chat) }
                    val hidden = group.chats.size - visible.size
                    if (hidden > 0) {
                        item(key = "more-${group.id}") { ShowMoreRow(hidden) { showingAll = showingAll + group.id } }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceMenu(state: ChatListUiState, onSelect: (Workspace) -> Unit) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.home_account)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Box(Modifier.size(LodySize.avatarMd).background(colors.fillSecondary, CircleShape), contentAlignment = Alignment.Center) {
                Text(state.initial, style = LodyTheme.typography.subhead, color = colors.textPrimary)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bgOverlay) {
            if (state.workspaces.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_no_workspaces), style = LodyTheme.typography.body, color = colors.textSecondary) },
                    onClick = { open = false },
                    enabled = false,
                )
            }
            state.workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = { Text(workspace.name, style = LodyTheme.typography.body, color = colors.textPrimary) },
                    trailingIcon = {
                        if (workspace.id == state.workspaceId) Glyph(LodyIcons.Check, colors.accentFg, LodyIconSize.sm)
                    },
                    onClick = { open = false; if (workspace.id != state.workspaceId) onSelect(workspace) },
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(onSignOut: () -> Unit) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.home_more)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Glyph(LodyIcons.More, colors.iconPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bgOverlay) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_sign_out), style = LodyTheme.typography.body, color = colors.dangerDefault) },
                leadingIcon = { Glyph(LodyIcons.LogOut, colors.dangerDefault) },
                onClick = { open = false; onSignOut() },
            )
        }
    }
}

/** Search pill floating above the list and the keyboard. */
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val placeholder = stringResource(R.string.home_search_placeholder)
    val clear = stringResource(R.string.home_search_clear)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.sm)
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
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text(placeholder, style = type.body, color = colors.textPlaceholder)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(Modifier.clip(CircleShape).clickable { onQueryChange("") }.semantics { contentDescription = clear }) {
                Glyph(LodyIcons.Close, colors.iconSecondary, LodyIconSize.sm)
            }
        }
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: Color, size: Dp = LodyIconSize.md, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
private fun AttentionGlyph(attention: ChatAttention) {
    val colors = LodyTheme.colors
    when (attention) {
        ChatAttention.Running -> {
            val angle by rememberInfiniteTransition(label = "running").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                label = "angle",
            )
            Glyph(LodyIcons.StateRunning, colors.stateRunning, LodyIconSize.sm, Modifier.graphicsLayer { rotationZ = angle })
        }
        ChatAttention.Waiting -> Glyph(LodyIcons.StateWaiting, colors.stateWaiting, LodyIconSize.sm)
        ChatAttention.Failed -> Glyph(LodyIcons.StateFailed, colors.stateFailed, LodyIconSize.sm)
    }
}

@Composable
private fun GroupHeaderRow(group: ChatGroup, collapsed: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val colors = LodyTheme.colors
    val name = when (group.kind) {
        ChatGroup.Kind.Pinned -> stringResource(R.string.home_section_pinned)
        ChatGroup.Kind.Chats -> stringResource(R.string.home_section_chats)
        ChatGroup.Kind.Project -> group.name
    }
    val icon = when (group.kind) {
        ChatGroup.Kind.Pinned -> LodyIcons.Pin
        ChatGroup.Kind.Chats -> LodyIcons.Session
        ChatGroup.Kind.Project -> LodyIcons.Folder
    }
    val description = if (collapsed) {
        stringResource(R.string.home_group_collapsed, name, group.chats.size)
    } else {
        stringResource(R.string.home_group_expanded, name)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(onClick = onToggle)
            .semantics { heading(); contentDescription = description }
            .padding(horizontal = LodyLayout.screenPaddingX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodyLayout.listRowGap),
    ) {
        Glyph(icon, colors.iconPrimary)
        Text(
            name,
            style = LodyTheme.typography.bodyEmphasis,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (collapsed) {
            group.mostUrgent?.let { AttentionGlyph(it) }
            Text("${group.chats.size}", style = LodyTheme.typography.footnote, color = colors.textTertiary)
        }
    }
}

@Composable
private fun ChatRow(chat: ChatItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .padding(horizontal = LodyLayout.screenPaddingX),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The gutter: its glyph sits exactly under the group icon, empty unless the chat needs attention.
        Box(Modifier.width(LodyLayout.childIndent - LodyLayout.screenPaddingX), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(LodyIconSize.md), contentAlignment = Alignment.Center) {
                chat.attention?.let { AttentionGlyph(it) }
            }
        }
        Text(
            chat.title,
            style = LodyTheme.typography.body,
            color = LodyTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
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
        Text(stringResource(R.string.home_show_more, count), style = LodyTheme.typography.body, color = LodyTheme.colors.textTertiary)
    }
}
