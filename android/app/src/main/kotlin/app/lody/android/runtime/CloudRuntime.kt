package app.lody.android.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Platform-neutral host contract. Calling coroutines may use any dispatcher. */
interface CloudRuntime {
    val scope: RuntimeScope
    val state: StateFlow<RuntimeStatus>
    val events: Flow<RuntimeEmission>
    suspend fun start()
    suspend fun setActive(active: Boolean)
    suspend fun execute(command: RuntimeCommand): RuntimeResult
    suspend fun stop()
}

data class RuntimeScope(val accountId: String, val workspaceId: String) {
    init { require(accountId.isNotBlank() && workspaceId.isNotBlank()); require(accountId.length <= 128 && workspaceId.length <= 128) }
}
sealed interface RuntimeCommand {
    data class GetCreationOptions(val projectId: String?) : RuntimeCommand
    data class CreateAndSend(val request: CreateRequest) : RuntimeCommand
    data class SendText(val request: SendTextRequest) : RuntimeCommand
    data class WatchSession(val id: String) : RuntimeCommand
    data class CloseSession(val id: String) : RuntimeCommand
    /** One step's detail from the watched session (command, output, diff). Read only. */
    data class ItemDetail(val sessionId: String, val entryId: String, val itemId: String) : RuntimeCommand
    data object Ping : RuntimeCommand
    data object InspectSchema : RuntimeCommand
}
sealed interface RuntimeResult {
    data class Options(val value: CreationOptions) : RuntimeResult
    data class Created(val value: CreationReceipt) : RuntimeResult
    /** Upstream send state (`sent` / `queued` / `not_sent` / `unknown`); a machine ACK is delivery only. */
    data class Sent(val state: String, val reason: String? = null) : RuntimeResult
    data object Accepted : RuntimeResult
    data class Detail(val value: StepDetail) : RuntimeResult
    data class Pong(val healthy: Boolean) : RuntimeResult
    data class Schema(val machineIds: Set<String>, val creationTrials: Map<String, String>) : RuntimeResult
}
enum class RuntimeErrorCategory { AUTH, NETWORK, PROTOCOL, RUNTIME, BUSINESS, CANCELLED, UNSUPPORTED }
data class RuntimeFault(val code: String, val category: RuntimeErrorCategory)
class RuntimeException(val fault: RuntimeFault) : Exception(fault.code)

data class CatalogProject(val id: String, val machineId: String, val name: String, val rootPath: String, val repoFullName: String? = null)
/** [ci] is the web CI verdict code, [merge] GitHub's merge state (`c` clean, `d` conflicts, …); [url] opens it. */
data class CatalogPullRequest(
    val repository: String, val number: Int, val status: String, val ci: String? = null,
    val url: String? = null, val merge: String? = null,
)
/** Required identity / state fields first; the rest are optional display fields (times in epoch ms). */
data class CatalogSession(
    val id: String, val machineId: String, val projectId: String, val title: String, val status: String, val archived: Boolean, val pinned: Boolean, val createdAt: String,
    val branchName: String? = null, val lastMessageAt: Long? = null, val lastReadAt: Long? = null, val awaitingUserSince: Long? = null,
    val pullRequests: List<CatalogPullRequest> = emptyList(),
    /** The owning Lody user (session meta `userId`); null from caches written before the field. */
    val userId: String? = null,
    /** Opened-by relations (Home nests a chat under the one that opened it) and Tab containment. */
    val openedBySessionId: String? = null,
    val openedByRootSessionId: String? = null,
    val parentSessionId: String? = null,
    /** The assistant the session runs, needed to send a turn. */
    val cliType: String? = null,
    val agentType: String? = null,
    val resume: String? = null,
    /** The last model's display name, for the agent's header. */
    val modelName: String? = null,
    /** Working tree vs base (`diffStats.allChange`) and git publish state, for the action bar. */
    val diffAdded: Int = 0,
    val diffRemoved: Int = 0,
    val workspaceDirty: Boolean = false,
    val workspaceUnpushed: Boolean = false,
)
/** Read projection, never a CRDT replica. Optional feature-specific fields come later. */
/** [machineNames] is optional display data (computer id to name); empty from older caches. */
data class CatalogSnapshot(
    val revision: Long,
    val projects: List<CatalogProject>,
    val sessions: List<CatalogSession>,
    val machineIds: List<String>,
    val machineNames: Map<String, String> = emptyMap(),
    /** Computer id to OS (`darwin` / `win32` / `linux`), for its icon. */
    val machineOs: Map<String, String> = emptyMap(),
)
/**
 * One transcript turn. [timestamp] is ISO-8601; [status] is upstream's (`seen` once the agent
 * picked a user turn up, `pending` / `queued` before); [userId] is a user turn's sender;
 * [startedAt] / [endedAt] (epoch ms) time an agent turn; [model] names its model.
 */
data class SessionMessage(
    val id: String,
    val role: String,
    val text: String,
    val streaming: Boolean,
    val timestamp: String? = null,
    val status: String = "",
    val userId: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val model: String? = null,
    /** The turn's items in order (text, thought, tool steps, plans, notices) for the process view. */
    val items: List<TurnItem> = emptyList(),
)

/**
 * One item of a turn, after upstream's session entry summary. Assistant turns render these as
 * prose, folded thought / tool groups and never-folded plans and notices (web `MessageContent`).
 */
sealed interface TurnItem {
    val id: String

    data class Text(override val id: String, val text: String) : TurnItem
    /** Reasoning; only builtin DSH shows it (web `showThoughts`). */
    data class Thought(override val id: String, val text: String) : TurnItem
    /**
     * A tool step. [kind] is the ACP kind (`execute`, `read`, `edit`, `search`, `fetch`, `think`,
     * `switch_mode`, …); [status] `pending` / `in_progress` / `completed` / `failed`.
     */
    data class Tool(
        override val id: String,
        val kind: String,
        val title: String,
        val status: String,
        val path: String? = null,
        val added: Int? = null,
        val removed: Int? = null,
        val awaitingPermission: Boolean = false,
        /** The runtime holds a detail (command, output, diff) to open under the step. */
        val hasDetail: Boolean = false,
    ) : TurnItem
    /** A proposed plan (Markdown); never folded. */
    data class ProposedPlan(override val id: String, val markdown: String) : TurnItem
    /** The agent's checklist (`plan`); never folded. */
    data class Checklist(override val id: String, val entries: List<Pair<String, String>>) : TurnItem
    /** A failure or notice the turn ended with; never folded. */
    data class Notice(override val id: String, val text: String, val failure: Boolean) : TurnItem
    /**
     * A background task or sub-agent run (web `subagent_task`). [state]: `pending` / `running` /
     * `completed` / `failed` / `cancelled` / `unknown`; [latest] its latest step while it runs;
     * [steps] its own run's items, for its detail sheet.
     */
    data class Task(
        override val id: String,
        val actor: String,
        val description: String,
        val state: String,
        val latest: String = "",
        val result: String = "",
        val background: Boolean = false,
        val steps: List<TurnItem> = emptyList(),
        /** Web `groupOf`, from the runtime: `subagents` (agent runs) or `tasks` (commands, MCP tasks, monitors, wakeups). */
        val group: String = "subagents",
    ) : TurnItem {
        val running: Boolean get() = state == "pending" || state == "running"
    }
}

/** A text turn in an existing conversation; [turnId] is stable so a retry can never duplicate it. */
data class SendTextRequest(
    val sessionId: String,
    val turnId: String,
    val text: String,
    val machineId: String,
    val cliType: String,
    val agentType: String,
    val resume: String? = null,
    val queue: Boolean = false,
)
data class SessionSnapshot(val id: String, val status: String, val messages: List<SessionMessage>)
sealed interface RuntimeEvent {
    data class SessionUpdated(val snapshot: SessionSnapshot) : RuntimeEvent
    data class CatalogUpdated(val snapshot: CatalogSnapshot) : RuntimeEvent
    data class Synced(val revision: Long, val streams: Int) : RuntimeEvent
    data class SyncFailed(val reason: String) : RuntimeEvent
    data object GrantRequested : RuntimeEvent
}
/** Stamped at source receipt, before any coroutine buffering. */
data class RuntimeEmission(val generation: Long, val event: RuntimeEvent)
data class ScopedRuntimeEvent(val scope: RuntimeScope, val event: RuntimeEvent, val generation: Long)
data class OwnedRuntimeState(val scope: RuntimeScope? = null, val status: RuntimeStatus = RuntimeStatus())

/** Stage-2 port. Phase 1 has no credential provider implementation or cloud capability. */
fun interface GrantProvider { suspend fun request(scope: RuntimeScope): ShortLivedGrant }
class ShortLivedGrant(val token: String, val gatewayBaseUrl: String, val expiresIn: Int, private val receivedAtNanos: Long = System.nanoTime()) {
    init { require(token.isNotBlank()); require(expiresIn in 1..3600) }
    fun remainingSeconds(nowNanos: Long = System.nanoTime()): Int =
        (expiresIn - ((nowNanos - receivedAtNanos).coerceAtLeast(0) / 1_000_000_000L)).coerceAtLeast(0).toInt()
    override fun toString() = "ShortLivedGrant(token=<redacted>, expiresIn=$expiresIn)"
}

/**
 * A tool step's detail, after upstream `itemDetail`: its content blocks in order. [truncated] when
 * the runtime cut a long detail; the rest stays on desktop.
 */
data class StepDetail(val blocks: List<StepBlock>, val truncated: Boolean = false)

sealed interface StepBlock {
    data class Command(val command: String, val cwd: String? = null) : StepBlock
    data class Output(val text: String, val exitCode: Int? = null) : StepBlock
    data class Diff(val path: String, val oldText: String, val newText: String) : StepBlock
    data class Text(val text: String) : StepBlock
}
