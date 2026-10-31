// Home in the mobile design language; iOS counterpart: LodyKit HomeController + HomeTabs + ChatListView.
//
// Chats tab: workspace capsule leading, search and view trailing; search expands over the bar and
// hides the capsule. Chats are grouped by time (the view menu will add by project / by status).
// Rows are one line: the gutter shows a glyph only when a chat needs attention and the project
// name sits trailing. A floating bar holds labelled Chats / Projects / Settings tabs and a separate
// new-chat button. This phase is read-only, so new chat explains that it is not available yet.

package app.lody.android.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.launch

/** Everything Home renders; HomeActivity derives it from account and catalog state. */
data class ChatListUiState(
    val workspaceName: String,
    val initial: String,
    /** Actionable connection state (syncing, offline); empty when live. */
    val status: String,
    val accountName: String,
    val timeline: List<TimeSection>,
    val projects: List<ChatGroup>,
    val placeholder: String,
    val workspaces: List<Workspace>,
    val workspaceId: String?,
)

enum class HomeTab(@StringRes val label: Int) {
    Chats(R.string.home_tab_chats),
    Projects(R.string.home_tab_projects),
    Settings(R.string.home_tab_settings);

    val icon: ImageVector
        get() = when (this) {
            Chats -> LodyIcons.Sessions
            Projects -> LodyIcons.Folder
            Settings -> LodyIcons.Settings
        }
}

private val BarHeight = LodySize.controlLg + LodySpacing.sm

@Composable
fun ChatListScreen(
    state: ChatListUiState,
    onSelectWorkspace: (Workspace) -> Unit,
    onSignOut: () -> Unit,
    onOpenChat: (ChatItem) -> Unit,
) {
    val colors = LodyTheme.colors
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    val project = state.projects.firstOrNull { it.id == projectId }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.home_new_chat_unavailable)
    val closeSearch = { searching = false; query = "" }

    BackHandler(enabled = searching || project != null) {
        if (searching) closeSearch() else projectId = null
    }

    Scaffold(
        containerColor = colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when {
                searching -> SearchTopBar(query, onQueryChange = { query = it }, onClose = closeSearch)
                project != null -> TitleTopBar(project.name, onBack = { projectId = null })
                tab == HomeTab.Chats -> ChatsTopBar(state, onSearch = { searching = true }, onSelectWorkspace = onSelectWorkspace)
                else -> TitleTopBar(stringResource(tab.label), onBack = null)
            }
        },
        bottomBar = {
            if (!searching && project == null) {
                HomeBottomBar(
                    selected = tab,
                    onSelect = { tab = it },
                    onCreate = { scope.launch { snackbar.showSnackbar(unavailable) } },
                )
            }
        },
    ) { padding ->
        when {
            searching -> TimelineList(ChatListModel.searchTimeline(state.timeline, query), padding, empty = stringResource(R.string.home_search_empty, query.trim()).takeIf { query.isNotBlank() }, onOpenChat)
            project != null -> ProjectChatList(project, padding, onOpenChat)
            tab == HomeTab.Chats -> TimelineList(state.timeline, padding, empty = state.placeholder, onOpenChat)
            tab == HomeTab.Projects -> ProjectList(state.projects, padding, onOpen = { projectId = it.id })
            else -> SettingsList(state, padding, onSelectWorkspace, onSignOut)
        }
    }
}

// MARK: - Top bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsTopBar(state: ChatListUiState, onSearch: () -> Unit, onSelectWorkspace: (Workspace) -> Unit) {
    val colors = LodyTheme.colors
    TopAppBar(
        title = { WorkspaceCapsule(state, onSelectWorkspace) },
        actions = {
            val search = stringResource(R.string.home_search)
            IconButton(onClick = onSearch, modifier = Modifier.semantics { contentDescription = search }) {
                Glyph(LodyIcons.Search, colors.iconPrimary)
            }
            ViewMenu()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase, scrolledContainerColor = colors.bgBase),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleTopBar(title: String, onBack: (() -> Unit)?) {
    val colors = LodyTheme.colors
    TopAppBar(
        title = { Text(title, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                val back = stringResource(R.string.chat_back)
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                    Glyph(LodyIcons.Back, colors.iconPrimary)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase, scrolledContainerColor = colors.bgBase),
    )
}

/** Workspace logo + name; tapping switches workspace. Connection trouble shows as a second line. */
@Composable
private fun WorkspaceCapsule(state: ChatListUiState, onSelect: (Workspace) -> Unit) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.home_account)
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "$label, ${state.workspaceName}" }
                .padding(end = LodySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
        ) {
            Box(Modifier.size(LodySize.avatarMd).background(colors.fillSecondary, CircleShape), contentAlignment = Alignment.Center) {
                Text(state.initial, style = LodyTheme.typography.subhead, color = colors.textPrimary)
            }
            Column {
                Text(state.workspaceName, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.status.isNotEmpty()) {
                    Text(state.status, style = LodyTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
                }
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
                    trailingIcon = { if (workspace.id == state.workspaceId) Glyph(LodyIcons.Check, colors.accentFg, LodyIconSize.sm) },
                    onClick = { open = false; if (workspace.id != state.workspaceId) onSelect(workspace) },
                )
            }
        }
    }
}

/** Grouping by time is the only view for now; by project and by status come later. */
@Composable
private fun ViewMenu() {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.home_view)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Glyph(LodyIcons.Filter, colors.iconPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bgOverlay) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_view_time), style = LodyTheme.typography.body, color = colors.textPrimary) },
                trailingIcon = { Glyph(LodyIcons.Check, colors.accentFg, LodyIconSize.sm) },
                onClick = { open = false },
            )
        }
    }
}

/** The search field takes the whole bar, so the workspace capsule steps aside while searching. */
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val placeholder = stringResource(R.string.home_search_placeholder)
    val close = stringResource(R.string.home_search_close)
    val clear = stringResource(R.string.home_search_clear)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgBase)
            .statusBarsPadding()
            .padding(horizontal = LodySpacing.xs, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = close }) {
            Glyph(LodyIcons.Back, colors.iconPrimary)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(LodySize.controlMd)
                .clip(CircleShape)
                .background(colors.bgSunken)
                .padding(horizontal = LodySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
        ) {
            Glyph(LodyIcons.Search, colors.iconSecondary, LodyIconSize.sm)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentDefault),
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text(placeholder, style = type.body, color = colors.textPlaceholder)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(Modifier.clip(CircleShape).clickable(role = Role.Button) { onQueryChange("") }.semantics { contentDescription = clear }) {
                    Glyph(LodyIcons.Close, colors.iconSecondary, LodyIconSize.sm)
                }
            }
        }
        Spacer(Modifier.width(LodySpacing.sm))
    }
}

// MARK: - Bottom bar

/** Labelled tabs in a floating capsule plus a separate new-chat button. */
@Composable
private fun HomeBottomBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, onCreate: () -> Unit) {
    val colors = LodyTheme.colors
    val create = stringResource(R.string.home_new_chat)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(BarHeight)
                .dropShadow(CircleShape, LodyElevations.md.shadow())
                .clip(CircleShape)
                .background(colors.bgOverlay)
                .padding(LodySpacing.xs),
        ) {
            HomeTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) colors.accentFg else colors.iconSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) colors.fillSelected else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Glyph(tab.icon, tint)
                    Text(stringResource(tab.label), style = LodyTheme.typography.micro, color = tint, maxLines = 1)
                }
            }
        }
        Box(
            modifier = Modifier
                .size(BarHeight)
                .dropShadow(CircleShape, LodyElevations.md.shadow())
                .clip(CircleShape)
                .background(colors.bgOverlay)
                .clickable(role = Role.Button, onClick = onCreate)
                .semantics { contentDescription = create },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(LodyIcons.Compose, colors.iconPrimary, LodyIconSize.lg)
        }
    }
}

// MARK: - Lists

@Composable
private fun TimelineList(sections: List<TimeSection>, padding: PaddingValues, empty: String?, onOpenChat: (ChatItem) -> Unit) {
    if (sections.isEmpty()) {
        EmptyText(empty.orEmpty(), padding)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        sections.forEach { section ->
            item(key = "bucket-${section.bucket}") { SectionTitle(stringResource(section.bucket.label)) }
            items(section.chats, key = { "${section.bucket}/${it.id}" }) { chat -> ChatRow(chat, showsPlace = true) { onOpenChat(chat) } }
        }
    }
}

private val TimeBucket.label: Int
    get() = when (this) {
        TimeBucket.Today -> R.string.home_bucket_today
        TimeBucket.Yesterday -> R.string.home_bucket_yesterday
        TimeBucket.Week -> R.string.home_bucket_week
        TimeBucket.Month -> R.string.home_bucket_month
        TimeBucket.Older -> R.string.home_bucket_older
    }

@Composable
private fun ProjectList(projects: List<ChatGroup>, padding: PaddingValues, onOpen: (ChatGroup) -> Unit) {
    val projectsOnly = projects.filter { it.kind == ChatGroup.Kind.Project }
    if (projectsOnly.isEmpty()) {
        EmptyText(stringResource(R.string.home_projects_empty), padding)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(projectsOnly, key = { it.id }) { project -> ProjectRow(project) { onOpen(project) } }
    }
}

@Composable
private fun ProjectChatList(project: ChatGroup, padding: PaddingValues, onOpenChat: (ChatItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(project.chats, key = { it.id }) { chat -> ChatRow(chat, showsPlace = false) { onOpenChat(chat) } }
    }
}

@Composable
private fun SettingsList(state: ChatListUiState, padding: PaddingValues, onSelectWorkspace: (Workspace) -> Unit, onSignOut: () -> Unit) {
    val colors = LodyTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { SectionTitle(stringResource(R.string.home_settings_account)) }
        item { SettingsRow(state.accountName.ifEmpty { "—" }, LodyIcons.User, colors.textPrimary, onClick = null) }
        item { SectionTitle(stringResource(R.string.home_settings_workspace)) }
        items(state.workspaces, key = { it.id }) { workspace ->
            SettingsRow(
                title = workspace.name,
                icon = if (workspace.id == state.workspaceId) LodyIcons.Check else null,
                tint = colors.textPrimary,
                onClick = { if (workspace.id != state.workspaceId) onSelectWorkspace(workspace) },
            )
        }
        item { Spacer(Modifier.height(LodySpacing.xxl)) }
        item { SettingsRow(stringResource(R.string.home_sign_out), LodyIcons.LogOut, colors.dangerDefault, onClick = onSignOut) }
    }
}

// MARK: - Rows

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = LodyTheme.typography.subhead,
        color = LodyTheme.colors.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = LodyLayout.screenPaddingX, end = LodyLayout.screenPaddingX, top = LodySpacing.lg, bottom = LodySpacing.xs),
    )
}

@Composable
private fun ChatRow(chat: ChatItem, showsPlace: Boolean, onClick: () -> Unit) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = LodyLayout.screenPaddingX),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The gutter: empty unless the chat needs attention.
        Box(Modifier.width(LodyLayout.childIndent - LodyLayout.screenPaddingX), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(LodyIconSize.md), contentAlignment = Alignment.Center) {
                chat.attention?.let { AttentionGlyph(it) }
            }
        }
        Text(
            chat.title,
            style = LodyTheme.typography.body,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showsPlace && chat.place.isNotEmpty()) {
            Text(
                chat.place,
                style = LodyTheme.typography.footnote,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = LodySpacing.sm).widthIn(max = LodyLayout.trailingMetaMaxWidth),
            )
        }
    }
}

@Composable
private fun ProjectRow(project: ChatGroup, onClick: () -> Unit) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = LodyLayout.screenPaddingX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodyLayout.listRowGap),
    ) {
        Glyph(LodyIcons.Folder, colors.iconPrimary)
        Text(
            project.name,
            style = LodyTheme.typography.bodyEmphasis,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        project.mostUrgent?.let { AttentionGlyph(it) }
        Text("${project.chats.size}", style = LodyTheme.typography.footnote, color = colors.textTertiary)
        Glyph(LodyIcons.Forward, colors.iconTertiary, LodyIconSize.sm)
    }
}

@Composable
private fun SettingsRow(title: String, icon: ImageVector?, tint: Color, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = LodyLayout.screenPaddingX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodyLayout.listRowGap),
    ) {
        Text(title, style = LodyTheme.typography.body, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (icon != null) Glyph(icon, if (icon == LodyIcons.Check) LodyTheme.colors.accentFg else tint)
    }
}

@Composable
private fun EmptyText(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = LodyLayout.screenPaddingX), contentAlignment = Alignment.Center) {
        Text(text, style = LodyTheme.typography.body, color = LodyTheme.colors.textSecondary)
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
