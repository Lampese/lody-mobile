// A conversation, after web's mobile session page (checked by driving its Storybook stories):
// a header with the title and project / branch; user turns as right-aligned bubbles with the
// sender's avatar, name, time and delivery mark; agent turns with the agent's mark, name and
// time, a full-width answer and "Worked for …"; a floating composer at the bottom. The
// transcript runs closer to the edges than other pages. iOS counterpart: SessionController.

package app.lody.android.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val snackbar = remember { SnackbarHostState() }
    val send = send@{
        val text = prompt.trim()
        if (!canSend || text.isEmpty() || session == null) return@send
        val turnId = UUID.randomUUID().toString()
        outgoing = outgoing + Outgoing(turnId, text, Instant.now().toString(), Delivery.Sending)
        prompt = ""
        coroutines.launch {
            val running = session.status in setOf("running", "initializing", "processing", "in_progress", "queued")
            val result = try {
                repository.send(scope, SendTextRequest(id, turnId, text, session.machineId, session.cliType!!, session.agentType!!, session.resume, queue = running)).state
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

    val colors = LodyTheme.colors
    Scaffold(
        containerColor = colors.bgBase,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(context.title, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (context.subtitle.isNotEmpty()) {
                            Text(context.subtitle, style = LodyTheme.typography.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(LodyIcons.Back, stringResource(R.string.chat_back), tint = colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase, scrolledContainerColor = colors.bgBase),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().navigationBarsPadding()) {
            if (status == "offline") {
                Row(Modifier.fillMaxWidth().padding(horizontal = TranscriptMargin), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chat_offline), style = LodyTheme.typography.footnote, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_retry), color = colors.accentFg) }
                }
            }
            if (all.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (status == "live") R.string.chat_empty else if (status == "offline") R.string.chat_offline else R.string.chat_loading), style = LodyTheme.typography.body, color = colors.textSecondary)
                }
            } else ChatTranscript(all, Modifier.weight(1f), contentPadding = PaddingValues(vertical = LodySpacing.md))
            ChatComposer(prompt, onPromptChange = { if (it.length <= 32000) prompt = it }, enabled = canSend, onSend = send)
        }
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

/** The new-session page's composer, for a conversation: + and send around the prompt. */
@Composable
private fun ChatComposer(prompt: String, onPromptChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val shape = RoundedCornerShape(LodyRadius.xl)
    val placeholder = stringResource(if (enabled) R.string.chat_composer_placeholder else R.string.chat_composer_unavailable)
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = TranscriptMargin, vertical = LodySpacing.sm)
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
            enabled = enabled,
            textStyle = type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentDefault),
            minLines = if (focused) 2 else 1,
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
