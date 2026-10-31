package app.lody.android.cloud

import java.net.URI
import java.net.URLDecoder

/** Only a successful official grant response may construct this permission. */
internal class StreamAccess(val workspaceId: String, gateway: String) {
    val origin: String
    init {
        require(workspaceId.matches(Regex("[a-zA-Z0-9_-]{1,128}")))
        val uri = URI(gateway)
        require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null && uri.path in setOf("", "/"))
        val host = uri.host ?: throw IllegalArgumentException()
        require(host.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}")) && !host.endsWith(".local") && !host.endsWith(".internal"))
        origin = "https://${host.lowercase()}"
    }
    fun allows(url: String, method: String): Boolean = try {
        val uri = URI(url)
        val parts = uri.rawPath.split('/')
        val stream = if (parts.size >= 4) URLDecoder.decode(parts[3], "UTF-8") else ""
        val scoped = stream == "$workspaceId:meta" || stream.matches(Regex("${Regex.escape(workspaceId)}:(?:mf|s):[a-zA-Z0-9_-]{1,128}"))
        method in setOf("GET", "HEAD", "OPTIONS") && uri.scheme == "https" && "https://${uri.host}" == origin && uri.port == -1 && uri.userInfo == null && uri.fragment == null &&
            parts.size in 4..5 && parts[1] == "ds" && parts[2] == "lody" && (parts.size == 4 || parts[4] == "bootstrap") && scoped
    } catch (_: Exception) { false }
}
