import LodyDesignSystem
import UIKit

/// iPhone Home's top-level destinations. iPad keeps the split view and does not show tabs.
enum HomeTab: CaseIterable, Sendable {
  case chats, projects, settings

  var icon: LodyIcon {
    switch self {
    case .chats: .sessions
    case .projects: .folder
    case .settings: .settings
    }
  }

  var title: String {
    switch self {
    case .chats: String(localized: "home.tab.chats", bundle: .lody)
    case .projects: String(localized: "inbox.section.projects", bundle: .lody)
    case .settings: String(localized: "tabs.settings", bundle: .lody)
    }
  }
}

/// The floating bottom bar from the mobile design: the system tab bar (iOS 26 Liquid Glass, with
/// its draggable, refracting selection) holding labelled tabs, plus a separate glass new-chat
/// button. The bar is a standalone `UITabBar` rather than a `UITabBarController`, because Home
/// stays a split view and the slot beside a controller's capsule belongs to search.
///
/// It is installed on each tab's root page, so pushing a session or project slides it away
/// with the page and popping brings it back; no visibility bookkeeping is needed.
@MainActor
final class HomeTabBar: NSObject, UITabBarDelegate {
  struct Actions {
    var select: (HomeTab) -> Void
    var create: () -> Void
    /// Long-pressing Settings opens the development Debug page, as the old gear did.
    var openDebug: () -> Void
  }

  static let height = LodySize.controlLg + LodySpacing.sm

  private let bar = HomeSystemTabBar()
  private let create = UIButton(type: .system)
  private var createCenter: NSLayoutConstraint!
  private var createSize: NSLayoutConstraint!
  private var barLeading: NSLayoutConstraint!
  private var barTrailing: NSLayoutConstraint!
  private let actions: Actions
  private weak var page: UIViewController?
  private weak var content: UIViewController?

  /// - Parameters:
  ///   - page: the hosting `LodyPageController`; the bar is pinned to its bottom edge, as a
  ///     system tab bar is, and lays its capsule out above the home indicator itself.
  ///   - content: the page's content; its bottom safe area grows so lists scroll clear of the bar.
  init(page: UIViewController, content: UIViewController, selected: HomeTab, actions: Actions) {
    self.page = page
    self.content = content
    self.actions = actions
    super.init()
    page.loadViewIfNeeded()

    let items = HomeTab.allCases.enumerated().map { index, tab in
      let item = UITabBarItem(title: tab.title, image: tab.icon.uiImage?.withRenderingMode(.alwaysTemplate), tag: index)
      item.accessibilityIdentifier = "home-tab-\(String(describing: tab))"
      return item
    }
    bar.items = items
    bar.selectedItem = items[HomeTab.allCases.firstIndex(of: selected) ?? 0]
    bar.delegate = self
    bar.tintColor = LodyUIColor.accentFg
    bar.unselectedItemTintColor = LodyUIColor.iconSecondary
    bar.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(longPressed(_:))))

    var config = UIButton.Configuration.glass()
    config.image = LodyIcon.compose.uiImage?.withRenderingMode(.alwaysTemplate)
    config.baseForegroundColor = LodyUIColor.iconPrimary
    config.cornerStyle = .capsule
    create.configuration = config
    create.accessibilityLabel = String(localized: "tabs.newSession", bundle: .lody)
    create.accessibilityIdentifier = "home-new-session"
    create.addAction(UIAction { [weak self] _ in self?.actions.create() }, for: .primaryActionTriggered)

    for view in [bar, create] as [UIView] {
      view.translatesAutoresizingMaskIntoConstraints = false
      page.view.addSubview(view)
    }
    let guide = page.view.safeAreaLayoutGuide
    // The bar's frame is taller than its glass capsule (it reaches the screen edge), so the
    // button follows the capsule the bar reports after layout rather than the bar's frame.
    createCenter = create.centerYAnchor.constraint(equalTo: bar.topAnchor, constant: Self.height / 2)
    createSize = create.heightAnchor.constraint(equalToConstant: Self.height)
    barLeading = bar.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: LodyLayout.screenPaddingX)
    barTrailing = bar.trailingAnchor.constraint(equalTo: create.leadingAnchor, constant: -LodySpacing.md)
    NSLayoutConstraint.activate([
      barLeading, barTrailing,
      bar.bottomAnchor.constraint(equalTo: page.view.bottomAnchor),
      create.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -LodyLayout.screenPaddingX),
      create.widthAnchor.constraint(equalTo: create.heightAnchor),
      createCenter, createSize,
    ])
    bar.capsuleChanged = { [weak self] capsule in self?.follow(capsule) }
    page.registerForTraitChanges([UITraitHorizontalSizeClass.self]) { [weak self] (_: UIViewController, _: UITraitCollection) in
      self?.updateVisibility()
    }
    updateVisibility()
  }

  /// Each tab page owns a bar, and a tap or drag moves only the bar it happened on. A cached page
  /// coming back would still show whatever its bar last selected, so the shell re-selects its
  /// own tab before the page is shown.
  func show(_ tab: HomeTab) {
    guard let index = HomeTab.allCases.firstIndex(of: tab), let item = bar.items?[index], bar.selectedItem !== item else { return }
    UIView.performWithoutAnimation {
      bar.selectedItem = item
      bar.layoutIfNeeded()
    }
  }

  func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
    guard HomeTab.allCases.indices.contains(item.tag) else { return }
    actions.select(HomeTab.allCases[item.tag])
  }

  @objc private func longPressed(_ gesture: UILongPressGestureRecognizer) {
    guard gesture.state == .began, let settings = HomeTab.allCases.firstIndex(of: .settings) else { return }
    // Tabs share the bar's width equally, so the touch's column names the tab.
    let column = Int(gesture.location(in: bar).x / max(1, bar.bounds.width) * CGFloat(HomeTab.allCases.count))
    let index = bar.effectiveUserInterfaceLayoutDirection == .rightToLeft ? HomeTab.allCases.count - 1 - column : column
    if index == settings { actions.openDebug() }
  }

  /// Centres and sizes the new-chat circle on the tab capsule, and keeps lists clear of it. The
  /// capsule is inset inside the bar's frame, so the bar is shifted by that inset: the capsule's
  /// outer edge then sits `screenPaddingX` from the screen, mirroring the button's.
  private func follow(_ capsule: CGRect) {
    let leading = LodyLayout.screenPaddingX - capsule.minX
    let trailing = -LodySpacing.md + (bar.bounds.width - capsule.maxX)
    if barLeading.constant != leading { barLeading.constant = leading }
    if barTrailing.constant != trailing { barTrailing.constant = trailing }
    if createCenter.constant != capsule.midY { createCenter.constant = capsule.midY }
    if createSize.constant != capsule.height { createSize.constant = capsule.height }
    updateVisibility()
  }

  private func updateVisibility() {
    guard let page else { return }
    let shown = page.traitCollection.horizontalSizeClass != .regular
    bar.isHidden = !shown
    create.isHidden = !shown
    // The content's bottom safe area already covers the home indicator; add what the capsule
    // rises above it, plus a gap.
    let covered = bar.capsule.map { bar.bounds.height - $0.minY - page.view.safeAreaInsets.bottom } ?? Self.height
    let inset = shown ? max(0, covered) + LodySpacing.xs : 0
    if content?.additionalSafeAreaInsets.bottom != inset { content?.additionalSafeAreaInsets.bottom = inset }
  }
}

/// Reports where iOS 26 draws the tab bar's glass capsule: its frame spans to the screen edge,
/// while the capsule is the widest visible subview inside it.
private final class HomeSystemTabBar: UITabBar {
  var capsuleChanged: ((CGRect) -> Void)?
  private(set) var capsule: CGRect?

  override func layoutSubviews() {
    super.layoutSubviews()
    let candidates = subviews.filter { view in
      !view.isHidden && view.alpha > 0 && view.frame.height > 0 && view.frame.height < bounds.height && view.frame.width > bounds.width / 2
    }
    // Without a recognisable capsule, fall back to the bar's area above the home indicator.
    let next = candidates.max { $0.frame.width * $0.frame.height < $1.frame.width * $1.frame.height }?.frame
      ?? CGRect(x: 0, y: 0, width: bounds.width, height: max(0, bounds.height - safeAreaInsets.bottom))
    guard next != capsule else { return }
    capsule = next
    capsuleChanged?(next)
  }
}
