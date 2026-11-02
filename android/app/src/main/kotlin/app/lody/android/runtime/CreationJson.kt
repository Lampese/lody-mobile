package app.lody.android.runtime

import org.json.JSONArray
import org.json.JSONObject

internal object CreationJson {
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun choices(array: JSONArray?) = array.objects().map { CreationChoice(it.getString("id"), it.getString("name")) }
    fun options(value: JSONObject): CreationOptions = CreationOptions(
        value.optJSONArray("agents").objects().map { CreationAgent(it.getString("id"), it.getString("name"), it.getString("machineId"), it.getString("cliType"), it.getString("agentType")) },
        value.optJSONArray("capabilities").objects().map { c ->
            val config = c.optJSONArray("configOptions").objects()
            val defaultModel = config.firstOrNull { it.optString("category") == "model" }?.optString("currentValue")?.takeIf { it.isNotBlank() }
            val thought = config.firstOrNull { it.optString("type") == "select" && (it.optString("category") == "thought_level" || it.optString("id") == "reasoning_effort") }
            val defaultEfforts = choices(thought?.optJSONArray("options")).map { it.id }
            val efforts = c.optJSONObject("reasoningEfforts") ?: JSONObject()
            CreationCapability(c.getString("machineId"), c.getString("cliType"), c.getString("agentType"), choices(c.optJSONArray("models")), choices(c.optJSONArray("modes")), efforts.keys().asSequence().associateWith { key ->
                val a = efforts.getJSONArray(key); (0 until a.length()).map { a.getString(it) }
            }, c.optString("agentConfigId").takeIf { it.isNotBlank() }, defaultModel, defaultEfforts)
        },
    )
    fun encodeOptions(value: CreationOptions) = JSONObject().apply {
        fun choices(values: List<CreationChoice>) = JSONArray(values.map { JSONObject().put("id", it.id).put("name", it.name) })
        put("agents", JSONArray(value.agents.map { JSONObject().put("id", it.id).put("name", it.name).put("machineId", it.machineId).put("cliType", it.cliType).put("agentType", it.agentType) }))
        put("capabilities", JSONArray(value.capabilities.map { c -> JSONObject().put("machineId", c.machineId).put("cliType", c.cliType).put("agentType", c.agentType).put("agentConfigId", c.agentConfigId)
            .put("configOptions", JSONArray().apply {
                c.defaultModelId?.let { put(JSONObject().put("id", "model").put("category", "model").put("currentValue", it)) }
                put(JSONObject().put("id", "reasoning_effort").put("type", "select").put("category", "thought_level").put("options", choices(c.defaultEfforts.map { CreationChoice(it, it) })))
            })
            .put("models", choices(c.models)).put("modes", choices(c.modes)).put("reasoningEfforts", JSONObject().apply { c.efforts.forEach { (model, levels) -> put(model, JSONArray(levels)) } }) }))
    }
    fun request(value: CreateRequest) = JSONObject().apply {
        put("sessionId", value.sessionId); put("turnId", value.turnId); put("projectId", value.projectId)
        put("machineId", value.machineId); put("agentConfigId", value.agentConfigId); put("text", value.text)
        put("branch", value.branch); put("worktree", value.worktree); put("modelId", value.modelId); put("modeId", value.modeId); put("effort", value.effort)
    }
}
