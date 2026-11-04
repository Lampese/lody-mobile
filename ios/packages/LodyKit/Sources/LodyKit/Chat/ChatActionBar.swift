import LodyDesignSystem
import SwiftUI
import UIKit

/// One action in a bar menu; `subtitle` reads as the system menu's second line.
struct ChatMenuAction: Decodable, Equatable, Identifiable {
  let id: String
  let title: String
  let symbol: String
  var subtitle: String?
  var destructive: Bool?
  var disabled: Bool?
}

/// Web `SessionInfoBar` for the native composer, as grouped glass capsules above the input:
/// the computer and live resources, the repository context with ±diff, and the repository
/// action. Colour is kept for status (PR state, CI, diff), so Send stays the one accent on screen.
struct ChatActionBarState: Decodable, Equatable {
  struct Item: Decodable, Equatable, Identifiable {
    let id: String
    let symbol: String
    var tint: String?
    /// Connecting: a spinner replaces the glyph.
    var busy: Bool?
    /// A count beside the glyph (sub-tasks).
    var badge: String?
    let accessibilityLabel: String
    /// Tap acts on the item itself; its menu (if any) opens on a long press.
    var tap: Bool?
    var menuTitle: String?
    var actions: [ChatMenuAction]?
  }

  struct Context: Decodable, Equatable {
    let label: String
    let symbol: String
    var tint: String?
    let accessibilityLabel: String
    var menuTitle: String?
    let actions: [ChatMenuAction]
    /// With a PR, a tap opens its detail panel; the menu moves to a long press.
    var tap: Bool?
  }

  struct Diff: Decodable, Equatable {
    let added: Int
    let removed: Int
    let accessibilityLabel: String
    /// Opens the workspace changes; otherwise the counts are display only.
    var tap: Bool?
  }

  /// The top repository action; [more] holds the rest in priority order.
  struct Primary: Decodable, Equatable {
    let label: String
    let more: [ChatMenuAction]
    let moreLabel: String
  }

  var items: [Item]?
  var context: Context?
  var diff: Diff?
  var primary: Primary?

  var isEmpty: Bool { (items ?? []).isEmpty && context == nil && diff == nil && primary == nil }
}

/// Three glass capsules grouped by purpose, so the composer stays the only full-width shape:
/// environment (computer, live resources, sub-tasks), context (PR or branch with ±diff) and the
/// repository action, pinned trailing. Never scrolls: `ViewThatFits` takes the first stage that
/// fits on one line — long labels capped, then the diff as a dot, then the environment folded
/// into one icon with a "+n" badge — and only at very large text moves the action to its own row.
struct ChatActionBarContent: View {
  var state = ChatActionBarState()
  var tasks = 0
  var onAction: (String) -> Void = { _ in }
  var onTasks: () -> Void = {}
  @Environment(\.dynamicTypeSize) private var typeSize

  var body: some View {
    GlassEffectContainer(spacing: 8) {
      ViewThatFits(in: .horizontal) {
        row(diffDot: false, folded: false)
        row(diffDot: true, folded: false)
        row(diffDot: true, folded: true)
        // ViewThatFits shows its last choice when none fits. At ordinary sizes that is still one
        // row, where the context label truncates as far as needed; only accessibility text sizes
        // give the action its own line.
        if typeSize.isAccessibilitySize {
          stacked
        } else {
          row(diffDot: true, folded: true)
        }
      }
    }
    .font(.subheadline)
  }

  private func row(diffDot: Bool, folded: Bool) -> some View {
    HStack(spacing: 8) {
      leading(diffDot: diffDot, folded: folded)
      Spacer(minLength: 0)
      if let primary = state.primary { primaryCapsule(primary) }
    }
    .frame(height: ChatActionBarView.rowHeight)
  }

  /// Last resort at very large text: information on the first line (the context truncates), the
  /// action whole on its own trailing line.
  private var stacked: some View {
    VStack(alignment: .trailing, spacing: 8) {
      HStack(spacing: 8) {
        leading(diffDot: true, folded: true)
        Spacer(minLength: 0)
      }
      .frame(minHeight: ChatActionBarView.rowHeight)
      if let primary = state.primary { primaryCapsule(primary) }
    }
  }

  @ViewBuilder private func leading(diffDot: Bool, folded: Bool) -> some View {
    if !environment.isEmpty {
      if folded && environment.count > 1 { foldedEnvironment } else { environmentCapsule }
    }
    if state.context != nil || state.diff != nil { contextCapsule(diffDot: diffDot) }
  }

  // MARK: Environment

  /// Computer and live resources, with sub-tasks as one more icon.
  private var environment: [ChatActionBarState.Item] {
    var items = state.items ?? []
    if tasks > 0 {
      items.append(.init(
        id: "tasks", symbol: "point.3.connected.trianglepath.dotted", tint: "secondary", badge: "\(tasks)",
        accessibilityLabel: String.localizedStringWithFormat(String(localized: "native.chat.overlay.tasks", bundle: .lody), tasks)
      ))
    }
    return items
  }

  private var environmentCapsule: some View {
    HStack(spacing: 0) {
      ForEach(environment) { item($0) }
    }
    .padding(.horizontal, 2)
    .glassEffect(.regular, in: .capsule)
    .fixedSize()
  }

  /// One icon stands for the group: a computer in trouble first, then a live resource, then
  /// sub-tasks; the menu lists every item.
  private var foldedEnvironment: some View {
    let items = environment
    let lead = items.first { $0.id == "machine" && $0.tint == "warning" }
      ?? items.first { ["browser", "simulator"].contains($0.id) }
      ?? items.first { $0.id == "tasks" }
      ?? items[0]
    return Menu {
      ForEach(items) { item in
        Button { open(item) } label: { Label(item.accessibilityLabel, systemImage: item.symbol) }
          .disabled(item.id == "machine")
      }
    } label: {
      HStack(spacing: 2) {
        glyph(lead)
        Text("+\(items.count - 1)").font(.caption.weight(.semibold).monospacedDigit()).foregroundStyle(LodyColor.iconSecondary)
          .padding(.trailing, 8)
      }
      .contentShape(Capsule())
    }
    .buttonStyle(.plain)
    .padding(.horizontal, 2)
    .glassEffect(.regular, in: .capsule)
    .fixedSize()
    .accessibilityLabel(items.map(\.accessibilityLabel).joined(separator: ", "))
    .accessibilityIdentifier("session-bar:environment")
  }

  private func open(_ item: ChatActionBarState.Item) {
    if item.id == "tasks" { onTasks() } else { onAction(item.id) }
  }

  @ViewBuilder private func item(_ item: ChatActionBarState.Item) -> some View {
    let actions = item.actions ?? []
    Group {
      if actions.isEmpty {
        Button { open(item) } label: { glyph(item) }
      } else if item.tap == true {
        Menu { menu(actions, prefix: item.id, title: item.menuTitle) } label: { glyph(item) } primaryAction: { open(item) }
      } else {
        Menu { menu(actions, prefix: item.id, title: item.menuTitle) } label: { glyph(item) }
      }
    }
    .buttonStyle(.plain)
    .accessibilityLabel(item.accessibilityLabel)
    .accessibilityIdentifier(Self.identifier(item.id))
  }

  private static func identifier(_ id: String) -> String {
    switch id {
    case "browser", "simulator": "session-preview"
    case "tasks": "session-subtasks"
    default: "session-bar:" + id
    }
  }

  private func glyph(_ item: ChatActionBarState.Item) -> some View {
    HStack(spacing: 3) {
      if item.busy == true {
        ProgressView().controlSize(.small)
      } else {
        Image(systemName: item.symbol).foregroundStyle(Self.tint(item.tint))
      }
      if let badge = item.badge {
        Text(badge).monospacedDigit().foregroundStyle(LodyColor.iconSecondary)
      }
    }
    .padding(.horizontal, item.badge == nil ? 0 : 6)
    .frame(minWidth: ChatActionBarView.rowHeight, minHeight: ChatActionBarView.rowHeight)
    .contentShape(Rectangle())
  }

  // MARK: Context

  /// PR or branch with the ±diff. Long labels are capped; once space runs short the diff becomes
  /// a dot and its counts move to the top of the menu.
  private func contextCapsule(diffDot: Bool) -> some View {
    let context = state.context
    var actions = context?.actions ?? []
    if let diff = state.diff, diff.tap == true {
      actions.insert(.init(
        id: "diff", title: String(localized: "workspaceChanges.menuTitle", bundle: .lody), symbol: "plus.forwardslash.minus"
      ), at: 0)
    }
    let label = HStack(spacing: 6) {
      if let context {
          Image(systemName: context.symbol).foregroundStyle(Self.tint(context.tint))
          Text(context.label).lineLimit(1).truncationMode(.middle).foregroundStyle(LodyColor.textPrimary)
            .frame(maxWidth: 140, alignment: .leading).fixedSize(horizontal: !diffDot, vertical: false)
        }
        if let diff = state.diff {
          if diffDot {
            Circle().fill(LodyColor.diffAddedText).frame(width: 6, height: 6)
          } else {
            HStack(spacing: 4) {
              Text("+\(diff.added)").foregroundStyle(LodyColor.diffAddedText)
              Text("−\(diff.removed)").foregroundStyle(LodyColor.diffRemovedText)
            }
            .font(.subheadline.weight(.medium).monospacedDigit())
            .fixedSize()
          }
        }
      }
      .padding(.horizontal, 12)
      .frame(minHeight: ChatActionBarView.rowHeight)
      .contentShape(Capsule())
    return ContextActionsPopover(
      actions: actions, title: context?.menuTitle, diff: state.diff,
      primaryAction: context?.tap == true ? { onAction("context:pr") } : nil,
      onAction: { onAction("context:" + $0) }, label: label
    )
    .buttonStyle(.plain)
    .glassEffect(.regular, in: .capsule)
    .accessibilityLabel([context?.accessibilityLabel, state.diff?.accessibilityLabel].compactMap { $0 }.joined(separator: ", "))
    .accessibilityIdentifier("session-bar:context")
  }

  // MARK: Action

  /// Web's primary action + chevron as one neutral capsule with a soft internal divider. The
  /// label never truncates.
  private func primaryCapsule(_ primary: ChatActionBarState.Primary) -> some View {
    HStack(spacing: 0) {
      Button {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        onAction("act")
      } label: {
        Text(primary.label)
          .fontWeight(.semibold)
          .lineLimit(1)
          .padding(.leading, 14)
          .padding(.trailing, primary.more.isEmpty ? 14 : 8)
          .frame(minHeight: ChatActionBarView.rowHeight)
          .contentShape(Rectangle())
      }
      .accessibilityIdentifier("session-bar:act")
      if !primary.more.isEmpty {
        Divider().frame(height: 16)
        Menu { menu(primary.more, prefix: "more", title: nil) } label: {
          Image(systemName: "chevron.down")
            .font(.footnote.weight(.semibold))
            .padding(.leading, 8)
            .padding(.trailing, 12)
            .frame(minHeight: ChatActionBarView.rowHeight)
            .contentShape(Rectangle())
        }
        .accessibilityLabel(primary.moreLabel)
        .accessibilityIdentifier("session-bar:more")
      }
    }
    .buttonStyle(.plain)
    .foregroundStyle(LodyColor.textPrimary)
    .fixedSize()
    .glassEffect(.regular.interactive(), in: .capsule)
  }

  // MARK: Menus

  @ViewBuilder private func menu(_ actions: [ChatMenuAction], prefix: String, title: String?) -> some View {
    if let title, !title.isEmpty {
      Section(title) { menuItems(actions, prefix: prefix) }
    } else {
      menuItems(actions, prefix: prefix)
    }
  }

  private func menuItems(_ actions: [ChatMenuAction], prefix: String) -> some View {
    ForEach(actions) { action in
      Button(role: action.destructive == true ? .destructive : nil) { onAction(prefix + ":" + action.id) } label: {
        Label {
          Text(action.title)
          if let subtitle = action.subtitle { Text(subtitle) }
        } icon: {
          Image(systemName: action.symbol)
        }
      }
      .disabled(action.disabled == true)
    }
  }

  static func tint(_ name: String?) -> Color {
    switch name {
    case "open": LodyColor.prOpen
    case "merged": LodyColor.prMerged
    case "closed": LodyColor.prClosed
    case "draft": LodyColor.prDraft
    case "warning": LodyColor.warningDefault
    case "danger": LodyColor.dangerDefault
    case "primary": LodyColor.textPrimary
    default: LodyColor.iconSecondary
    }
  }
}

/// UIKit host for the bar inside the composer. One row is `rowHeight`; at very large text the
/// stacked fallback is taller, so the composer asks for `fittingHeight(width:)`.
final class ChatActionBarView: UIView {
  static let rowHeight: CGFloat = 36

  private let host = UIHostingController(rootView: ChatActionBarContent())
  private var rendered: (state: ChatActionBarState, tasks: Int)?
  var onAction: ((String) -> Void)?
  var onTasks: (() -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    accessibilityIdentifier = "session-action-bar"
    host.safeAreaRegions = []
    host.sizingOptions = []
    host.view.backgroundColor = .clear
    host.view.isOpaque = false
    host.view.translatesAutoresizingMaskIntoConstraints = false
    addSubview(host.view)
    NSLayoutConstraint.activate([
      host.view.topAnchor.constraint(equalTo: topAnchor),
      host.view.leadingAnchor.constraint(equalTo: leadingAnchor),
      host.view.trailingAnchor.constraint(equalTo: trailingAnchor),
      host.view.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])
    alpha = 0
    isHidden = true
  }

  required init?(coder: NSCoder) { fatalError() }

  /// Whether the bar has anything to show for this state.
  static func shows(_ state: ChatActionBarState?, tasks: Int) -> Bool { !(state?.isEmpty ?? true) || tasks > 0 }

  /// The height the chosen stage needs at this width: one row, or two at very large text.
  func fittingHeight(width: CGFloat) -> CGFloat {
    guard width > 0 else { return Self.rowHeight }
    let size = host.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude))
    return max(Self.rowHeight, ceil(size.height))
  }

  func render(_ state: ChatActionBarState?, tasks: Int, visible: Bool, animated: Bool) {
    let state = state ?? ChatActionBarState()
    if rendered?.state != state || rendered?.tasks != tasks {
      rendered = (state, tasks)
      let content = ChatActionBarContent(
        state: state, tasks: tasks,
        onAction: { [weak self] in self?.onAction?($0) },
        onTasks: { [weak self] in self?.onTasks?() }
      )
      // Glass capsules morph between stages and as groups come and go.
      if animated && window != nil && !UIAccessibility.isReduceMotionEnabled {
        withAnimation(.smooth(duration: 0.3)) { host.rootView = content }
      } else {
        host.rootView = content
      }
    }
    let shown = visible && Self.shows(state, tasks: tasks)
    guard isUserInteractionEnabled != shown || isHidden == shown else { return }
    isUserInteractionEnabled = shown
    accessibilityElementsHidden = !shown
    if shown { isHidden = false }
    let update = { self.alpha = shown ? 1 : 0 }
    if animated && window != nil && !UIAccessibility.isReduceMotionEnabled {
      UIView.animate(withDuration: 0.2, delay: 0, options: [.beginFromCurrentState, .curveEaseOut], animations: update) { _ in
        self.isHidden = !self.isUserInteractionEnabled
      }
    } else {
      update()
      isHidden = !shown
    }
  }
}

/// Native popover content keeps semantic diff colours, which system menu titles discard.
private struct ContextActionsPopover<Label: View>: View {
  let actions: [ChatMenuAction]
  let title: String?
  let diff: ChatActionBarState.Diff?
  let primaryAction: (() -> Void)?
  let onAction: (String) -> Void
  let label: Label
  @State private var presented = false

  var body: some View {
    label
      .accessibilityElement(children: .ignore)
      .onTapGesture {
        if let primaryAction { primaryAction() } else { presented = true }
      }
      .onLongPressGesture { presented = true }
      .accessibilityAddTraits(.isButton)
      .popover(isPresented: $presented, arrowEdge: .bottom) {
        VStack(alignment: .leading, spacing: LodySpacing.xs) {
          if let title, !title.isEmpty {
            Text(title).font(.subheadline).foregroundStyle(LodyColor.textSecondary)
              .lineLimit(1).padding(.horizontal, LodySpacing.md)
              .padding(.vertical, LodySpacing.sm)
          }
          ForEach(actions) { action in
            Button {
              presented = false
              onAction(action.id)
            } label: {
              HStack(spacing: LodySpacing.md) {
                Image(systemName: action.symbol).frame(width: LodyIconSize.md)
                Text(action.title)
                if action.id == "diff", let diff {
                  Text("+\(diff.added)").foregroundStyle(LodyColor.diffAddedText)
                  Text("−\(diff.removed)").foregroundStyle(LodyColor.diffRemovedText)
                }
              }
              .font(.body.monospacedDigit())
              .lineLimit(1).fixedSize(horizontal: true, vertical: false)
              .padding(LodySpacing.md)
              .frame(maxWidth: .infinity, alignment: .leading)
              .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(LodyColor.textPrimary)
            .disabled(action.disabled == true)
          }
        }
        .padding(LodySpacing.sm)
        .fixedSize(horizontal: true, vertical: true)
        .presentationCompactAdaptation(.popover)
      }
  }
}
