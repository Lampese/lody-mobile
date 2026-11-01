package app.lody.android.create

import app.lody.android.runtime.*

interface CreationService {
    suspend fun options(scope: RuntimeScope, projectId: String?): CreationOptions
    suspend fun create(scope: RuntimeScope, request: CreateRequest): CreationReceipt
}

/** Uses the existing verified account/workspace lease; never retries a mutation. */
class CreateSessionRepository(private val owner: RuntimeOwner) : CreationService {
    override suspend fun options(scope: RuntimeScope, projectId: String?): CreationOptions = owner.use(scope) {
        (it.execute(RuntimeCommand.GetCreationOptions(projectId)) as RuntimeResult.Options).value
    }
    override suspend fun create(scope: RuntimeScope, request: CreateRequest): CreationReceipt = owner.use(scope) {
        (it.execute(RuntimeCommand.CreateAndSend(request)) as RuntimeResult.Created).value
    }
}
