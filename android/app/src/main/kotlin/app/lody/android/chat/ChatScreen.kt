// A conversation, after web's mobile session page as it runs in the app: a floating header (round
// back button, title, repo) over the transcript; user turns as large right-aligned bubbles with
// the delivery mark, time and sender above and the avatar beside; agent turns open with the
// process line and run full width; a round scroll-to-latest button; a bordered composer card
// (+, the run-config chip, send) under the action bar (web `SessionInfoBar`: computer, repository
// context, repository actions). iOS counterpart: SessionController.

package app.lody.android.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.lody.android.R
import app.lody.android.auth.Account
import app.lody.android.designsystem.*
import app.lody.android.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** What the page knows about the conversation from the catalog, for its header and sending. */
data class ChatContext(
    val title: String,
    val subtitle: String = "",
    val session: CatalogSession? = null,
    val me: Account? = null,
    /** The GitHub repository (`owner/name`) the session works in; empty without one. */
    val repository: String = "",
    val machineName: String = "",
    val machineKind: app.lody.android.create.CreateSessionModel.MachineKind = app.lody.android.create.CreateSessionModel.MachineKind.Laptop,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(repository: ChatRepository, scope: RuntimeScope, id: String, context: ChatContext, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var retry by remember(id, scope) { mutableIntStateOf(0) }
    var status by remember(id, scope) { mutableStateOf("syncing") }
    var messages by remember(id, scope) { mutableStateOf(emptyList<SessionMessage>()) }
    var entries by remember(id, scope) { mutableStateOf(emptyList<ChatEntry>()) }
    // Turns this page sent that the transcript has not shown yet (optimistic, never resent).
    var outgoing by remember(id, scope) { mutableStateOf(emptyList<Outgoing>()) }
    var prompt by rememberSaveable(id) { mutableStateOf("") }
    val appContext = LocalContext.current.applicationContext
    val coroutines = rememberCoroutineScope()
    val agentName = agentLabel(context.session)
    val meName = context.me?.name.orEmpty()
    // Resolved in composition: the transcript is built off the main thread.
    val memberLabel = stringResource(R.string.chat_member)
    val workedFormat = stringResource(R.string.chat_worked_for)
    LaunchedEffect(scope, id, retry) {
        val repair = QuickJsMarkdownRepair.fromAssets(appContext)
        val rendered = mutableMapOf<String, Pair<SessionMessage, ChatEntry>>()
        try {
            repository.observe(scope, id).conflate().collect { snapshot ->
                status = snapshot.status
                messages = snapshot.messages
                entries = withContext(Dispatchers.Default) {
                    val next = snapshot.messages.map { message ->
                        rendered[message.id]?.takeIf { it.first == message }?.second ?: when (message.role) {
                            "user" -> ChatEntry.User(
                                message.id, message.text,
                                TurnIdentity(senderName(message.userId, context.me, memberLabel), senderImage(message.userId, context.me), turnTime(message.timestamp)),
                                delivery(message.status),
                            )
                            else -> ChatEntry.Assistant(
                                message.id,
                                flowOf(MessageText(message.text, message.streaming)).renderMarkdown(repair).first(),
                                TurnIdentity(message.model?.let { "$agentName · $it" } ?: agentName, time = turnTime(message.timestamp)),
                                worked(message, workedFormat),
                                working = message.streaming,
                            )
                        }.also { rendered[message.id] = message to it }
                    }
                    rendered.keys.retainAll(snapshot.messages.map { it.id }.toSet())
                    next
                }
                if (snapshot.messages.any { it.streaming }) delay(33)
            }
        } finally { repair.close() }
    }
    // A sent turn leaves the optimistic list once the transcript carries its id.
    val shownIds = remember(messages) { messages.map { it.id }.toSet() }
    val pending = outgoing.filter { it.turnId !in shownIds }
    val all = entries + pending.map {
        ChatEntry.User(it.turnId, it.text, TurnIdentity(meName, context.me?.image, turnTime(it.at)), it.delivery)
    }
    val session = context.session
    val canSend = status == "live" && session?.cliType != null && session.agentType != null
    val failedText = stringResource(R.string.chat_send_failed)
    val attachText = stringResource(R.string.chat_attach_unavailable)
    val snackbar = remember { SnackbarHostState() }
    val running = session?.status in RunningStatuses || pending.any { it.delivery == Delivery.Sending }
    // One send path for typed turns and the action bar's prompts: optimistic bubble, never resent.
    val sendText: (String) -> Unit = sendText@{ text ->
        if (!canSend || text.isEmpty() || session == null) return@sendText
        val turnId = UUID.randomUUID().toString()
        outgoing = outgoing + Outgoing(turnId, text, Instant.now().toString(), Delivery.Sending)
        coroutines.launch {
            val queue = session.status in RunningStatuses
            val result = try {
                repository.send(scope, SendTextRequest(id, turnId, text, session.machineId, session.cliType!!, session.agentType!!, session.resume, queue = queue)).state
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { "unknown" }
            val delivery = when (result) {
                "queued" -> Delivery.Queued
                "not_sent" -> Delivery.Failed
                else -> Delivery.Sent
            }
            outgoing = outgoing.map { if (it.turnId == turnId) it.copy(delivery = delivery) else it }
            if (result == "not_sent") snackbar.showSnackbar(failedText)
        }
        Unit
    }
    val send = {
        val text = prompt.trim()
        if (canSend && text.isNotEmpty()) {
            sendText(text)
            prompt = ""
        }
    }
    val bar = session?.let { SessionInfoBar.of(it, context.repository, busy = running || !canSend) }
    val prompts = SessionInfoBar.Prompts(
        stringResource(R.string.bar_prompt_createPrOrigin), stringResource(R.string.bar_prompt_createDraftPrOrigin),
        stringResource(R.string.bar_prompt_createPr), stringResource(R.string.bar_prompt_createDraftPr),
        stringResource(R.string.bar_prompt_prBranchUpkeep), stringResource(R.string.bar_prompt_commitAndPush),
        stringResource(R.string.bar_prompt_resolveConflicts), stringResource(R.string.bar_prompt_fixCi),
    )

    val colors = LodyTheme.colors
    val listState = rememberLazyListState()
    val atLatest by remember { derivedStateOf { !listState.canScrollForward } }
    val headerHeight = LodySize.controlLg + LodySpacing.md
    Scaffold(
        containerColor = colors.bgBase,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + headerHeight
                if (all.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (status == "live") R.string.chat_empty else if (status == "offline") R.string.chat_offline else R.string.chat_loading), style = LodyTheme.typography.body, color = colors.textSecondary)
                    }
                } else {
                    // The transcript scrolls under the floating header.
                    ChatTranscript(all, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = LodySpacing.md), state = listState)
                }
                ChatHeader(context.title, context.subtitle, onBack)
                if (status == "offline") {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = top).padding(horizontal = TranscriptMargin).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.chat_offline), style = LodyTheme.typography.footnote, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_retry), color = colors.accentFg) }
                    }
                }
                // Web's round "scroll to latest" button, shown once the reader has scrolled up.
                if (!atLatest && all.isNotEmpty()) {
                    val label = stringResource(R.string.chat_scroll_latest)
                    RoundButton(
                        LodyIcons.Expand, label,
                        Modifier.align(Alignment.BottomEnd).padding(end = TranscriptMargin, bottom = LodySpacing.md),
                    ) { coroutines.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE) } }
                }
            }
            if (bar != null) {
                ChatActionBar(
                    bar, context.machineName, context.machineKind,
                    onAction = { action ->
                        // Only while still offered, so a menu left open cannot send for a state that moved on.
                        if (action in bar.actions) sendText(bar.prompt(action, prompts))
                    },
                    onCopied = { coroutines.launch { snackbar.showSnackbar(it) } },
                )
            }
            ChatComposer(
                prompt,
                onPromptChange = { if (it.length <= 32000) prompt = it },
                enabled = canSend,
                model = session?.modelName,
                onAttach = { coroutines.launch { snackbar.showSnackbar(attachText) } },
                onSend = send,
            )
        }
    }
}

private val RunningStatuses = setOf("running", "initializing", "processing", "in_progress", "queued")

/** Web's floating header: a round back button, then the title over the repository line. */
@Composable
private fun BoxScope.ChatHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val colors = LodyTheme.colors
    Row(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            // The page colour fades out under the header so the transcript reads beneath it.
            .background(Brush.verticalGradient(0f to colors.bgBase, 0.7f to colors.bgBase.copy(alpha = 0.85f), 1f to colors.bgBase.copy(alpha = 0f)))
            .statusBarsPadding()
            .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        RoundButton(LodyIcons.Back, stringResource(R.string.chat_back), onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs)) {
                    Icon(LodyIcons.Github, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
                    Text(subtitle, style = LodyTheme.typography.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Web's round glass-like control (header back, scroll to latest). */
@Composable
private fun RoundButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LodyTheme.colors
    Box(
        modifier
            .size(LodySize.controlLg)
            .dropShadow(CircleShape, LodyElevations.sm.shadow())
            .clip(CircleShape)
            .background(colors.bgOverlay)
            .border(0.5.dp, colors.borderSubtle, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md))
    }
}

private data class Outgoing(val turnId: String, val text: String, val at: String, val delivery: Delivery)

private fun delivery(status: String) = when (status) {
    "seen", "completed", "done" -> Delivery.Seen
    "queued" -> Delivery.Queued
    "failed", "not_sent" -> Delivery.Failed
    else -> Delivery.Sent
}

/** The signed-in user is named and pictured; other members need the member list (not loaded yet). */
private fun senderName(userId: String?, me: Account?, member: String): String =
    if (me != null && (userId == null || userId == me.id)) me.name else member

private fun senderImage(userId: String?, me: Account?): String? =
    if (me != null && (userId == null || userId == me.id)) me.image else null

/** The session's assistant, by its last model or its agent type ("claude-code" → "Claude Code"). */
private fun agentLabel(session: CatalogSession?): String =
    session?.agentType?.split('-', '_')?.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        ?: session?.modelName ?: "Agent"

/** "Worked for 8s" / "1m 5s" once the turn finished; [format] holds one `%1$s`. */
private fun worked(message: SessionMessage, format: String): String {
    val start = message.startedAt ?: return ""
    val end = message.endedAt ?: return ""
    if (message.streaming || end < start) return ""
    val seconds = (end - start) / 1000
    val span = if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
    return String.format(format, span)
}

/** Today: "09:31"; otherwise "7/9 09:31", in the device's zone. */
private fun turnTime(timestamp: String?): String {
    val instant = timestamp?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val zoned = instant.atZone(ZoneId.systemDefault())
    val pattern = if (zoned.toLocalDate() == LocalDate.now()) "HH:mm" else "M/d HH:mm"
    return zoned.format(DateTimeFormatter.ofPattern(pattern))
}

/**
 * Web's mobile composer card: the prompt, then + and the run-config chip (agent mark + model)
 * leading and send trailing. One line at rest, two while editing.
 */
@Composable
private fun ChatComposer(prompt: String, onPromptChange: (String) -> Unit, enabled: Boolean, model: String?, onAttach: () -> Unit, onSend: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val shape = RoundedCornerShape(LodySpacing.xl)
    val placeholder = stringResource(if (enabled) R.string.chat_composer_placeholder else R.string.chat_composer_unavailable)
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgRaised, shape)
            .border(0.5.dp, colors.borderSubtle, shape)
            .animateContentSize()
            .padding(horizontal = LodySpacing.sm, vertical = LodySpacing.sm),
    ) {
        BasicTextField(
            value = prompt,
            onValueChange = onPromptChange,
            enabled = enabled,
            textStyle = type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentDefault),
            minLines = if (focused) 2 else 1,
            maxLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = LodySpacing.md, vertical = LodySpacing.sm)
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (prompt.isEmpty()) Text(placeholder, style = type.body, color = colors.textPlaceholder)
                    inner()
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val attach = stringResource(R.string.create_attach)
            IconButton(onClick = onAttach, modifier = Modifier.semantics { contentDescription = attach }) {
                Icon(LodyIcons.Plus, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
            }
            if (model != null) {
                Row(
                    Modifier.weight(1f, fill = false).padding(horizontal = LodySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                ) {
                    Icon(LodyIcons.Agent, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
                    Text(model, style = type.subhead, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            val ready = enabled && prompt.isNotBlank()
            val send = stringResource(R.string.create_send)
            Box(
                Modifier
                    .size(LodySize.controlLg - LodySpacing.xs)
                    .clip(CircleShape)
                    .background(if (ready) colors.accentSolid else colors.fillSecondary)
                    .clickable(enabled = ready, role = Role.Button, onClick = onSend)
                    .semantics { contentDescription = send },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LodyIcons.Send, contentDescription = null, tint = if (ready) colors.textOnAccent else colors.iconTertiary, modifier = Modifier.size(LodyIconSize.md))
            }
        }
    }
}

/**
 * Web `SessionInfoBar` above the composer, with native menus: the computer (icon; its menu names
 * it), the repository context (PR / branch, CI, ±diff; its menu opens the PR or copies the
 * branch) and the repository actions as one accent split button — the top action, then a chevron
 * whose menu holds the rest. iOS counterpart: `SessionController+InfoBar`.
 */
@Composable
private fun ChatActionBar(
    bar: SessionInfoBar,
    machine: String,
    machineKind: app.lody.android.create.CreateSessionModel.MachineKind,
    onAction: (SessionInfoBar.Action) -> Unit,
    onCopied: (String) -> Unit,
) {
    if (machine.isEmpty() && !bar.hasContext && bar.actions.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LodySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        if (machine.isNotEmpty()) MachineChip(machine, machineKind)
        if (bar.hasContext) ContextChip(bar, onCopied)
        if (bar.actions.isNotEmpty()) ActionSplit(bar.actions, onAction)
    }
}

/** A raised pill in the bar; its 48 dp touch target does not grow the pill. */
@Composable
private fun BarChip(label: String, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val colors = LodyTheme.colors
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(LodySize.controlSm)
            .clip(CircleShape)
            .background(colors.bgRaised)
            .border(0.5.dp, colors.borderSubtle, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = LodySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs),
        content = content,
    )
}

@Composable
private fun MachineChip(name: String, kind: app.lody.android.create.CreateSessionModel.MachineKind) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val icon = when (kind) {
        app.lody.android.create.CreateSessionModel.MachineKind.Laptop -> LodyIcons.Machine
        app.lody.android.create.CreateSessionModel.MachineKind.Desktop -> LodyIcons.Desktop
        app.lody.android.create.CreateSessionModel.MachineKind.Server -> LodyIcons.Server
    }
    Box {
        BarChip("${stringResource(R.string.bar_machine)}, $name", onClick = { open = true }) {
            Icon(icon, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(name, style = LodyTheme.typography.body, color = colors.textPrimary) },
                leadingIcon = { Icon(icon, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md)) },
                onClick = { open = false },
                enabled = false,
            )
        }
    }
}

@Composable
private fun ContextChip(bar: SessionInfoBar, onCopied: (String) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val pr = bar.pullRequest
    val (icon, tint) = when (pr?.status) {
        null -> LodyIcons.Branch to colors.iconSecondary
        "merged" -> LodyIcons.Merge to colors.prMerged
        "closed" -> LodyIcons.PullRequestClosed to colors.prClosed
        "draft" -> LodyIcons.PullRequestDraft to colors.prDraft
        else -> LodyIcons.PullRequest to colors.prOpen
    }
    val ci = pr?.ci?.takeIf { pr.status == "open" || pr.status == "draft" }
    val verdict = when (ci) {
        "f" -> stringResource(R.string.bar_ci_f) to colors.dangerDefault
        "e" -> stringResource(R.string.bar_ci_e) to colors.dangerDefault
        "p" -> stringResource(R.string.bar_ci_p) to colors.warningDefault
        else -> null
    }
    val label = pr?.let { "#${it.number}" } ?: bar.branch.ifEmpty { bar.repository }
    val copied = stringResource(R.string.bar_branch_copied)
    val description = listOf(bar.repository, bar.branch, label, verdict?.first.orEmpty()).filter { it.isNotEmpty() }.distinct().joinToString(", ")
    Box {
        BarChip(description, onClick = { open = true }) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(LodyIconSize.sm))
            Text(label, style = type.subhead, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.widthIn(max = 160.dp))
            verdict?.let { (text, color) -> Text(text, style = type.footnote, color = color, maxLines = 1) }
            if (bar.added + bar.removed > 0) {
                Text("+${bar.added}", style = type.footnote.copy(fontFeatureSettings = "tnum"), color = colors.diffAddedText)
                Text("−${bar.removed}", style = type.footnote.copy(fontFeatureSettings = "tnum"), color = colors.diffRemovedText)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            if (pr != null) {
                val state = stringResource(
                    when (pr.status) {
                        "merged" -> R.string.home_pr_merged
                        "closed" -> R.string.home_pr_closed
                        "draft" -> R.string.home_pr_draft
                        else -> R.string.home_pr_open
                    },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("${pr.repository} #${pr.number}", style = type.body, color = colors.textPrimary)
                            Text(listOfNotNull(state, verdict?.first, if (pr.merge == "d") stringResource(R.string.bar_conflicts) else null).joinToString(" · "), style = type.footnote, color = colors.textSecondary)
                        }
                    },
                    leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(LodyIconSize.md)) },
                    trailingIcon = { Icon(LodyIcons.ExternalLink, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm)) },
                    onClick = { open = false; uri.openUri(bar.pullRequestUrl) },
                )
            }
            if (bar.branch.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.bar_copy_branch), style = type.body, color = colors.textPrimary)
                            Text(bar.branch, style = type.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                    },
                    leadingIcon = { Icon(LodyIcons.Copy, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md)) },
                    onClick = {
                        open = false
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(bar.branch))
                        onCopied(copied)
                    },
                )
            }
        }
    }
}

/**
 * Web's primary action + chevron as one accent surface with a soft internal divider: the top
 * action sends on tap (with a confirm haptic); the chevron's menu holds the rest in priority order.
 */
@Composable
private fun ActionSplit(actions: List<SessionInfoBar.Action>, onAction: (SessionInfoBar.Action) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val first = actions.first()
    val more = stringResource(R.string.bar_more)
    Box(Modifier.minimumInteractiveComponentSize()) {
        Row(
            Modifier.height(LodySize.controlSm).clip(CircleShape).background(colors.accentSolid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = actionTitle(first)
            Row(
                Modifier
                    .fillMaxHeight()
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onAction(first)
                    }
                    .padding(start = LodySpacing.md, end = if (actions.size > 1) LodySpacing.sm else LodySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs),
            ) {
                Icon(actionIcon(first), contentDescription = null, tint = colors.textOnAccent, modifier = Modifier.size(LodyIconSize.sm))
                Text(title, style = type.subhead, color = colors.textOnAccent, maxLines = 1)
            }
            if (actions.size > 1) {
                Box(Modifier.width(1.dp).height(LodyIconSize.md).background(colors.textOnAccent.copy(alpha = 0.3f)))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = more) { open = true }
                        .semantics { contentDescription = more }
                        .padding(horizontal = LodySpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LodyIcons.Expand, contentDescription = null, tint = colors.textOnAccent, modifier = Modifier.size(LodyIconSize.sm))
                }
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            for (action in actions.drop(1)) {
                DropdownMenuItem(
                    text = { Text(actionTitle(action), style = type.body, color = colors.textPrimary) },
                    leadingIcon = { Icon(actionIcon(action), contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md)) },
                    onClick = {
                        open = false
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onAction(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun actionTitle(action: SessionInfoBar.Action): String = stringResource(
    when (action) {
        SessionInfoBar.Action.CreatePr -> R.string.bar_action_create_pr
        SessionInfoBar.Action.CreateDraftPr -> R.string.bar_action_create_draft_pr
        SessionInfoBar.Action.CommitAndPush -> R.string.bar_action_commit_and_push
        SessionInfoBar.Action.ResolveConflicts -> R.string.bar_action_resolve_conflicts
        SessionInfoBar.Action.FixCi -> R.string.bar_action_fix_ci_errors
    },
)

private fun actionIcon(action: SessionInfoBar.Action): ImageVector = when (action) {
    SessionInfoBar.Action.CreatePr -> LodyIcons.PullRequest
    SessionInfoBar.Action.CreateDraftPr -> LodyIcons.PullRequestDraft
    SessionInfoBar.Action.CommitAndPush -> LodyIcons.Send
    SessionInfoBar.Action.ResolveConflicts -> LodyIcons.Merge
    SessionInfoBar.Action.FixCi -> LodyIcons.Check
}
