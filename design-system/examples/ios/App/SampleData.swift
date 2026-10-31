// Keep in sync with android/.../SampleData.kt so both previews show the same screen.

import LodyDesignSystem
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

struct SampleChat: Identifiable {
    let id = UUID()
    let title: String
    var state: SessionState = .idle
    var unread = false
}

struct SampleGroup: Identifiable {
    enum Kind { case chats, project }

    let id: String
    let name: String
    let kind: Kind
    let chats: [SampleChat]

    var icon: LodyIcon { kind == .chats ? .session : .folder }
}

enum SampleData {
    static let workspace = (name: "Kurage", organization: "Lody", initial: "L")

    /// Groups beyond this many chats show a "Show N more" row.
    static let visibleChatLimit = 6

    static let initiallyCollapsed: Set<String> = ["lody", "loro-streams"]

    static let groups: [SampleGroup] = [
        SampleGroup(id: "chats", name: "Chats", kind: .chats, chats: [
            SampleChat(title: "Untitled session"),
        ]),
        SampleGroup(id: "foliant", name: "foliant", kind: .project, chats: [
            SampleChat(title: "为 foliant serve 增加服务端密钥保护新 Vault 流程", state: .running),
            SampleChat(title: "Keyhive 用于 foliant 协作成员管理", state: .waiting),
            SampleChat(title: "Build and verify iOS redesign", unread: true),
            SampleChat(title: "桌面端同步管理与历史恢复界面"),
            SampleChat(title: "Foliant 首次使用与同步流程体验分析"),
            SampleChat(title: "Foliant 高频使用场景端到端体验审查", state: .failed),
            SampleChat(title: "实现 Foliant 恢复包与全量恢复流程"),
            SampleChat(title: "协作分享与成员权限体验审计"),
        ]),
        SampleGroup(id: "lody", name: "lody", kind: .project, chats: [
            SampleChat(title: "Mobile design tokens", state: .running),
            SampleChat(title: "同一个 worktree 多个 tabs"),
            SampleChat(title: "Fix flaky sync test"),
            SampleChat(title: "实现 refresh token"),
        ]),
        SampleGroup(id: "loro-streams", name: "loro-streams", kind: .project, chats: [
            SampleChat(title: "Bounded persistence retry"),
            SampleChat(title: "Live-read spin after failed flush"),
        ]),
    ]

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
