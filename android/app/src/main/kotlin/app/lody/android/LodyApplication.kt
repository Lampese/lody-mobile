package app.lody.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import app.lody.android.runtime.*
import app.lody.android.auth.*
import app.lody.android.catalog.CatalogRepository
import app.lody.android.catalog.AtomicCatalogStore
import kotlinx.coroutines.*
import app.lody.android.cloud.NetworkAvailability

open class LodyApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var runtimeOwner: RuntimeOwner
        private set
    lateinit var account: AccountController
        private set
    lateinit var cloud: CatalogRepository
        private set
    lateinit var creation: app.lody.android.create.CreationService
        private set
    private var prepared: Pair<RuntimeScope, ShortLivedGrant>? = null
    protected open fun createRuntime(scope: RuntimeScope): CloudRuntime {
        val ready = prepared?.takeIf { it.first == scope }
        if (ready != null) {
            prepared = null
            return ReadOnlyCloudRuntime(this, scope, ready.second, GrantProvider { key ->
                try { account.grant(key) }
                catch (error: AuthFailure) { throw app.lody.android.runtime.RuntimeException(RuntimeFault(error.code, if (error.code == "network_unavailable") RuntimeErrorCategory.NETWORK else RuntimeErrorCategory.AUTH)) }
            })
        }
        return createOfflineRuntime(scope)
    }
    protected open fun createOfflineRuntime(scope: RuntimeScope): CloudRuntime = WebViewRuntime(this, scope)
    override fun onCreate() {
        super.onCreate()
        runtimeOwner = RuntimeOwner(::createRuntime, Dispatchers.Main.immediate)
        val cache = AtomicCatalogStore(this)
        creation = app.lody.android.create.CreateSessionRepository(runtimeOwner, cache)
        account = AccountController(CloudAuthHttp(OkHttpAuthTransport()), KeystoreCredentialStore(this), applicationScope, cache) {
            prepared = null
            cloud.disconnect()
        }
        cloud = CatalogRepository(account, runtimeOwner, applicationScope, cache) { scope, grant -> prepared = scope to grant }
        ProcessLifecycleOwner.get().lifecycle.addObserver(RuntimeLifecycle(runtimeOwner, applicationScope, NetworkAvailability(this).available) {
            ConnectionTrace.mark("network_validated")
            cloud.networkAvailable()
        })
    }
}
