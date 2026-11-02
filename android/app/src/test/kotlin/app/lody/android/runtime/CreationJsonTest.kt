package app.lody.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CreationJsonTest {
    @Test fun completeOptionsSurvivePersistenceRoundTrip() {
        val a = CreationAgent("provider", "Provider", "machine", "acp", "codex")
        val c = CreationCapability("machine", "acp", "codex", listOf(CreationChoice("m", "Model")), listOf(CreationChoice("auto", "Auto")), mapOf("m" to listOf("low", "high")), "provider")
        val options = CreationOptions(listOf(a), listOf(c, c.copy(agentConfigId = "other")))
        assertEquals(options, CreationJson.options(JSONObject(CreationJson.encodeOptions(options).toString())))
    }

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
    @Test fun thoughtLevelFallbackSurvivesCacheAndHonorsPerModelOverrides() {
        val options = CreationJson.options(JSONObject("""{"capabilities":[{"machineId":"m","cliType":"builtin","agentType":"grok","reasoningEfforts":{"model":["high"],"off":[]},"configOptions":[{"id":"model","category":"model","currentValue":"model"},{"id":"thinking","type":"select","category":"thought_level","options":[{"id":"low","name":"Low"},{"id":"high","name":"High"}]}]}]}"""))
        val capability = options.capabilities.single()
        assertEquals(listOf("high"), capability.effortsFor(null))
        assertEquals(listOf("low", "high"), capability.effortsFor("other"))
        assertEquals(emptyList<String>(), capability.effortsFor("off"))
        assertEquals(options, CreationJson.options(CreationJson.encodeOptions(options)))
    }
    @Test fun creationPayloadKeepsStableIdsAndExplicitSelectionsWithoutIdentity() {
        val request = CreateRequest("session", "turn", null, "m", "a", "中文", null, true, "model", "auto", "high")
        val json = CreationJson.request(request)
        assertEquals("session", json.getString("sessionId")); assertEquals("turn", json.getString("turnId"))
        assertEquals("中文", json.getString("text")); assertTrue(json.getBoolean("worktree"))
        assertFalse(json.has("userId")); assertFalse(json.has("workspaceId")); assertFalse(json.has("projectId"))
    }
}
