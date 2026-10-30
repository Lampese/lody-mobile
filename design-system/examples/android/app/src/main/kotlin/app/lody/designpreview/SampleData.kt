// Keep in sync with ios/App/SampleData.swift so both previews show the same screen.

package app.lody.designpreview

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyTheme

enum class SessionState(val label: String) {
    Idle("Backlog"),
    Running("Running"),
    Waiting("Needs input"),
    Done("Done"),
    Failed("Failed"),
    Merged("Merged");

    val icon: ImageVector
        get() = when (this) {
            Idle -> LodyIcons.StateIdle
            Running -> LodyIcons.StateRunning
            Waiting -> LodyIcons.StateWaiting
            Done -> LodyIcons.StateDone
            Failed -> LodyIcons.StateFailed
            Merged -> LodyIcons.Merge
        }

    val color: Color
        @Composable get() = with(LodyTheme.colors) {
            when (this@SessionState) {
                Idle -> stateIdle
                Running -> stateRunning
                Waiting -> stateWaiting
                Done -> stateDone
                Failed -> stateFailed
                Merged -> stateMerged
            }
        }
}

data class SampleSession(val title: String, val subtitle: String, val meta: String, val state: SessionState)

data class SampleSection(val title: String, val sessions: List<SampleSession>)

enum class DiffKind { Context, Added, Removed }

object SampleData {
    val sections = listOf(
        SampleSection(
            "Running",
            listOf(
                SampleSession("同一个 worktree 多个 tabs", "lody · Claude Code", "2m", SessionState.Running),
                SampleSession("Migrate mobile tokens to Compose", "lody-mobile · Codex", "5m", SessionState.Running),
            ),
        ),
        SampleSection(
            "Needs input",
            listOf(SampleSession("Review auth middleware changes", "Approve 3 file edits", "12m", SessionState.Waiting)),
        ),
        SampleSection(
            "Done",
            listOf(
                SampleSession("为 loro swift 添加 debug trait 的支持", "loro-swift · PR #42", "1h", SessionState.Done),
                SampleSession("Fix flaky sync test", "lody · PR #3935 merged", "3h", SessionState.Merged),
                SampleSession("worktree 环境 setup 配置", "Exited with code 1", "4h", SessionState.Failed),
            ),
        ),
        SampleSection(
            "Backlog",
            listOf(SampleSession("实现 refresh token", "lody-ios", "Yesterday", SessionState.Idle)),
        ),
    )

    val filters = listOf("All", "Running", "Needs input", "Done")

    val code = """
        import { openSession } from '@lody/sdk'

        const session = await openSession(id)
        await session.send('Run the test suite')
    """.trimIndent()

    val diff = listOf(
        DiffKind.Context to "  func refresh() async throws {",
        DiffKind.Removed to "-     let token = try await api.token()",
        DiffKind.Added to "+     let token = try await api.refreshToken(current)",
        DiffKind.Added to "+     store.save(token)",
        DiffKind.Context to "  }",
    )
}
