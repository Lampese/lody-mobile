package app.lody.android.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.lody.android.R
import app.lody.android.designsystem.LodyIcons

/** What a chat row's second line shows; iOS counterpart: `ChatRowDetails`. None selected leaves the title line alone. */
enum class ChatRowDetail(val key: String, @StringRes val label: Int) {
    Project("project", R.string.home_view_project),
    PullRequest("pullRequest", R.string.home_view_pull_request),
    Branch("branch", R.string.home_view_branch);

    val icon: ImageVector
        get() = when (this) {
            Project -> LodyIcons.Folder
            PullRequest -> LodyIcons.PullRequest
            Branch -> LodyIcons.Branch
        }
}

/** Display preferences, so they live in plain SharedPreferences (no account data). */
object HomeGroupings {
    private const val FILE = "home"
    private const val KEY = "chatGrouping"

    fun load(context: Context): HomeGrouping {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        return HomeGrouping.entries.firstOrNull { it.key == stored } ?: HomeGrouping.Time
    }

    fun save(context: Context, grouping: HomeGrouping) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, grouping.key).apply()
    }
}

object ChatRowDetails {
    private const val FILE = "home"
    private const val KEY = "chatRowDetails"
    val defaults = setOf(ChatRowDetail.Project, ChatRowDetail.PullRequest)

    fun load(context: Context): Set<ChatRowDetail> {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, null) ?: return defaults
        return ChatRowDetail.entries.filterTo(mutableSetOf()) { it.key in stored }
    }

    fun save(context: Context, details: Set<ChatRowDetail>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, details.mapTo(mutableSetOf()) { it.key }).apply()
    }
}
