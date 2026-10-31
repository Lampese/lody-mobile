package app.lody.android.home

import app.lody.android.runtime.CatalogSession
import app.lody.android.runtime.CatalogSnapshot

/** What a chat (or a collapsed group) wants from the user, if anything. */
enum class ChatAttention { Running, Waiting, Failed }

data class ChatItem(val id: String, val title: String, val attention: ChatAttention?)

data class ChatGroup(val id: String, val name: String, val kind: Kind, val chats: List<ChatItem>) {
    enum class Kind { Pinned, Chats, Project }

    /** Waiting and failed need a decision, so they outrank work in progress. */
    val mostUrgent: ChatAttention?
        get() = listOf(ChatAttention.Waiting, ChatAttention.Failed, ChatAttention.Running)
            .firstOrNull { wanted -> chats.any { it.attention == wanted } }
}

/**
 * Home list projection. Mirrors the iOS `InboxSections.projects` grouping: pinned chats
 * first, projects by name, unassigned chats last; archived chats are hidden.
 */
object ChatListModel {
    const val PINNED_ID = "pinned"
    const val CHATS_ID = "chats"

    /** Groups beyond this many chats show a "Show N more" row (iOS shows the same 5). */
    const val VISIBLE_CHATS = 5

    private val running = setOf("running", "initializing", "processing", "in_progress", "queued")
    private val waiting = setOf("requestPermission", "waiting")

    fun attention(status: String): ChatAttention? = when (status) {
        in running -> ChatAttention.Running
        in waiting -> ChatAttention.Waiting
        "error" -> ChatAttention.Failed
        else -> null
    }

    fun isChat(projectId: String) = projectId.endsWith(":unassigned")

    fun groups(snapshot: CatalogSnapshot): List<ChatGroup> {
        // createdAt is ISO-8601, so string order is time order; the catalog has no activity time yet.
        val visible = snapshot.sessions.filter { !it.archived }.sortedByDescending { it.createdAt }
        val (pinned, rest) = visible.partition { it.pinned }
        val groups = mutableListOf<ChatGroup>()
        if (pinned.isNotEmpty()) groups += ChatGroup(PINNED_ID, "", ChatGroup.Kind.Pinned, pinned.map(::item))
        snapshot.projects
            .filterNot { isChat(it.id) }
            .sortedBy { it.name.lowercase() }
            .forEach { project ->
                val chats = rest.filter { it.projectId == project.id }
                if (chats.isNotEmpty()) groups += ChatGroup(project.id, project.name, ChatGroup.Kind.Project, chats.map(::item))
            }
        val chats = rest.filter { isChat(it.projectId) }
        if (chats.isNotEmpty()) groups += ChatGroup(CHATS_ID, "", ChatGroup.Kind.Chats, chats.map(::item))
        return groups
    }

    /** Keeps only matching chats; groups without matches drop out. */
    fun search(groups: List<ChatGroup>, query: String): List<ChatGroup> {
        val term = query.trim()
        if (term.isEmpty()) return groups
        return groups.mapNotNull { group ->
            group.chats.filter { it.title.contains(term, ignoreCase = true) }
                .takeIf { it.isNotEmpty() }
                ?.let { group.copy(chats = it) }
        }
    }

    private fun item(session: CatalogSession) = ChatItem(session.id, session.title, attention(session.status))
}
