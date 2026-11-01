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

    /** The computer is a choice only for chats and GitHub projects with more than one computer. */
    fun machineChoosable(snapshot: CatalogSnapshot, target: CreateTarget): Boolean =
        (target.chat || isGithub(target.projectId)) && machines(snapshot).size > 1
}
