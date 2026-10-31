import LodyDesignSystem
import SwiftUI
import UIKit

// The iPhone Home list in the mobile design language. Reference design:
// design-system/examples/ios/App/SessionListScreen.swift.
//
// It renders the same `InboxSections` projection as the other list hosts and emits
// the same `rowPress` / `rowAction` events, so `InboxController` keeps owning state,
// preferences and actions. Rows are one line; the indent gutter shows a glyph only
// when a chat needs attention; collapsed groups show their count and most urgent
// state. Cells are UIKit list cells (selection, swipe actions, context menus) whose
// content is SwiftUI on design-system tokens.
@MainActor
final class ChatListView: UIView, UICollectionViewDelegate {
  private enum Kind { case header, link, session, more }

  private struct Item: Hashable {
    let section: String
    let row: String
  }

  private struct Entry {
    let kind: Kind
    let row: LodyListRow
    /// nil when the group cannot collapse.
    let expanded: Bool?
    let canToggle: Bool
    /// Rows outside a group header (time buckets, search) name their project, since nothing above them does.
    var showsPlace = false
  }

  var forwarded: ((String, [String: Any]) -> Void)?
  private(set) var collection: UICollectionView!
  private var dataSource: UICollectionViewDiffableDataSource<String, Item>!
  private var entries: [Item: Entry] = [:]
  private var titles: [String: String] = [:]
  private let placeholder = UILabel()
  private var placeholderText = ""

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = LodyUIColor.bgBase
    var config = UICollectionLayoutListConfiguration(appearance: .plain)
    config.showsSeparators = false
    config.backgroundColor = .clear
    config.headerMode = .supplementary
    config.headerTopPadding = 0
    config.trailingSwipeActionsConfigurationProvider = { [weak self] path in self?.swipe(path, leading: false) }
    config.leadingSwipeActionsConfigurationProvider = { [weak self] path in self?.swipe(path, leading: true) }
    collection = UICollectionView(frame: bounds, collectionViewLayout: UICollectionViewCompositionalLayout.list(using: config))
    collection.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    collection.backgroundColor = .clear
    collection.delegate = self
    collection.accessibilityIdentifier = "home-chat-list"
    addSubview(collection)

    let cell = UICollectionView.CellRegistration<UICollectionViewListCell, Item> { [weak self] cell, _, item in
      self?.configure(cell, item)
    }
    let header = UICollectionView.SupplementaryRegistration<UICollectionViewListCell>(
      elementKind: UICollectionView.elementKindSectionHeader
    ) { [weak self] view, _, path in
      let title = self?.dataSource.sectionIdentifier(for: path.section).flatMap { self?.titles[$0] } ?? ""
      view.contentConfiguration = UIHostingConfiguration {
        SectionTitle(text: title)
      }
      .margins(.all, 0)
      view.backgroundConfiguration = .clear()
    }
    dataSource = UICollectionViewDiffableDataSource(collectionView: collection) { view, path, item in
      view.dequeueConfiguredReusableCell(using: cell, for: path, item: item)
    }
    dataSource.supplementaryViewProvider = { view, _, path in
      view.dequeueConfiguredReusableSupplementary(using: header, for: path)
    }

    placeholder.font = LodyTextStyle.body.uiFont
    placeholder.textColor = LodyUIColor.textSecondary
    placeholder.textAlignment = .center
    placeholder.numberOfLines = 0
    placeholder.adjustsFontForContentSizeCategory = true
    placeholder.isHidden = true
    addSubview(placeholder)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  override func layoutSubviews() {
    super.layoutSubviews()
    collection.frame = bounds
    let inset = LodyLayout.screenPaddingX * 2
    placeholder.frame = CGRect(x: inset, y: bounds.midY - 60, width: max(0, bounds.width - inset * 2), height: 120)
  }

  func setPlaceholder(_ value: String) {
    placeholderText = value
    updatePlaceholder()
  }

  func setSections(_ sections: [LodyListSection]) {
    var snapshot = NSDiffableDataSourceSnapshot<String, Item>()
    var next: [Item: Entry] = [:]
    var nextTitles: [String: String] = [:]
    for section in sections where !section.rows.isEmpty {
      snapshot.appendSections([section.id])
      // Only flat sections (inbox buckets, search) carry a text header; groups use their parent row.
      nextTitles[section.id] = section.rows.first?.parent == true ? "" : section.header
      let children = section.rows.contains { !$0.parent }
      for row in section.rows {
        let item = Item(section: section.id, row: row.id)
        let entry: Entry
        if row.parent {
          entry = Entry(kind: .header, row: row, expanded: section.headerExpanded, canToggle: section.headerExpanded != nil && children)
        } else if section.headerExpanded == false {
          continue
        } else if row.image == "folder" {
          entry = Entry(kind: .link, row: row, expanded: nil, canToggle: false)
        } else if row.id.hasPrefix("project:") || row.id.hasPrefix("view:") {
          entry = Entry(kind: .more, row: row, expanded: nil, canToggle: false)
        } else {
          entry = Entry(kind: .session, row: row, expanded: nil, canToggle: false, showsPlace: section.rows.first?.parent != true)
        }
        guard next[item] == nil else { continue }
        next[item] = entry
        snapshot.appendItems([item], toSection: section.id)
      }
    }
    let changed = Set(next.keys).filter { key in entries[key].map { !Self.same($0.row, next[key]!.row, $0.expanded, next[key]!.expanded) } ?? false }
    entries = next
    titles = nextTitles
    snapshot.reconfigureItems(Array(changed))
    dataSource.apply(snapshot, animatingDifferences: window != nil && !UIAccessibility.isReduceMotionEnabled)
    updatePlaceholder()
  }

  private static func same(_ a: LodyListRow, _ b: LodyListRow, _ ea: Bool?, _ eb: Bool?) -> Bool {
    a.title == b.title && a.subtitle == b.subtitle && a.unread == b.unread && a.imageTint == b.imageTint
      && a.badge == b.badge && a.pinned == b.pinned && a.parentId == b.parentId && a.highlight == b.highlight && ea == eb
  }

  private func updatePlaceholder() {
    placeholder.text = placeholderText
    placeholder.isHidden = !entries.isEmpty || placeholderText.isEmpty
  }

  // MARK: Cells

  private func configure(_ cell: UICollectionViewListCell, _ item: Item) {
    guard let entry = entries[item] else { return }
    let emit: (String) -> Void = { [weak self] action in
      self?.forwarded?("rowAction", ["id": entry.row.id, "actionId": action])
    }
    cell.contentConfiguration = UIHostingConfiguration {
      switch entry.kind {
      case .header: GroupHeader(row: entry.row, expanded: entry.expanded, canToggle: entry.canToggle, onAction: emit)
      case .link: GroupHeader(row: entry.row, expanded: nil, canToggle: false, onAction: emit)
      case .session: ChatRowContent(row: entry.row, showsPlace: entry.showsPlace)
      case .more: MoreRowContent(title: entry.row.title)
      }
    }
    .margins(.all, 0)
    .minSize(height: LodySize.listRow)
    cell.accessories = []
    // iOS highlights rows on press; Android uses ripple.
    cell.configurationUpdateHandler = { cell, state in
      var background = UIBackgroundConfiguration.clear()
      background.backgroundColor = state.isHighlighted || state.isSelected ? LodyUIColor.fillPressed : .clear
      cell.backgroundConfiguration = background
    }
  }

  // MARK: Interaction

  func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
    collectionView.deselectItem(at: indexPath, animated: true)
    guard let item = dataSource.itemIdentifier(for: indexPath), let entry = entries[item] else { return }
    if entry.kind == .header, entry.canToggle, let expanded = entry.expanded {
      forwarded?("rowPress", ["id": entry.row.id, "expanded": !expanded])
    } else {
      forwarded?("rowPress", ["id": entry.row.id])
    }
  }

  private func swipe(_ path: IndexPath, leading: Bool) -> UISwipeActionsConfiguration? {
    guard let item = dataSource.itemIdentifier(for: path), let entry = entries[item], entry.kind == .session else { return nil }
    let actions = (leading ? entry.row.leadingActions : entry.row.actions).map { action in
      let contextual = UIContextualAction(style: action.destructive ? .destructive : .normal, title: action.title) { [weak self] _, _, done in
        self?.forwarded?("rowAction", ["id": entry.row.id, "actionId": action.id])
        done(true)
      }
      contextual.image = UIImage(systemName: action.symbol)
      if !action.destructive { contextual.backgroundColor = Self.swipeTint(action.tint) }
      return contextual
    }
    guard !actions.isEmpty else { return nil }
    let config = UISwipeActionsConfiguration(actions: actions)
    config.performsFirstActionWithFullSwipe = !leading
    return config
  }

  private static func swipeTint(_ tint: String) -> UIColor {
    tint == "yellow" ? .systemYellow : LodyUIColor.accentSolid
  }

  func collectionView(
    _ collectionView: UICollectionView, contextMenuConfigurationForItemsAt indexPaths: [IndexPath], point: CGPoint
  ) -> UIContextMenuConfiguration? {
    guard let path = indexPaths.first, let item = dataSource.itemIdentifier(for: path), let entry = entries[item],
      !entry.row.menuActions.isEmpty
    else { return nil }
    let row = entry.row
    return UIContextMenuConfiguration(identifier: nil, previewProvider: nil) { [weak self] _ in
      UIMenu(children: row.menuActions.map { action in
        UIAction(
          title: action.title, image: UIImage(systemName: action.symbol),
          attributes: action.destructive ? .destructive : []
        ) { _ in self?.forwarded?("rowAction", ["id": row.id, "actionId": action.id]) }
      })
    }
  }
}

// MARK: - Content

/// The most urgent thing a chat (or a collapsed group) wants, from the row's state tint.
private enum Attention {
  case running, waiting, failed, unread

  init?(tint: String, unread: Bool) {
    switch tint {
    case "blue": self = .running
    case "warning": self = .waiting
    case "danger": self = .failed
    default:
      guard unread else { return nil }
      self = .unread
    }
  }
}

private struct Glyph: View {
  let icon: LodyIcon
  var size: CGFloat = LodyIconSize.md
  var color: Color = LodyColor.iconPrimary

  var body: some View {
    icon.image.resizable().renderingMode(.template).foregroundStyle(color).frame(width: size, height: size)
  }
}

private struct AttentionGlyph: View {
  let attention: Attention
  @State private var spinning = false

  var body: some View {
    switch attention {
    case .running:
      Glyph(icon: .stateRunning, size: LodyIconSize.sm, color: LodyColor.stateRunning)
        .rotationEffect(.degrees(spinning ? 360 : 0))
        .animation(.linear(duration: 1).repeatForever(autoreverses: false), value: spinning)
        .onAppear { spinning = !UIAccessibility.isReduceMotionEnabled }
    case .waiting: Glyph(icon: .stateWaiting, size: LodyIconSize.sm, color: LodyColor.stateWaiting)
    case .failed: Glyph(icon: .stateFailed, size: LodyIconSize.sm, color: LodyColor.stateFailed)
    case .unread: Circle().fill(LodyColor.accentDefault).frame(width: LodySize.dot, height: LodySize.dot)
    }
  }
}

private struct GroupHeader: View {
  let row: LodyListRow
  let expanded: Bool?
  let canToggle: Bool
  let onAction: (String) -> Void

  private var icon: LodyIcon {
    switch row.image {
    case "bubble.left": .session
    case "pin.fill": .pin
    default: .folder
    }
  }

  /// The group's own new-chat action, surfaced as the trailing button.
  private var compose: LodyListAction? {
    row.menuActions.first { $0.id == "newSession" || $0.id == "newChat" }
  }

  var body: some View {
    HStack(spacing: LodyLayout.listRowGap) {
      Glyph(icon: icon)
      Text(row.title)
        .lodyTextStyle(.bodyEmphasis)
        .foregroundStyle(LodyColor.textPrimary)
        .lineLimit(1)
      Spacer(minLength: LodySpacing.sm)
      if expanded == false || !canToggle, !row.badge.isEmpty, row.badge != "0" {
        if let attention = Attention(tint: row.imageTint, unread: false) { AttentionGlyph(attention: attention) }
        Text(row.badge)
          .lodyTextStyle(.footnote)
          .foregroundStyle(LodyColor.textTertiary)
          .monospacedDigit()
      }
      if let compose {
        Button { onAction(compose.id) } label: { Glyph(icon: .compose, color: LodyColor.iconSecondary) }
          .buttonStyle(.borderless)
          .frame(width: LodySize.touchTarget, height: LodySize.touchTarget)
          .accessibilityLabel(compose.title)
      } else {
        Color.clear.frame(width: LodySpacing.xs, height: 1)
      }
    }
    .padding(.leading, LodyLayout.screenPaddingX)
    .padding(.trailing, LodySpacing.xs)
    .frame(maxWidth: .infinity, minHeight: LodySize.listRow, alignment: .leading)
    .accessibilityElement(children: .contain)
    .accessibilityAddTraits(.isHeader)
    .accessibilityValue(accessibilityValue)
  }

  private var accessibilityValue: String {
    guard canToggle, let expanded else { return row.badge }
    if expanded { return String(localized: "native.list.expanded", bundle: .lody) }
    let state = String(localized: "native.list.collapsed", bundle: .lody)
    return [state, row.badge].filter { !$0.isEmpty }.joined(separator: ", ")
  }
}

private struct ChatRowContent: View {
  let row: LodyListRow
  var showsPlace = false

  /// The project part of "project · branch"; empty when the row already sits under its project.
  private var place: String {
    guard showsPlace, !row.wrapSubtitle else { return "" }
    return row.subtitle.components(separatedBy: " · ").first ?? ""
  }

  /// Search results keep their match snippet; everything else is one line.
  private var detail: String? {
    row.wrapSubtitle && !row.subtitle.isEmpty ? row.subtitle : nil
  }

  var body: some View {
    HStack(alignment: detail == nil ? .center : .firstTextBaseline, spacing: 0) {
      // The gutter: its glyph sits exactly under the group icon, empty unless the chat needs attention.
      ZStack {
        if let attention = Attention(tint: row.imageTint, unread: row.unread) { AttentionGlyph(attention: attention) }
      }
      .frame(width: LodyIconSize.md)
      .frame(width: LodyLayout.childIndent - LodyLayout.screenPaddingX + (row.parentId.isEmpty ? 0 : LodySpacing.lg), alignment: .leading)
      VStack(alignment: .leading, spacing: LodySpacing.xxs) {
        Text(row.title)
          .lodyTextStyle(row.unread ? .bodyEmphasis : .body)
          .foregroundStyle(LodyColor.textPrimary)
          .lineLimit(1)
        if let detail {
          Text(detail)
            .lodyTextStyle(.footnote)
            .foregroundStyle(LodyColor.textSecondary)
            .lineLimit(2)
        }
      }
      Spacer(minLength: LodySpacing.sm)
      if !place.isEmpty {
        Text(place)
          .lodyTextStyle(.footnote)
          .foregroundStyle(LodyColor.textTertiary)
          .lineLimit(1)
          // Ideal width clamped to the token, then fixed: short names take only their width, long ones truncate.
          .frame(maxWidth: LodyLayout.trailingMetaMaxWidth, alignment: .trailing)
          .fixedSize(horizontal: true, vertical: false)
      }
    }
    .padding(.leading, LodyLayout.screenPaddingX)
    .padding(.trailing, LodyLayout.screenPaddingX)
    .padding(.vertical, detail == nil ? 0 : LodySpacing.sm)
    .frame(maxWidth: .infinity, minHeight: LodySize.listRow, alignment: .leading)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(row.title)
    .accessibilityValue([row.badge, place, row.accessibilityValue].filter { !$0.isEmpty }.joined(separator: ", "))
    .accessibilityAddTraits(.isButton)
  }
}

private struct MoreRowContent: View {
  let title: String

  var body: some View {
    Text(title)
      .lodyTextStyle(.body)
      .foregroundStyle(LodyColor.textTertiary)
      .padding(.leading, LodyLayout.childIndent)
      .frame(maxWidth: .infinity, minHeight: LodySize.listRow, alignment: .leading)
      .accessibilityAddTraits(.isButton)
  }
}

private struct SectionTitle: View {
  let text: String

  var body: some View {
    if text.isEmpty {
      Color.clear.frame(height: LodySpacing.sm)
    } else {
      Text(text)
        .lodyTextStyle(.subhead)
        .foregroundStyle(LodyColor.textTertiary)
        .padding(.horizontal, LodyLayout.screenPaddingX)
        .padding(.top, LodySpacing.lg)
        .padding(.bottom, LodySpacing.xs)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityAddTraits(.isHeader)
    }
  }
}
