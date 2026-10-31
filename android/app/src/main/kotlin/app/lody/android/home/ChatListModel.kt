package app.lody.android.home

import app.lody.android.runtime.CatalogSession
import app.lody.android.runtime.CatalogSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** What a chat (or a collapsed group) wants from the user, if anything. */
enum class ChatAttention { Running, Waiting, Failed }

/** [place] is the project name, empty for unassigned chats; time views show it beside the title. */
data class ChatItem(val id: String, val title: String, val attention: ChatAttention?, val place: String = "")

/** Activity buckets, matching iOS `InboxSections.activityBucket`. */
enum class TimeBucket { Today, Yesterday, Week, Month, Older }

data class TimeSection(val bucket: TimeBucket, val chats: List<ChatItem>)

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

    /**
     * Home "by time": non-archived chats in activity buckets, newest first. The catalog has no
     * activity time yet, so creation time stands in for it.
     */
    fun timeline(snapshot: CatalogSnapshot, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<TimeSection> {
        val names = snapshot.projects.associate { it.id to it.name }
        return snapshot.sessions
            .filter { !it.archived }
            .sortedByDescending { it.createdAt }
            .groupBy { bucket(it.createdAt, today, zone) }
            .toSortedMap()
            .map { (bucket, sessions) -> TimeSection(bucket, sessions.map { item(it, if (isChat(it.projectId)) "" else names[it.projectId].orEmpty()) }) }
    }

    fun bucket(createdAt: String, today: LocalDate, zone: ZoneId): TimeBucket {
        val date = runCatching { Instant.parse(createdAt).atZone(zone).toLocalDate() }.getOrNull() ?: return TimeBucket.Older
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0 -> TimeBucket.Today
            days == 1L -> TimeBucket.Yesterday
            days <= 7 -> TimeBucket.Week
            days <= 30 -> TimeBucket.Month
            else -> TimeBucket.Older
        }
    }

    fun searchTimeline(sections: List<TimeSection>, query: String): List<TimeSection> {
        val term = query.trim()
        if (term.isEmpty()) return sections
        return sections.mapNotNull { section ->
            section.chats.filter { it.title.contains(term, ignoreCase = true) || it.place.contains(term, ignoreCase = true) }
                .takeIf { it.isNotEmpty() }
                ?.let { section.copy(chats = it) }
        }
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

    private fun item(session: CatalogSession, place: String = "") = ChatItem(session.id, session.title, attention(session.status), place)
}
