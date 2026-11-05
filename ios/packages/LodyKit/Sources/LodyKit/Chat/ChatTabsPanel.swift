import LodyDesignSystem
import SwiftUI
import UIKit

/// One Tab of the conversation, as its panel row shows it.
struct ChatTabItem: Identifiable {
  let id: String
  let title: String
  /// The Chats list's status mark (failed, waiting, working, unread); nil when quiet.
  let attention: Attention?
  let current: Bool
}

/// The title's Tabs panel: the conversation's Tabs (each with the Chats list's status mark, the
/// current one checked), then New Tab.
struct ChatTabsPanel: View {
  let tabs: [ChatTabItem]
  let creating: Bool
  var onSelect: (String) -> Void
  var onNew: () -> Void

  var body: some View {
    VStack(spacing: 0) {
      ForEach(tabs) { tab in
        Button { onSelect(tab.id) } label: {
          HStack(spacing: LodySpacing.md) {
            // The mark's column stays even when a Tab is quiet, so titles line up.
            ZStack {
              if let attention = tab.attention { AttentionGlyph(attention: attention) }
            }
            .frame(width: LodyIconSize.md, height: LodyIconSize.md)
            Text(tab.title.isEmpty ? String(localized: "session.tabs.defaultTitle", bundle: .lody) : tab.title)
              .lodyTextStyle(.body)
              .foregroundStyle(LodyColor.textPrimary)
              .lineLimit(1)
              .frame(maxWidth: .infinity, alignment: .leading)
            if tab.current {
              LodyIcon.check.image.resizable().renderingMode(.template)
                .frame(width: LodyIconSize.sm, height: LodyIconSize.sm)
                .foregroundStyle(LodyColor.accentFg)
            }
          }
          .padding(.horizontal, LodySpacing.lg)
          .frame(minHeight: LodySize.listRow)
          .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("tab:" + tab.id)
        .accessibilityAddTraits(tab.current ? [.isButton, .isSelected] : .isButton)
      }
      if !tabs.isEmpty { Divider().padding(.leading, LodySpacing.lg) }
      Button(action: onNew) {
        HStack(spacing: LodySpacing.md) {
          Group {
            if creating { ProgressView().controlSize(.small) } else {
              LodyIcon.plus.image.resizable().renderingMode(.template).frame(width: LodyIconSize.sm, height: LodyIconSize.sm)
            }
          }
          .frame(width: LodyIconSize.md, height: LodyIconSize.md)
          .foregroundStyle(LodyColor.accentFg)
          Text(String(localized: "session.tabs.new", bundle: .lody))
            .lodyTextStyle(.body)
            .foregroundStyle(LodyColor.accentFg)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, LodySpacing.lg)
        .frame(minHeight: LodySize.listRow)
        .contentShape(Rectangle())
      }
      .buttonStyle(.plain)
      .disabled(creating)
      .accessibilityIdentifier("newTab")
    }
    .padding(.vertical, LodySpacing.xs)
  }

  /// Height for the popover: one row per Tab, the divider, New Tab.
  static func height(tabs: Int) -> CGFloat {
    CGFloat(tabs + 1) * LodySize.listRow + (tabs > 0 ? 1 : 0) + LodySpacing.xs * 2
  }
}

/// Shows the panel as a card pinned just below the header (the page's safe-area top), over a
/// clear tap-to-dismiss layer. A popover anchored in the navigation bar was drawn over the title;
/// this always opens under it and drops in from the header.
@MainActor
final class ChatTabsOverlay: UIView {
  private let card = UIVisualEffectView(effect: UIGlassEffect(style: .regular))
  private let content: UIHostingController<ChatTabsPanel>
  private var onClose: (() -> Void)?

  /// The panel currently shown in `host`, if any.
  static func shown(in host: UIView) -> ChatTabsOverlay? { host.subviews.compactMap { $0 as? ChatTabsOverlay }.first }

  static func present(_ panel: ChatTabsPanel, tabs: Int, in host: UIViewController) -> ChatTabsOverlay {
    let overlay = ChatTabsOverlay(panel: panel)
    overlay.show(in: host, height: ChatTabsPanel.height(tabs: tabs))
    return overlay
  }

  private init(panel: ChatTabsPanel) {
    content = UIHostingController(rootView: panel)
    super.init(frame: .zero)
    accessibilityViewIsModal = true
    accessibilityIdentifier = "chat-tabs-panel"
    let dismiss = UITapGestureRecognizer(target: self, action: #selector(backgroundTapped(_:)))
    dismiss.cancelsTouchesInView = false
    addGestureRecognizer(dismiss)
    card.cornerConfiguration = .corners(radius: .fixed(22))
    card.clipsToBounds = true
    content.view.backgroundColor = .clear
    content.view.translatesAutoresizingMaskIntoConstraints = false
    card.contentView.addSubview(content.view)
    card.translatesAutoresizingMaskIntoConstraints = false
    addSubview(card)
    NSLayoutConstraint.activate([
      content.view.topAnchor.constraint(equalTo: card.contentView.topAnchor),
      content.view.bottomAnchor.constraint(equalTo: card.contentView.bottomAnchor),
      content.view.leadingAnchor.constraint(equalTo: card.contentView.leadingAnchor),
      content.view.trailingAnchor.constraint(equalTo: card.contentView.trailingAnchor),
    ])
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  private func show(in host: UIViewController, height: CGFloat) {
    let root = host.view!
    host.addChild(content)
    translatesAutoresizingMaskIntoConstraints = false
    root.addSubview(self)
    NSLayoutConstraint.activate([
      topAnchor.constraint(equalTo: root.topAnchor), bottomAnchor.constraint(equalTo: root.bottomAnchor),
      leadingAnchor.constraint(equalTo: root.leadingAnchor), trailingAnchor.constraint(equalTo: root.trailingAnchor),
      // Just under the header, centred, as wide as a menu.
      card.topAnchor.constraint(equalTo: root.safeAreaLayoutGuide.topAnchor, constant: LodySpacing.sm),
      card.centerXAnchor.constraint(equalTo: root.safeAreaLayoutGuide.centerXAnchor),
      card.widthAnchor.constraint(equalToConstant: min(320, max(200, root.bounds.width - 2 * LodyLayout.screenPaddingX))),
      card.heightAnchor.constraint(equalToConstant: height),
    ])
    content.didMove(toParent: host)
    root.layoutIfNeeded()
    UISelectionFeedbackGenerator().selectionChanged()
    guard !UIAccessibility.isReduceMotionEnabled else { return }
    // Drops in from the header: grows from its top edge while fading in.
    card.alpha = 0
    card.transform = CGAffineTransform(translationX: 0, y: -height * 0.08).scaledBy(x: 0.94, y: 0.9)
    UIView.animate(springDuration: 0.38, bounce: 0.18) {
      self.card.alpha = 1
      self.card.transform = .identity
    }
  }

  @objc private func backgroundTapped(_ gesture: UITapGestureRecognizer) {
    guard !card.frame.contains(gesture.location(in: self)) else { return }
    close()
  }

  /// Fades the card back into the header, then removes the overlay.
  func close(then done: (() -> Void)? = nil) {
    isUserInteractionEnabled = false
    let finish = {
      self.content.willMove(toParent: nil)
      self.content.removeFromParent()
      self.removeFromSuperview()
      done?()
    }
    guard window != nil, !UIAccessibility.isReduceMotionEnabled else { return finish() }
    UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseIn, .beginFromCurrentState]) {
      self.card.alpha = 0
      self.card.transform = CGAffineTransform(translationX: 0, y: -8).scaledBy(x: 0.96, y: 0.94)
    } completion: { _ in finish() }
  }
}
