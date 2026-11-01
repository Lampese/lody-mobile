package app.lody.android.home

import app.lody.android.runtime.CatalogSession
import app.lody.android.runtime.CatalogSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** What a chat (or a collapsed group) wants from the user, if anything. Web `SessionRowStatusIndicator` order. */
enum class ChatAttention { Running, Waiting, Failed, Unread }

/**
 * One Home row. [place] is the project (or repo) name, empty for unassigned chats; [activityAt]
 * is epoch ms (last message, else creation) and drives buckets and the trailing age. [prStatus]
 * is the latest pull request's status (open / merged / closed / draft), empty when there is none.
 */
data class ChatItem(
    val id: String,
    val title: String,
    val attention: ChatAttention?,
    val place: String = "",
    val placeIsRepo: Boolean = false,
    val branch: String = "",
    val prStatus: String = "",
    val activityAt: Long? = null,
) {
    val unread: Boolean get() = attention == ChatAttention.Unread
}

/** Activity buckets, matching iOS `InboxSections.activityBucket`. */
enum class TimeBucket { Today, Yesterday, Week, Month, Older }

data class TimeSection(val bucket: TimeBucket, val chats: List<ChatItem>)

data class ChatGroup(val id: String, val name: String, val kind: Kind, val chats: List<ChatItem>) {
    enum class Kind { Pinned, Chats, Project }

    /** Failed and waiting need a decision, so they outrank work in progress and unread. */
    val mostUrgent: ChatAttention?
        get() = listOf(ChatAttention.Failed, ChatAttention.Waiting, ChatAttention.Running, ChatAttention.Unread)
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

    /** Status first; an idle chat still flags an open question (`awaitingUserSince`) or an unread reply. */
    fun attention(session: CatalogSession): ChatAttention? {
        val status = attention(session.status)
        return when {
            status == ChatAttention.Failed -> status
            session.awaitingUserSince != null && status != ChatAttention.Running -> ChatAttention.Waiting
            status != null -> status
            isUnread(session) -> ChatAttention.Unread
            else -> null
        }
    }

    fun isUnread(session: CatalogSession): Boolean {
        val message = session.lastMessageAt ?: return false
        return session.lastReadAt?.let { message > it } ?: true
    }

    fun activityAt(session: CatalogSession): Long? =
        session.lastMessageAt ?: runCatching { Instant.parse(session.createdAt).toEpochMilli() }.getOrNull()

    fun isChat(projectId: String) = projectId.endsWith(":unassigned")

    fun groups(snapshot: CatalogSnapshot): List<ChatGroup> {
        val visible = snapshot.sessions.filter { !it.archived }.sortedByDescending { activityAt(it) ?: Long.MIN_VALUE }
        val (pinned, rest) = visible.partition { it.pinned }
        val groups = mutableListOf<ChatGroup>()
        if (pinned.isNotEmpty()) groups += ChatGroup(PINNED_ID, "", ChatGroup.Kind.Pinned, pinned.map { item(it) })
        snapshot.projects
            .filterNot { isChat(it.id) }
            .sortedBy { it.name.lowercase() }
            .forEach { project ->
                val chats = rest.filter { it.projectId == project.id }
                if (chats.isNotEmpty()) groups += ChatGroup(project.id, project.name, ChatGroup.Kind.Project, chats.map { item(it) })
            }
        val chats = rest.filter { isChat(it.projectId) }
        if (chats.isNotEmpty()) groups += ChatGroup(CHATS_ID, "", ChatGroup.Kind.Chats, chats.map { item(it) })
        return groups
    }

    /** Home "by time": non-archived chats in activity buckets, newest first. */
    fun timeline(snapshot: CatalogSnapshot, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<TimeSection> {
        val projects = snapshot.projects.associateBy { it.id }
        return snapshot.sessions
            .filter { !it.archived }
            .map { session ->
                val project = projects[session.projectId]?.takeUnless { isChat(it.id) }
                item(session, project?.name.orEmpty(), project?.repoFullName != null)
            }
            .sortedByDescending { it.activityAt ?: Long.MIN_VALUE }
            .groupBy { bucket(it.activityAt, today, zone) }
            .toSortedMap()
            .map { (bucket, chats) -> TimeSection(bucket, chats) }
    }

    fun bucket(createdAt: String, today: LocalDate, zone: ZoneId): TimeBucket =
        bucket(runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrNull(), today, zone)

    fun bucket(at: Long?, today: LocalDate, zone: ZoneId): TimeBucket {
        val date = at?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return TimeBucket.Older
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0 -> TimeBucket.Today
            days == 1L -> TimeBucket.Yesterday
            days <= 7 -> TimeBucket.Week
            days <= 30 -> TimeBucket.Month
            else -> TimeBucket.Older
        }
    }

    /** Units of the trailing age; under a week it is now / minutes / hours / days, then a date. */
    sealed interface Age {
        data object Now : Age
        data class Minutes(val value: Long) : Age
        data class Hours(val value: Long) : Age
        data class Days(val value: Long) : Age
        data class Date(val date: LocalDate) : Age
    }

    /** Mirrors iOS `LodyRelativeTime.compact`. */
    fun age(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Age {
        val elapsed = now - at
        return when {
            elapsed < MINUTE -> Age.Now
            elapsed < HOUR -> Age.Minutes(elapsed / MINUTE)
            elapsed < DAY -> Age.Hours(elapsed / HOUR)
            elapsed < 7 * DAY -> Age.Days(elapsed / DAY)
            else -> Age.Date(Instant.ofEpochMilli(at).atZone(zone).toLocalDate())
        }
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

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

    private fun item(session: CatalogSession, place: String = "", placeIsRepo: Boolean = false) = ChatItem(
        id = session.id,
        title = session.title,
        attention = attention(session),
        place = place,
        placeIsRepo = placeIsRepo,
        branch = session.branchName.orEmpty(),
        prStatus = session.pullRequests.lastOrNull()?.status.orEmpty(),
        activityAt = activityAt(session),
    )
}
