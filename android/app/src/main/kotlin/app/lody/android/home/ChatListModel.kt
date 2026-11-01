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
    /** CI on the latest PR's head: pass / fail / pending, empty when unknown. */
    val prCi: String = "",
    val projectId: String = "",
    /** The "by status" group, decided from the catalog session (see [StatusGroup]). */
    val statusGroup: StatusGroup = StatusGroup.Idle,
    /** The chat that opened this one (resolved like iOS `SessionRows.tree`), if any. */
    val openerId: String? = null,
    /** Set by [ChatListModel.nest]: shown nested under this row id. */
    val nestedUnder: String? = null,
    val lastChild: Boolean = false,
    /** Has nested rows right below it; the tree trunk starts here. */
    val treeParent: Boolean = false,
) {
    val unread: Boolean get() = attention == ChatAttention.Unread
}

/** Activity buckets, matching iOS `InboxSections.activityBucket`. */
enum class TimeBucket { Today, Yesterday, Week, Month, Older }

data class TimeSection(val bucket: TimeBucket, val chats: List<ChatItem>)

/** Whose chats Home lists: only the signed-in user's, or the workspace's. iOS: `HomeScope`. */
enum class HomeScope(val key: String) { Mine("mine"), Team("team") }

/** How Home groups chats; within a group chats are always newest activity first. iOS: `HomeGrouping`. */
enum class HomeGrouping(val key: String) { Time("time"), Project("project"), Status("status") }

/**
 * "By status" groups, most pressing first. Mirrors web `lib/home-now.ts` (iOS `HomeStatusGroup`):
 * every chat lands in exactly one group, under the first reason that applies.
 */
enum class StatusGroup {
    /** Failed, waiting on the person, CI failed on an idle chat's open PR, or a draft PR. */
    Attention,
    /** Finished since the person last looked. */
    Finished,
    /** Working, even when unread or its CI is red: it may be fixing exactly that. */
    Running,
    /** An open pull request with passing or pending CI. */
    PullRequest,
    /** Everything else, including chats whose PR is merged or closed. */
    Idle,
}

/** One Home group; the screen localizes [header]. */
data class HomeSection(val id: String, val header: Header, val chats: List<ChatItem>) {
    sealed interface Header {
        data class Bucket(val bucket: TimeBucket) : Header
        data class Project(val name: String) : Header
        data object Chats : Header
        data class Status(val group: StatusGroup) : Header
    }
}

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

    /** The catalog's CI letters as web reads them: s pass; f / e fail; p / x pending. */
    fun ci(letter: String?): String = when (letter) {
        "s" -> "pass"
        "f", "e" -> "fail"
        "p", "x" -> "pending"
        else -> ""
    }

    fun statusGroup(session: CatalogSession): StatusGroup {
        val status = attention(session.status)
        val waiting = status == ChatAttention.Waiting || status == ChatAttention.Failed ||
            (session.awaitingUserSince != null && status != ChatAttention.Running)
        val pr = session.pullRequests.lastOrNull()?.takeIf { it.status == "open" || it.status == "draft" }
        return when {
            waiting -> StatusGroup.Attention
            status == ChatAttention.Running -> StatusGroup.Running
            pr != null && ci(pr.ci) == "fail" -> StatusGroup.Attention
            isUnread(session) -> StatusGroup.Finished
            pr?.status == "draft" -> StatusGroup.Attention
            pr != null -> StatusGroup.PullRequest
            else -> StatusGroup.Idle
        }
    }

    /**
     * Home in the view menu's grouping; archived chats are hidden. With [owner] ("My tasks"),
     * only chats whose session meta `userId` is that user are listed.
     */
    fun home(
        snapshot: CatalogSnapshot,
        grouping: HomeGrouping,
        owner: String? = null,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HomeSection> {
        // A contained Tab of the owner's chat stays with it, as on iOS (`InboxSections.owned`).
        val scoped = owner?.let { id ->
            val mine = snapshot.sessions.filter { it.userId == id }.mapTo(mutableSetOf()) { it.id }
            snapshot.copy(sessions = snapshot.sessions.filter { it.id in mine || it.parentSessionId in mine })
        } ?: snapshot
        return grouped(scoped, grouping, today, zone)
    }

    private fun grouped(snapshot: CatalogSnapshot, grouping: HomeGrouping, today: LocalDate, zone: ZoneId): List<HomeSection> =
        when (grouping) {
            HomeGrouping.Time -> timeline(snapshot, today, zone).map { HomeSection("bucket-${it.bucket}", HomeSection.Header.Bucket(it.bucket), it.chats) }
            HomeGrouping.Project -> byProject(snapshot)
            HomeGrouping.Status -> byStatus(snapshot)
        }

    /** One group per project, most recently active first; unassigned chats form the "Chats" group. */
    fun byProject(snapshot: CatalogSnapshot): List<HomeSection> {
        val names = snapshot.projects.associate { it.id to it.name }
        return homeItems(snapshot)
            .groupBy { if (isChat(it.projectId)) CHATS_ID else it.projectId }
            .map { (key, chats) ->
                val header = if (key == CHATS_ID) HomeSection.Header.Chats else HomeSection.Header.Project(names[key].orEmpty())
                HomeSection("project-$key", header, nest(chats))
            }
    }

    /** [StatusGroup] order; the screen folds a long idle group behind "Show all". */
    fun byStatus(snapshot: CatalogSnapshot): List<HomeSection> {
        val groups = homeItems(snapshot).groupBy { it.statusGroup }
        return StatusGroup.entries.mapNotNull { group ->
            groups[group]?.let { HomeSection("status-$group", HomeSection.Header.Status(group), nest(it)) }
        }
    }

    /** Quiet chats beyond this many fold behind "Show all" in the status view (iOS shows the same 20). */
    const val IDLE_LIMIT = 20

    /** Non-archived rows, newest activity first, carrying their project for the detail line. */
    private fun homeItems(snapshot: CatalogSnapshot): List<ChatItem> {
        val projects = snapshot.projects.associateBy { it.id }
        val all = snapshot.sessions.associateBy { it.id }
        return snapshot.sessions
            .filter { !it.archived }
            .map { session ->
                val project = projects[session.projectId]?.takeUnless { isChat(it.id) }
                item(session, project?.name.orEmpty(), project?.repoFullName != null).copy(openerId = opener(session, all))
            }
            .sortedByDescending { it.activityAt ?: Long.MIN_VALUE }
    }

    /**
     * The session that opened [session], mirroring iOS `SessionRows.tree`: contained Tabs keep
     * their own entry; an opener inside a Tab resolves to that Tab's root; the opener must share
     * the project and archive state.
     */
    private fun opener(session: CatalogSession, all: Map<String, CatalogSession>): String? {
        if (session.parentSessionId != null) return null
        var id = session.openedByRootSessionId ?: session.openedBySessionId ?: return null
        val seen = mutableSetOf<String>()
        while (seen.add(id)) id = all[id]?.parentSessionId ?: break
        val opener = all[id] ?: return null
        return id.takeIf { it != session.id && opener.projectId == session.projectId && opener.archived == session.archived }
    }

    /**
     * Nests each chat under its root opener when that opener is in the same group (one level, as
     * on web and iOS). A family takes its freshest member's place; members keep their order.
     */
    fun nest(chats: List<ChatItem>): List<ChatItem> {
        val byId = chats.associateBy { it.id }
        fun root(chat: ChatItem): String {
            val seen = mutableSetOf(chat.id)
            var current = chat
            while (true) {
                val next = current.openerId?.let(byId::get) ?: return current.id
                if (!seen.add(next.id)) return chat.id
                current = next
            }
        }
        val order = mutableListOf<String>()
        val children = mutableMapOf<String, MutableList<ChatItem>>()
        for (chat in chats) {
            val id = root(chat)
            if (id !in children) {
                children[id] = mutableListOf()
                order += id
            }
            if (id != chat.id) children.getValue(id) += chat
        }
        return order.flatMap { id ->
            val kids = children.getValue(id)
            listOf(byId.getValue(id).copy(treeParent = kids.isNotEmpty(), nestedUnder = null, lastChild = false)) +
                kids.mapIndexed { index, kid -> kid.copy(nestedUnder = id, lastChild = index == kids.lastIndex, treeParent = false) }
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
        return homeItems(snapshot)
            .groupBy { bucket(it.activityAt, today, zone) }
            .toSortedMap()
            .map { (bucket, chats) -> TimeSection(bucket, nest(chats)) }
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
                ?.let { section.copy(chats = nest(it)) }
        }
    }

    /**
     * Hides the nested chats of [folded] openers. A folded opener stands for its family, so it
     * takes the family's most urgent attention.
     */
    fun fold(chats: List<ChatItem>, folded: Set<String>): List<ChatItem> {
        if (folded.isEmpty()) return chats
        val family = chats.filter { it.nestedUnder in folded }.groupBy { it.nestedUnder }
        return chats.mapNotNull { chat ->
            when {
                chat.nestedUnder in folded -> null
                chat.treeParent && chat.id in folded -> chat.copy(attention = mostUrgent(listOf(chat) + family[chat.id].orEmpty()))
                else -> chat
            }
        }
    }

    private fun mostUrgent(chats: List<ChatItem>): ChatAttention? =
        listOf(ChatAttention.Failed, ChatAttention.Waiting, ChatAttention.Running, ChatAttention.Unread)
            .firstOrNull { wanted -> chats.any { it.attention == wanted } }

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
        prCi = ci(session.pullRequests.lastOrNull()?.ci),
        projectId = session.projectId,
        statusGroup = statusGroup(session),
    )
}
