package app.lody.android.runtime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class RuntimeProtocolTest {
 private val fixtures=JSONObject(File(System.getProperty("runtime.fixtures")).readText())
 @Test fun sharedFixturesDecodeToTypedResults() {
  assertEquals(setOf("ping","setActive","probeSchema"),RuntimeProtocol.capabilities(fixtures.getJSONObject("hostReady")))
  val event=RuntimeProtocol.event(fixtures.getJSONObject("catalog")) as RuntimeEvent.CatalogUpdated
  assertEquals("中文 😀",event.snapshot.sessions.single().title);assertEquals(7L,event.snapshot.revision)
  assertEquals(RuntimeEvent.Synced(7,2),RuntimeProtocol.event(fixtures.getJSONObject("synced")))
  assertEquals(RuntimeEvent.GrantRequested,RuntimeProtocol.event(fixtures.getJSONObject("grant")))
  assertEquals(setOf("m"),RuntimeProtocol.schema(fixtures.getJSONObject("schema")).machineIds)
 }
 @Test fun networkFailureCannotBeMistakenForLiveAndDoesNotLeakRemoteText() {
  assertEquals(RuntimeEvent.SyncFailed("network_unavailable"),RuntimeProtocol.event(JSONObject("""{"type":"networkStatus","status":"unavailable"}""")))
  assertEquals(RuntimeEvent.SyncFailed("sync_failed"),RuntimeProtocol.event(JSONObject("""{"type":"syncError","reason":"private server contents"}""")))
 }
 @Test fun incompatibleOrIncompleteHandshakeFailsClosed() {
  assertThrows(Exception::class.java){RuntimeProtocol.capabilities(JSONObject("""{"protocol":1,"capabilities":["ping"]}"""))}
  assertThrows(Exception::class.java){RuntimeProtocol.capabilities(JSONObject("""{"protocol":2,"capabilities":["ping"]}"""))}
 }
 @Test fun malformedRequiredFieldsAreRejectedButFutureEventsIgnored() {
  assertThrows(Exception::class.java){RuntimeProtocol.event(JSONObject("""{"type":"synced","revision":"7","streams":2}"""))}
  assertThrows(Exception::class.java){RuntimeProtocol.event(JSONObject("""{"type":"catalog","revision":7,"catalog":{}}"""))}
  assertNull(RuntimeProtocol.event(JSONObject("""{"type":"futureEvent"}""")))
 }
}
