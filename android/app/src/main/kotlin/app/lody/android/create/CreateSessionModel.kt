package app.lody.android.create

import app.lody.android.runtime.CatalogProject
import app.lody.android.runtime.CatalogSnapshot

/**
 * Where a new session would run; iOS counterpart: `CreateSessionForm` + `CreateSessionContext`.
 * A local project fixes its computer; a GitHub project or a plain chat may pick any computer.
 */
data class CreateTarget(
    val chat: Boolean = false,
    val projectId: String? = null,
    val machineId: String? = null,
    /** Local projects only: start in a new git worktree. GitHub sessions always do. */
    val worktree: Boolean = false,
)

object CreateSessionModel {
    fun isGithub(projectId: String?) = projectId?.startsWith("github:") == true
    fun isChat(projectId: String) = projectId.endsWith(":unassigned")

    /** Computers by id with their display name (the id when the catalog has none). */
    fun machines(snapshot: CatalogSnapshot): List<Pair<String, String>> =
        snapshot.machineIds.distinct().map { it to (snapshot.machineNames[it] ?: it) }

    /** Projects, most recently active first (matching Home's by-project order). */
    fun projects(snapshot: CatalogSnapshot): List<CatalogProject> {
        val latest = snapshot.sessions.groupBy { it.projectId }.mapValues { (_, sessions) ->
            sessions.maxOf { it.lastMessageAt ?: 0L }
        }
        return snapshot.projects.filterNot { isChat(it.id) }.sortedByDescending { latest[it.id] ?: 0L }
    }

    fun initial(snapshot: CatalogSnapshot, projectId: String? = null): CreateTarget {
        val project = projects(snapshot).firstOrNull { it.id == projectId } ?: projects(snapshot).firstOrNull()
        return if (project == null) CreateTarget(chat = true, machineId = machines(snapshot).firstOrNull()?.first)
        else select(snapshot, CreateTarget(), project.id)
    }

    /** Picks a project, keeping a chosen computer where the project allows any. */
    fun select(snapshot: CatalogSnapshot, target: CreateTarget, projectId: String): CreateTarget {
        val project = snapshot.projects.firstOrNull { it.id == projectId } ?: return target
        val machine = if (isGithub(project.id)) target.machineId ?: machines(snapshot).firstOrNull()?.first else project.machineId
        return CreateTarget(chat = false, projectId = project.id, machineId = machine, worktree = target.worktree && !isGithub(project.id))
    }

    fun chat(snapshot: CatalogSnapshot, target: CreateTarget): CreateTarget =
        CreateTarget(chat = true, machineId = target.machineId ?: machines(snapshot).firstOrNull()?.first)

    /** Every known computer is offered whenever there is more than one (web's machine menu). */
    fun machineChoosable(snapshot: CatalogSnapshot): Boolean = machines(snapshot).size > 1

    /**
     * A local project belongs to one computer: picking another moves to that computer's most
     * recent local project, or to a chat there when it has none. Chats and GitHub projects switch.
     */
    fun pickMachine(snapshot: CatalogSnapshot, target: CreateTarget, machineId: String): CreateTarget {
        val local = !target.chat && target.projectId != null && !isGithub(target.projectId)
        if (!local) return target.copy(machineId = machineId)
        val current = snapshot.projects.firstOrNull { it.id == target.projectId }
        if (current?.machineId == machineId) return target
        val next = projects(snapshot).firstOrNull { it.machineId == machineId && !isGithub(it.id) }
        return if (next != null) select(snapshot, target, next.id) else CreateTarget(chat = true, machineId = machineId)
    }

    const val RECENT_LIMIT = 5

    /**
     * The project menu's recent part: the five most recent projects usable on the chosen
     * computer (its local projects and any GitHub project); a selected project outside them
     * still shows so the check matches the line.
     */
    fun recent(snapshot: CatalogSnapshot, target: CreateTarget): List<CatalogProject> {
        val usable = projects(snapshot).filter { isGithub(it.id) || target.machineId == null || it.machineId == target.machineId }
        val recent = usable.take(RECENT_LIMIT)
        val current = snapshot.projects.firstOrNull { it.id == target.projectId && !target.chat }
        return if (current == null || recent.any { it.id == current.id }) recent else listOf(current) + recent
    }
}
