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

/// Web `SessionInfoBar` for the native composer: one Liquid Glass panel above the input.
/// Leading: the computer and live resources (browser preview, simulator, sub-tasks) as icons;
/// then the repository context and ±diff; trailing: the repository action as a neutral split
/// control. Colour is kept for status (PR state, diff), so Send stays the one accent on screen.
struct ChatActionBarState: Decodable, Equatable {
  struct Item: Decodable, Equatable, Identifiable {
    let id: String
    let symbol: String
    var tint: String?
    /// Connecting: a spinner replaces the glyph.
    var busy: Bool?
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

struct ChatActionBarContent: View {
  var state = ChatActionBarState()
  var tasks = 0
  var onAction: (String) -> Void = { _ in }
  var onTasks: () -> Void = {}

  var body: some View {
    HStack(spacing: 2) {
      ForEach(state.items ?? []) { item($0) }
      if tasks > 0 { tasksItem }
      if hasLeading && (state.context != nil || state.diff != nil) {
        Divider().frame(height: 18).padding(.horizontal, 4)
      }
      if let context = state.context { contextItem(context) }
      if let diff = state.diff { diffItem(diff) }
      Spacer(minLength: 8)
      if let primary = state.primary { primaryItem(primary) }
    }
    .font(.subheadline)
    .padding(.horizontal, 6)
    .frame(height: ChatActionBarView.height)
    .glassEffect(.regular, in: .capsule)
  }

  private var hasLeading: Bool { !(state.items ?? []).isEmpty || tasks > 0 }

  // MARK: Leading icons

  @ViewBuilder private func item(_ item: ChatActionBarState.Item) -> some View {
    let actions = item.actions ?? []
    Group {
      if actions.isEmpty {
        Button { onAction(item.id) } label: { glyph(item) }
      } else if item.tap == true {
        Menu { menu(actions, prefix: item.id, title: item.menuTitle) } label: { glyph(item) } primaryAction: { onAction(item.id) }
      } else {
        Menu { menu(actions, prefix: item.id, title: item.menuTitle) } label: { glyph(item) }
      }
    }
    .buttonStyle(.plain)
    .accessibilityLabel(item.accessibilityLabel)
    .accessibilityIdentifier(["browser", "simulator"].contains(item.id) ? "session-preview" : "session-bar:" + item.id)
  }

  private func glyph(_ item: ChatActionBarState.Item) -> some View {
    ZStack {
      if item.busy == true {
        ProgressView().controlSize(.small)
      } else {
        Image(systemName: item.symbol).foregroundStyle(Self.tint(item.tint))
      }
    }
    .frame(width: 36, height: 36)
    .contentShape(Rectangle())
  }

  private var tasksItem: some View {
    let label = String.localizedStringWithFormat(String(localized: "native.chat.overlay.tasks", bundle: .lody), tasks)
    return Button(action: onTasks) {
      HStack(spacing: 3) {
        Image(systemName: "point.3.connected.trianglepath.dotted")
        Text("\(tasks)").monospacedDigit()
      }
      .foregroundStyle(LodyColor.iconSecondary)
      .padding(.horizontal, 6)
      .frame(minWidth: 36, minHeight: 36)
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityLabel(label)
    .accessibilityIdentifier("session-subtasks")
  }

  // MARK: Context and diff

  private func contextItem(_ context: ChatActionBarState.Context) -> some View {
    Menu { menu(context.actions, prefix: "context", title: context.menuTitle) } label: {
      HStack(spacing: 5) {
        Image(systemName: context.symbol).foregroundStyle(Self.tint(context.tint))
        // The context gives way first when the bar is short.
        Text(context.label).lineLimit(1).truncationMode(.middle).foregroundStyle(LodyColor.textPrimary)
      }
      .padding(.horizontal, 6)
      .frame(minHeight: 36)
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .layoutPriority(0)
    .accessibilityLabel(context.accessibilityLabel)
    .accessibilityIdentifier("session-bar:context")
  }

  private func diffItem(_ diff: ChatActionBarState.Diff) -> some View {
    Button { onAction("diff") } label: {
      HStack(spacing: 4) {
        Text("+\(diff.added)").foregroundStyle(LodyColor.diffAddedText)
        Text("−\(diff.removed)").foregroundStyle(LodyColor.diffRemovedText)
      }
      .font(.subheadline.weight(.medium).monospacedDigit())
      .padding(.horizontal, 8)
      .frame(minHeight: 28)
      .background(Capsule().fill(LodyColor.fillTertiary))
      .frame(minHeight: 36)
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .disabled(diff.tap != true)
    .fixedSize()
    .layoutPriority(1)
    .accessibilityLabel(diff.accessibilityLabel)
    .accessibilityIdentifier("session-bar:diff")
  }

  // MARK: Primary action

  /// Web's primary action + chevron as one neutral surface with a soft internal divider. The
  /// action label never truncates: the context yields first.
  private func primaryItem(_ primary: ChatActionBarState.Primary) -> some View {
    HStack(spacing: 0) {
      Button {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        onAction("act")
      } label: {
        Text(primary.label)
          .fontWeight(.semibold)
          .lineLimit(1)
          .padding(.leading, 12)
          .padding(.trailing, primary.more.isEmpty ? 12 : 8)
          .frame(minHeight: 32)
          .contentShape(Rectangle())
      }
      .accessibilityIdentifier("session-bar:act")
      if !primary.more.isEmpty {
        Divider().frame(height: 16)
        Menu { menu(primary.more, prefix: "more", title: nil) } label: {
          Image(systemName: "chevron.down")
            .font(.footnote.weight(.semibold))
            .padding(.horizontal, 10)
            .frame(minHeight: 32)
            .contentShape(Rectangle())
        }
        .accessibilityLabel(primary.moreLabel)
        .accessibilityIdentifier("session-bar:more")
      }
    }
    .buttonStyle(.plain)
    .foregroundStyle(LodyColor.textPrimary)
    .background(Capsule().fill(LodyColor.fillSecondary))
    .fixedSize()
    .layoutPriority(2)
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

/// UIKit host for the bar inside the composer: fixed height, fades in and out with its content.
final class ChatActionBarView: UIView {
  static let height: CGFloat = 44

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

  func render(_ state: ChatActionBarState?, tasks: Int, visible: Bool, animated: Bool) {
    let state = state ?? ChatActionBarState()
    if rendered?.state != state || rendered?.tasks != tasks {
      rendered = (state, tasks)
      host.rootView = ChatActionBarContent(
        state: state, tasks: tasks,
        onAction: { [weak self] in self?.onAction?($0) },
        onTasks: { [weak self] in self?.onTasks?() }
      )
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
