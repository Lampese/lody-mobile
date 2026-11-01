// New session, full screen like web; iOS counterpart: CreateSessionController + CreateSessionContext.
//
// Context lines (computer, project or chat only, GitHub branch, worktree switch for local
// projects, assistant) sit right above the composer in the title style, within thumb reach and
// riding up with the keyboard. The composer follows web's mobile composer: a card with the
// prompt, + and @ leading, the run-config chip and the send button trailing.
//
// Android is still read-only: assistants and branches need Machine RPC / GitHub reads, and
// sending needs writes, so those say they are not available yet instead of pretending.

package app.lody.android.create

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import app.lody.android.runtime.CatalogSnapshot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(snapshot: CatalogSnapshot?, onBack: () -> Unit) {
    val colors = LodyTheme.colors
    val catalog = snapshot ?: CatalogSnapshot(0, emptyList(), emptyList(), emptyList())
    var target by remember(catalog.revision == 0L) { mutableStateOf(CreateSessionModel.initial(catalog)) }
    var prompt by rememberSaveable { mutableStateOf("") }
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
            // The lines hug the composer; extra height stays above them.
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LodyLayout.screenPaddingX * 2),
            ) {
                ContextLines(catalog, target, onChange = { target = it })
            }
            Composer(prompt, onPromptChange = { prompt = it }, onAttach = notice, onMention = notice, onSend = notice)
        }
    }
}

@Composable
private fun ContextLines(catalog: CatalogSnapshot, target: CreateTarget, onChange: (CreateTarget) -> Unit) {
    val colors = LodyTheme.colors
    val machines = CreateSessionModel.machines(catalog)
    val machineName = machines.firstOrNull { it.first == target.machineId }?.second
    val machineChoosable = CreateSessionModel.machineChoosable(catalog, target)
    MenuLine(
        icon = LodyIcons.Machine,
        title = machineName ?: stringResource(R.string.create_pick_machine),
        placeholder = machineName == null,
        options = if (machineChoosable) machines.map { (id, name) -> MenuOption(id, name, selected = id == target.machineId) } else emptyList(),
        onPick = { onChange(target.copy(machineId = it)) },
    )

    val projects = CreateSessionModel.projects(catalog)
    val project = projects.firstOrNull { it.id == target.projectId }
    val chatLabel = stringResource(R.string.create_chat_only)
    MenuLine(
        icon = when {
            target.chat -> LodyIcons.Session
            CreateSessionModel.isGithub(project?.id) -> LodyIcons.Github
            else -> LodyIcons.Folder
        },
        title = if (target.chat) chatLabel else project?.name ?: stringResource(R.string.create_pick_project),
        placeholder = !target.chat && project == null,
        options = listOf(MenuOption(CHAT, chatLabel, selected = target.chat, icon = LodyIcons.Session)) +
            projects.map {
                MenuOption(it.id, it.name, selected = it.id == target.projectId, icon = if (CreateSessionModel.isGithub(it.id)) LodyIcons.Github else LodyIcons.Folder)
            },
        onPick = { id -> onChange(if (id == CHAT) CreateSessionModel.chat(catalog, target) else CreateSessionModel.select(catalog, target, id)) },
    )

    if (CreateSessionModel.isGithub(target.projectId) && !target.chat) {
        // Branches come from GitHub reads Android does not make yet.
        Line(LodyIcons.Branch, stringResource(R.string.create_branch_unavailable), placeholder = true)
    }
    if (!target.chat && project != null && !CreateSessionModel.isGithub(project.id)) {
        Line(LodyIcons.Branch, stringResource(R.string.create_worktree), placeholder = !target.worktree) {
            Switch(
                checked = target.worktree,
                onCheckedChange = { onChange(target.copy(worktree = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accentSolid),
            )
        }
    }
    // Assistants are read from the computer over Machine RPC, which Android does not run yet.
    Line(LodyIcons.Agent, stringResource(R.string.create_agent_unavailable), placeholder = true)
    Text(
        stringResource(R.string.create_readonly_note),
        style = LodyTheme.typography.footnote,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = LodySpacing.xs, bottom = LodySpacing.md),
    )
}

private const val CHAT = "chat"

private data class MenuOption(val id: String, val title: String, val selected: Boolean, val icon: ImageVector? = null)

/** A context line that opens a menu in place; with no options it is plain text. */
@Composable
private fun MenuLine(icon: ImageVector, title: String, placeholder: Boolean, options: List<MenuOption>, onPick: (String) -> Unit) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Line(icon, title, placeholder, chooser = options.isNotEmpty(), onClick = if (options.isNotEmpty()) ({ open = true }) else null)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(LodyRadius.lg),
            containerColor = colors.bgOverlay,
            border = BorderStroke(0.5.dp, colors.borderSubtle),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title, style = LodyTheme.typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = option.icon?.let { { Icon(it, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm)) } },
                    trailingIcon = { if (option.selected) Icon(LodyIcons.Check, contentDescription = null, tint = colors.accentFg, modifier = Modifier.size(LodyIconSize.sm)) },
                    onClick = { open = false; onPick(option.id) },
                )
            }
        }
    }
}

@Composable
private fun Line(
    icon: ImageVector,
    title: String,
    placeholder: Boolean,
    chooser: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.controlLg)
            .clip(RoundedCornerShape(LodyRadius.md))
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.lg))
        Text(
            title,
            style = LodyTheme.typography.title,
            color = if (placeholder) colors.textTertiary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = trailing != null),
        )
        // The pop-up indicator (⌃⌄) of a line with a choice, as iOS shows.
        if (chooser) Icon(LodyIcons.Expand, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
        trailing?.invoke()
    }
}

/** Web's mobile composer: prompt in a card; + and @ leading, run config and send trailing. */
@Composable
private fun Composer(prompt: String, onPromptChange: (String) -> Unit, onAttach: () -> Unit, onMention: () -> Unit, onSend: () -> Unit) {
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
