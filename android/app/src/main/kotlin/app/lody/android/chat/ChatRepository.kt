package app.lody.android.chat

import app.lody.android.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** A screen subscription uses the app's existing workspace lease; it never creates a runtime. */
class ChatRepository(private val owner: RuntimeOwner) {
    /** Sends one text turn on the scope's lease. Never retried: an uncertain write is `unknown`. */
    suspend fun send(scope: RuntimeScope, request: SendTextRequest): RuntimeResult.Sent =
        owner.use(scope) { it.execute(RuntimeCommand.SendText(request)) as RuntimeResult.Sent }

    fun observe(scope: RuntimeScope, id: String): Flow<SessionSnapshot> = channelFlow {
        var latest = SessionSnapshot(id, "syncing", emptyList())
        suspend fun status(value: String) { latest = latest.copy(status = value); send(latest) }
        val events = launch(start = CoroutineStart.UNDISPATCHED) {
            owner.events.collect { emission ->
                val event = emission.event as? RuntimeEvent.SessionUpdated ?: return@collect
                if (emission.scope == scope && emission.generation == owner.state.value.status.generation && event.snapshot.id == id) {
                    latest = event.snapshot
                    send(latest)
                }
            }
        }
        try {
            owner.state.map { Triple(it.scope, it.status.generation, it.status.phase) }.distinctUntilChanged().collectLatest { (current, _, phase) ->
                if (current != scope || phase != RuntimePhase.READY) {
                    status(if (phase == RuntimePhase.STARTING) "syncing" else "offline")
                    return@collectLatest
                }
                // READY precedes the grant/connect handshake. Retry the read, never a write.
                while (currentCoroutineContext().isActive) {
                    try {
                        status("syncing")
                        owner.use(scope) { it.execute(RuntimeCommand.WatchSession(id)) }
                        break
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { status("offline"); delay(2_000) }
                }
                awaitCancellation()
            }
        } finally {
            events.cancel()
            withContext(NonCancellable) {
                try { owner.use(scope) { it.execute(RuntimeCommand.CloseSession(id)) } }
                catch (_: Exception) { /* A replaced/suspended runtime already fences this lease. */ }
            }
        }
    }.conflate()
}
