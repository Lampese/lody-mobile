package app.lody.android.chat

import app.lody.android.runtime.CatalogPullRequest
import app.lody.android.runtime.CatalogSession

/**
 * Web `SessionInfoBar` (the action bar above the composer), reduced to what the catalog carries:
 * the repository context (PR, CI, branch, ±diff) and the repository actions, ranked like
 * `resolveSessionInfoBarGitHubActionIds`. Goal, schedule and merge stay on web: the catalog has
 * no goal or schedule, and merging needs a GitHub write grant. iOS: `SessionInfoBar`.
 */
data class SessionInfoBar(
    val repository: String,
    val branch: String,
    val pullRequest: CatalogPullRequest?,
    val added: Int,
    val removed: Int,
    /** Priority order: the first is the bar's one text button, the rest hang off its menu. */
    val actions: List<Action>,
) {
    enum class Action { CreatePr, CreateDraftPr, CommitAndPush, ResolveConflicts, FixCi }

    val hasContext: Boolean get() = repository.isNotEmpty() || branch.isNotEmpty() || added + removed > 0 || pullRequest != null

    val pullRequestUrl: String
        get() = pullRequest?.let { it.url ?: "https://github.com/${it.repository}/pull/${it.number}" }.orEmpty()

    /** Localized templates, resolved by the screen (web's quick-action prompts). */
    data class Prompts(
        val createPrOrigin: String,
        val createDraftPrOrigin: String,
        val createPr: String,
        val createDraftPr: String,
        val upkeep: String,
        val commitAndPush: String,
        /** `%1$s` repository, `%2$s` PR label, `%3$s` URL. */
        val resolveConflicts: String,
        /** `%1$s` repository, `%2$d` number, `%3$s` URL, `%4$s` head branch. */
        val fixCi: String,
    )

    /** The turn an action sends; empty when the action needs a PR that is gone. */
    fun prompt(action: Action, prompts: Prompts): String = when (action) {
        Action.CreatePr -> "${prompts.createPrOrigin}\n\n${prompts.createPr}\n\n${prompts.upkeep}"
        Action.CreateDraftPr -> "${prompts.createDraftPrOrigin}\n\n${prompts.createDraftPr}\n\n${prompts.upkeep}"
        Action.CommitAndPush -> prompts.commitAndPush
        Action.ResolveConflicts -> pullRequest?.let { prompts.resolveConflicts.format(repository, "#${it.number}", pullRequestUrl) }.orEmpty()
        Action.FixCi -> pullRequest?.let { prompts.fixCi.format(repository, it.number, pullRequestUrl, branch.ifEmpty { "-" }) }.orEmpty()
    }

    companion object {
        fun of(session: CatalogSession, repository: String, busy: Boolean): SessionInfoBar {
            // The machine appends associations, so the last is the current PR (web `getLatestPullRequest`).
            val pr = session.pullRequests.lastOrNull().takeIf { repository.isNotEmpty() }
            return SessionInfoBar(
                repository, session.branchName.orEmpty(), pr, session.diffAdded, session.diffRemoved,
                actions(session, pr, repository, busy),
            )
        }

        fun actions(session: CatalogSession, pr: CatalogPullRequest?, repository: String, busy: Boolean): List<Action> {
            if (repository.isEmpty() || busy) return emptyList()
            val dirty = session.workspaceDirty
            if (pr == null) {
                // Create PR needs anything to base a PR on, committed or not; Commit & Push needs uncommitted work.
                val changed = dirty || session.diffAdded + session.diffRemoved > 0
                return (if (changed) listOf(Action.CreatePr, Action.CreateDraftPr) else emptyList()) +
                    (if (dirty) listOf(Action.CommitAndPush) else emptyList())
            }
            if (pr.status == "merged" || pr.status == "closed") return emptyList()
            // Unpublished work outranks everything: the PR head is not the author's latest work.
            val actions = mutableListOf<Action>()
            if (dirty || session.workspaceUnpushed) actions += Action.CommitAndPush
            if (pr.status == "draft") return actions
            if (pr.merge == "d") actions += Action.ResolveConflicts
            if (pr.ci == "f" || pr.ci == "e") actions += Action.FixCi
            return actions
        }
    }
}
