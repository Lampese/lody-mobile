import LodyDesignSystem
import SwiftUI
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

/// The floating bottom bar from the mobile design: a capsule of labelled tabs plus a separate
/// new-chat button, both on system Liquid Glass. The system tab bar cannot host a second
/// control beside its capsule (that slot belongs to search), so this bar draws its own glass.
///
/// It is installed on each tab's root page, so pushing a session or project slides it away
/// with the page and popping brings it back; no visibility bookkeeping is needed.
@MainActor
final class HomeTabBar {
  struct Actions {
    var select: (HomeTab) -> Void
    var create: () -> Void
    /// Long-pressing Settings opens the development Debug page, as the old gear did.
    var openDebug: () -> Void
  }

  static let height = LodySize.controlLg + LodySpacing.sm

  private let hosting: UIHostingController<HomeTabBarView>
  private weak var page: UIViewController?
  private weak var content: UIViewController?

  /// - Parameters:
  ///   - page: the hosting `LodyPageController`; the bar is pinned to its safe area.
  ///   - content: the page's content; its bottom safe area grows so lists scroll clear of the bar.
  init(page: UIViewController, content: UIViewController, selected: HomeTab, actions: Actions) {
    self.page = page
    self.content = content
    hosting = UIHostingController(rootView: HomeTabBarView(selected: selected, actions: actions))
    hosting.view.backgroundColor = .clear
    hosting.sizingOptions = .intrinsicContentSize
    page.loadViewIfNeeded()
    page.addChild(hosting)
    hosting.view.translatesAutoresizingMaskIntoConstraints = false
    page.view.addSubview(hosting.view)
    NSLayoutConstraint.activate([
      hosting.view.leadingAnchor.constraint(equalTo: page.view.safeAreaLayoutGuide.leadingAnchor),
      hosting.view.trailingAnchor.constraint(equalTo: page.view.safeAreaLayoutGuide.trailingAnchor),
      hosting.view.bottomAnchor.constraint(equalTo: page.view.safeAreaLayoutGuide.bottomAnchor, constant: -LodySpacing.xs),
      hosting.view.heightAnchor.constraint(equalToConstant: Self.height),
    ])
    hosting.didMove(toParent: page)
    page.registerForTraitChanges([UITraitHorizontalSizeClass.self]) { [weak self] (_: UIViewController, _: UITraitCollection) in
      self?.updateVisibility()
    }
    updateVisibility()
  }

  private func updateVisibility() {
    guard let page else { return }
    let shown = page.traitCollection.horizontalSizeClass != .regular
    hosting.view.isHidden = !shown
    content?.additionalSafeAreaInsets.bottom = shown ? Self.height + LodySpacing.xs : 0
  }
}

private struct HomeTabBarView: View {
  let selected: HomeTab
  let actions: HomeTabBar.Actions

  var body: some View {
    GlassEffectContainer(spacing: LodySpacing.md) {
      HStack(spacing: LodySpacing.md) {
        HStack(spacing: 0) {
          ForEach(HomeTab.allCases, id: \.self) { tab in
            TabButton(tab: tab, selected: tab == selected, actions: actions)
          }
        }
        .padding(LodySpacing.xs)
        .glassEffect(.regular.interactive(), in: .capsule)

        Button(action: actions.create) {
          LodyIcon.compose.image
            .resizable()
            .renderingMode(.template)
            .foregroundStyle(LodyColor.iconPrimary)
            .frame(width: LodyIconSize.lg, height: LodyIconSize.lg)
            .frame(width: HomeTabBar.height, height: HomeTabBar.height)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .circle)
        .accessibilityLabel(String(localized: "tabs.newSession", bundle: .lody))
        .accessibilityIdentifier("home-new-session")
      }
      .padding(.horizontal, LodyLayout.screenPaddingX)
    }
    .accessibilityElement(children: .contain)
  }
}

private struct TabButton: View {
  let tab: HomeTab
  let selected: Bool
  let actions: HomeTabBar.Actions

  var body: some View {
    Button { actions.select(tab) } label: {
      VStack(spacing: LodySpacing.xxs) {
        tab.icon.image
          .resizable()
          .renderingMode(.template)
          .frame(width: LodyIconSize.md, height: LodyIconSize.md)
        Text(tab.title)
          .font(LodyTextStyle.micro.font)
          .tracking(LodyTextStyle.micro.tracking)
          .lineLimit(1)
          .minimumScaleFactor(0.8)
      }
      .foregroundStyle(selected ? LodyColor.accentFg : LodyColor.iconSecondary)
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .background(selected ? LodyColor.fillSelected : .clear, in: Capsule())
      .contentShape(Capsule())
    }
    .buttonStyle(.plain)
    .simultaneousGesture(LongPressGesture(minimumDuration: 0.6).onEnded { _ in
      if tab == .settings { actions.openDebug() }
    })
    .accessibilityLabel(tab.title)
    .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    .accessibilityIdentifier("home-tab-\(String(describing: tab))")
  }
}
