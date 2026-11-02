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
                val text = entry.getJSONArray("items").objects().mapNotNull { item ->
                    when (item.optString("type")) {
                        "text" -> item.optString("text")
                        "proposed_plan" -> item.optString("markdown").takeUnless { item.optString("status") == "cleared" }
                        else -> null
                    }
                }.joinToString("\n\n")
                if (text.isBlank()) null else SessionMessage(entry.text("id"), role, text, role == "assistant" && !entry.optBoolean("finished", true))
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
    private fun JSONObject.text(key: String) = get(key) as String
    private fun JSONObject.nonnegative(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long)
        return (value as Number).toLong().also { require(it >= 0) }
    }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun strings(array: JSONArray) = (0 until array.length()).map { array.get(it) as String }
}
