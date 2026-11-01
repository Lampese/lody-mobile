// Home in the mobile design language; iOS counterpart: LodyKit HomeController + HomeTabs + ChatListView.
//
// Chats tab: workspace capsule leading, search and view trailing; search expands over the bar and
// hides the capsule. Connection trouble floats as a centred pill over the bar (web parity). Chats
// are grouped by time. A row is the status mark, title and age; an optional second line carries
// project, branch and PR state, chosen in the view menu. A floating bar holds labelled Chats /
// Projects / Settings tabs and a separate new-chat button. This phase is read-only, so new chat
// explains that it is not available yet.

package app.lody.android.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.background
import app.lody.android.designsystem.LodyRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import app.lody.android.designsystem.LodySpring
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lody.android.R
import app.lody.android.auth.Workspace
import app.lody.android.runtime.CatalogSnapshot
import app.lody.android.designsystem.LodyElevations
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme
import app.lody.android.designsystem.LodyWorkingGrid
import app.lody.android.designsystem.shadow
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/** Everything Home renders; HomeActivity derives it from account and catalog state. */
data class ChatListUiState(
    val workspaceName: String,
    val initial: String,
    /** Catalog connection; Home floats a pill over the bar unless live. */
    val sync: HomeSyncState,
    val accountName: String,
    /** Search results are by time; the Chats list follows the view menu's grouping of [snapshot]. */
    val timeline: List<TimeSection>,
    val snapshot: CatalogSnapshot?,
    /** The signed-in user, for "My tasks". */
    val userId: String?,
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
    onNewChat: () -> Unit,
) {
    val colors = LodyTheme.colors
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    val project = state.projects.firstOrNull { it.id == projectId }
    val snackbar = remember { SnackbarHostState() }
    val closeSearch = { searching = false; query = "" }
    val context = LocalContext.current
    var details by remember { mutableStateOf(ChatRowDetails.load(context)) }
    val toggleDetail = { detail: ChatRowDetail ->
        details = if (detail in details) details - detail else details + detail
        ChatRowDetails.save(context, details)
    }
    var grouping by remember { mutableStateOf(HomeGroupings.load(context)) }
    val selectGrouping = { next: HomeGrouping -> grouping = next; HomeGroupings.save(context, next) }
    var scope by remember { mutableStateOf(HomeGroupings.loadScope(context)) }
    val selectScope = { next: HomeScope -> scope = next; HomeGroupings.saveScope(context, next) }
    var idleExpanded by rememberSaveable { mutableStateOf(false) }
    val owner = state.userId.takeIf { scope == HomeScope.Mine }
    val sections = remember(state.snapshot, grouping, owner) { state.snapshot?.let { ChatListModel.home(it, grouping, owner) }.orEmpty() }

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
                tab == HomeTab.Chats -> Box {
                    ChatsTopBar(state, scope, selectScope, grouping, selectGrouping, details, onToggleDetail = toggleDetail, onSearch = { searching = true }, onSelectWorkspace = onSelectWorkspace)
                    HomeSyncPill(state.sync, Modifier.align(Alignment.Center).statusBarsPadding())
                }
                else -> TitleTopBar(stringResource(tab.label), onBack = null)
            }
        },
        bottomBar = {
            if (!searching && project == null) {
                HomeBottomBar(
                    selected = tab,
                    onSelect = { tab = it },
                    onCreate = onNewChat,
                )
            }
        },
    ) { padding ->
        when {
            searching -> HomeList(
                ChatListModel.searchTimeline(state.timeline, query).map { HomeSection("bucket-${it.bucket}", HomeSection.Header.Bucket(it.bucket), it.chats) },
                details, padding, empty = stringResource(R.string.home_search_empty, query.trim()).takeIf { query.isNotBlank() },
                pinsLabels = false, onOpenChat = onOpenChat,
            )
            project != null -> ProjectChatList(project, details - ChatRowDetail.Project, padding, onOpenChat)
            // A project header already names the project, so rows drop it from their detail line.
            tab == HomeTab.Chats -> HomeList(
                sections,
                if (grouping == HomeGrouping.Project) details - ChatRowDetail.Project else details,
                padding,
                empty = state.placeholder,
                idleExpanded = idleExpanded,
                onToggleIdle = { idleExpanded = !idleExpanded },
                onOpenChat = onOpenChat,
            )
            tab == HomeTab.Projects -> ProjectList(state.projects, padding, onOpen = { projectId = it.id })
            else -> SettingsList(state, padding, onSelectWorkspace, onSignOut)
        }
    }
}

// MARK: - Top bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsTopBar(
    state: ChatListUiState,
    scope: HomeScope,
    onSelectScope: (HomeScope) -> Unit,
    grouping: HomeGrouping,
    onSelectGrouping: (HomeGrouping) -> Unit,
    details: Set<ChatRowDetail>,
    onToggleDetail: (ChatRowDetail) -> Unit,
    onSearch: () -> Unit,
    onSelectWorkspace: (Workspace) -> Unit,
) {
    val colors = LodyTheme.colors
    TopAppBar(
        title = { WorkspaceCapsule(state, onSelectWorkspace) },
        actions = {
            val search = stringResource(R.string.home_search)
            IconButton(onClick = onSearch, modifier = Modifier.semantics { contentDescription = search }) {
                Glyph(LodyIcons.Search, colors.iconPrimary)
            }
            ViewMenu(scope, onSelectScope, grouping, onSelectGrouping, details, onToggleDetail)
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

/** Workspace logo + name; tapping switches workspace. Connection state floats in [HomeSyncPill] instead. */
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
            // Capped so a long name ellipsizes before the centred sync pill.
            Text(
                state.workspaceName,
                style = LodyTheme.typography.headline,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = LodyLayout.trailingMetaMaxWidth),
            )
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

/**
 * The view menu: scope (my / team tasks) and grouping (time / project / status) as segmented
 * controls, then a multi-select of what the second row line shows.
 */
@Composable
private fun ViewMenu(
    scope: HomeScope,
    onSelectScope: (HomeScope) -> Unit,
    grouping: HomeGrouping,
    onSelectGrouping: (HomeGrouping) -> Unit,
    details: Set<ChatRowDetail>,
    onToggle: (ChatRowDetail) -> Unit,
) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.home_view)
    val shape = RoundedCornerShape(LodyRadius.lg)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Glyph(LodyIcons.Filter, colors.iconPrimary)
        }
        // Every choice applies in place and the menu stays open, so the list behind it updates
        // live; tapping outside closes it.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = shape,
            containerColor = colors.bgOverlay,
            tonalElevation = 0.dp,
            // Material elevation casts roughly twice its value as blur; match the md shadow.
            shadowElevation = LodyElevations.md.blur / 2,
            border = BorderStroke(0.5.dp, colors.borderSubtle),
            modifier = Modifier.width(MenuWidth),
        ) {
            MenuHeading(stringResource(R.string.home_view_scope))
            Segmented(HomeScope.entries, scope, { stringResource(it.label) }, onSelectScope)
            MenuHeading(stringResource(R.string.home_view_grouping))
            Segmented(HomeGrouping.entries, grouping, { stringResource(it.label) }, onSelectGrouping)
            HorizontalDivider(Modifier.padding(top = LodySpacing.md), thickness = 0.5.dp, color = colors.borderSubtle)
            MenuHeading(stringResource(R.string.home_view_details))
            ChatRowDetail.entries.forEach { detail ->
                val checked = detail in details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LodySize.controlMd)
                        .toggleable(value = checked, role = Role.Checkbox) { onToggle(detail) }
                        .padding(horizontal = LodySpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
                ) {
                    Glyph(detail.icon, colors.iconSecondary, LodyIconSize.sm)
                    Text(stringResource(detail.label), style = LodyTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Glyph(LodyIcons.Check, if (checked) colors.accentFg else Color.Transparent, LodyIconSize.sm)
                }
            }
            Spacer(Modifier.height(LodySpacing.xs))
        }
    }
}

private val MenuWidth = 280.dp

/**
 * A single choice as a segmented control (the iOS one's shape on design tokens): a sunken
 * track with the chosen segment raised on the overlay colour.
 */
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val colors = LodyTheme.colors
    val track = RoundedCornerShape(LodyRadius.md)
    val thumb = RoundedCornerShape(LodyRadius.sm)
    Row(
        modifier = Modifier
            .padding(horizontal = LodySpacing.lg)
            .fillMaxWidth()
            .height(LodySize.controlMd)
            .background(colors.fillSecondary, track)
            .padding(LodySpacing.xxs)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val chosen = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (chosen) Modifier.dropShadow(thumb, LodyElevations.sm.shadow()).background(colors.bgOverlay, thumb) else Modifier)
                    .clip(thumb)
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = LodyTheme.typography.label,
                    color = if (chosen) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = LodyTheme.typography.footnote,
        color = LodyTheme.colors.textTertiary,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = LodySpacing.lg, end = LodySpacing.lg, top = LodySpacing.md, bottom = LodySpacing.xs),
    )
}

private val HomeScope.label: Int
    get() = when (this) {
        HomeScope.Mine -> R.string.home_scope_mine
        HomeScope.Team -> R.string.home_scope_team
    }

private val HomeGrouping.label: Int
    get() = when (this) {
        HomeGrouping.Time -> R.string.home_grouping_time
        HomeGrouping.Project -> R.string.home_grouping_project
        HomeGrouping.Status -> R.string.home_grouping_status
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

/** Flat groups with a text header. The status view's idle group folds after [ChatListModel.IDLE_LIMIT] chats. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeList(
    sections: List<HomeSection>,
    details: Set<ChatRowDetail>,
    padding: PaddingValues,
    empty: String?,
    idleExpanded: Boolean = false,
    onToggleIdle: () -> Unit = {},
    pinsLabels: Boolean = true,
    onOpenChat: (ChatItem) -> Unit,
) {
    if (sections.isEmpty()) {
        EmptyText(empty.orEmpty(), padding)
        return
    }
    val now = rememberNow()
    // Resolved here: LazyListScope builders are not composable.
    val titles = sections.associate { it.id to it.header.title() }
    val showLess = stringResource(R.string.home_group_show_less)
    val context = LocalContext.current
    var folded by remember { mutableStateOf(FoldedOpeners.load(context)) }
    val toggleOpener = { id: String ->
        folded = if (id in folded) folded - id else folded + id
        FoldedOpeners.save(context, folded)
    }
    val shown = remember(sections, folded) { sections.map { it.copy(chats = ChatListModel.fold(it.chats, folded)) } }
    val direction = LocalLayoutDirection.current
    // Keep the sticky slot below the app bar; content padding alone scrolls away.
    LazyColumn(
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction),
            end = padding.calculateEndPadding(direction),
            bottom = padding.calculateBottomPadding(),
        ),
    ) {
        shown.forEach { section ->
            if (pinsLabels) {
                stickyHeader(key = section.id) { SectionTitle(titles.getValue(section.id)) }
            } else {
                item(key = section.id) { SectionTitle(titles.getValue(section.id)) }
            }
            items(section.visibleChats(idleExpanded), key = { "${section.id}/${it.id}" }) { chat ->
                ChatRow(
                    chat, details, now,
                    expanded = if (chat.treeParent) chat.id !in folded else null,
                    onToggle = { toggleOpener(chat.id) },
                ) { onOpenChat(chat) }
            }
            if (section.folds) {
                item(key = "${section.id}/more") {
                    MoreRow(
                        if (idleExpanded) showLess else pluralStringResource(R.plurals.home_group_show_all, section.chats.size, section.chats.size),
                        onToggleIdle,
                    )
                }
            }
        }
    }
}

/** The status view's idle group folds after [ChatListModel.IDLE_LIMIT] chats. */
private val HomeSection.folds: Boolean
    get() = (header as? HomeSection.Header.Status)?.group == StatusGroup.Idle && chats.size > ChatListModel.IDLE_LIMIT

private fun HomeSection.visibleChats(idleExpanded: Boolean) = if (folds && !idleExpanded) chats.take(ChatListModel.IDLE_LIMIT) else chats

@Composable
private fun HomeSection.Header.title(): String = when (this) {
    is HomeSection.Header.Bucket -> stringResource(bucket.label)
    is HomeSection.Header.Project -> name
    HomeSection.Header.Chats -> stringResource(R.string.home_group_chats)
    is HomeSection.Header.Status -> stringResource(
        when (group) {
            StatusGroup.Attention -> R.string.home_group_attention
            StatusGroup.Finished -> R.string.home_group_finished
            StatusGroup.Running -> R.string.home_group_running
            StatusGroup.PullRequest -> R.string.home_group_pull_request
            StatusGroup.Idle -> R.string.home_group_idle
        },
    )
}

@Composable
private fun MoreRow(title: String, onClick: () -> Unit) {
    Text(
        title,
        style = LodyTheme.typography.body,
        color = LodyTheme.colors.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = LodyLayout.childIndent, end = LodyLayout.screenPaddingX)
            .wrapContentHeight(Alignment.CenterVertically),
    )
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
private fun ProjectChatList(project: ChatGroup, details: Set<ChatRowDetail>, padding: PaddingValues, onOpenChat: (ChatItem) -> Unit) {
    val now = rememberNow()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(project.chats, key = { it.id }) { chat -> ChatRow(chat, details, now) { onOpenChat(chat) } }
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
            .background(LodyTheme.colors.bgBase)
            .semantics { heading() }
            .padding(start = LodyLayout.screenPaddingX, end = LodyLayout.screenPaddingX, top = LodySpacing.lg, bottom = LodySpacing.xs),
    )
}

/**
 * Line 1: status slot, title, age. Line 2 (only when an enabled detail has a value): repo or folder
 * mark + project, branch, and the PR mark trailing. iOS counterpart: `ChatRowContent`.
 *
 * [expanded] is set on a row with nested chats: whether they are shown. Its status slot then
 * holds the disclosure (web's opener chevron) and its status mark moves inline, before the age.
 */
@Composable
private fun ChatRow(
    chat: ChatItem,
    details: Set<ChatRowDetail>,
    now: Long,
    expanded: Boolean? = null,
    onToggle: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val place = chat.place.takeIf { ChatRowDetail.Project in details && it.isNotEmpty() }
    val branch = chat.branch.takeIf { ChatRowDetail.Branch in details && it.isNotEmpty() }
    val pr = chat.prStatus.takeIf { ChatRowDetail.PullRequest in details && it in PrStatuses }
    // A PR mark alone stays on the title line, before the age; it is always the trailing mark of its line.
    val twoLine = place != null || branch != null
    val nested = chat.nestedUnder != null
    val treeColor = colors.borderDefault
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .drawBehind { if (nested || expanded == true) drawTree(treeColor, nested, chat.lastChild, expanded == true, chat.attention != null) }
            .clickable(onClick = onClick)
            .padding(end = LodyLayout.screenPaddingX)
            .padding(vertical = if (twoLine) LodySpacing.sm else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The status slot spans the gutter and centres on the whole row; empty when idle. A nested
        // row's slot (and so its title) steps in one level.
        if (expanded != null) {
            val label = stringResource(if (expanded) R.string.home_tree_collapse else R.string.home_tree_expand)
            val angle by animateFloatAsState(if (expanded) 0f else -90f, LodySpring.snappy(), label = "disclosure")
            Box(
                Modifier
                    .width(LodyLayout.childIndent)
                    .heightIn(min = LodySize.listRow)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = label, onClick = onToggle)
                    .semantics { stateDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(LodyIcons.Expand, colors.iconTertiary, LodyIconSize.sm, Modifier.rotate(angle))
            }
        } else {
            Box(Modifier.padding(start = if (nested) TreeLevel else 0.dp).width(LodyLayout.childIndent), contentAlignment = Alignment.Center) {
                chat.attention?.let { AttentionGlyph(it) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LodySpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    style = if (chat.unread) type.bodyEmphasis else type.body,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // An opener's own mark (or, folded, its family's) sits before the age.
                if (expanded != null) chat.attention?.let { Box(Modifier.padding(start = LodySpacing.sm)) { AttentionGlyph(it) } }
                if (pr != null && !twoLine) PullRequestMark(pr, chat.prCi, Modifier.padding(start = LodySpacing.sm))
                chat.activityAt?.let { at ->
                    Text(
                        ageText(at, now),
                        style = type.footnote.copy(fontFeatureSettings = "tnum"),
                        color = colors.textTertiary,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = if (pr != null && !twoLine) LodySpacing.xs else LodySpacing.sm),
                    )
                }
            }
            if (twoLine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Project and branch share the leading space so the PR mark stays pinned trailing.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs)) {
                        if (place != null) {
                            Glyph(if (chat.placeIsRepo) LodyIcons.Github else LodyIcons.Folder, colors.iconSecondary, LodyIconSize.sm)
                            Text(place, style = type.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        if (branch != null) {
                            Glyph(LodyIcons.Branch, colors.iconTertiary, LodyIconSize.sm, Modifier.padding(start = if (place != null) LodySpacing.xs else 0.dp))
                            Text(branch, style = type.footnote, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                    }
                    if (pr != null) PullRequestMark(pr, chat.prCi, Modifier.padding(start = LodySpacing.sm))
                }
            }
        }
    }
}

/** One nesting level: how far a nested row's mark and title step in (iOS `TreeLines.level`). */
private val TreeLevel = LodySpacing.lg

/**
 * Web's opened-by tree connectors (`session-row-leading-slot.tsx`) in the mobile gutter: a trunk
 * down the parent's status-mark column, from below its mark through each nested row (stopping at
 * the last one's centre), and an elbow into the nested row's own mark, or toward its title when
 * it has none. Rows sit edge to edge, so each draws only its own height and the pieces join.
 */
private fun DrawScope.drawTree(color: Color, nested: Boolean, lastChild: Boolean, parent: Boolean, marked: Boolean) {
    val x = (LodyLayout.childIndent / 2).toPx()
    val mid = size.height / 2
    val stroke = 1.dp.toPx()
    if (parent) {
        // Below the opener's chevron.
        drawLine(color, Offset(x, mid + (LodyIconSize.sm / 2 + LodySpacing.xxs).toPx()), Offset(x, size.height), stroke)
    }
    if (nested) {
        drawLine(color, Offset(x, 0f), Offset(x, if (lastChild) mid else size.height), stroke)
        val end = if (marked) TreeLevel + LodyLayout.childIndent / 2 - LodyIconSize.md / 2 + LodySpacing.xxs
        else LodyLayout.childIndent + TreeLevel - LodySpacing.sm
        drawLine(color, Offset(x, mid), Offset(end.toPx(), mid), stroke)
    }
}

private val PrStatuses = setOf("open", "merged", "closed", "draft")

/** Web `SessionPrIcon`: open, merged, closed and draft each have their own Lucide mark and colour. */
@Composable
private fun PullRequestMark(status: String, ci: String, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    val (icon, tint, label) = when (status) {
        "merged" -> Triple(LodyIcons.Merge, colors.prMerged, R.string.home_pr_merged)
        "closed" -> Triple(LodyIcons.PullRequestClosed, colors.prClosed, R.string.home_pr_closed)
        "draft" -> Triple(LodyIcons.PullRequestDraft, colors.prDraft, R.string.home_pr_draft)
        else -> Triple(LodyIcons.PullRequest, colors.prOpen, R.string.home_pr_open)
    }
    // CI shows only while the PR is open or a draft, as in the web sidebar.
    val verdict = ci.takeIf { (status == "open" || status == "draft") && it.isNotEmpty() }
    val ciLabel = when (verdict) {
        "pass" -> stringResource(R.string.home_ci_pass)
        "fail" -> stringResource(R.string.home_ci_fail)
        "pending" -> stringResource(R.string.home_ci_pending)
        else -> null
    }
    val description = listOfNotNull(stringResource(label), ciLabel).joinToString(", ")
    val size = LodyIconSize.sm
    val unit = size / 16
    Box(modifier.size(size).semantics { contentDescription = description }) {
        if (verdict == null) {
            Glyph(icon, tint, size)
        } else {
            // Web `MaskedPrCiIcon` on a 16-unit box: a circular cutout clears the PR stroke under
            // the bottom-trailing verdict slot, so no backdrop is painted. Pending is a dot.
            val radius = unit * if (verdict == "pending") 3.75f else 5f
            Glyph(
                icon, tint, size,
                Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawCircle(Color.Black, radius.toPx(), Offset((unit * 12).toPx(), (unit * 12).toPx()), blendMode = BlendMode.Clear)
                    },
            )
            Box(Modifier.offset(unit * 7, unit * 7).size(unit * 10), contentAlignment = Alignment.Center) {
                when (verdict) {
                    "pass" -> Glyph(LodyIcons.Check, colors.successDefault, unit * 10)
                    "fail" -> Glyph(LodyIcons.Close, colors.dangerDefault, unit * 10)
                    else -> Box(Modifier.size(unit * 5).background(colors.warningDefault, CircleShape))
                }
            }
        }
    }
}

/** Wall clock for row ages, refreshed each minute while the list is on screen. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - now % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun ageText(at: Long, now: Long): String = when (val age = ChatListModel.age(at, now)) {
    ChatListModel.Age.Now -> stringResource(R.string.home_age_now)
    is ChatListModel.Age.Minutes -> stringResource(R.string.home_age_minutes, age.value)
    is ChatListModel.Age.Hours -> stringResource(R.string.home_age_hours, age.value)
    is ChatListModel.Age.Days -> stringResource(R.string.home_age_days, age.value)
    is ChatListModel.Age.Date -> {
        val locale = LocalConfiguration.current.locales[0]
        remember(locale) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }.format(age.date)
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

/** Web `SessionRowStatusIndicator`: alert when failed, hand when waiting, working grid, unread dot. */
@Composable
private fun AttentionGlyph(attention: ChatAttention) {
    val colors = LodyTheme.colors
    when (attention) {
        ChatAttention.Failed -> Glyph(LodyIcons.Alert, colors.dangerDefault, LodyIconSize.sm)
        ChatAttention.Waiting -> Glyph(LodyIcons.Hand, colors.accentDefault, LodyIconSize.sm)
        ChatAttention.Running -> LodyWorkingGrid()
        ChatAttention.Unread -> Box(Modifier.size(LodySpacing.sm).background(colors.accentDefault, CircleShape))
    }
}
