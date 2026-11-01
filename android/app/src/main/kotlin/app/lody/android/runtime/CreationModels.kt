package app.lody.android.runtime

data class CreationChoice(val id: String, val name: String)
data class CreationAgent(val id: String, val name: String, val machineId: String, val cliType: String, val agentType: String)
data class CreationCapability(val machineId: String, val cliType: String, val agentType: String, val models: List<CreationChoice>, val modes: List<CreationChoice>, val efforts: Map<String, List<String>>, val agentConfigId: String? = null)
data class CreationOptions(val agents: List<CreationAgent>, val capabilities: List<CreationCapability>) {
    fun capability(agent: CreationAgent?): CreationCapability? {
        if (agent == null) return null
        return capabilities.firstOrNull { it.machineId == agent.machineId && it.agentConfigId == agent.id }
            ?: capabilities.firstOrNull { it.machineId == agent.machineId && it.agentConfigId == null && it.cliType == agent.cliType && it.agentType == agent.agentType }
    }
}
data class CreateRequest(val sessionId: String, val turnId: String, val projectId: String?, val machineId: String, val agentConfigId: String, val text: String, val branch: String?, val worktree: Boolean, val modelId: String?, val modeId: String?, val effort: String?)
data class CreationReceipt(val sessionId: String, val state: String)
