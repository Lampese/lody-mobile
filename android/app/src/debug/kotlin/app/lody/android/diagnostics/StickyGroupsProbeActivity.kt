package app.lody.android.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodyTheme
import app.lody.android.home.ChatItem
import app.lody.android.home.HomeList
import app.lody.android.home.HomeSection

/** Offline production-list fixture; does not touch login, workspace, or preferences. */
class StickyGroupsProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sections = listOf("Alpha project", "Beta project", "Gamma project").mapIndexed { group, name ->
            HomeSection("group-$group", HomeSection.Header.Project(name), (1..18).map { row ->
                ChatItem("$group-$row", "Conversation ${group + 1}.$row", null)
            })
        }
        setContent {
            LodyTheme {
                Surface(color = LodyTheme.colors.bgBase) {
                    HomeList(sections, emptySet(), PaddingValues(top = LodySize.controlLg), empty = null,
                        pinsLabels = !intent.getBooleanExtra("search", false), onOpenChat = {})
                }
            }
        }
    }
}
