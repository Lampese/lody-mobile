package app.lody.android.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodyTheme
import app.lody.android.home.ChatAttention
import app.lody.android.home.ChatRowDetail
import app.lody.android.home.ChatItem
import app.lody.android.home.HomeList
import app.lody.android.home.HomeSection

/** Offline production-list fixture; does not touch login, workspace, or preferences. */
class StickyGroupsProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listReview = intent.getBooleanExtra("list-review", false)
        val sections = if (listReview) listOf(HomeSection("list-review", HomeSection.Header.Bucket(app.lody.android.home.TimeBucket.Today), listOf(
            ChatItem("list-review-unread", "Lody E2E improvements", ChatAttention.Unread, place = "Lody", prStatus = "merged", activityAt = System.currentTimeMillis() - 3_600_000),
            ChatItem("list-review-parent", "Local-first attachments", ChatAttention.Running, place = "Lody", prStatus = "open", prCi = "s", activityAt = System.currentTimeMillis() - 3_600_000, treeParent = true, childCount = 3, childAttention = ChatAttention.Waiting),
            ChatItem("list-review-child-idle", "Review attachments PR", null, prStatus = "merged", activityAt = System.currentTimeMillis() - 3_600_000, nestedUnder = "list-review-parent"),
            ChatItem("list-review-child-waiting", "Confirm storage changes", ChatAttention.Waiting, place = "Loro", prStatus = "open", prCi = "s", activityAt = System.currentTimeMillis() - 3_600_000, nestedUnder = "list-review-parent"),
            ChatItem("list-review-child-failed", "Retry integration tests", ChatAttention.Failed, activityAt = System.currentTimeMillis() - 3_600_000, nestedUnder = "list-review-parent", lastChild = true),
            ChatItem("list-review-idle", "Native session synchronization", null, place = "Lody", prStatus = "open", prCi = "s", activityAt = System.currentTimeMillis() - 3_600_000),
            ChatItem("list-review-plain", "A conversation without extra details", null, activityAt = System.currentTimeMillis() - 3_600_000),
        ))) else listOf("Alpha project", "Beta project", "Gamma project").mapIndexed { group, name ->
            HomeSection("group-$group", HomeSection.Header.Project(name), (1..18).map { row ->
                ChatItem("$group-$row", "Conversation ${group + 1}.$row", null)
            })
        }
        setContent {
            LodyTheme {
                Surface(color = LodyTheme.colors.bgBase) {
                    HomeList(sections, if (listReview) setOf(ChatRowDetail.Project, ChatRowDetail.PullRequest) else emptySet(), PaddingValues(top = LodySize.controlLg), empty = null,
                        pinsLabels = !intent.getBooleanExtra("search", false), onOpenChat = {})
                }
            }
        }
    }
}
