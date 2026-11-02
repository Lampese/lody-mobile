import UIKit

/// The action bar above the composer (web `SessionInfoBar`), as native glass chips leading the
/// composer's chip row: the computer (icon; its menu says which and whether it is online), the
/// repository context (PR / branch with ±diff; its menu opens the PR page, the workspace changes
/// or copies the branch) and the top repository action as the prominent chip, with the rest in a
/// menu. An action sends web's quick-action prompt as an ordinary turn.
extension SessionController {
  func infoBarChips(_ session: SessionItem, busy: Bool) -> [[String: Any]] {
    let state = source.state
    let project = state.catalog.projects.first { $0.id == session.projectId }
    let repository = project?.repoFullName ?? (session.projectId.hasPrefix("github:") ? String(session.projectId.dropFirst(7)) : "")
    let bar = SessionInfoBar(session: session, repository: repository, busy: busy)
    infoBar = bar
    var chips: [[String: Any]] = []
    if let machine = machineChip(session) { chips.append(machine) }
    if bar.hasContext { chips.append(contextChip(bar)) }
    if let first = bar.actions.first {
      chips.append([
        "id": "act", "label": SessionInfoBar.title(first), "symbol": Self.symbol(first), "prominent": true, "tap": true,
        "accessibilityLabel": SessionInfoBar.title(first),
      ])
      let rest = bar.actions.dropFirst()
      if !rest.isEmpty {
        chips.append([
          "id": "more", "symbol": "ellipsis", "tint": "label",
          "accessibilityLabel": String(localized: "bar.more", bundle: .lody),
          "actions": rest.map { ["id": $0.rawValue, "title": SessionInfoBar.title($0), "symbol": Self.symbol($0)] },
        ])
      }
    }
    return chips
  }

  private func machineChip(_ session: SessionItem) -> [String: Any]? {
    let state = source.state
    guard let name = state.catalog.machineNames[session.machineId], !name.isEmpty else { return nil }
    let status = state.presence.status(of: session.machineId)
    let symbols = ["win32": "pc", "linux": "server.rack"]
    let statusText = String(localized: String.LocalizationValue("devices." + status), bundle: .lody)
    return [
      "id": "machine", "symbol": symbols[state.catalog.machineOs[session.machineId] ?? ""] ?? "desktopcomputer",
      // Colour only for a confirmed problem; unknown presence stays neutral.
      "tint": status == "offline" ? "orange" : "secondary",
      "accessibilityLabel": "\(name), \(statusText)",
      "menuTitle": name,
      "actions": [["id": "status", "title": statusText, "symbol": status == "online" ? "checkmark.circle" : "circle.dashed", "disabled": true]],
    ]
  }

  private func contextChip(_ bar: SessionInfoBar) -> [String: Any] {
    let diff = bar.added + bar.removed > 0 ? "+\(bar.added) −\(bar.removed)" : ""
    var label = bar.branch.isEmpty ? bar.repository : bar.branch
    var symbol = "arrow.triangle.branch"
    var tint = "secondary"
    var actions: [[String: Any]] = []
    if let pr = bar.pullRequest {
      label = "#\(pr.number)"
      symbol = pr.status == "merged" ? "arrow.triangle.merge" : "arrow.triangle.pull"
      tint = ["open": "green", "merged": "purple", "closed": "red"][pr.status] ?? "secondary"
      let state = String(localized: String.LocalizationValue("pr.state." + pr.status), bundle: .lody)
      var subtitle = state
      if ["open", "draft"].contains(pr.status), let ci = pr.ci, ["s", "f", "e", "p"].contains(ci) {
        let verdict = String(localized: String.LocalizationValue("bar.ci." + ci), bundle: .lody)
        subtitle += " · " + verdict
        if ci != "s" { label += " · " + verdict }
      }
      if pr.merge == "d" { subtitle += " · " + String(localized: "bar.conflicts", bundle: .lody) }
      actions.append(["id": "pr", "title": "\(pr.repository) #\(pr.number)", "subtitle": subtitle, "symbol": symbol])
    }
    if !diff.isEmpty {
      label += " · " + diff
      if reviewable {
        actions.append(["id": "changes", "title": String(localized: "workspaceChanges.title", bundle: .lody), "subtitle": diff, "symbol": "plus.forwardslash.minus"])
      }
    }
    if !bar.branch.isEmpty {
      actions.append(["id": "branch", "title": String(localized: "session.title.copyBranch", bundle: .lody), "subtitle": bar.branch, "symbol": "doc.on.doc"])
    }
    return [
      "id": "context", "label": label, "symbol": symbol, "tint": tint,
      "accessibilityLabel": [bar.repository, bar.branch, label].filter { !$0.isEmpty }.joined(separator: ", "),
      "menuTitle": [bar.repository, bar.branch].filter { !$0.isEmpty }.joined(separator: " · "),
      "actions": actions,
    ]
  }

  private static func symbol(_ action: SessionInfoBar.Action) -> String {
    switch action {
    case .createPr: "arrow.triangle.pull"
    case .createDraftPr: "pencil.and.outline"
    case .commitAndPush: "arrow.up.circle"
    case .resolveConflicts: "arrow.triangle.merge"
    case .fixCi: "wrench.and.screwdriver"
    }
  }

  /// `chip` or `chip:menuAction`, from the composer's chip row.
  func infoBarAction(_ id: String) {
    guard let bar = infoBar else { return }
    switch id {
    case "act":
      if let first = bar.actions.first { sendInfoBarPrompt(first) }
    case "context:pr":
      if let pr = bar.pullRequest { openPullRequest(pr) }
    case "context:changes":
      openWorkspaceChanges()
    case "context:branch":
      guard !bar.branch.isEmpty else { return }
      UIPasteboard.general.string = bar.branch
      LodyToastOverlay.shared.show(message: String(localized: "session.title.branchCopied", bundle: .lody), kind: "info")
    default:
      if id.hasPrefix("more:"), let action = SessionInfoBar.Action(rawValue: String(id.dropFirst(5))) { sendInfoBarPrompt(action) }
    }
  }

  /// Sent like a typed turn (pending bubble, outbox, no automatic replay); only while the action
  /// is still offered, so a stale menu cannot send for a state that moved on.
  private func sendInfoBarPrompt(_ action: SessionInfoBar.Action) {
    guard let bar = infoBar, bar.actions.contains(action) else { return }
    let text = bar.prompt(action)
    guard !text.isEmpty else { return }
    chat.composer.onSend?([
      "id": UUID().uuidString.lowercased(), "queue": false, "guide": false, "text": text,
      "startedAt": Date().timeIntervalSince1970 * 1000, "attachments": [] as [[String: Any]],
    ])
  }
}
