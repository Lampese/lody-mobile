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

data class SampleSession(
    val title: String,
    val subtitle: String,
    val meta: String,
    val state: SessionState,
    val label: LabelColor? = null,
)

data class SampleSection(val title: String, val sessions: List<SampleSession>)

enum class DiffKind { Hunk, Context, Added, Removed }

enum class SyntaxKind {
    Keyword, StringLit, NumberLit, Constant, FunctionName, TypeName, Variable, Property, Tag, Comment, Punctuation;

    val color: Color
        @Composable get() = with(LodyTheme.colors) {
            when (this@SyntaxKind) {
                Keyword -> syntaxKeyword
                StringLit -> syntaxString
                NumberLit -> syntaxNumber
                Constant -> syntaxConstant
                FunctionName -> syntaxFunction
                TypeName -> syntaxType
                Variable -> syntaxVariable
                Property -> syntaxProperty
                Tag -> syntaxTag
                Comment -> syntaxComment
                Punctuation -> syntaxPunctuation
            }
        }
}

enum class LabelColor {
    Gray, Red, Orange, Yellow, Green, Teal, Blue, Indigo, Purple, Pink;

    val fg: Color
        @Composable get() = with(LodyTheme.colors) {
            when (this@LabelColor) {
                Gray -> labelGrayFg
                Red -> labelRedFg
                Orange -> labelOrangeFg
                Yellow -> labelYellowFg
                Green -> labelGreenFg
                Teal -> labelTealFg
                Blue -> labelBlueFg
                Indigo -> labelIndigoFg
                Purple -> labelPurpleFg
                Pink -> labelPinkFg
            }
        }

    val bg: Color
        @Composable get() = with(LodyTheme.colors) {
            when (this@LabelColor) {
                Gray -> labelGrayBg
                Red -> labelRedBg
                Orange -> labelOrangeBg
                Yellow -> labelYellowBg
                Green -> labelGreenBg
                Teal -> labelTealBg
                Blue -> labelBlueBg
                Indigo -> labelIndigoBg
                Purple -> labelPurpleBg
                Pink -> labelPinkBg
            }
        }
}

object SampleData {
    val sections = listOf(
        SampleSection(
            "Running",
            listOf(
                SampleSession("同一个 worktree 多个 tabs", "lody · Claude Code", "2m", SessionState.Running, LabelColor.Indigo),
                SampleSession("Migrate mobile tokens to Compose", "lody-mobile · Codex", "5m", SessionState.Running, LabelColor.Green),
            ),
        ),
        SampleSection(
            "Needs input",
            listOf(SampleSession("Review auth middleware changes", "Approve 3 file edits", "12m", SessionState.Waiting, LabelColor.Orange)),
        ),
        SampleSection(
            "Done",
            listOf(
                SampleSession("为 loro swift 添加 debug trait 的支持", "loro-swift · PR #42", "1h", SessionState.Done, LabelColor.Purple),
                SampleSession("Fix flaky sync test", "lody · PR #3935 merged", "3h", SessionState.Merged),
                SampleSession("worktree 环境 setup 配置", "Exited with code 1", "4h", SessionState.Failed),
            ),
        ),
        SampleSection(
            "Backlog",
            listOf(SampleSession("实现 refresh token", "lody-ios", "Yesterday", SessionState.Idle, LabelColor.Blue)),
        ),
    )

    val filters = listOf("All", "Running", "Needs input", "Done")

    /** Pre-tokenized so the code block shows every syntax color; mirrors the HTML preview. */
    val code: List<List<Pair<SyntaxKind, String>>> = listOf(
        listOf(SyntaxKind.Keyword to "import", SyntaxKind.Punctuation to " { ", SyntaxKind.Variable to "openSession", SyntaxKind.Punctuation to " } ", SyntaxKind.Keyword to "from", SyntaxKind.StringLit to " '@lody/sdk'"),
        emptyList(),
        listOf(SyntaxKind.Comment to "// Retry up to 3 times"),
        listOf(
            SyntaxKind.Keyword to "const", SyntaxKind.Variable to " session", SyntaxKind.Punctuation to " = ",
            SyntaxKind.Keyword to "await", SyntaxKind.FunctionName to " openSession", SyntaxKind.Punctuation to "(",
            SyntaxKind.Variable to "id", SyntaxKind.Punctuation to ", { ", SyntaxKind.Property to "retries",
            SyntaxKind.Punctuation to ": ", SyntaxKind.NumberLit to "3", SyntaxKind.Punctuation to ", ",
            SyntaxKind.Property to "verbose", SyntaxKind.Punctuation to ": ", SyntaxKind.Constant to "true",
            SyntaxKind.Punctuation to " })",
        ),
        listOf(SyntaxKind.Keyword to "await", SyntaxKind.Variable to " session", SyntaxKind.Punctuation to ".", SyntaxKind.FunctionName to "send", SyntaxKind.Punctuation to "(", SyntaxKind.StringLit to "'Run the test suite'", SyntaxKind.Punctuation to ")"),
    )

    val diff = listOf(
        DiffKind.Hunk to "@@ -12,4 +12,5 @@ func refresh()",
        DiffKind.Context to "  func refresh() async throws {",
        DiffKind.Removed to "-     let token = try await api.token()",
        DiffKind.Added to "+     let token = try await api.refreshToken(current)",
        DiffKind.Added to "+     store.save(token)",
        DiffKind.Context to "  }",
    )
}
