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
            Text(tab.title.isEmpty ? String(localized: "session.tabs.new", bundle: .lody) : tab.title)
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

/// Presents the panel as a popover hanging from the bottom edge of the navigation bar, so it
/// opens below the title (a title menu grew over it). Stays a popover on iPhone.
@MainActor
final class ChatTabsPopover: UIHostingController<ChatTabsPanel>, UIPopoverPresentationControllerDelegate {
  static func present(
    _ panel: ChatTabsPanel, tabs: Int, from host: UIViewController, below bar: UINavigationBar?, fallback source: UIView
  ) {
    let controller = ChatTabsPopover(rootView: panel)
    controller.modalPresentationStyle = .popover
    controller.preferredContentSize = CGSize(width: 300, height: ChatTabsPanel.height(tabs: tabs))
    if let popover = controller.popoverPresentationController {
      popover.delegate = controller
      popover.permittedArrowDirections = .up
      if let bar {
        popover.sourceView = bar
        popover.sourceRect = CGRect(x: bar.bounds.midX - 1, y: bar.bounds.maxY - 1, width: 2, height: 1)
      } else {
        popover.sourceView = source
        popover.sourceRect = CGRect(x: source.bounds.midX - 1, y: source.bounds.maxY, width: 2, height: 1)
      }
    }
    host.present(controller, animated: true)
  }

  func adaptivePresentationStyle(for controller: UIPresentationController, traitCollection: UITraitCollection) -> UIModalPresentationStyle {
    .none
  }
}
