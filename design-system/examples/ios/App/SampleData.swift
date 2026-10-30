// Keep in sync with android/.../SampleData.kt so both previews show the same screen.

import SwiftUI

enum SessionState: CaseIterable {
    case idle, running, waiting, done, failed, merged

    var icon: LodyIcon {
        switch self {
        case .idle: .stateIdle
        case .running: .stateRunning
        case .waiting: .stateWaiting
        case .done: .stateDone
        case .failed: .stateFailed
        case .merged: .merge
        }
    }

    var color: Color {
        switch self {
        case .idle: LodyColor.stateIdle
        case .running: LodyColor.stateRunning
        case .waiting: LodyColor.stateWaiting
        case .done: LodyColor.stateDone
        case .failed: LodyColor.stateFailed
        case .merged: LodyColor.stateMerged
        }
    }

    var label: String {
        switch self {
        case .idle: "Backlog"
        case .running: "Running"
        case .waiting: "Needs input"
        case .done: "Done"
        case .failed: "Failed"
        case .merged: "Merged"
        }
    }
}

struct SampleSession: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let meta: String
    let state: SessionState
    var label: LabelColor? = nil
}

struct SampleSection: Identifiable {
    let id = UUID()
    let title: String
    let sessions: [SampleSession]
}

enum SampleData {
    static let sections: [SampleSection] = [
        SampleSection(title: "Running", sessions: [
            SampleSession(title: "同一个 worktree 多个 tabs", subtitle: "lody · Claude Code", meta: "2m", state: .running, label: .indigo),
            SampleSession(title: "Migrate mobile tokens to Compose", subtitle: "lody-mobile · Codex", meta: "5m", state: .running, label: .green),
        ]),
        SampleSection(title: "Needs input", sessions: [
            SampleSession(title: "Review auth middleware changes", subtitle: "Approve 3 file edits", meta: "12m", state: .waiting, label: .orange),
        ]),
        SampleSection(title: "Done", sessions: [
            SampleSession(title: "为 loro swift 添加 debug trait 的支持", subtitle: "loro-swift · PR #42", meta: "1h", state: .done, label: .purple),
            SampleSession(title: "Fix flaky sync test", subtitle: "lody · PR #3935 merged", meta: "3h", state: .merged),
            SampleSession(title: "worktree 环境 setup 配置", subtitle: "Exited with code 1", meta: "4h", state: .failed),
        ]),
        SampleSection(title: "Backlog", sessions: [
            SampleSession(title: "实现 refresh token", subtitle: "lody-ios", meta: "Yesterday", state: .idle, label: .blue),
        ]),
    ]

    static let filters = ["All", "Running", "Needs input", "Done"]

    /// Pre-tokenized so the code block shows every syntax color; mirrors the HTML preview.
    static let code: [[(SyntaxKind, String)]] = [
        [(.keyword, "import"), (.punctuation, " { "), (.variable, "openSession"), (.punctuation, " } "), (.keyword, "from"), (.string, " '@lody/sdk'")],
        [],
        [(.comment, "// Retry up to 3 times")],
        [(.keyword, "const"), (.variable, " session"), (.punctuation, " = "), (.keyword, "await"), (.function, " openSession"), (.punctuation, "("), (.variable, "id"), (.punctuation, ", { "), (.property, "retries"), (.punctuation, ": "), (.number, "3"), (.punctuation, ", "), (.property, "verbose"), (.punctuation, ": "), (.constant, "true"), (.punctuation, " })")],
        [(.keyword, "await"), (.variable, " session"), (.punctuation, "."), (.function, "send"), (.punctuation, "("), (.string, "'Run the test suite'"), (.punctuation, ")")],
    ]

    static let diff: [(kind: DiffKind, text: String)] = [
        (.hunk, "@@ -12,4 +12,5 @@ func refresh()"),
        (.context, "  func refresh() async throws {"),
        (.removed, "-     let token = try await api.token()"),
        (.added, "+     let token = try await api.refreshToken(current)"),
        (.added, "+     store.save(token)"),
        (.context, "  }"),
    ]
}

enum DiffKind { case hunk, context, added, removed }

enum SyntaxKind {
    case keyword, string, number, constant, function, type, variable, property, tag, comment, punctuation

    var color: Color {
        switch self {
        case .keyword: LodyColor.syntaxKeyword
        case .string: LodyColor.syntaxString
        case .number: LodyColor.syntaxNumber
        case .constant: LodyColor.syntaxConstant
        case .function: LodyColor.syntaxFunction
        case .type: LodyColor.syntaxType
        case .variable: LodyColor.syntaxVariable
        case .property: LodyColor.syntaxProperty
        case .tag: LodyColor.syntaxTag
        case .comment: LodyColor.syntaxComment
        case .punctuation: LodyColor.syntaxPunctuation
        }
    }
}

enum LabelColor: String, CaseIterable {
    case gray, red, orange, yellow, green, teal, blue, indigo, purple, pink

    var fg: Color {
        switch self {
        case .gray: LodyColor.labelGrayFg
        case .red: LodyColor.labelRedFg
        case .orange: LodyColor.labelOrangeFg
        case .yellow: LodyColor.labelYellowFg
        case .green: LodyColor.labelGreenFg
        case .teal: LodyColor.labelTealFg
        case .blue: LodyColor.labelBlueFg
        case .indigo: LodyColor.labelIndigoFg
        case .purple: LodyColor.labelPurpleFg
        case .pink: LodyColor.labelPinkFg
        }
    }

    var bg: Color {
        switch self {
        case .gray: LodyColor.labelGrayBg
        case .red: LodyColor.labelRedBg
        case .orange: LodyColor.labelOrangeBg
        case .yellow: LodyColor.labelYellowBg
        case .green: LodyColor.labelGreenBg
        case .teal: LodyColor.labelTealBg
        case .blue: LodyColor.labelBlueBg
        case .indigo: LodyColor.labelIndigoBg
        case .purple: LodyColor.labelPurpleBg
        case .pink: LodyColor.labelPinkBg
        }
    }
}
