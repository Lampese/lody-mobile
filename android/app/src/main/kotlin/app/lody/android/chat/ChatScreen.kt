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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
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
    val tabs: List<CatalogSession> = emptyList(),
    val repository: String = "",
    val machineName: String = "",
    val machineKind: app.lody.android.create.CreateSessionModel.MachineKind = app.lody.android.create.CreateSessionModel.MachineKind.Laptop,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(repository: ChatRepository, scope: RuntimeScope, id: String, context: ChatContext, onBack: () -> Unit, onSelectTab: (String) -> Unit = {}) {
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
    val application = appContext.applicationContext as app.lody.android.LodyApplication
    val accountState by application.account.state.collectAsState()
    var members by remember(scope) { mutableStateOf(emptyMap<String, app.lody.android.auth.WorkspaceMember>()) }
    LaunchedEffect(scope, accountState.verified) {
        application.members.observe(scope).collect { members = it }
    }
    val memberLabel = stringResource(R.string.chat_member)
    val workedFormat = stringResource(R.string.chat_worked_for)
    LaunchedEffect(scope, id, retry) {
        val repair = QuickJsMarkdownRepair.fromAssets(appContext)
        val rendered = mutableMapOf<String, Pair<SessionMessage, ChatEntry>>()
        // Prose items keep their Markdown while their text is unchanged, so a streaming turn
        // re-renders only the item that grows.
        val proseCache = mutableMapOf<String, Pair<String, MarkdownBlocks>>()
        // Web shows thoughts only for builtin DSH.
        val showThoughts = context.session?.cliType == "builtin" && context.session.agentType == "deepseek"
        try {
            repository.observe(scope, id).conflate().collect { snapshot ->
                status = snapshot.status
                messages = snapshot.messages
                entries = withContext(Dispatchers.Default) {
                    val next = snapshot.messages.map { message ->
                        rendered[message.id]?.takeIf { it.first == message }?.second ?: when (message.role) {
                            "user" -> ChatEntry.User(
                                message.id, message.text,
                                TurnIdentity("", time = turnTime(message.timestamp)),
                                delivery(message.status),
                            )
                            else -> {
                                val items = message.items.ifEmpty { listOf(TurnItem.Text(message.id, message.text)) }
                                val growing = items.lastOrNull { it is TurnItem.Text || it is TurnItem.Thought || it is TurnItem.ProposedPlan }?.id
                                val prose = items.mapNotNull { item ->
                                    val text = when (item) {
                                        is TurnItem.Text -> item.text
                                        is TurnItem.Thought -> item.text
                                        is TurnItem.ProposedPlan -> item.markdown
                                        else -> return@mapNotNull null
                                    }
                                    val streaming = message.streaming && item.id == growing
                                    val cacheKey = "${message.id}:${item.id}"
                                    val cached = proseCache[cacheKey]?.takeIf { it.first == text && !streaming }?.second
                                    item.id to (cached ?: flowOf(MessageText(text, streaming)).renderMarkdown(repair).first()
                                        .also { proseCache[cacheKey] = text to it })
                                }.toMap()
                                ChatEntry.Assistant(
                                    message.id,
                                    TurnLayout.build(message.id, items, finished = !message.streaming, showThoughts),
                                    prose,
                                    TurnIdentity(message.model?.let { "$agentName · $it" } ?: agentName, time = turnTime(message.timestamp)),
                                    worked(message, workedFormat),
                                    working = message.streaming,
                                    files = message.files,
                                )
                            }
                        }.also { rendered[message.id] = message to it }
                    }
                    rendered.keys.retainAll(snapshot.messages.map { it.id }.toSet())
                    proseCache.keys.retainAll { key -> snapshot.messages.any { key.startsWith(it.id + ":") } }
                    next
                }
                if (snapshot.messages.any { it.streaming }) delay(33)
            }
        } finally { repair.close() }
    }
    // A sent turn leaves the optimistic list once the transcript carries its id.
    val authorsByMessage = remember(messages) { messages.associate { it.id to it.userId } }
    val pending = outgoing.filter { it.turnId !in authorsByMessage }
    val all = entries.map { entry ->
        if (entry is ChatEntry.User) {
            val userId = authorsByMessage[entry.id]
            entry.copy(who = senderIdentity(userId, context.me, members[userId], memberLabel, entry.who.time))
        } else entry
    } + pending.map {
        ChatEntry.User(it.turnId, it.text, TurnIdentity(meName, context.me?.image, turnTime(it.at)), it.delivery)
    }
    val session = context.session
    val canSend = status == "live" && session?.cliType != null && session.agentType != null
    val failedText = stringResource(R.string.chat_send_failed)
    val attachText = stringResource(R.string.chat_attach_unavailable)
    val snackbar = remember { SnackbarHostState() }
    var creatingTab by remember(id, scope) { mutableStateOf(false) }
    var attemptedTab by rememberSaveable(id, scope.workspaceId) { mutableStateOf<String?>(null) }
    val newTabTitle = stringResource(R.string.chat_new_tab)
    val tabFailure = stringResource(R.string.chat_tab_creation_failed)
    val tabUnknown = stringResource(R.string.chat_tab_creation_unknown)
    val createTab = {
        if (!creatingTab) {
            creatingTab = true
            coroutines.launch {
                if (attemptedTab != null) {
                    onSelectTab(attemptedTab!!)
                } else {
                    val target = UUID.randomUUID().toString()
                    attemptedTab = target
                    try {
                        val result = repository.createTab(scope, id, target, newTabTitle)
                        when (result.state) {
                            "created" -> { attemptedTab = null; onSelectTab(target) }
                            "rejected" -> { attemptedTab = null; snackbar.showSnackbar(tabFailure) }
                            else -> snackbar.showSnackbar(tabUnknown)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { snackbar.showSnackbar(tabUnknown) }
                }
                creatingTab = false
            }
        }
    }
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
    // Merge / Ready for review go straight to GitHub, one at a time, never retried.
    var merging by remember(id) { mutableStateOf(false) }
    var writing by remember(id) { mutableStateOf(false) }
    val prefs = remember { appContext.getSharedPreferences("lody.chat", android.content.Context.MODE_PRIVATE) }
    val writeTexts = WriteTexts(
        stringResource(R.string.bar_merge_pending), stringResource(R.string.bar_merge_done), stringResource(R.string.bar_merge_error),
        stringResource(R.string.bar_ready_pending), stringResource(R.string.bar_ready_done), stringResource(R.string.bar_ready_error),
        WriteErrors.associateWith { code -> stringResource(writeErrorText(code)) },
    )
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val writePullRequest: (String?) -> Unit = write@{ method ->
        val pr = bar?.pullRequest ?: return@write
        if (writing) return@write
        writing = true
        coroutines.launch {
            snackbar.showSnackbar(if (method != null) writeTexts.mergePending else writeTexts.readyPending)
        }
        coroutines.launch {
            val message = try {
                if (method != null) repository.mergePullRequest(scope, pr.repository, pr.number, method)
                else repository.markReady(scope, pr.repository, pr.number)
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                if (method != null) writeTexts.mergeDone else writeTexts.readyDone
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
                (error as? GitHubWriteFailure)?.code?.let { writeTexts.errors[it] }
                    ?: if (method != null) writeTexts.mergeError else writeTexts.readyError
            } finally { writing = false }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    if (merging && bar?.pullRequest != null) {
        MergeSheet(
            "${bar.pullRequest.repository} #${bar.pullRequest.number}",
            preferred = prefs.getString("prMergeMethod", "merge") ?: "merge",
            onDismiss = { merging = false },
            onMerge = { method ->
                merging = false
                prefs.edit().putString("prMergeMethod", method).apply()
                writePullRequest(method)
            },
        )
    }
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
                    ChatTranscript(
                        all, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = LodySpacing.md), state = listState,
                        loadDetail = { entryId, itemId -> repository.detail(scope, id, entryId, itemId) },
                    )
                }
                ChatHeader(context.title, context.tabs, id, creatingTab, onBack, onSelectTab, createTab)
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
                        // Only while still offered, so a menu left open cannot act for a state that moved on.
                        when {
                            action !in bar.actions -> Unit
                            action == SessionInfoBar.Action.Merge -> merging = true
                            action == SessionInfoBar.Action.ReadyForReview -> writePullRequest(null)
                            else -> sendText(bar.prompt(action, prompts))
                        }
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
private fun BoxScope.ChatHeader(title: String, tabs: List<CatalogSession>, currentId: String, creating: Boolean, onBack: () -> Unit, onSelectTab: (String) -> Unit, onNewTab: () -> Unit) {
    var expanded by remember(currentId) { mutableStateOf(false) }
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
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().heightIn(min = LodySize.touchTarget).clickable(role = Role.Button) { expanded = true }) {
                Text(title, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tabs.size > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs)) {
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.chat_directory_tabs, tabs.size, tabs.size), modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = LodyTheme.typography.footnote, color = colors.textSecondary)
                    DirectoryTabs.attention(tabs)?.let { app.lody.android.home.AttentionGlyph(it) }
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (tabs.size > 1) tabs.forEach { tab ->
                    DropdownMenuItem(text = { Text(tab.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { DirectoryTabs.attention(listOf(tab))?.let { app.lody.android.home.AttentionGlyph(it) } },
                        trailingIcon = { if (tab.id == currentId) Icon(LodyIcons.Check, contentDescription = stringResource(R.string.chat_current_tab)) },
                        onClick = { expanded = false; if (tab.id != currentId) onSelectTab(tab.id) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_new_tab)) }, enabled = !creating,
                    leadingIcon = { Icon(LodyIcons.Plus, contentDescription = null) }, onClick = { expanded = false; onNewTab() })
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

internal fun delivery(status: String) = when (status) {
    // Execution failure/cancellation still acknowledges receipt, as on desktop.
    // Only a send failure means the turn was not delivered.
    "seen", "processing", "handled", "failed", "canceled", "completed", "done" -> Delivery.Seen
    "queued" -> Delivery.Queued
    "not_sent" -> Delivery.Failed
    else -> Delivery.Sent
}

/** Resolve once at display time so member refreshes do not rebuild Markdown. */
internal fun senderIdentity(userId: String?, me: Account?, member: app.lody.android.auth.WorkspaceMember?, fallback: String, time: String): TurnIdentity {
    if (me != null && (userId == null || userId == me.id)) return TurnIdentity(me.name, me.image, time)
    return TurnIdentity(member?.name?.ifBlank { fallback } ?: fallback, member?.image, time)
}

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
            // The one flexible slot: the model takes the middle and Send stays on the trailing edge.
            // (Two weighted children split the free space, which left Send short of the edge.)
            Row(
                Modifier.weight(1f).padding(horizontal = LodySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
            ) {
                if (model != null) {
                    Icon(LodyIcons.Agent, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
                    Text(model, style = type.subhead, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
 * Web `SessionInfoBar` above the composer as grouped capsules, so the composer stays the only
 * full-width shape: the computer (menu names it), the context (PR or branch with ±diff; menu
 * opens the PR or copies the branch) and, pinned trailing, the repository action as a neutral
 * split capsule. Colour is for status only, so Send stays the one accent. It never scrolls: the
 * first stage that fits on one line wins — labels capped, then the diff as a dot — and only at
 * very large text does the action move to its own row. iOS: `ChatActionBarView`.
 */
@Composable
private fun ChatActionBar(
    bar: SessionInfoBar,
    machine: String,
    machineKind: app.lody.android.create.CreateSessionModel.MachineKind,
    onAction: (SessionInfoBar.Action) -> Unit,
    onCopied: (String) -> Unit,
) {
    val context = bar.pullRequest != null || bar.branch.isNotEmpty() || bar.repository.isNotEmpty()
    val diff = bar.added + bar.removed > 0
    if (machine.isEmpty() && !context && !diff && bar.actions.isEmpty()) return
    @Composable fun Leading(diffDot: Boolean, modifier: Modifier = Modifier) {
        if (machine.isNotEmpty()) MachineItem(machine, machineKind)
        if (context || diff) ContextItem(bar, diffDot, onCopied, modifier)
    }
    @Composable fun Action() { if (bar.actions.isNotEmpty()) ActionSplit(bar.actions, onAction) }
    @Composable fun Line(fill: Boolean, diffDot: Boolean) {
        Row(
            if (fill) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
        ) {
            Leading(diffDot)
            if (fill) Spacer(Modifier.weight(1f))
            Action()
        }
    }
    FirstThatFits(
        Modifier.padding(horizontal = LodySpacing.md).padding(top = LodySpacing.xs).fillMaxWidth(),
        stages = listOf({ fill -> Line(fill, diffDot = false) }, { fill -> Line(fill, diffDot = true) }),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                Leading(diffDot = true, Modifier.weight(1f, fill = false))
            }
            Action()
        }
    }
}

/**
 * Shows the first stage whose natural one-line width fits (each is probed unfilled, then shown
 * filled to the available width), else [fallback].
 */
@Composable
private fun FirstThatFits(modifier: Modifier, stages: List<@Composable (fill: Boolean) -> Unit>, fallback: @Composable () -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val index = stages.indices.firstOrNull { i ->
            val width = subcompose("probe$i") { stages[i](false) }.maxOfOrNull { it.measure(loose).width } ?: 0
            width <= constraints.maxWidth
        }
        val placeables = subcompose("show${index ?: "fallback"}") {
            if (index != null) stages[index](true) else fallback()
        }.map { it.measure(constraints.copy(minWidth = 0)) }
        layout(constraints.maxWidth, placeables.maxOfOrNull { it.height } ?: 0) { placeables.forEach { it.place(0, 0) } }
    }
}

private val CapsuleHeight = 36.dp

/** One raised capsule of the bar. */
private fun Modifier.barCapsule(colors: LodyColors): Modifier = this
    .height(CapsuleHeight)
    .clip(CircleShape)
    .background(colors.bgRaised)
    .border(0.5.dp, colors.borderSubtle, CircleShape)

/** A tappable cell inside a capsule; the capsule draws the surface. */
@Composable
private fun BarCell(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .height(CapsuleHeight)
            .widthIn(min = CapsuleHeight)
            .clip(RoundedCornerShape(percent = 50))
            .combinedClickable(role = Role.Button, onClickLabel = label, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label }
            .padding(horizontal = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs, Alignment.CenterHorizontally),
        content = content,
    )
}

@Composable
private fun MachineItem(name: String, kind: app.lody.android.create.CreateSessionModel.MachineKind) {
    val colors = LodyTheme.colors
    var open by remember { mutableStateOf(false) }
    val icon = when (kind) {
        app.lody.android.create.CreateSessionModel.MachineKind.Laptop -> LodyIcons.Machine
        app.lody.android.create.CreateSessionModel.MachineKind.Desktop -> LodyIcons.Desktop
        app.lody.android.create.CreateSessionModel.MachineKind.Server -> LodyIcons.Server
    }
    Box(Modifier.barCapsule(colors)) {
        BarCell("${stringResource(R.string.bar_machine)}, $name", onClick = { open = true }) {
            Icon(icon, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
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

/**
 * PR number, else branch, else the repository name without its owner, with the ±diff in the same
 * capsule (a dot when space is short; the counts then lead the menu). CI trouble tints the glyph.
 */
@Composable
private fun ContextItem(bar: SessionInfoBar, diffDot: Boolean, onCopied: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val pr = bar.pullRequest
    val ci = pr?.ci?.takeIf { pr.status == "open" || pr.status == "draft" }
    val (icon, statusTint) = when (pr?.status) {
        null -> LodyIcons.Branch to colors.iconSecondary
        "merged" -> LodyIcons.Merge to colors.prMerged
        "closed" -> LodyIcons.PullRequestClosed to colors.prClosed
        "draft" -> LodyIcons.PullRequestDraft to colors.prDraft
        else -> LodyIcons.PullRequest to colors.prOpen
    }
    val tint = when (ci) {
        "f", "e" -> colors.dangerDefault
        "p" -> colors.warningDefault
        else -> statusTint
    }
    val verdict = when (ci) {
        "s" -> stringResource(R.string.bar_ci_s)
        "f" -> stringResource(R.string.bar_ci_f)
        "e" -> stringResource(R.string.bar_ci_e)
        "p" -> stringResource(R.string.bar_ci_p)
        else -> null
    }
    val label = pr?.let { "#${it.number}" } ?: bar.branch.ifEmpty { bar.repository.substringAfterLast('/') }
    val copied = stringResource(R.string.bar_branch_copied)
    val diff = bar.added + bar.removed > 0
    val counts = "+${bar.added} −${bar.removed}"
    val showsContext = pr != null || bar.branch.isNotEmpty() || bar.repository.isNotEmpty()
    val description = listOfNotNull(bar.repository, bar.branch, label, verdict, counts.takeIf { diff })
        .filter { it.isNotEmpty() }.distinct().joinToString(", ")
    var panel by remember { mutableStateOf(false) }
    if (panel && pr != null) PullRequestSheet(bar, pr, icon, tint, verdict, onCopied) { panel = false }
    Box(modifier.barCapsule(colors)) {
        // A PR capsule opens the PR panel on tap and keeps its menu on a long press.
        BarCell(
            description,
            onClick = { if (pr != null) panel = true else open = true },
            Modifier.padding(horizontal = LodySpacing.xs),
            onLongClick = if (pr != null) ({ open = true }) else null,
        ) {
            if (showsContext) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(LodyIconSize.sm))
                Text(label, style = type.subhead, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                    // Not weighted: the fitting probe measures unbounded, where a weight would read as zero.
                    modifier = Modifier.widthIn(max = 160.dp))
            }
            if (diff && diffDot) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.diffAddedText))
            } else if (diff) {
                val style = type.subhead.copy(fontFeatureSettings = "tnum")
                Text("+${bar.added}", style = style, color = colors.diffAddedText, maxLines = 1)
                Text("−${bar.removed}", style = style, color = colors.diffRemovedText, maxLines = 1)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            if (diff) {
                // Display only until Android has a workspace changes page.
                DropdownMenuItem(
                    text = { Text(counts, style = type.body.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary) },
                    leadingIcon = { Icon(LodyIcons.Branch, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md)) },
                    onClick = { open = false },
                    enabled = false,
                )
            }
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
                            Text(listOfNotNull(state, verdict, if (pr.merge == "d") stringResource(R.string.bar_conflicts) else null).joinToString(" · "), style = type.footnote, color = colors.textSecondary)
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
 * Web's primary action + chevron as one neutral capsule with a soft internal divider: the top
 * action runs on tap (with a confirm haptic); the chevron's menu holds the rest in priority order.
 * Its label never truncates.
 */
@Composable
private fun ActionSplit(actions: List<SessionInfoBar.Action>, onAction: (SessionInfoBar.Action) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val first = actions.first()
    val more = stringResource(R.string.bar_more)
    Box {
        Row(
            Modifier.barCapsule(colors),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onAction(first)
                    }
                    .padding(start = LodySpacing.md, end = if (actions.size > 1) LodySpacing.sm else LodySpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionTitle(first), style = type.subhead, color = colors.textPrimary, maxLines = 1)
            }
            if (actions.size > 1) {
                Box(Modifier.width(0.5.dp).height(LodyIconSize.sm).background(colors.borderStrong))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = more) { open = true }
                        .semantics { contentDescription = more }
                        .padding(horizontal = LodySpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LodyIcons.Expand, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
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
    SessionInfoBar.Action.ReadyForReview -> R.string.bar_action_ready_for_review
    SessionInfoBar.Action.Merge -> R.string.bar_action_merge
    },
)

private fun actionIcon(action: SessionInfoBar.Action): ImageVector = when (action) {
    SessionInfoBar.Action.CreatePr -> LodyIcons.PullRequest
    SessionInfoBar.Action.CreateDraftPr -> LodyIcons.PullRequestDraft
    SessionInfoBar.Action.CommitAndPush -> LodyIcons.Send
    SessionInfoBar.Action.ResolveConflicts -> LodyIcons.Merge
    SessionInfoBar.Action.FixCi -> LodyIcons.Check
    SessionInfoBar.Action.ReadyForReview -> LodyIcons.Eye
    SessionInfoBar.Action.Merge -> LodyIcons.Merge
}

private class WriteTexts(
    val mergePending: String, val mergeDone: String, val mergeError: String,
    val readyPending: String, val readyDone: String, val readyError: String,
    val errors: Map<String, String>,
)

private val WriteErrors = listOf("not_mergeable", "head_changed", "authorization_required", "merge_unknown", "ready_unknown", "not_open", "forbidden")

private fun writeErrorText(code: String): Int = when (code) {
    "not_mergeable" -> R.string.bar_error_not_mergeable
    "head_changed" -> R.string.bar_error_head_changed
    "authorization_required" -> R.string.bar_error_authorization_required
    "merge_unknown" -> R.string.bar_error_merge_unknown
    "ready_unknown" -> R.string.bar_error_ready_unknown
    "not_open" -> R.string.bar_error_not_open
    else -> R.string.bar_error_forbidden
}

/**
 * Merging cannot be undone, so choosing the method is also the confirmation: a native sheet
 * with web's three methods and their descriptions, the last one used first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeSheet(title: String, preferred: String, onDismiss: () -> Unit, onMerge: (String) -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val methods = listOf(
        Triple("merge", R.string.bar_merge_merge, R.string.bar_merge_merge_desc),
        Triple("squash", R.string.bar_merge_squash, R.string.bar_merge_squash_desc),
        Triple("rebase", R.string.bar_merge_rebase, R.string.bar_merge_rebase_desc),
    ).sortedByDescending { it.first == preferred }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgBase) {
        Column(Modifier.padding(bottom = LodySpacing.lg)) {
            Text(title, style = type.headline, color = colors.textPrimary, modifier = Modifier.padding(horizontal = LodySpacing.lg))
            Text(stringResource(R.string.bar_merge_message), style = type.footnote, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = LodySpacing.lg, vertical = LodySpacing.xs))
            for ((method, label, description) in methods) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = LodySize.listRow)
                        .clickable(role = Role.Button) { onMerge(method) }
                        .padding(horizontal = LodySpacing.lg, vertical = LodySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
                ) {
                    Icon(LodyIcons.Merge, contentDescription = null, tint = if (method == preferred) colors.prMerged else colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), style = type.body, color = colors.textPrimary)
                        Text(stringResource(description), style = type.footnote, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * The PR panel the context capsule opens: a native bottom sheet with what the catalog knows (state,
 * CI, conflicts, branch, ±diff), opening the PR on GitHub and copying the branch. iOS opens its
 * native PR page as a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRequestSheet(
    bar: SessionInfoBar,
    pr: CatalogPullRequest,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    verdict: String?,
    onCopied: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val copied = stringResource(R.string.bar_branch_copied)
    val state = stringResource(
        when (pr.status) {
            "merged" -> R.string.home_pr_merged
            "closed" -> R.string.home_pr_closed
            "draft" -> R.string.home_pr_draft
            else -> R.string.home_pr_open
        },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgBase) {
        Column(Modifier.padding(horizontal = LodySpacing.lg).padding(bottom = LodySpacing.xl), verticalArrangement = Arrangement.spacedBy(LodySpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(LodyIconSize.lg))
                Column(Modifier.weight(1f)) {
                    Text("${pr.repository} #${pr.number}", style = type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(state, verdict, if (pr.merge == "d") stringResource(R.string.bar_conflicts) else null).joinToString(" · "),
                        style = type.footnote, color = colors.textSecondary)
                }
            }
            if (bar.branch.isNotEmpty() || bar.added + bar.removed > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                    Icon(LodyIcons.Branch, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(LodyIconSize.sm))
                    Text(bar.branch, style = type.subhead, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
                    if (bar.added + bar.removed > 0) {
                        val style = type.subhead.copy(fontFeatureSettings = "tnum")
                        Text("+${bar.added}", style = style, color = colors.diffAddedText)
                        Text("−${bar.removed}", style = style, color = colors.diffRemovedText)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                Button(
                    onClick = { onDismiss(); uri.openUri(bar.pullRequestUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.fillSecondary, contentColor = colors.textPrimary),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(LodyIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(LodyIconSize.sm))
                    Spacer(Modifier.width(LodySpacing.xs))
                    Text(stringResource(R.string.bar_open_github), style = type.subhead)
                }
                if (bar.branch.isNotEmpty()) {
                    Button(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(bar.branch))
                            onCopied(copied)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.fillSecondary, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(LodyIcons.Copy, contentDescription = null, modifier = Modifier.size(LodyIconSize.sm))
                        Spacer(Modifier.width(LodySpacing.xs))
                        Text(stringResource(R.string.bar_copy_branch), style = type.subhead)
                    }
                }
            }
        }
    }
}
