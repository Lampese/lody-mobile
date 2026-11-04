package app.lody.android.runtime

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Coroutine facade. All WebView access and cancellation cleanup uses the main executor. */
open class WebViewRuntime internal constructor(
    override val scope: RuntimeScope,
    internal val backend: WebViewCloudRuntime,
) : CloudRuntime {
    constructor(context: Context, scope: RuntimeScope) : this(scope, WebViewCloudRuntime(context))
    private val mutableState = MutableStateFlow(backend.status)
    override val state: StateFlow<RuntimeStatus> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<RuntimeEmission>(extraBufferCapacity = 64)
    override val events: Flow<RuntimeEmission> = mutableEvents.asSharedFlow()
    private var stateSubscription: AutoCloseable? = null
    private var eventSubscription: AutoCloseable? = null
    override suspend fun start() = withContext(Dispatchers.Main.immediate) {
        if (stateSubscription == null) stateSubscription = backend.observe { mutableState.value = it }
        if (eventSubscription == null) eventSubscription = backend.observeEvents { check(mutableEvents.tryEmit(it)) { "event_buffer_full" } }
        backend.start()
    }
    override suspend fun setActive(active: Boolean) = withContext(Dispatchers.Main.immediate) { backend.setActive(active) }
    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        backend.stop()
        stateSubscription?.close(); stateSubscription = null
        eventSubscription?.close(); eventSubscription = null
        mutableState.value = backend.status
    }
    override suspend fun execute(command: RuntimeCommand): RuntimeResult = when (command) {
        is RuntimeCommand.CreateTab -> {
            val value = call("createTab", JSONArray().put(command.parentId).put(command.id).put(command.title), CommandDeadline.WRITE) as JSONObject
            RuntimeResult.Created(CreationReceipt(command.id, value.getString("state")))
        }
        is RuntimeCommand.GetCreationOptions -> RuntimeResult.Options(CreationJson.options(call("creationOptions", JSONArray().put(command.projectId ?: JSONObject.NULL).put(command.agentConfigId ?: JSONObject.NULL), CommandDeadline.QUERY) as JSONObject))
        is RuntimeCommand.CreateAndSend -> {
            val value = call("createAndSend", JSONArray().put(CreationJson.request(command.request)), CommandDeadline.WRITE) as JSONObject
            RuntimeResult.Created(CreationReceipt(value.getString("sessionId"), value.getString("state")))
        }
        is RuntimeCommand.SendText -> {
            val request = command.request
            val payload = JSONObject()
                .put("sessionId", request.sessionId).put("turnId", request.turnId).put("text", request.text)
                .put("machineId", request.machineId).put("cliType", request.cliType).put("agentType", request.agentType)
                .put("queue", request.queue)
            request.resume?.let { payload.put("resume", it) }
            request.modelId?.let { payload.put("modelId", it) }
            request.modeId?.let { payload.put("modeId", it) }
            request.effort?.let { payload.put("effort", it) }
            val value = call("sendText", JSONArray().put(payload), CommandDeadline.WRITE) as JSONObject
            RuntimeResult.Sent(value.optString("state", "unknown"), value.optString("reason").ifBlank { null })
        }
        is RuntimeCommand.WatchSession -> {
            call("watchSession", JSONArray().put(command.id), CommandDeadline.QUERY)
            RuntimeResult.Accepted
        }
        is RuntimeCommand.CloseSession -> {
            call("closeSession", JSONArray().put(command.id))
            RuntimeResult.Accepted
        }
        is RuntimeCommand.ItemDetail -> {
            val value = call("itemDetail", JSONArray().put(command.sessionId).put(command.entryId).put(command.itemId), CommandDeadline.QUERY)
            RuntimeResult.Detail(RuntimeProtocol.detail(value as JSONObject))
        }
        is RuntimeCommand.TurnDiff -> {
            val value = call("turnDiff", JSONArray().put(command.sessionId).put(command.entryId).put(command.path), CommandDeadline.QUERY)
            RuntimeResult.Diff(RuntimeProtocol.diff(value as JSONObject))
        }
        is RuntimeCommand.StopTurn -> {
            call("controlTurn", JSONArray().put(command.sessionId).put(command.machineId).put(command.turnId), CommandDeadline.WRITE)
            RuntimeResult.Accepted
        }
        RuntimeCommand.Ping -> RuntimeResult.Pong(call("ping") as? Boolean ?: throw RuntimeException(runtimeFault("invalid_ping_reply")))
        RuntimeCommand.InspectSchema -> {
            val value = call("probeSchema", deadline = CommandDeadline.QUERY)
            try { RuntimeProtocol.schema(value as JSONObject) }
            catch (_: Exception) { throw RuntimeException(runtimeFault("invalid_schema_reply")) }
        }
    }
    internal suspend fun call(method: String, args: JSONArray = JSONArray(), deadline: CommandDeadline = CommandDeadline.CONTROL): Any? = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val handle = backend.request(method, args, deadline) { result ->
                result.fold(
                    { continuation.resume(it) },
                    { continuation.resumeWithException(RuntimeException(runtimeFault((it as? RuntimeFailure)?.code ?: "runtime_failed"))) },
                )
            }
            continuation.invokeOnCancellation {
                // Cleanup must outlive the cancelled caller, but never own application state.
                android.os.Handler(android.os.Looper.getMainLooper()).post { handle.close() }
            }
        }
    }
}
