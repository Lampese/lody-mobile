package app.lody.android.runtime

import org.json.JSONArray
import org.json.JSONObject

/** Decode the shared runtime's projection, never carry org.json into feature code. */
internal object RuntimeProtocol {
    fun capabilities(message: JSONObject): Set<String> {
        require(message.get("protocol") == 2)
        val result = strings(message.getJSONArray("capabilities")).toSet()
        require(result.containsAll(setOf("ping", "setActive", "probeSchema")))
        require(result.size <= 64 && result.all { it.length <= 80 })
        return result
    }
    fun event(message: JSONObject): RuntimeEvent? = when (message.getString("type")) {
        "session" -> {
            val envelope = JSONObject(message.getString("session"))
            require(envelope.getInt("v") == 1)
            val entries = envelope.getJSONArray("entries").objects().mapNotNull { entry ->
                val role = entry.text("role")
                if (role !in setOf("user", "assistant")) return@mapNotNull null
                val rawItems = entry.getJSONArray("items").objects()
                val text = rawItems.mapNotNull { item ->
                    when (item.optString("type")) {
                        "text" -> item.optString("text")
                        "proposed_plan" -> item.optString("markdown").takeUnless { item.optString("status") == "cleared" }
                        else -> null
                    }
                }.joinToString("\n\n")
                val items = rawItems.mapNotNull(::turnItem)
                // An assistant turn may so far be only steps; a user turn needs its text.
                if (text.isBlank() && (role == "user" || items.isEmpty())) null else SessionMessage(
                    id = entry.text("id"),
                    role = role,
                    text = text,
                    streaming = role == "assistant" && !entry.optBoolean("finished", true),
                    timestamp = (entry.opt("timestamp") as? String)?.takeIf { it.isNotBlank() },
                    status = entry.optString("status"),
                    userId = (entry.opt("userId") as? String)?.takeIf { it.isNotBlank() },
                    startedAt = (entry.opt("startedAt") as? Number)?.toLong(),
                    endedAt = (entry.opt("endedAt") as? Number)?.toLong(),
                    model = entry.optJSONObject("modelInfo")?.let { info -> info.optString("name").ifBlank { info.optString("modelId") }.ifBlank { null } },
                    items = if (role == "assistant") items else emptyList(),
                )
            }
            RuntimeEvent.SessionUpdated(SessionSnapshot(message.text("sessionId"), envelope.text("status"), entries))
        }
        "catalog" -> {
            val catalog = JSONObject(message.get("catalog") as String)
            val projects = catalog.getJSONArray("projects").objects().map {
                CatalogJson.project(CatalogProject(it.text("id"), it.text("machineId"), it.text("name"), it.text("rootPath")), it)
            }
            val sessions = catalog.getJSONArray("sessions").objects().map {
                CatalogJson.session(CatalogSession(it.text("id"), it.text("machineId"), it.text("projectId"), it.text("title"), it.text("status"), it.get("archived") as Boolean, it.get("pinned") as Boolean, it.text("createdAt")), it)
            }
            RuntimeEvent.CatalogUpdated(CatalogSnapshot(message.nonnegative("revision"), projects, sessions, strings(catalog.getJSONArray("machineIds")), CatalogJson.machineNames(catalog), CatalogJson.machineOs(catalog)))
        }
        "synced" -> {
            val count = message.nonnegative("streams"); require(count <= Int.MAX_VALUE)
            RuntimeEvent.Synced(message.nonnegative("revision"), count.toInt())
        }
        "syncError" -> {
            val reason = message.optString("reason")
            RuntimeEvent.SyncFailed(if (reason in setOf("catalog_limit", "stream_closed", "stalled_cursor", "network_or_auth")) reason else "sync_failed")
        }
        "networkStatus" -> RuntimeEvent.SyncFailed(if (message.optString("status") == "authorization_required") "grant_expired" else "network_unavailable")
        "grant" -> RuntimeEvent.GrantRequested
        else -> null
    }
    fun schema(message: JSONObject): RuntimeResult.Schema {
        val trials = message.getJSONObject("creationOptionsTrial")
        return RuntimeResult.Schema(message.keys().asSequence().filter { it != "creationOptionsTrial" }.toSet(), trials.keys().asSequence().associateWith { trials.text(it) })
    }
    /** Upstream `itemDetail` blocks (`terminal_command` / `terminal_output` / `diff` / `text` / …), leniently. */
    fun detail(json: JSONObject): StepDetail {
        val blocks = json.optJSONArray("blocks")?.objects().orEmpty().mapNotNull { block ->
            val command = block.optString("command").takeIf { it.isNotBlank() }?.let { text ->
                val args = block.optJSONArray("args")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty()
                (listOf(text) + args).joinToString(" ")
            }
            when {
                block.optString("type") == "diff" -> StepBlock.Diff(block.optString("path"), block.optString("oldText"), block.optString("newText"))
                command != null -> StepBlock.Command(command, block.optString("cwd").ifBlank { null })
                block.has("output") -> StepBlock.Output(
                    block.optString("output"),
                    block.optJSONObject("exitStatus")?.let { (it.opt("exitCode") as? Number)?.toInt() },
                )
                else -> (block.optString("text").ifBlank { block.optJSONObject("content")?.optString("text").orEmpty() })
                    .takeIf { it.isNotBlank() }?.let { StepBlock.Text(it) }
            }
        }
        return StepDetail(blocks, json.optBoolean("truncated"))
    }

    /** One upstream `ItemSummary`, leniently: unknown types and malformed fields are dropped. */
    private fun turnItem(item: JSONObject): TurnItem? {
        val id = item.optString("itemId").ifEmpty { return null }
        return when (item.optString("type")) {
            "text" -> item.optString("text").takeIf { it.isNotBlank() }?.let { TurnItem.Text(id, it) }
            "thought" -> item.optString("text").takeIf { it.isNotBlank() }?.let { TurnItem.Thought(id, it) }
            "tool_call" -> TurnItem.Tool(
                id,
                kind = item.optString("kind"),
                title = item.optString("title"),
                status = item.optString("status"),
                path = item.optString("path").ifEmpty { null },
                added = (item.opt("added") as? Number)?.toInt(),
                removed = (item.opt("removed") as? Number)?.toInt(),
                awaitingPermission = item.optJSONObject("permission")?.optBoolean("pending") == true,
                hasDetail = item.optBoolean("hasDetail"),
            )
            "proposed_plan" -> item.optString("markdown").takeIf { it.isNotBlank() && item.optString("status") != "cleared" }
                ?.let { TurnItem.ProposedPlan(id, it) }
            "plan" -> item.optJSONArray("entries")?.objects()?.map { it.optString("content") to it.optString("status") }
                ?.takeIf { it.isNotEmpty() }?.let { TurnItem.Checklist(id, it) }
            "subagent_task" -> {
                if (item.optBoolean("skipTranscript")) return null
                val run = item.optJSONObject("run")
                val steps = run?.optJSONArray("items")?.objects().orEmpty().mapNotNull(::turnItem)
                // The run's own state can say cancelled or unknown; the legacy status cannot (web).
                val state = run?.optString("state")?.takeIf { it.isNotBlank() } ?: when (item.optString("status")) {
                    "in_progress" -> "running"
                    "" -> "pending"
                    else -> item.optString("status")
                }
                val latest = when (val last = steps.lastOrNull()) {
                    is TurnItem.Tool -> last.title
                    is TurnItem.Text -> last.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    is TurnItem.Thought -> ""
                    else -> ""
                }.ifBlank { item.optString("lastToolName") }
                TurnItem.Task(
                    id,
                    actor = item.optString("actor"),
                    description = item.optString("description"),
                    state = state,
                    latest = latest.trim(),
                    result = item.optString("summary").ifBlank { item.optString("error") }.trim(),
                    background = item.optBoolean("isBackgrounded"),
                    steps = steps,
                )
            }
            "system_notice" -> {
                val name = item.optString("name")
                val message = item.optJSONObject("meta")?.optString("message").orEmpty()
                // Agent warnings and unnamed placeholders are not part of the conversation (iOS too).
                if (name.isEmpty() || name == "agent_warning") null
                else TurnItem.Notice(id, message.ifBlank { name }, failure = name == "chat_failed")
            }
            else -> null
        }
    }
    private fun JSONObject.text(key: String) = get(key) as String
    private fun JSONObject.nonnegative(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long)
        return (value as Number).toLong().also { require(it >= 0) }
    }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun strings(array: JSONArray) = (0 until array.length()).map { array.get(it) as String }
}
