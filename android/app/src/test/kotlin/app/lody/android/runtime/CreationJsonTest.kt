package app.lody.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CreationJsonTest {
    @Test fun providerCapabilityNeverFallsBackToAnotherProviderOfTheSameType() {
        val agent = CreationAgent("a", "A", "m", "builtin", "codex")
        val other = CreationCapability("m", "builtin", "codex", emptyList(), emptyList(), emptyMap(), "b")
        assertNull(CreationOptions(listOf(agent), listOf(other)).capability(agent))
        val own = other.copy(agentConfigId = "a")
        assertEquals(own, CreationOptions(listOf(agent), listOf(other, own)).capability(agent))
    }
    @Test fun readsAdvertisedAgentModelAndReasoningChoices() {
        val options = CreationJson.options(JSONObject("""{"agents":[{"id":"a","name":"Assistant","machineId":"m","cliType":"builtin","agentType":"codex"}],"capabilities":[{"machineId":"m","cliType":"builtin","agentType":"codex","models":[{"id":"model","name":"Model"}],"modes":[{"id":"auto","name":"Auto"}],"reasoningEfforts":{"model":["low","high"]}}]}"""))
        assertEquals("a", options.agents.single().id)
        assertEquals(listOf("low", "high"), options.capabilities.single().efforts["model"])
        assertEquals("auto", options.capabilities.single().modes.single().id)
    }
    @Test fun creationPayloadKeepsStableIdsAndExplicitSelectionsWithoutIdentity() {
        val request = CreateRequest("session", "turn", null, "m", "a", "中文", null, true, "model", "auto", "high")
        val json = CreationJson.request(request)
        assertEquals("session", json.getString("sessionId")); assertEquals("turn", json.getString("turnId"))
        assertEquals("中文", json.getString("text")); assertTrue(json.getBoolean("worktree"))
        assertFalse(json.has("userId")); assertFalse(json.has("workspaceId")); assertFalse(json.has("projectId"))
    }
}
