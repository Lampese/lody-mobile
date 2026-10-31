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
    data class WatchSession(val id: String) : RuntimeCommand
    data class CloseSession(val id: String) : RuntimeCommand
    data object Ping : RuntimeCommand
    data object InspectSchema : RuntimeCommand
}
sealed interface RuntimeResult {
    data object Accepted : RuntimeResult
    data class Pong(val healthy: Boolean) : RuntimeResult
    data class Schema(val machineIds: Set<String>, val creationTrials: Map<String, String>) : RuntimeResult
}
enum class RuntimeErrorCategory { AUTH, NETWORK, PROTOCOL, RUNTIME, BUSINESS, CANCELLED, UNSUPPORTED }
data class RuntimeFault(val code: String, val category: RuntimeErrorCategory)
class RuntimeException(val fault: RuntimeFault) : Exception(fault.code)

data class CatalogProject(val id: String, val machineId: String, val name: String, val rootPath: String)
data class CatalogSession(val id: String, val machineId: String, val projectId: String, val title: String, val status: String, val archived: Boolean, val pinned: Boolean, val createdAt: String)
/** Read projection, never a CRDT replica. Optional feature-specific fields come later. */
data class CatalogSnapshot(val revision: Long, val projects: List<CatalogProject>, val sessions: List<CatalogSession>, val machineIds: List<String>)
data class SessionMessage(val id: String, val role: String, val text: String, val streaming: Boolean)
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
