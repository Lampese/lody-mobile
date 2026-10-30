package app.lody.android.cloud
import org.junit.Assert.*
import org.junit.Test
class StreamAccessTest {
 @Test fun permitsOnlyExactGrantOriginAndScopedCatalogReads() {
  val access=StreamAccess("workspace","https://streams.example.org")
  assertTrue(access.allows("https://streams.example.org/ds/lody/workspace%3Ameta/bootstrap","GET"))
  assertTrue(access.allows("https://streams.example.org/ds/lody/workspace%3Amf%3Amachine?offset=3&live=long-poll","GET"))
  for(url in listOf("https://evil.example.org/ds/lody/workspace%3Ameta","https://streams.example.org/ds/lody/other%3Ameta","https://streams.example.org/ds/lody/workspace%3Arpc","https://streams.example.org/script.js","http://streams.example.org/ds/lody/workspace%3Ameta"))assertFalse(url,access.allows(url,"GET"))
  assertFalse(access.allows("https://streams.example.org/ds/lody/workspace%3Ameta","POST"))
 }
 @Test fun rejectsCredentialsPortsPrivateAddressesAndUntrustedPathInGrant() {
  for(url in listOf("http://streams.example.org","https://user@streams.example.org","https://127.0.0.1","https://localhost","https://streams.example.org:8080","https://streams.example.org/path","https://streams.example.org?secret=x"))assertThrows(IllegalArgumentException::class.java){StreamAccess("workspace",url)}
 }
}
