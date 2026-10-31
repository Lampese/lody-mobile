package app.lody.android.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.lody.android.R
import app.lody.android.designsystem.*
import app.lody.android.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(repository: ChatRepository, scope: RuntimeScope, id: String, title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var retry by remember(id, scope) { mutableIntStateOf(0) }
    var status by remember(id, scope) { mutableStateOf("syncing") }
    var entries by remember(id, scope) { mutableStateOf(emptyList<ChatEntry>()) }
    val context = LocalContext.current.applicationContext
    LaunchedEffect(scope, id, retry) {
        val repair = QuickJsMarkdownRepair.fromAssets(context)
        val rendered = mutableMapOf<String, Pair<SessionMessage, ChatEntry>>()
        try {
            repository.observe(scope, id).conflate().collect { snapshot ->
                status = snapshot.status
                entries = withContext(Dispatchers.Default) {
                    val next = snapshot.messages.map { message ->
                        rendered[message.id]?.takeIf { it.first == message }?.second ?: when (message.role) {
                            "user" -> ChatEntry.User(message.id, message.text)
                            else -> ChatEntry.Assistant(message.id, flowOf(MessageText(message.text, message.streaming)).renderMarkdown(repair).first())
                        }.also { rendered[message.id] = message to it }
                    }
                    rendered.keys.retainAll(snapshot.messages.map { it.id }.toSet())
                    next
                }
                if (snapshot.messages.any { it.streaming }) delay(33)
            }
        } finally { repair.close() }
    }
    val colors = LodyTheme.colors
    Scaffold(
        containerColor = colors.bgBase,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = LodyTheme.typography.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(LodyIcons.Back, stringResource(R.string.chat_back), tint = colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = LodyLayout.screenPaddingX)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when (status) {
                    "live" -> R.string.chat_read_only
                    "offline" -> R.string.chat_offline
                    else -> R.string.chat_loading
                }), style = LodyTheme.typography.footnote, color = colors.textSecondary, modifier = Modifier.weight(1f))
                if (status == "offline") TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_retry), color = colors.accentFg) }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (status == "live") R.string.chat_empty else if (status == "offline") R.string.chat_offline else R.string.chat_loading), style = LodyTheme.typography.body, color = colors.textSecondary)
                }
            } else ChatTranscript(entries, Modifier.weight(1f), contentPadding = PaddingValues(vertical = LodySpacing.md))
        }
    }
}
