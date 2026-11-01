package app.lody.android.create

import app.lody.android.runtime.*
import kotlinx.coroutines.CancellationException

interface CreationOptionsStore {
    suspend fun readOptions(scope: RuntimeScope, projectId: String?): CreationOptions?
    suspend fun writeOptions(scope: RuntimeScope, projectId: String?, options: CreationOptions)
}

interface CreationService {
    suspend fun cachedOptions(scope: RuntimeScope, projectId: String?): CreationOptions? = null
    suspend fun options(scope: RuntimeScope, projectId: String?): CreationOptions
    suspend fun create(scope: RuntimeScope, request: CreateRequest): CreationReceipt
}

/** Uses the existing verified account/workspace lease; never retries a mutation. */
class CreateSessionRepository(private val owner: RuntimeOwner, private val store: CreationOptionsStore) : CreationService {
    override suspend fun cachedOptions(scope: RuntimeScope, projectId: String?) = store.readOptions(scope, projectId)
    override suspend fun options(scope: RuntimeScope, projectId: String?): CreationOptions = owner.use(scope) {
        val value = (it.execute(RuntimeCommand.GetCreationOptions(projectId)) as RuntimeResult.Options).value
        try { store.writeOptions(scope, projectId, value) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Disk failure must not hide live choices. */ }
        value
    }
    override suspend fun create(scope: RuntimeScope, request: CreateRequest): CreationReceipt = owner.use(scope) {
        (it.execute(RuntimeCommand.CreateAndSend(request)) as RuntimeResult.Created).value
    }
}
