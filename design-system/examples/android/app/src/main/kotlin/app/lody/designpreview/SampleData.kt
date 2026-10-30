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

data class SampleChat(
    val title: String,
    val state: SessionState = SessionState.Idle,
    val unread: Boolean = false,
)

data class SampleGroup(val id: String, val name: String, val kind: Kind, val chats: List<SampleChat>) {
    enum class Kind { Chats, Project }

    val icon: ImageVector
        get() = if (kind == Kind.Chats) LodyIcons.Session else LodyIcons.Folder
}

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
    const val workspaceName = "Kurage"
    const val organization = "Lody"
    const val workspaceInitial = "L"

    /** Groups beyond this many chats show a "Show N more" row. */
    const val visibleChatLimit = 6

    val initiallyCollapsed = setOf("lody", "loro-streams")

    val groups = listOf(
        SampleGroup("chats", "Chats", SampleGroup.Kind.Chats, listOf(SampleChat("Untitled session"))),
        SampleGroup(
            "foliant",
            "foliant",
            SampleGroup.Kind.Project,
            listOf(
                SampleChat("为 foliant serve 增加服务端密钥保护新 Vault 流程", SessionState.Running),
                SampleChat("Keyhive 用于 foliant 协作成员管理", SessionState.Waiting),
                SampleChat("Build and verify iOS redesign", unread = true),
                SampleChat("桌面端同步管理与历史恢复界面"),
                SampleChat("Foliant 首次使用与同步流程体验分析"),
                SampleChat("Foliant 高频使用场景端到端体验审查", SessionState.Failed),
                SampleChat("实现 Foliant 恢复包与全量恢复流程"),
                SampleChat("协作分享与成员权限体验审计"),
            ),
        ),
        SampleGroup(
            "lody",
            "lody",
            SampleGroup.Kind.Project,
            listOf(
                SampleChat("Mobile design tokens", SessionState.Running),
                SampleChat("同一个 worktree 多个 tabs"),
                SampleChat("Fix flaky sync test"),
                SampleChat("实现 refresh token"),
            ),
        ),
        SampleGroup(
            "loro-streams",
            "loro-streams",
            SampleGroup.Kind.Project,
            listOf(SampleChat("Bounded persistence retry"), SampleChat("Live-read spin after failed flush")),
        ),
    )

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
