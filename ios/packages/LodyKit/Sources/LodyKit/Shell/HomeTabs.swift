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

  private let bar = UITabBar()
  private let create = UIButton(type: .system)
  private let actions: Actions
  private weak var page: UIViewController?
  private weak var content: UIViewController?

  /// - Parameters:
  ///   - page: the hosting `LodyPageController`; the bar is pinned to its safe area.
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
    NSLayoutConstraint.activate([
      bar.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: LodyLayout.screenPaddingX),
      bar.trailingAnchor.constraint(equalTo: create.leadingAnchor, constant: -LodySpacing.md),
      bar.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -LodySpacing.xs),
      create.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -LodyLayout.screenPaddingX),
      create.centerYAnchor.constraint(equalTo: bar.centerYAnchor),
      create.widthAnchor.constraint(equalToConstant: Self.height),
      create.heightAnchor.constraint(equalToConstant: Self.height),
    ])
    page.registerForTraitChanges([UITraitHorizontalSizeClass.self]) { [weak self] (_: UIViewController, _: UITraitCollection) in
      self?.updateVisibility()
    }
    updateVisibility()
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

  private func updateVisibility() {
    guard let page else { return }
    let shown = page.traitCollection.horizontalSizeClass != .regular
    bar.isHidden = !shown
    create.isHidden = !shown
    let height = max(Self.height, bar.sizeThatFits(page.view.bounds.size).height)
    content?.additionalSafeAreaInsets.bottom = shown ? height + LodySpacing.xs : 0
  }
}
