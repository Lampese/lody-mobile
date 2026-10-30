package app.lody.android.auth

import app.lody.android.runtime.ShortLivedGrant

class Credential(val token: String) {
    init { require(token.isNotBlank() && token.length <= 16384 && token.all { it in '!'..'~' }) }
    override fun toString() = "Credential(<redacted>)"
}
data class Workspace(val id: String, val name: String)
data class Account(val id: String, val name: String, val workspaces: List<Workspace>)
class DeviceAuthorization(val deviceCode: String, val userCode: String, val verificationUrl: String, val expiresIn: Int, val interval: Int) {
    override fun toString() = "DeviceAuthorization(<redacted>)"
}
sealed interface TokenPoll {
    data object Pending : TokenPoll
    data object SlowDown : TokenPoll
    data object Denied : TokenPoll
    data object Expired : TokenPoll
    class Authorized(val credential: Credential) : TokenPoll
}
class AuthFailure(val code: String) : Exception(code)
interface CloudAuthApi {
    suspend fun authorize(): DeviceAuthorization
    suspend fun poll(code: DeviceAuthorization): TokenPoll
    suspend fun account(credential: Credential): Account
    suspend fun grant(credential: Credential, workspaceId: String): ShortLivedGrant
    suspend fun signOut(credential: Credential)
}
interface CredentialStore {
    suspend fun read(): Credential?
    suspend fun write(credential: Credential)
    suspend fun clear()
}
