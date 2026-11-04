package app.lody.android.runtime

data class CreationChoice(val id: String, val name: String)
data class CreationAgent(val id: String, val name: String, val machineId: String, val cliType: String, val agentType: String)
data class CreationCapability(val machineId: String, val cliType: String, val agentType: String, val models: List<CreationChoice>, val modes: List<CreationChoice>, val efforts: Map<String, List<String>>, val agentConfigId: String? = null, val defaultModelId: String? = null, val defaultEfforts: List<String> = emptyList()) {
    fun effortsFor(modelId: String?): List<String> = efforts[modelId ?: defaultModelId] ?: defaultEfforts
}
data class CreationOptions(val agents: List<CreationAgent>, val capabilities: List<CreationCapability>, val availability: String = "online", val onlineMachineIds: Set<String>? = null) {
    fun capability(agent: CreationAgent?): CreationCapability? {
        if (agent == null) return null
        return capabilities.firstOrNull { it.machineId == agent.machineId && it.agentConfigId == agent.id }
            ?: capabilities.firstOrNull { it.machineId == agent.machineId && it.agentConfigId == null && it.cliType == agent.cliType && it.agentType == agent.agentType }
    }

    /** A running session's provider choices: its computer's capability for the session's agent type. */
    fun capability(machineId: String, cliType: String?, agentType: String?): CreationCapability? {
        val matching = capabilities.filter { it.machineId == machineId && it.cliType == cliType && it.agentType == agentType }
        return matching.firstOrNull { it.agentConfigId == null } ?: matching.firstOrNull()
    }
}
data class CreateRequest(
    val sessionId: String, val turnId: String, val projectId: String?, val machineId: String, val agentConfigId: String, val text: String,
    val branch: String?, val worktree: Boolean, val modelId: String?, val modeId: String?, val effort: String?,
    /** The Agent Role the session starts as; the runtime re-reads it and applies it or refuses. */
    val agentRoleId: String? = null,
    /** Option values the Role pins beyond model / mode / effort (strings or booleans). */
    val configOptionValues: Map<String, Any> = emptyMap(),
)

/** What one machine target of an Agent Role pins (runtime listing; secrets never cross). */
data class RoleRunConfig(val modelId: String? = null, val modeId: String? = null, val options: Map<String, Any> = emptyMap())
data class AgentRoleTarget(val machineId: String, val agentConfigId: String, val state: String, val runConfig: RoleRunConfig = RoleRunConfig())
/** A workspace Agent Role readable by this user (web `AgentRole`); [emoji] defaults like web's. */
data class AgentRole(val id: String, val name: String, val emoji: String, val revision: Int, val targets: List<AgentRoleTarget>)
data class CreationReceipt(val sessionId: String, val state: String)
