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
  }

  var forwarded: ((String, [String: Any]) -> Void)?
  private(set) var collection: UICollectionView!
  private var dataSource: UICollectionViewDiffableDataSource<String, Item>!
  private var entries: [Item: Entry] = [:]
  private var titles: [String: String] = [:]
  private let placeholder = UILabel()
  private let topBackdrop = ChatHeaderBackdrop()
  private var placeholderText = ""
  private var details = ChatRowDetails.stored
  /// The last projection, re-applied when an opener folds or unfolds.
  private var lastSections: [LodyListSection] = []
  /// Openers whose nested chats are hidden; default unfolded, as on web. Stored per device.
  private var foldedOpeners = Set(UserDefaults.standard.stringArray(forKey: ChatListView.foldedKey) ?? [])
  private static let foldedKey = "homeFoldedOpeners"
  /// UIKit owns a single section header and its push-off at the next section.
  /// Search labels remain in the flow.
  var pinsGroupLabels = true {
    didSet {
      guard pinsGroupLabels != oldValue else { return }
      collection.collectionViewLayout.invalidateLayout()
      updateBackdrop()
    }
  }
  private var detailsObserver: NSObjectProtocol?

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
    let layout = UICollectionViewCompositionalLayout { [weak self] index, environment in
      let section = NSCollectionLayoutSection.list(using: config, layoutEnvironment: environment)
      let id = self?.dataSource?.sectionIdentifier(for: index)
      let title = id.flatMap { self?.titles[$0] } ?? ""
      section.boundarySupplementaryItems.forEach {
        $0.pinToVisibleBounds = self?.pinsGroupLabels == true && !title.isEmpty
        $0.zIndex = 2
      }
      return section
    }
    // Group spacing belongs between sections, not inside the pinned label.
    let layoutConfiguration = UICollectionViewCompositionalLayoutConfiguration()
    layoutConfiguration.interSectionSpacing = LodySpacing.md
    layout.configuration = layoutConfiguration
    collection = UICollectionView(frame: bounds, collectionViewLayout: layout)
    collection.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    collection.backgroundColor = .clear
    collection.delegate = self
    collection.accessibilityIdentifier = "home-chat-list"
    addSubview(collection)
    topBackdrop.layer.zPosition = 1
    topBackdrop.isHidden = true
    collection.addSubview(topBackdrop)
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
      view.layer.zPosition = 2
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
    detailsObserver = NotificationCenter.default.addObserver(forName: .lodyChatRowDetailsChanged, object: nil, queue: .main) {
      [weak self] _ in MainActor.assumeIsolated { self?.detailsChanged() }
    }
  }

  isolated deinit {
    if let detailsObserver { NotificationCenter.default.removeObserver(detailsObserver) }
  }

  /// The view menu changed which details rows show: re-render visible rows in place.
  private func detailsChanged() {
    details = ChatRowDetails.stored
    var snapshot = dataSource.snapshot()
    snapshot.reconfigureItems(snapshot.itemIdentifiers.filter { entries[$0]?.kind == .session })
    dataSource.apply(snapshot, animatingDifferences: false)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  override func layoutSubviews() {
    super.layoutSubviews()
    collection.frame = bounds
    updateBackdrop()
    let inset = LodyLayout.screenPaddingX * 2
    placeholder.frame = CGRect(x: inset, y: bounds.midY - 60, width: max(0, bounds.width - inset * 2), height: 120)
  }

  func setPlaceholder(_ value: String) {
    placeholderText = value
    updatePlaceholder()
  }

  func setSections(_ sections: [LodyListSection]) {
    lastSections = sections
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
        } else if !row.parentId.isEmpty, foldedOpeners.contains(row.parentId) {
          continue
        } else if row.treeParent {
          // A folded opener stands for its family: it shows their most urgent state.
          let open = !foldedOpeners.contains(row.id)
          entry = Entry(kind: .session, row: row.displayingCollapsed(!open, keepingTime: true), expanded: open, canToggle: true)
        } else {
          entry = Entry(kind: .session, row: row, expanded: nil, canToggle: false)
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
    updateBackdrop()
    updatePlaceholder()
  }

  private func toggleOpener(_ id: String) {
    if foldedOpeners.remove(id) == nil { foldedOpeners.insert(id) }
    UserDefaults.standard.set(Array(foldedOpeners).sorted(), forKey: Self.foldedKey)
    setSections(lastSections)
  }

  func scrollViewDidScroll(_ scrollView: UIScrollView) {
    updateBackdrop()
  }

  /// One continuous backdrop runs from the pinned label to the window top.
  /// Keep UIKit's original supplementary label, including its native push-off.
  private func updateBackdrop() {
    let top = collection.contentOffset.y + collection.adjustedContentInset.top
    let paths = collection.indexPathsForVisibleSupplementaryElements(ofKind: UICollectionView.elementKindSectionHeader)
    let active = paths.compactMap { path -> UICollectionViewLayoutAttributes? in
      guard pinsGroupLabels,
            let id = dataSource.sectionIdentifier(for: path.section), !(titles[id] ?? "").isEmpty,
            let attributes = collection.layoutAttributesForSupplementaryElement(ofKind: UICollectionView.elementKindSectionHeader, at: path)
      else { return nil }
      return attributes.frame.minY <= top + 1 ? attributes : nil
    }.max { $0.indexPath.section < $1.indexPath.section }
    // Once pushed above the pinned slot, a departing header becomes background
    // content too. Restore its foreground layer when scrolling back down.
    for path in paths {
      guard let header = collection.supplementaryView(forElementKind: UICollectionView.elementKindSectionHeader, at: path),
            let attributes = collection.layoutAttributesForSupplementaryElement(ofKind: UICollectionView.elementKindSectionHeader, at: path)
      else { continue }
      header.layer.zPosition = pinsGroupLabels && attributes.frame.minY < top - 1 ? 0 : 2
    }
    // A second system edge would introduce a seam in the middle of the gradient.
    collection.topEdgeEffect.isHidden = active != nil
    topBackdrop.isHidden = active == nil
    if let attributes = active {
      topBackdrop.frame = CGRect(x: collection.bounds.minX, y: collection.bounds.minY,
                                width: collection.bounds.width,
                                height: collection.adjustedContentInset.top + attributes.frame.height + LodySpacing.xxl)
    }
  }

  func collectionView(_ collectionView: UICollectionView, willDisplaySupplementaryView view: UICollectionReusableView,
                      forElementKind elementKind: String, at indexPath: IndexPath) {
    updateBackdrop()
  }

  private static func same(_ a: LodyListRow, _ b: LodyListRow, _ ea: Bool?, _ eb: Bool?) -> Bool {
    a.title == b.title && a.subtitle == b.subtitle && a.unread == b.unread && a.imageTint == b.imageTint
      && a.value == b.value && a.place == b.place && a.placeIsRepo == b.placeIsRepo && a.branch == b.branch && a.prStatus == b.prStatus && a.prCI == b.prCI
      && a.badge == b.badge && a.pinned == b.pinned && a.parentId == b.parentId && a.lastChild == b.lastChild && a.treeParent == b.treeParent && a.highlight == b.highlight && ea == eb
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
      case .session:
        ChatRowContent(
          row: entry.row, details: details, expanded: entry.canToggle ? entry.expanded : nil,
          onToggle: { [weak self] in self?.toggleOpener(entry.row.id) }
        )
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

/// Keep a blurred base under the upper gradient: fading a single blur view mixes
/// sharp source text back in. Only the tail below the label fades to clear.
private final class ChatHeaderBackdrop: UIView {
  private let base = ChatBlurLayer(intensity: 0.2)
  private let upper = ChatBlurLayer(intensity: 0.75)

  init() {
    super.init(frame: .zero)
    isUserInteractionEnabled = false
    addSubview(base)
    addSubview(upper)
    updateTone()
    registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: ChatHeaderBackdrop, _: UITraitCollection) in
      view.updateTone()
    }
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  private func updateTone() {
    let dark = traitCollection.userInterfaceStyle == .dark
    base.contentView.backgroundColor = dark ? LodyUIColor.bgBase.withAlphaComponent(0.1) : .clear
    upper.contentView.backgroundColor = dark ? LodyUIColor.bgBase.withAlphaComponent(0.3) : .clear
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    base.frame = bounds
    upper.frame = bounds
    let labelEnd = max(0, min(1, (bounds.height - LodySpacing.xxl) / max(1, bounds.height)))
    base.setFade(colors: [1, 0.85, 0], locations: [0, labelEnd, 1])
    upper.setFade(colors: [1, 0, 0], locations: [0, labelEnd, 1])
  }
}

private final class ChatBlurLayer: UIVisualEffectView {
  private let fadeView = ChatBlurMask()
  private var animator: UIViewPropertyAnimator?
  private var lastSize = CGSize.zero
  private var lastLocations: [CGFloat] = []

  init(intensity: CGFloat) {
    super.init(effect: nil)
    let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
      self?.effect = UIBlurEffect(style: .regular)
    }
    animator.pausesOnCompletion = true
    animator.fractionComplete = intensity
    self.animator = animator
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  isolated deinit { animator?.stopAnimation(true) }

  func setFade(colors: [CGFloat], locations: [CGFloat]) {
    guard lastSize != bounds.size || lastLocations != locations else { return }
    lastSize = bounds.size
    lastLocations = locations
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    fadeView.frame = bounds
    let gradient = fadeView.layer as! CAGradientLayer
    gradient.colors = colors.map { UIColor.black.withAlphaComponent($0).cgColor }
    gradient.locations = locations.map { NSNumber(value: Double($0)) }
    mask = nil
    mask = fadeView
    CATransaction.commit()
  }
}

private final class ChatBlurMask: UIView {
  override class var layerClass: AnyClass { CAGradientLayer.self }
}

// MARK: - Content

/// The status mark a chat shows, matching the web `SessionRowStatusIndicator` and its priority:
/// failed (circle alert, danger) > waiting for permission (hand) > working (grid) > unread (dot).
/// Idle and done chats show nothing.
private enum Attention {
  case failed, waiting, working, unread

  init?(tint: String, unread: Bool) {
    switch tint {
    case "danger": self = .failed
    case "warning": self = .waiting
    case "blue": self = .working
    default:
      guard unread else { return nil }
      self = .unread
    }
  }

  /// The web draws a 12 px mark in a 14 px box beside 14 px text.
  static let size = LodyIconSize.xs + LodySpacing.xxs
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

  var body: some View {
    switch attention {
    case .failed: Glyph(icon: .alert, size: Attention.size, color: LodyColor.dangerDefault)
    case .waiting: Glyph(icon: .hand, size: Attention.size, color: LodyColor.accentDefault)
    case .working: LodyWorkingGrid(size: Attention.size, color: LodyColor.accentDefault)
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

/// Which details a chat row's second line shows. Chosen from the Home view menu and persisted;
/// with none selected (or nothing to show for a row) the row is a single title line.
struct ChatRowDetails: OptionSet, Sendable {
  let rawValue: Int
  static let project = ChatRowDetails(rawValue: 1 << 0)
  static let pullRequest = ChatRowDetails(rawValue: 1 << 1)
  static let branch = ChatRowDetails(rawValue: 1 << 2)
  static let defaults: ChatRowDetails = [.project, .pullRequest]

  private static let key = "homeChatRowDetails"

  static var stored: ChatRowDetails {
    guard UserDefaults.standard.object(forKey: key) != nil else { return defaults }
    return ChatRowDetails(rawValue: UserDefaults.standard.integer(forKey: key))
  }

  static func store(_ value: ChatRowDetails) {
    UserDefaults.standard.set(value.rawValue, forKey: key)
    NotificationCenter.default.post(name: .lodyChatRowDetailsChanged, object: nil)
  }
}

extension Notification.Name {
  static let lodyChatRowDetailsChanged = Notification.Name("LodyChatRowDetailsChanged")
}

/// Line 1: status mark slot · title · updated time. Line 2 (aligned with the title): folder or
/// repository mark · project · branch, with the pull request mark trailing.
private struct ChatRowContent: View {
  let row: LodyListRow
  let details: ChatRowDetails
  /// Set on a row with nested chats: whether they are shown. Its status slot then holds the
  /// disclosure (web's opener chevron) and its status mark moves inline, before the age.
  var expanded: Bool?
  var onToggle: () -> Void = {}

  private var project: String { details.contains(.project) ? row.place : "" }
  private var branch: String { details.contains(.branch) ? row.branch : "" }
  private var pullRequest: PullRequestMark? { details.contains(.pullRequest) ? PullRequestMark(row.prStatus, ci: row.prCI) : nil }
  /// The detail line needs a project or branch; a PR mark alone stays on the title line, before the age.
  private var hasDetailLine: Bool { !project.isEmpty || !branch.isEmpty }
  private var inlinePullRequest: PullRequestMark? { snippet != nil || !hasDetailLine ? pullRequest : nil }

  private var attention: Attention? { Attention(tint: row.imageTint, unread: row.unread) }
  private var nested: Bool { !row.parentId.isEmpty }
  /// An opener's own mark (or, folded, its family's) sits before the age.
  private var inlineAttention: Attention? { expanded != nil ? attention : nil }

  /// Search results keep their match snippet in place of the detail line.
  private var snippet: String? {
    row.wrapSubtitle && !row.subtitle.isEmpty ? row.subtitle : nil
  }

  var body: some View {
    HStack(alignment: .center, spacing: 0) {
      // The status slot, centred on the whole row; a nested row's slot steps in one level.
      if let expanded {
        Button(action: onToggle) {
          Glyph(icon: .expand, size: LodyIconSize.sm, color: LodyColor.iconTertiary)
            .rotationEffect(.degrees(expanded ? 0 : -90))
            .animation(LodySpring.snappy, value: expanded)
            .frame(width: LodyIconSize.md, height: LodyIconSize.md)
            .frame(width: LodyLayout.childIndent - LodyLayout.screenPaddingX, alignment: .leading)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
      } else {
        ZStack {
          if let attention { AttentionGlyph(attention: attention) }
        }
        .frame(width: LodyIconSize.md, height: LodyIconSize.md)
        .padding(.leading, nested ? TreeLines.level : 0)
        .frame(width: LodyLayout.childIndent - LodyLayout.screenPaddingX + (nested ? TreeLines.level : 0), alignment: .leading)
      }
      VStack(alignment: .leading, spacing: LodySpacing.xxs) {
        HStack(alignment: .firstTextBaseline, spacing: LodySpacing.sm) {
          Text(row.title)
            .lodyTextStyle(row.unread ? .bodyEmphasis : .body)
            .foregroundStyle(LodyColor.textPrimary)
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
          // The text inside sets the stack's baseline, so the PR mark centres on the age.
          if inlineAttention != nil || inlinePullRequest != nil || !row.value.isEmpty {
            HStack(spacing: LodySpacing.xs) {
              if let inlineAttention { AttentionGlyph(attention: inlineAttention) }
              inlinePullRequest?.glyph
              if !row.value.isEmpty {
                Text(row.value)
                  .lodyTextStyle(.footnote)
                  .foregroundStyle(LodyColor.textTertiary)
                  .monospacedDigit()
              }
            }
            .fixedSize()
          }
        }
        if let snippet {
          Text(snippet).lodyTextStyle(.footnote).foregroundStyle(LodyColor.textSecondary).lineLimit(2)
        } else if hasDetailLine {
          detailLine
        }
      }
    }
    .padding(.horizontal, LodyLayout.screenPaddingX)
    .padding(.vertical, snippet != nil || hasDetailLine ? LodySpacing.sm : 0)
    .frame(maxWidth: .infinity, minHeight: LodySize.listRow, alignment: .leading)
    .background {
      if nested || expanded == true {
        TreeLines(nested: nested, lastChild: row.lastChild, parent: expanded == true, marked: attention != nil)
      }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(row.title)
    .accessibilityValue(
      [row.badge, project, branch, pullRequest?.label ?? "", row.accessibilityValue].filter { !$0.isEmpty }.joined(separator: ", ")
    )
    .accessibilityAddTraits(.isButton)
    .accessibilityActions {
      if let expanded {
        Button(String(localized: String.LocalizationValue(expanded ? "home.tree.collapse" : "home.tree.expand"), bundle: .lody), action: onToggle)
      }
    }
  }

  private var detailLine: some View {
    HStack(spacing: LodySpacing.xs) {
      if !project.isEmpty {
        Glyph(icon: row.placeIsRepo ? .github : .folder, size: LodyIconSize.sm, color: LodyColor.iconSecondary)
        Text(project).lodyTextStyle(.footnote).foregroundStyle(LodyColor.textSecondary).lineLimit(1)
      }
      if !branch.isEmpty {
        Glyph(icon: .branch, size: LodyIconSize.sm, color: LodyColor.iconTertiary)
          .padding(.leading, project.isEmpty ? 0 : LodySpacing.xs)
        Text(branch).lodyTextStyle(.footnote).foregroundStyle(LodyColor.textTertiary).lineLimit(1).truncationMode(.middle)
      }
      Spacer(minLength: LodySpacing.sm)
      pullRequest?.glyph
    }
    .frame(maxWidth: .infinity)
  }
}

/// Web's opened-by tree connectors (`session-row-leading-slot.tsx`) in the mobile gutter: a
/// trunk down the parent's status-mark column, from below its mark through each nested row
/// (stopping at the last one's centre), and an elbow into the nested row's own mark, or toward
/// its title when it has none. Rows sit edge to edge, so each draws only its own height and the
/// pieces read as one line.
private struct TreeLines: View {
  /// One nesting level: how far a nested row's mark and title step in.
  static let level = LodySpacing.lg

  let nested: Bool
  let lastChild: Bool
  let parent: Bool
  let marked: Bool

  var body: some View {
    Canvas { context, size in
      let x = LodyLayout.screenPaddingX + LodyIconSize.md / 2
      let mid = size.height / 2
      var path = Path()
      if parent {
        // Below the opener's chevron.
        path.move(to: CGPoint(x: x, y: mid + LodyIconSize.sm / 2 + LodySpacing.xxs))
        path.addLine(to: CGPoint(x: x, y: size.height))
      }
      if nested {
        path.move(to: CGPoint(x: x, y: 0))
        path.addLine(to: CGPoint(x: x, y: lastChild ? mid : size.height))
        let end = marked
          ? LodyLayout.screenPaddingX + Self.level + LodySpacing.xxs
          : LodyLayout.childIndent + Self.level - LodySpacing.sm
        path.move(to: CGPoint(x: x, y: mid))
        path.addLine(to: CGPoint(x: end, y: mid))
      }
      context.stroke(path, with: .color(LodyColor.borderDefault), lineWidth: 1)
    }
    .accessibilityHidden(true)
  }
}

/// The web `SessionPrIcon` states: open, merged, closed, draft.
private struct PullRequestMark {
  let icon: LodyIcon
  let color: Color
  let label: String
  /// Shown only while the PR is open or a draft, as in the web sidebar.
  let ci: PullRequestCI?

  /// Always the trailing mark of the line that carries it.
  var glyph: some View { PullRequestGlyph(icon: icon, color: color, ci: ci).fixedSize() }

  init?(_ status: String, ci: String = "") {
    switch status {
    case "open":
      icon = .pullRequest
      color = LodyColor.prOpen
    case "merged":
      icon = .merge
      color = LodyColor.prMerged
    case "closed":
      icon = .pullRequestClosed
      color = LodyColor.prClosed
    case "draft":
      icon = .pullRequestDraft
      color = LodyColor.prDraft
    default:
      return nil
    }
    self.ci = ["open", "draft"].contains(status) ? PullRequestCI(rawValue: ci) : nil
    let pr = String(localized: String.LocalizationValue("home.pr." + status), bundle: .lody)
    label = self.ci.map { pr + ", " + String(localized: String.LocalizationValue("home.ci." + $0.rawValue), bundle: .lody) } ?? pr
  }
}

/// Web `MaskedPrCiIcon` geometry on a 16-unit box: the verdict sits in the bottom-trailing
/// 10-unit slot and a circular cutout clears the PR stroke beneath it, so no backdrop is
/// painted. Pending is a static dot with a tighter cutout.
private struct PullRequestGlyph: View {
  let icon: LodyIcon
  let color: Color
  let ci: PullRequestCI?
  private let size = LodyIconSize.sm

  var body: some View {
    if let ci {
      let unit = size / 16
      ZStack(alignment: .bottomTrailing) {
        Glyph(icon: icon, size: size, color: color)
          .mask {
            Rectangle()
              .overlay(alignment: .topLeading) {
                let radius = (ci == .pending ? 3.75 : 5) * unit
                Circle().frame(width: radius * 2, height: radius * 2)
                  .offset(x: 12 * unit - radius, y: 12 * unit - radius)
                  .blendMode(.destinationOut)
              }
              .compositingGroup()
          }
        verdict(ci, unit: unit).frame(width: 10 * unit, height: 10 * unit).offset(x: unit, y: unit)
      }
      .frame(width: size, height: size)
    } else {
      Glyph(icon: icon, size: size, color: color)
    }
  }

  @ViewBuilder private func verdict(_ ci: PullRequestCI, unit: CGFloat) -> some View {
    switch ci {
    case .pass: Glyph(icon: .check, size: 10 * unit, color: LodyColor.successDefault)
    case .fail: Glyph(icon: .close, size: 10 * unit, color: LodyColor.dangerDefault)
    case .pending: Circle().fill(LodyColor.warningDefault).frame(width: 5 * unit, height: 5 * unit)
    }
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
        .padding(.top, LodySpacing.xs)
        .padding(.bottom, LodySpacing.xs)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityAddTraits(.isHeader)
    }
  }
}
