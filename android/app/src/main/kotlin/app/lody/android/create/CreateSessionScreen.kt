// New session using the existing scoped cloud runtime.
//
// Follows web's mobile new-chat page (`mobile-new-chat-sheet.tsx`, `mobile-inline-picker.tsx`,
// `mobile-run-config-sheet.tsx`), checked by driving its Storybook stories: computer and project
// are full-width cards whose options expand in place right below them (one open at a time, the
// project list searchable); then the worktree chip for local projects or the branch line for
// GitHub projects; then the composer (+, the run-config chip, send; no @, as web and iOS). The
// assistant lives in the run-config chip's sheet, not on the page. Styled like iOS: no fills on
// lines or lists, lists aligned with their line, computers with OS icons.
//
// Text creation and dispatch use the same workspace lease as the catalog.

package app.lody.android.create

import app.lody.android.designsystem.shadow
import app.lody.android.home.AgentMark

import app.lody.android.designsystem.LodyElevations

import androidx.compose.ui.draw.dropShadow

import androidx.compose.ui.text.withStyle

import androidx.compose.ui.text.buildAnnotatedString

import androidx.compose.ui.text.SpanStyle

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.animation.animateContentSize

import app.lody.android.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CancellationException
import java.util.UUID

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
import androidx.compose.material3.rememberModalBottomSheetState
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
fun CreateSessionScreen(snapshot: CatalogSnapshot?, repository: CreationService, runtimeScope: RuntimeScope, onCreated: (String, String) -> Unit, onBack: () -> Unit) {
    val colors = LodyTheme.colors
    val catalog = snapshot ?: CatalogSnapshot(0, emptyList(), emptyList(), emptyList())
    var target by remember(catalog.revision == 0L) { mutableStateOf(CreateSessionModel.initial(catalog)) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var runConfig by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.create_extras_unavailable)
    val notice = { scope.launch { snackbar.showSnackbar(unavailable) }; Unit }
    var options by remember(runtimeScope) { mutableStateOf<CreationOptions?>(null) }
    var targetChosen by remember(runtimeScope) { mutableStateOf(false) }
    var optionsFresh by remember(runtimeScope) { mutableStateOf(false) }
    var agentId by rememberSaveable { mutableStateOf<String?>(null) }
    var modelId by rememberSaveable { mutableStateOf<String?>(null) }
    var modeId by rememberSaveable { mutableStateOf<String?>(null) }
    var effort by rememberSaveable { mutableStateOf<String?>(null) }
    val branchRepo = target.projectId.orEmpty().removePrefix("github:")
    var branches by remember(runtimeScope, target.projectId, target.chat) { mutableStateOf(BranchState()) }
    var branchLoading by remember(runtimeScope, target.projectId, target.chat) { mutableStateOf(false) }
    var branchFailed by remember(runtimeScope, target.projectId, target.chat) { mutableStateOf(false) }
    var branchPage by remember(runtimeScope, target.projectId, target.chat) { mutableStateOf(1) }
    var branchAttempt by remember(runtimeScope, target.projectId, target.chat) { mutableStateOf(0) }
    val branch = branches.selected
    val sessionId = rememberSaveable { UUID.randomUUID().toString() }
    val turnId = rememberSaveable { UUID.randomUUID().toString() }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf<String?>(null) }
    val agents = options?.agents.orEmpty().filter { it.machineId == target.machineId }
    val agent = agents.firstOrNull { it.id == agentId } ?: agents.firstOrNull()
    val capability = options?.capability(agent)
    LaunchedEffect(runtimeScope, agent?.id, optionsFresh, capability != null) {
        val selected = agent ?: return@LaunchedEffect
        if (optionsFresh && capability == null) {
            try { repository.refreshCapability(runtimeScope, selected.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Cached choices remain usable for display. */ }
        }
    }
    val github = CreateSessionModel.isGithub(target.projectId) && !target.chat
    val canSend = optionsFresh && agent != null && !attempted && !busy && (!github || branch.isNotBlank())
    val waitingText = stringResource(R.string.create_agent)
    val unknownText = stringResource(R.string.create_result_unknown)
    val rejectedText = stringResource(R.string.create_result_rejected)
    LaunchedEffect(runtimeScope, target.machineId) {
        agentId = null; modelId = null; modeId = null; effort = null
    }
    LaunchedEffect(runtimeScope) {
        optionsFresh = false
        options = null; agentId = null; modelId = null; modeId = null; effort = null
        try { options = repository.cachedOptions(runtimeScope, null) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A missing/corrupt cache is a normal cold start. */ }
        options?.let { target = CreateSessionModel.withInitialAvailability(target, it, explicitlySelected = targetChosen) }
        // Refresh on runtime/catalog/presence changes, never on a timer.
        try {
            repository.observeOptions(runtimeScope).collect { loaded ->
                optionsFresh = loaded != null && loaded.availability == "online"
                if (loaded != null) {
                    options = loaded
                    target = CreateSessionModel.withInitialAvailability(target, loaded, explicitlySelected = targetChosen)
                }
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { optionsFresh = false }
    }
    LaunchedEffect(runtimeScope, target.projectId, target.chat, branchPage, branchAttempt) {
        if (!github) return@LaunchedEffect
        branchLoading = true
        branchFailed = false
        if (branchPage == 1 && branches.page == null) {
            try { branches = branches.restore(repository.cachedBranches(runtimeScope, branchRepo)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
        try {
            val loaded = repository.branches(runtimeScope, branchRepo, branchPage)
            branches = branches.accept(loaded, branchPage)
            try { repository.cacheBranches(runtimeScope, branchRepo, branches.page!!) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { branchFailed = true }
        finally { branchLoading = false }
    }
    BackHandler(enabled = busy) { }
    val send = {
        val chosen = agent
        if (canSend && !attempted && !busy && prompt.isNotBlank()) {
            attempted = true; busy = true
            val request = CreateRequest(sessionId, turnId, target.projectId.takeUnless { target.chat }, chosen.machineId, chosen.id, prompt, branch.takeIf { github }, target.worktree, modelId, modeId, effort)
            scope.launch {
                try {
                    val receipt = repository.create(runtimeScope, request)
                    if (receipt.state == "accepted" || receipt.state == "queued") onCreated(receipt.sessionId, prompt.take(200))
                    else outcome = if (receipt.state == "rejected") rejectedText else unknownText
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { outcome = unknownText }
                finally { busy = false }
            }
        }
        Unit
    }


    Scaffold(
        containerColor = colors.bgBase,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title), style = LodyTheme.typography.headline, color = colors.textPrimary) },
                navigationIcon = {
                    val back = stringResource(R.string.chat_back)
                    IconButton(onClick = onBack, enabled = !busy, modifier = Modifier.semantics { contentDescription = back }) {
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
                    verticalArrangement = Arrangement.spacedBy(LodySpacing.xs),
                ) {
                    ContextPanel(catalog, target,
                        availableMachines = options?.agents?.map { it.machineId }?.toSet().takeIf { optionsFresh },
                        onChange = { if (!attempted) { targetChosen = true; target = it } })
                    if (github) BranchLine(branches, branchLoading, branchFailed, enabled = !attempted,
                        onChange = { name ->
                            branches = branches.select(name)
                            val selectedRepo = branchRepo
                            scope.launch {
                                try { repository.selectBranch(runtimeScope, selectedRepo, name) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { }
                            }
                        },
                        onMore = { if (!branchLoading) { if (!branchFailed) branchPage = branches.page?.nextPage ?: 1; branchAttempt++ } })
                    if (attempted) {
                        Text(if (busy) stringResource(R.string.create_sending) else outcome ?: unknownText, style = LodyTheme.typography.footnote, color = colors.textSecondary)
                        if (!busy && outcome != rejectedText) TextButton(onClick = { onCreated(sessionId, prompt.take(200)) }) { Text(stringResource(R.string.create_open_result)) }
                    }
                }
            }
            val modelName = capability?.models?.firstOrNull { it.id == modelId }?.name
            val modeName = capability?.modes?.firstOrNull { it.id == modeId }?.name
            Composer(
                prompt,
                onPromptChange = { if (!attempted && it.length <= 32000) prompt = it },
                onAttach = notice,
                onRunConfig = { if (!attempted) runConfig = true },
                onSend = send,
                enabled = canSend,
                agentReady = agent != null,
                agentCliType = agent?.cliType,
                agentType = agent?.agentType,
                face = listOfNotNull(modelName ?: agent?.name, effort),
                mode = modeId,
                modeLabel = modeName,
                waiting = waitingText,
            )
        }
    }
    if (runConfig) {
        ModalBottomSheet(
            onDismissRequest = { runConfig = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.bgBase,
        ) {
            if (agents.isEmpty()) {
                Text(
                    stringResource(if (optionsFresh) R.string.create_no_available_agents else R.string.create_waiting_agents),
                    style = LodyTheme.typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(LodyLayout.screenPaddingX),
                )
            }
            RunConfigSheet(
                rows = buildList {
                    if (agents.isNotEmpty()) add(RunConfigRow(AGENT, stringResource(R.string.create_agent), agents.map { PickerOption(it.id, it.name, it.id == agent?.id, agent = it.cliType to it.agentType) }))
                    capability?.models?.takeIf { it.isNotEmpty() }?.let { models ->
                        add(RunConfigRow(MODEL, stringResource(R.string.create_model), models.map { PickerOption(it.id, it.name, it.id == modelId) }))
                    }
                    capability?.effortsFor(modelId)?.takeIf { it.isNotEmpty() }?.let { efforts ->
                        add(RunConfigRow(EFFORT, stringResource(R.string.create_effort), efforts.map { PickerOption(it, it, it == effort) }))
                    }
                    capability?.modes?.takeIf { it.isNotEmpty() }?.let { modes ->
                        add(RunConfigRow(MODE, stringResource(R.string.create_mode), modes.map { PickerOption(it.id, it.name, it.id == modeId) }))
                    }
                },
                defaultLabel = stringResource(R.string.create_default),
            ) { row, id ->
                when (row) {
                    AGENT -> { agentId = id; modelId = null; modeId = null; effort = null }
                    MODEL -> { modelId = id; effort = null }
                    EFFORT -> effort = id
                    MODE -> modeId = id
                }
            }
        }
    }
}

private const val AGENT = "agent"
private const val MODEL = "model"
private const val EFFORT = "effort"
private const val MODE = "mode"

@Composable
private fun ContextPanel(catalog: CatalogSnapshot, target: CreateTarget, availableMachines: Set<String>?, onChange: (CreateTarget) -> Unit) {
    // One list open at a time, as web's picker coordinator.
    var open by remember { mutableStateOf<String?>(null) }
    val toggle = { id: String -> open = if (open == id) null else id }

    val machines = CreateSessionModel.machines(catalog)
    val machineName = machines.firstOrNull { it.first == target.machineId }?.second
    val unavailableMachine = stringResource(R.string.create_no_available_agents)
    PickerCard(
        icon = machineIcon(catalog, target.machineId),
        title = machineName ?: stringResource(R.string.create_pick_machine),
        placeholder = machineName == null,
        options = if (CreateSessionModel.machineChoosable(catalog)) machines.map { (id, name) ->
            val unavailable = availableMachines != null && id !in availableMachines
            PickerOption(id, name, selected = id == target.machineId, icon = machineIcon(catalog, id),
                subtitle = if (unavailable) unavailableMachine else "", disabled = unavailable)
        } else emptyList(),
        open = open == MACHINE,
        onToggle = { toggle(MACHINE) },
        onPick = { open = null; onChange(CreateSessionModel.pickMachine(catalog, target, it)) },
        searchPlaceholder = stringResource(R.string.create_search_machines),
    )

    val projects = CreateSessionModel.projects(catalog)
    val project = projects.firstOrNull { it.id == target.projectId }
    val chatLabel = stringResource(R.string.create_chat_only)
    val chatOption = PickerOption(CHAT, chatLabel, selected = target.chat, icon = LodyIcons.Close)
    val usable = projects.filter { CreateSessionModel.isGithub(it.id) || target.machineId == null || it.machineId == target.machineId }
    PickerCard(
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
                MORE -> Unit // More expands the list to every project in place.
                CHAT -> { open = null; onChange(CreateSessionModel.chat(catalog, target)) }
                else -> { open = null; onChange(CreateSessionModel.select(catalog, target, id)) }
            }
        },
        expandAllOn = MORE,
    )

    val local = !target.chat && project != null && !CreateSessionModel.isGithub(project.id)
    if (local) WorktreeChip(target.worktree) { onChange(target.copy(worktree = it)) }
}

/** iOS draws SF Symbols by OS; Android uses the matching Lucide marks. */
private fun machineIcon(catalog: CatalogSnapshot, id: String?): ImageVector = when (CreateSessionModel.machineKind(catalog, id)) {
    CreateSessionModel.MachineKind.Laptop -> LodyIcons.Machine
    CreateSessionModel.MachineKind.Desktop -> LodyIcons.Desktop
    CreateSessionModel.MachineKind.Server -> LodyIcons.Server
}

private const val MACHINE = "machine"
private const val PROJECT = "project"
private const val CHAT = "chat"
private const val MORE = "more"

internal data class PickerOption(
    val id: String,
    val title: String,
    val selected: Boolean,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val disabled: Boolean = false,
    /** An agent's provider (cliType to agentType): leads with its mark instead of [icon]. */
    val agent: Pair<String, String>? = null,
)

/** The option's leading mark: the agent's provider mark, else its icon. */
@Composable
private fun OptionMark(option: PickerOption, size: androidx.compose.ui.unit.Dp) {
    val tint = LodyTheme.colors.iconSecondary
    when {
        option.agent != null -> AgentMark(option.agent.first, option.agent.second, tint, Modifier.size(size))
        option.icon != null -> Icon(option.icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

private fun CatalogProject.option(target: CreateTarget) = PickerOption(
    id = id,
    title = name,
    selected = !target.chat && id == target.projectId,
    subtitle = if (CreateSessionModel.isGithub(id)) "GitHub" else rootPath.replace(Regex("^/(Users|home)/[^/]+"), "~"),
    icon = if (CreateSessionModel.isGithub(id)) LodyIcons.Github else LodyIcons.Folder,
)

/**
 * A plain full-width line (no fill, as iOS); its options expand in place right below it. The
 * panel is bottom-anchored, so the list is revealed from its top edge and moves up with the
 * layout instead of sliding against it.
 */
@Composable
private fun PickerCard(
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
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LodySize.controlLg)
                .clip(RoundedCornerShape(LodyRadius.md))
                .clickable(enabled = options.isNotEmpty(), role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = title }
                .padding(horizontal = LodySpacing.xs),
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
            if (options.isNotEmpty()) PopupIndicator()
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            OptionList(options, searchOptions, searchPlaceholder, onPick, expandAllOn)
        }
    }
}

/** The pop-up indicator (⌃⌄), drawn as a stacked up / down pair. */
@Composable
private fun PopupIndicator() {
    val tint = LodyTheme.colors.iconTertiary
    Column {
        Icon(LodyIcons.Collapse, contentDescription = null, tint = tint, modifier = Modifier.size(LodyIconSize.xs))
        Icon(LodyIcons.Expand, contentDescription = null, tint = tint, modifier = Modifier.size(LodyIconSize.xs))
    }
}

/** The expanded options, aligned with the line (no indent or surface): search, then rows. */
@Composable
private fun OptionList(
    options: List<PickerOption>,
    searchOptions: List<PickerOption>?,
    searchPlaceholder: String,
    onPick: (String) -> Unit,
    expandAllOn: String?,
    expandsToContent: Boolean = false,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    val term = query.trim()
    val shown = when {
        term.isNotEmpty() -> (searchOptions ?: options).filter { "${it.title} ${it.subtitle}".contains(term, ignoreCase = true) }
        all && searchOptions != null -> searchOptions
        else -> options
    }
    Column(Modifier.fillMaxWidth()) {
        if (searchOptions != null || options.size > 8) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = LodySize.controlMd).padding(horizontal = LodySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
            ) {
                Icon(LodyIcons.Search, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.md))
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
        }
        val optionContent: @Composable (PickerOption) -> Unit = { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LodySize.listRow)
                        .clip(RoundedCornerShape(LodyRadius.md))
                        .clickable(enabled = !option.disabled, role = Role.Button) { if (option.id == expandAllOn) all = true; onPick(option.id) }
                        .semantics { selected = option.selected }
                        .padding(horizontal = LodySpacing.xs, vertical = LodySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
                ) {
                    OptionMark(option, LodyIconSize.md)
                    Column(Modifier.weight(1f)) {
                        Text(option.title, style = type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (option.subtitle.isNotEmpty()) {
                            Text(option.subtitle, style = type.footnote, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                    }
                    if (option.selected) Icon(LodyIcons.Check, contentDescription = null, tint = colors.accentFg, modifier = Modifier.size(LodyIconSize.sm))
                }
        }
        if (expandsToContent) {
            Column { shown.forEach { option -> androidx.compose.runtime.key(option.id) { optionContent(option) } } }
        } else {
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(shown, key = { it.id }) { option -> optionContent(option) }
            }
        }
    }
}

/** Web's worktree chip as iOS draws it: a checkbox and the word, no fill. */
@Composable
private fun WorktreeChip(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = LodySize.controlMd)
            .clip(RoundedCornerShape(LodyRadius.md))
            .clickable(role = Role.Checkbox) { onChange(!on) }
            .semantics { selected = on }
            .padding(horizontal = LodySpacing.xs),
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

/** Cached choices stay interactive while the next snapshot loads. */
@Composable
private fun BranchLine(state: BranchState, loading: Boolean, failed: Boolean, enabled: Boolean, onChange: (String) -> Unit, onMore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val colors = LodyTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = LodySize.controlMd).clickable(enabled = enabled) { open = !open }
            .padding(horizontal = LodySpacing.xs), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.md)) {
            Icon(LodyIcons.Branch, null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
            Text(state.selected.ifEmpty { stringResource(R.string.create_branch_name) }, style = LodyTheme.typography.body,
                color = if (state.selected.isEmpty()) colors.textPlaceholder else colors.textPrimary, modifier = Modifier.weight(1f))
            if (loading && state.page == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(LodyIconSize.sm))
            else PopupIndicator()
        }
        if (open) {
            BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = LodyTheme.typography.body.copy(color = colors.textPrimary),
                modifier = Modifier.fillMaxWidth().padding(LodySpacing.sm),
                decorationBox = { field -> Box { if (query.isEmpty()) Text(stringResource(R.string.create_search_branches), style = LodyTheme.typography.body, color = colors.textPlaceholder); field() } })
            LazyColumn(Modifier.heightIn(max = LodySize.controlMd * 6)) {
              items(state.page?.names.orEmpty().filter { it.contains(query.trim(), ignoreCase = true) }, key = { it }) { name ->
                Row(Modifier.fillMaxWidth().heightIn(min = LodySize.controlMd).clickable(enabled = enabled) { onChange(name); open = false }
                    .padding(horizontal = LodySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = LodyTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (name == state.selected) Icon(LodyIcons.Check, null, tint = colors.accentFg, modifier = Modifier.size(LodyIconSize.sm))
                }
            }
            }
            if (failed || state.page?.nextPage != null || (loading && state.page == null)) {
                TextButton(onClick = onMore, enabled = enabled && !loading) {
                    if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(LodyIconSize.sm))
                    else Text(stringResource(if (failed) R.string.chat_retry else R.string.create_more_branches))
                }
            }
        }
    }
}

internal data class RunConfigRow(val id: String, val label: String, val options: List<PickerOption>)

/** Web's run-config sheet as iOS draws it: label + value rows whose options expand in place. */
@Composable
internal fun RunConfigSheet(rows: List<RunConfigRow>, defaultLabel: String, onPick: (String, String) -> Unit) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = LodyLayout.screenPaddingX).padding(bottom = LodySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LodySpacing.xs),
    ) {
        Text(
            stringResource(R.string.create_run_config),
            style = LodyTheme.typography.headline,
            color = colors.textPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = LodySpacing.sm),
        )
        rows.forEach { row ->
            val current = row.options.firstOrNull { it.selected }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = LodySize.controlLg)
                    .clip(RoundedCornerShape(LodyRadius.md))
                    .clickable(role = Role.Button) { open = if (open == row.id) null else row.id }
                    .padding(horizontal = LodySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
            ) {
                Text(row.label, style = LodyTheme.typography.subhead, color = colors.textSecondary, modifier = Modifier.widthIn(min = RunConfigLabelWidth))
                if (current != null) OptionMark(current, LodyIconSize.sm)
                Text(
                    current?.title ?: defaultLabel,
                    style = LodyTheme.typography.body,
                    color = if (current == null) colors.textTertiary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PopupIndicator()
            }
            AnimatedVisibility(
                open == row.id,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                OptionList(row.options, null, "", { id -> open = null; onPick(row.id, id) }, null, expandsToContent = true)
            }
        }
    }
}

private val RunConfigLabelWidth = LodySize.controlLg * 2

/**
 * Web's mobile composer on a floating surface (Android has no Liquid Glass; the Home tab bar's
 * overlay + shadow stands in): prompt, then + and the run-config chip leading, send trailing.
 * No @ button, as web and iOS. One line at rest; three while editing.
 */
@Composable
private fun Composer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRunConfig: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    agentReady: Boolean,
    agentCliType: String?,
    agentType: String?,
    face: List<String>,
    mode: String?,
    modeLabel: String?,
    waiting: String,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val shape = RoundedCornerShape(LodyRadius.xl)
    val placeholder = stringResource(R.string.create_composer_placeholder)
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.sm)
            .fillMaxWidth()
            .dropShadow(shape, LodyElevations.md.shadow())
            .clip(shape)
            .background(colors.bgOverlay, shape)
            .animateContentSize()
            .padding(LodySpacing.sm),
    ) {
        BasicTextField(
            value = prompt,
            onValueChange = onPromptChange,
            textStyle = type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentDefault),
            minLines = if (focused) 3 else 1,
            maxLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
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
            // The run-config chip: assistant mark + model · effort · mode (mode dimmed), as iOS.
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = LodySize.controlMd)
                    .clip(RoundedCornerShape(LodyRadius.md))
                    .clickable(role = Role.Button, onClick = onRunConfig)
                    .padding(horizontal = LodySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
            ) {
                AgentMark(agentCliType, agentType, colors.iconSecondary, Modifier.size(LodyIconSize.sm))
                val label = buildAnnotatedString {
                    if (!agentReady || face.isEmpty()) {
                        withStyle(SpanStyle(color = colors.textTertiary)) { append(waiting) }
                    } else {
                        face.forEachIndexed { index, part ->
                            if (index > 0) withStyle(SpanStyle(color = colors.textTertiary)) { append("  ·  ") }
                            withStyle(SpanStyle(color = colors.textPrimary)) { append(part) }
                        }

                    }
                }
                Text(label, style = type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                ModeMark(mode, modeLabel)

            }
            Spacer(Modifier.weight(1f))
            val ready = enabled && prompt.isNotBlank()
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

/** The run-config chip's permission face: a glyph per known mode, `Auto` for auto modes, else nothing. */
@Composable
internal fun ModeMark(mode: String?, label: String?) {
    val colors = LodyTheme.colors
    val warning = mode in listOf("agent-full-access", "danger-full-access", "dontAsk", "bypassPermissions", "yolo", "always-approve")
    val icon = when {
        warning -> LodyIcons.PermissionWarning
        mode == "agent" || mode == "default" -> LodyIcons.PermissionDefault
        mode == "read-only" -> LodyIcons.Eye
        mode == "acceptEdits" -> LodyIcons.Edit
        mode == "plan" -> LodyIcons.PermissionPlan
        else -> null
    }
    if (icon != null) Icon(icon, contentDescription = label, tint = if (warning) colors.warningDefault else colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
    if (mode == "auto" || mode == "agent-auto-review") Text(stringResource(R.string.create_mode_auto), style = LodyTheme.typography.caption, color = colors.textSecondary)
}

@Composable
private fun ComposerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }.widthIn(min = LodySize.controlMd)) {
        Icon(icon, contentDescription = null, tint = LodyTheme.colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md))
    }
}
