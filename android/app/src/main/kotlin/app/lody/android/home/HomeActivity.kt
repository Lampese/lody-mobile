package app.lody.android.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.lody.android.chat.ChatRepository
import app.lody.android.chat.ChatScreen
import app.lody.android.create.CreateSessionScreen
import androidx.activity.compose.BackHandler
import app.lody.android.runtime.RuntimeScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import app.lody.android.LodyApplication
import app.lody.android.R
import app.lody.android.auth.AuthState
import app.lody.android.catalog.CatalogState
import app.lody.android.catalog.ConnectionPhase
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodyRadius
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyTheme

/** Launcher: sign-in when there is no account, otherwise the Home chat list. Reads app-owned state only. */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as LodyApplication
        val auth = app.account.state.value
        if (savedInstanceState == null && auth.account == null && !auth.busy) app.account.restore()
        setContent {
            LodyTheme {
                val account by app.account.state.collectAsState()
                val catalog by app.cloud.state.collectAsState()
                val user = account.account
                var selectedId by rememberSaveable(user?.id, catalog.workspace?.id) { mutableStateOf<String?>(null) }
                var selectedTitle by rememberSaveable(user?.id, catalog.workspace?.id) { mutableStateOf("") }
                var creating by rememberSaveable(user?.id, catalog.workspace?.id) { mutableStateOf(false) }
                val repository = remember { ChatRepository(app.runtimeOwner, app.pullRequests, app.attachments) }
                val creator = app.creation
                val screens = rememberSaveableStateHolder()
                // The repository restores the cached workspace; a first sign-in picks the first one.
                LaunchedEffect(user, catalog.workspace) {
                    val first = user?.workspaces?.firstOrNull()
                    if (first != null && catalog.workspace == null && !account.busy) app.cloud.select(first)
                }
                if (user == null) {
                    SignInScreen(
                        state = account,
                        onSignIn = { app.account.login() },
                        onOpen = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        onCancel = { app.account.cancel() },
                    )
                } else {
                    val workspace = catalog.workspace
                    val id = selectedId
                    if (id != null && workspace != null) {
                        ChatScreen(repository, RuntimeScope(user.id, workspace.id), id, chatContext(catalog, id, selectedTitle, user), onBack = { selectedId = null }, onSelectTab = { selectedId = it; selectedTitle = "" })
                    } else if (creating && workspace != null) {
                        // Like web, new session is its own full screen over Home; back returns.
                        BackHandler { creating = false }
                        CreateSessionScreen(catalog.snapshot, creator, RuntimeScope(user.id, workspace.id), onCreated = { sessionId, title -> selectedId = sessionId; selectedTitle = title; creating = false }, onBack = { creating = false })
                    } else screens.SaveableStateProvider("list/${user.id}/${workspace?.id}") {
                        ChatListScreen(
                            state = uiState(account, catalog),
                            onSelectWorkspace = { app.cloud.select(it) },
                            onSignOut = { app.account.logout() },
                            onOpenChat = { selectedTitle = it.title; selectedId = it.id },
                            onNewChat = { creating = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun uiState(auth: AuthState, catalog: CatalogState): ChatListUiState {
    val user = auth.account
    val workspace = catalog.workspace
    val snapshot = catalog.snapshot
    // Only actionable connection state reaches the sync pill; a live list shows none.
    val sync = when (catalog.phase) {
        ConnectionPhase.CONNECTING, ConnectionPhase.SYNCING -> HomeSyncState.Syncing
        ConnectionPhase.UNAVAILABLE, ConnectionPhase.PAUSED -> HomeSyncState.Offline
        else -> HomeSyncState.Live
    }
    val placeholder = when {
        snapshot == null && catalog.phase in setOf(ConnectionPhase.UNAVAILABLE, ConnectionPhase.PAUSED) -> stringResource(R.string.home_offline)
        snapshot == null && user?.workspaces?.isEmpty() == true -> stringResource(R.string.home_no_workspaces)
        snapshot == null -> stringResource(R.string.home_loading)
        else -> stringResource(R.string.home_empty)
    }
    val name = workspace?.name ?: stringResource(R.string.app_name)
    return ChatListUiState(
        workspaceName = name,
        initial = name.take(1).uppercase(),
        // The selected workspace's logo comes from the account's list (the catalog may hold an older copy).
        workspaceImage = user?.workspaces?.firstOrNull { it.id == workspace?.id }?.image ?: workspace?.image,
        accountImage = user?.image,
        sync = sync,
        accountName = user?.name.orEmpty(),
        timeline = snapshot?.let { ChatListModel.timeline(it) }.orEmpty(),
        snapshot = snapshot,
        userId = user?.id,
        projects = snapshot?.let(ChatListModel::groups).orEmpty(),
        placeholder = placeholder,
        workspaces = user?.workspaces.orEmpty(),
        workspaceId = workspace?.id,
    )
}

@Composable
private fun SignInScreen(state: AuthState, onSignIn: () -> Unit, onOpen: (String) -> Unit, onCancel: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .safeDrawingPadding()
            .padding(horizontal = LodyLayout.screenPaddingX * 2),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(LodySpacing.md)) {
            Text(stringResource(R.string.sign_in_title), style = type.title, color = colors.textPrimary, textAlign = TextAlign.Center)
            val verification = state.verification
            if (verification == null) {
                Text(stringResource(R.string.sign_in_body), style = type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                PrimaryButton(
                    label = stringResource(if (state.busy) R.string.sign_in_connecting else R.string.sign_in_action),
                    enabled = !state.busy,
                    onClick = onSignIn,
                )
            } else {
                Text(stringResource(R.string.sign_in_code), style = type.subhead, color = colors.textTertiary)
                Text(verification.userCode, style = type.title.copy(fontFamily = type.code.fontFamily), color = colors.textPrimary)
                Text(
                    stringResource(R.string.sign_in_code_hint, verification.expiresIn / 60),
                    style = type.footnote,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(stringResource(R.string.sign_in_open), enabled = true) { onOpen(verification.url) }
                Text(
                    stringResource(R.string.sign_in_cancel),
                    style = type.subhead,
                    color = colors.textLink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(LodyRadius.md))
                        .clickable(role = Role.Button, onClick = onCancel)
                        .padding(horizontal = LodySpacing.lg, vertical = LodySpacing.md),
                )
            }
            state.failure?.let { Text(it, style = type.footnote, color = colors.dangerDefault, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.md)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LodySize.controlLg)
            .clip(shape)
            .background(if (enabled) colors.accentSolid else colors.fillDisabled, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = LodyTheme.typography.subhead, color = if (enabled) colors.textOnAccent else colors.textDisabled)
    }
}

/** The conversation's header and sending context from the catalog (title, project · branch). */
private fun chatContext(catalog: CatalogState, id: String, title: String, user: app.lody.android.auth.Account): app.lody.android.chat.ChatContext {
    val snapshot = catalog.snapshot
    val session = snapshot?.sessions?.firstOrNull { it.id == id }
    val project = snapshot?.projects?.firstOrNull { it.id == session?.projectId && !it.id.endsWith(":unassigned") }
    val subtitle = listOfNotNull(project?.name, session?.branchName).joinToString(" · ")
    val repository = project?.repoFullName ?: session?.projectId?.removePrefix("github:")?.takeIf { session.projectId.startsWith("github:") }
    return app.lody.android.chat.ChatContext(
        session?.title?.ifBlank { null } ?: title, subtitle, session, user,
        tabs = session?.let { app.lody.android.chat.DirectoryTabs.sessions(it, snapshot.sessions) }.orEmpty(),
        repository = repository.orEmpty(),
        machineName = session?.let { snapshot?.machineNames?.get(it.machineId) }.orEmpty(),
        machineKind = snapshot?.let { app.lody.android.create.CreateSessionModel.machineKind(it, session?.machineId) }
            ?: app.lody.android.create.CreateSessionModel.MachineKind.Laptop,
    )
}
