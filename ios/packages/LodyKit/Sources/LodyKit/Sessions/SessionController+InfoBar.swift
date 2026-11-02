import UIKit

/// The action bar above the composer (web `SessionInfoBar`), as native glass chips leading the
/// composer's chip row: the computer (icon; its menu says which and whether it is online), the
/// repository context (PR / branch with ±diff; its menu opens the PR page, the workspace changes
/// or copies the branch) and the top repository action as the prominent chip, with the rest in a
/// menu. Prompt actions send web's quick-action prompt as an ordinary turn; Merge (after a native
/// method sheet that doubles as confirmation) and Ready for review write to GitHub directly.
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
    case .readyForReview: "eye"
    case .merge: "arrow.triangle.merge"
    }
  }

  /// `chip` or `chip:menuAction`, from the composer's chip row.
  func infoBarAction(_ id: String) {
    guard let bar = infoBar else { return }
    switch id {
    case "act":
      if let first = bar.actions.first { performInfoBarAction(first) }
    case "context:pr":
      if let pr = bar.pullRequest { openPullRequest(pr) }
    case "context:changes":
      openWorkspaceChanges()
    case "context:branch":
      guard !bar.branch.isEmpty else { return }
      UIPasteboard.general.string = bar.branch
      LodyToastOverlay.shared.show(message: String(localized: "session.title.branchCopied", bundle: .lody), kind: "info")
    default:
      if id.hasPrefix("more:"), let action = SessionInfoBar.Action(rawValue: String(id.dropFirst(5))) { performInfoBarAction(action) }
    }
  }

  private func performInfoBarAction(_ action: SessionInfoBar.Action) {
    switch action {
    case .merge: chooseMergeMethod()
    case .readyForReview: writePullRequest("ready")
    default: sendInfoBarPrompt(action)
    }
  }

  /// Merging cannot be undone, so the method sheet is also the confirmation. The last method
  /// chosen leads (web `usePreferredPrMergeMethod`, default merge commit).
  private func chooseMergeMethod() {
    guard let pr = infoBar?.pullRequest else { return }
    let preferred = UserDefaults.standard.string(forKey: Self.mergeMethodKey) ?? "merge"
    let methods = ["merge", "squash", "rebase"].sorted { a, _ in a == preferred }
    let sheet = UIAlertController(
      title: "\(pr.repository) #\(pr.number)",
      message: String(localized: "bar.merge.message", bundle: .lody), preferredStyle: .actionSheet
    )
    for method in methods {
      sheet.addAction(UIAlertAction(title: String(localized: String.LocalizationValue("bar.merge." + method), bundle: .lody), style: .default) { [weak self] _ in
        UserDefaults.standard.set(method, forKey: Self.mergeMethodKey)
        self?.writePullRequest("merge", method: method)
      })
    }
    sheet.addAction(UIAlertAction(title: String(localized: "common.cancel", bundle: .lody), style: .cancel))
    sheet.popoverPresentationController?.sourceView = chat.composer
    sheet.popoverPresentationController?.sourceRect = chat.composer.bounds
    present(sheet, animated: true)
  }

  private static let mergeMethodKey = "lody.prMergeMethod"

  /// One GitHub write at a time, scoped to the account and workspace that started it. Never
  /// retried: an unknown result says so instead of claiming failure.
  private func writePullRequest(_ operation: String, method: String? = nil) {
    guard !githubWriting, let pr = infoBar?.pullRequest else { return }
    let state = source.state
    githubWriting = true
    var payload: [String: Any] = ["workspaceId": state.workspaceId, "repository": pr.repository, "number": pr.number, "operation": operation]
    if let method { payload["method"] = method }
    let pending = operation == "merge" ? "bar.merge.pending" : "bar.ready.pending"
    LodyToastOverlay.shared.show(message: String(localized: String.LocalizationValue(pending), bundle: .lody), kind: "info")
    Task { @MainActor [weak self] in
      defer { self?.githubWriting = false }
      do {
        _ = try await GitHubPullRequests.run(Self.json(payload))
        guard let self, self.source.state.key == state.key else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        let done = operation == "merge" ? "bar.merge.done" : "bar.ready.done"
        LodyToastOverlay.shared.show(message: String(localized: String.LocalizationValue(done), bundle: .lody), kind: "success")
      } catch {
        guard let self, self.source.state.key == state.key else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.error)
        LodyToastOverlay.shared.show(message: Self.writeError(error, operation: operation), kind: "error")
      }
    }
  }

  private static func writeError(_ error: Error, operation: String) -> String {
    let known = ["not_mergeable", "head_changed", "authorization_required", "merge_unknown", "ready_unknown", "not_open", "forbidden"]
    let code = error.localizedDescription
    let key = known.contains(code) ? "bar.error." + code : (operation == "merge" ? "bar.merge.error" : "bar.ready.error")
    return String(localized: String.LocalizationValue(key), bundle: .lody)
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
