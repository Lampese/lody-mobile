package app.lody.android.catalog

import androidx.test.platform.app.InstrumentationRegistry
import app.lody.android.auth.*
import app.lody.android.runtime.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CatalogStoreTest {
 private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
 private val account = Account("account", "Fixture", listOf(Workspace("w", "Work"), Workspace("other", "Other")))
 private val scope = RuntimeScope("account", "w")
 private val saved = SavedCatalog(CatalogSnapshot(99, listOf(CatalogProject("p", "m", "项目 🌙", "/fixture")), listOf(CatalogSession("s", "m", "p", "hello", "idle", false, true, "2026")), listOf("m")), 1234)
 @Test fun atomicSnapshotRestoresContextAndIsolatesScopeAcrossInstances() = runBlocking {
  val store = AtomicCatalogStore(context, "cache-test")
  try {
   store.clear(); store.saveAccount(account); store.select(scope); store.write(scope, saved)
   val reopened = AtomicCatalogStore(context, "cache-test")
   assertEquals(AccountContext(account, "w"), reopened.readContext())
   assertEquals(saved, reopened.read(scope))
   assertNull(reopened.read(RuntimeScope("other-account", "w")))
   assertNull(reopened.read(RuntimeScope("account", "other")))
   // Revisions belong to a runtime generation: a new bootstrap may restart at 1.
   reopened.write(scope, saved.copy(snapshot = saved.snapshot.copy(revision = 1)))
   assertEquals(1L, reopened.read(scope)!!.snapshot.revision)
   reopened.saveAccount(account.copy(workspaces = listOf(Workspace("other", "Other"))))
   assertNull(reopened.read(scope)); assertNull(reopened.readContext()!!.workspaceId)
   reopened.clear(); assertNull(store.readContext()); assertNull(store.read(scope))
  } finally { store.clear() }
 }
 @Test fun corruptOrOversizedCacheIsDiscardedWithoutLosingAccount() = runBlocking {
  val store = AtomicCatalogStore(context, "cache-corrupt")
  try {
   store.clear();store.saveAccount(account);store.write(scope,saved)
   val files = File(context.noBackupFilesDir,"cache-corrupt").listFiles()!!.filter {it.name != "account.json"}
   assertEquals(1,files.size)
   files.single().writeText("not valid json")
   assertNull(store.read(scope));assertEquals(account,store.readContext()!!.account)
   store.write(scope,saved)
   files.single().outputStream().use {out -> repeat(9) {out.write(ByteArray(1024*1024))}}
   assertNull(store.read(scope)); assertEquals(account,store.readContext()!!.account)
  } finally {store.clear()}
 }
}
