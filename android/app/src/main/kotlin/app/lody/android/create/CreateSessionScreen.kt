// New session, full screen like web; iOS counterpart: CreateSessionController + CreateSessionContext.
//
// Follows web's mobile new-chat page (`mobile-new-chat-sheet.tsx`, `mobile-inline-picker.tsx`,
// `mobile-run-config-sheet.tsx`), checked by driving its Storybook stories: computer and project
// are full-width cards whose options expand in place right below them (one open at a time, the
// project list searchable); then the worktree chip for local projects or the branch line for
// GitHub projects; then the composer card (+, @, the run-config chip, send). The assistant lives
// in the run-config chip's sheet, not on the page.
//
// Android is still read-only: assistants, models and branches need Machine RPC / GitHub reads,
// and sending needs writes, so those say they are not available yet instead of pretending.

package app.lody.android.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lody.android.R
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodyRadius
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme
import app.lody.android.runtime.CatalogProject
import app.lody.android.runtime.CatalogSnapshot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(snapshot: CatalogSnapshot?, onBack: () -> Unit) {
    val colors = LodyTheme.colors
    val catalog = snapshot ?: CatalogSnapshot(0, emptyList(), emptyList(), emptyList())
    var target by remember(catalog.revision == 0L) { mutableStateOf(CreateSessionModel.initial(catalog)) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var runConfig by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.home_new_chat_unavailable)
    val notice = { scope.launch { snackbar.showSnackbar(unavailable) }; Unit }

    Scaffold(
        containerColor = colors.bgBase,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title), style = LodyTheme.typography.headline, color = colors.textPrimary) },
                navigationIcon = {
                    val back = stringResource(R.string.chat_back)
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                        Icon(LodyIcons.Back, contentDescription = null, tint = colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase, scrolledContainerColor = colors.bgBase),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            // Bottom-aligned above the composer; an expanded list grows upward and, once the
            // panel outgrows the space, scrolls (anchored at its bottom).
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                        .padding(horizontal = LodyLayout.screenPaddingX),
                    verticalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                ) {
                    ContextPanel(catalog, target, onChange = { target = it })
                }
            }
            Composer(prompt, onPromptChange = { prompt = it }, onAttach = notice, onMention = notice, onRunConfig = { runConfig = true }, onSend = notice)
        }
    }
    if (runConfig) {
        ModalBottomSheet(onDismissRequest = { runConfig = false }, containerColor = colors.bgBase) {
            RunConfigSheet()
        }
    }
}

@Composable
private fun ContextPanel(catalog: CatalogSnapshot, target: CreateTarget, onChange: (CreateTarget) -> Unit) {
    val colors = LodyTheme.colors
    // One list open at a time, as web's picker coordinator.
    var open by remember { mutableStateOf<String?>(null) }
    val toggle = { id: String -> open = if (open == id) null else id }

    val machines = CreateSessionModel.machines(catalog)
    val machineName = machines.firstOrNull { it.first == target.machineId }?.second
    PickerCard(
        id = MACHINE,
        icon = LodyIcons.Machine,
        title = machineName ?: stringResource(R.string.create_pick_machine),
        placeholder = machineName == null,
        options = if (CreateSessionModel.machineChoosable(catalog)) machines.map { (id, name) -> PickerOption(id, name, selected = id == target.machineId) } else emptyList(),
        open = open == MACHINE,
        onToggle = { toggle(MACHINE) },
        onPick = { open = null; onChange(CreateSessionModel.pickMachine(catalog, target, it)) },
    )

    val projects = CreateSessionModel.projects(catalog)
    val project = projects.firstOrNull { it.id == target.projectId }
    val chatLabel = stringResource(R.string.create_chat_only)
    val chatOption = PickerOption(CHAT, chatLabel, selected = target.chat, icon = LodyIcons.Close)
    val usable = projects.filter { CreateSessionModel.isGithub(it.id) || target.machineId == null || it.machineId == target.machineId }
    PickerCard(
        id = PROJECT,
        icon = when {
            target.chat -> LodyIcons.Close
            CreateSessionModel.isGithub(project?.id) -> LodyIcons.Github
            else -> LodyIcons.Folder
        },
        title = if (target.chat) chatLabel else project?.name ?: stringResource(R.string.create_pick_project),
        placeholder = !target.chat && project == null,
        // No project, the five most recent, then More projects…; search covers every project.
        options = listOf(chatOption) + CreateSessionModel.recent(catalog, target).map { it.option(target) } +
            PickerOption(MORE, stringResource(R.string.create_more_projects), selected = false, icon = LodyIcons.More),
        searchOptions = listOf(chatOption) + usable.map { it.option(target) },
        searchPlaceholder = stringResource(R.string.create_search_projects),
        open = open == PROJECT,
        onToggle = { toggle(PROJECT) },
        onPick = { id ->
            when (id) {
                MORE -> Unit // The search field below lists every project; More keeps the list open.
                CHAT -> { open = null; onChange(CreateSessionModel.chat(catalog, target)) }
                else -> { open = null; onChange(CreateSessionModel.select(catalog, target, id)) }
            }
        },
        expandAllOn = MORE,
    )

    val local = !target.chat && project != null && !CreateSessionModel.isGithub(project.id)
    val github = !target.chat && CreateSessionModel.isGithub(target.projectId)
    if (local || github) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.md)) {
            if (local) WorktreeChip(target.worktree) { onChange(target.copy(worktree = it)) }
            // Branches come from GitHub / local git reads Android does not make yet.
            if (github) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.md)) {
                    Icon(LodyIcons.Branch, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
                    Text(stringResource(R.string.create_branch_unavailable), style = LodyTheme.typography.body, color = colors.textTertiary)
                }
            }
        }
    }
    Text(
        stringResource(R.string.create_readonly_note),
        style = LodyTheme.typography.footnote,
        color = colors.textTertiary,
        modifier = Modifier.padding(horizontal = LodySpacing.xs, vertical = LodySpacing.xs),
    )
}

private const val MACHINE = "machine"
private const val PROJECT = "project"
private const val CHAT = "chat"
private const val MORE = "more"

private data class PickerOption(
    val id: String,
    val title: String,
    val selected: Boolean,
    val subtitle: String = "",
    val icon: ImageVector? = null,
)

private fun CatalogProject.option(target: CreateTarget) = PickerOption(
    id = id,
    title = name,
    selected = !target.chat && id == target.projectId,
    subtitle = if (CreateSessionModel.isGithub(id)) "GitHub" else rootPath.replace(Regex("^/(Users|home)/[^/]+"), "~"),
    icon = if (CreateSessionModel.isGithub(id)) LodyIcons.Github else LodyIcons.Folder,
)

/** A full-width filled card; its options expand in place right below it (web's inline picker). */
@Composable
private fun PickerCard(
    id: String,
    icon: ImageVector,
    title: String,
    placeholder: Boolean,
    options: List<PickerOption>,
    open: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit,
    searchOptions: List<PickerOption>? = null,
    searchPlaceholder: String = "",
    expandAllOn: String? = null,
) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(LodySpacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LodySize.controlLg)
                .clip(shape)
                .background(colors.fillTertiary, shape)
                .clickable(enabled = options.isNotEmpty(), role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = title }
                .padding(horizontal = LodySpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
            Text(
                title,
                style = LodyTheme.typography.body,
                color = if (placeholder) colors.textTertiary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            // The pop-up indicator (⌃⌄), drawn as a stacked up / down pair.
            if (options.isNotEmpty()) {
                Column {
                    Icon(LodyIcons.Collapse, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.xs))
                    Icon(LodyIcons.Expand, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.xs))
                }
            }
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            OptionList(options, searchOptions, searchPlaceholder, onPick, expandAllOn)
        }
    }
}

/** The expanded options: optional search, then rows with a check on the selected one. */
@Composable
private fun OptionList(
    options: List<PickerOption>,
    searchOptions: List<PickerOption>?,
    searchPlaceholder: String,
    onPick: (String) -> Unit,
    expandAllOn: String?,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val shape = RoundedCornerShape(LodyRadius.xl)
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    val term = query.trim()
    val shown = when {
        term.isNotEmpty() -> (searchOptions ?: options).filter { "${it.title} ${it.subtitle}".contains(term, ignoreCase = true) }
        all && searchOptions != null -> searchOptions
        else -> options
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgRaised, shape)
            .border(0.5.dp, colors.borderSubtle, shape),
    ) {
        if (searchOptions != null || options.size > 8) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = LodySize.controlMd).padding(horizontal = LodySpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
            ) {
                Icon(LodyIcons.Search, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accentDefault),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(searchPlaceholder, style = type.body, color = colors.textPlaceholder)
                            inner()
                        }
                    },
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = colors.borderSubtle)
        }
        LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(shown, key = { it.id }) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LodySize.listRow)
                        .background(if (option.selected) colors.fillTertiary else Color.Transparent)
                        .clickable(role = Role.Button) { if (option.id == expandAllOn) all = true; onPick(option.id) }
                        .semantics { selected = option.selected }
                        .padding(horizontal = LodySpacing.lg, vertical = LodySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
                ) {
                    option.icon?.let { Icon(it, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md)) }
                    Column(Modifier.weight(1f)) {
                        Text(option.title, style = type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (option.subtitle.isNotEmpty()) {
                            Text(option.subtitle, style = type.footnote, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                    }
                    if (option.selected) Icon(LodyIcons.Check, contentDescription = null, tint = colors.accentFg, modifier = Modifier.size(LodyIconSize.sm))
                }
            }
        }
    }
}

/** Web's worktree chip: a checkbox and the word, toggling the local project's worktree mode. */
@Composable
private fun WorktreeChip(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.md)
    Row(
        modifier = Modifier
            .heightIn(min = LodySize.controlMd)
            .clip(shape)
            .background(colors.fillTertiary, shape)
            .clickable(role = Role.Checkbox) { onChange(!on) }
            .semantics { selected = on }
            .padding(horizontal = LodySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Box(
            Modifier
                .size(LodyIconSize.md)
                .clip(RoundedCornerShape(LodyRadius.xs))
                .background(if (on) colors.accentSolid else Color.Transparent)
                .border(1.dp, if (on) colors.accentSolid else colors.borderDefault, RoundedCornerShape(LodyRadius.xs)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(LodyIcons.Check, contentDescription = null, tint = colors.textOnAccent, modifier = Modifier.size(LodyIconSize.xs))
        }
        Text(stringResource(R.string.create_worktree), style = LodyTheme.typography.subhead, color = colors.textSecondary)
    }
}

/** Web's run-config sheet; assistants and models need Machine RPC, so it explains that for now. */
@Composable
private fun RunConfigSheet() {
    val colors = LodyTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        Text(stringResource(R.string.create_run_config), style = LodyTheme.typography.headline, color = colors.textPrimary)
        Text(stringResource(R.string.create_agent_unavailable), style = LodyTheme.typography.body, color = colors.textSecondary)
        Spacer(Modifier.heightIn(min = LodySpacing.xl))
    }
}

/** Web's mobile composer: prompt in a card; +, @ and the run-config chip leading, send trailing. */
@Composable
private fun Composer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onAttach: () -> Unit,
    onMention: () -> Unit,
    onRunConfig: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val shape = RoundedCornerShape(LodyRadius.xl)
    val placeholder = stringResource(R.string.create_composer_placeholder)
    Column(
        Modifier
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.sm)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgRaised, shape)
            .border(0.5.dp, colors.borderSubtle, shape)
            .padding(LodySpacing.sm),
    ) {
        BasicTextField(
            value = prompt,
            onValueChange = onPromptChange,
            textStyle = type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentDefault),
            minLines = 2,
            maxLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LodySpacing.sm, vertical = LodySpacing.xs)
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (prompt.isEmpty()) Text(placeholder, style = type.body, color = colors.textPlaceholder)
                    inner()
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ComposerButton(LodyIcons.Plus, stringResource(R.string.create_attach), onAttach)
            ComposerButton(LodyIcons.AtSign, stringResource(R.string.create_mention), onMention)
            // The run-config chip: assistant mark + model · reasoning · mode on web; Android has
            // no assistant data yet, so it reads as unavailable and opens the explaining sheet.
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = LodySize.controlMd)
                    .clip(RoundedCornerShape(LodyRadius.md))
                    .clickable(role = Role.Button, onClick = onRunConfig)
                    .padding(horizontal = LodySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs),
            ) {
                Icon(LodyIcons.Agent, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
                Text(stringResource(R.string.create_run_config_unavailable), style = type.subhead, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            val ready = prompt.isNotBlank()
            val send = stringResource(R.string.create_send)
            Box(
                Modifier
                    .size(LodySize.controlSm)
                    .clip(CircleShape)
                    .background(if (ready) colors.accentSolid else colors.fillSecondary)
                    .clickable(enabled = ready, role = Role.Button, onClick = onSend)
                    .semantics { contentDescription = send },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LodyIcons.Send, contentDescription = null, tint = if (ready) colors.textOnAccent else colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
            }
        }
    }
}

@Composable
private fun ComposerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }.widthIn(min = LodySize.controlMd)) {
        Icon(icon, contentDescription = null, tint = LodyTheme.colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md))
    }
}
