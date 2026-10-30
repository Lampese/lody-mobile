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
}

struct SampleSection: Identifiable {
    let id = UUID()
    let title: String
    let sessions: [SampleSession]
}

enum SampleData {
    static let sections: [SampleSection] = [
        SampleSection(title: "Running", sessions: [
            SampleSession(title: "同一个 worktree 多个 tabs", subtitle: "lody · Claude Code", meta: "2m", state: .running),
            SampleSession(title: "Migrate mobile tokens to Compose", subtitle: "lody-mobile · Codex", meta: "5m", state: .running),
        ]),
        SampleSection(title: "Needs input", sessions: [
            SampleSession(title: "Review auth middleware changes", subtitle: "Approve 3 file edits", meta: "12m", state: .waiting),
        ]),
        SampleSection(title: "Done", sessions: [
            SampleSession(title: "为 loro swift 添加 debug trait 的支持", subtitle: "loro-swift · PR #42", meta: "1h", state: .done),
            SampleSession(title: "Fix flaky sync test", subtitle: "lody · PR #3935 merged", meta: "3h", state: .merged),
            SampleSession(title: "worktree 环境 setup 配置", subtitle: "Exited with code 1", meta: "4h", state: .failed),
        ]),
        SampleSection(title: "Backlog", sessions: [
            SampleSession(title: "实现 refresh token", subtitle: "lody-ios", meta: "Yesterday", state: .idle),
        ]),
    ]

    static let filters = ["All", "Running", "Needs input", "Done"]

    static let code = """
    import { openSession } from '@lody/sdk'

    const session = await openSession(id)
    await session.send('Run the test suite')
    """

    static let diff: [(kind: DiffKind, text: String)] = [
        (.context, "  func refresh() async throws {"),
        (.removed, "-     let token = try await api.token()"),
        (.added, "+     let token = try await api.refreshToken(current)"),
        (.added, "+     store.save(token)"),
        (.context, "  }"),
    ]
}

enum DiffKind { case context, added, removed }
