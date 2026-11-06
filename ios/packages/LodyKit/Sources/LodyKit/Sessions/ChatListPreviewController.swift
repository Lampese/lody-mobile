import UIKit

/// Offline fixture for the production list, including the family's real fold action.
@MainActor
final class ChatListPreviewController: UIViewController {
  override func loadView() {
    let list = ChatListView(frame: .zero)
    view = list
    func row(_ id: String, _ title: String, tint: String = "", unread: Bool = false, project: String = "", pr: String = "", parent: String = "", last: Bool = false) -> LodyListRow {
      var row = LodyListRow()
      row.id = id; row.title = title; row.imageTint = tint; row.unread = unread
      row.place = project; row.prStatus = pr; row.prCI = "s"; row.value = "1h"
      row.parentId = parent; row.lastChild = last
      return row
    }
    var parent = row("list-review-parent", "Local-first attachments", tint: "blue", project: "Lody", pr: "open")
    parent.treeParent = true; parent.childCount = 3
    parent.collapsedImageTint = "warning"
    let rows = [
      row("list-review-unread", "Lody E2E improvements", unread: true, project: "Lody", pr: "merged"),
      parent,
      row("list-review-child-idle", "Review attachments PR", pr: "merged", parent: parent.id),
      row("list-review-child-waiting", "Confirm storage changes", tint: "warning", project: "Loro", pr: "open", parent: parent.id),
      row("list-review-child-failed", "Retry integration tests", tint: "danger", parent: parent.id, last: true),
      row("list-review-idle", "Native session synchronization", project: "Lody", pr: "open"),
      row("list-review-plain", "A conversation without extra details"),
    ]
    list.setSections([LodyListSection(id: "list-review", header: "Conversations", rows: rows)])
  }
}
