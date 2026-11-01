import LodyDesignSystem
import UIKit

/// Where a new session runs, shown above the composer of the full-screen new-session page:
/// computer, project (or a plain chat), branch for GitHub projects, the worktree switch for local
/// projects, and assistant. The page has room, so the values use the title style. Each line is an
/// icon and a value; one with a choice carries the system pop-up indicator. Short lists (computers,
/// assistants, project vs chat) open as native menus in place; long, searchable ones (projects,
/// branches) push their picker. The composer below stays the shared web-style one.
struct CreateSessionContextRow: Equatable {
  enum Icon: Equatable {
    case lody(LodyIcon)
    /// An agent mark from the app's asset catalog, drawn in its own colours.
    case asset(String)
  }

  enum Choice: Equatable {
    /// Not changeable here (a locked project, a single computer).
    case none
    /// Tapping pushes a picker.
    case push
    /// Tapping opens a menu of these options.
    case menu([Option])
    /// A switch at the trailing edge.
    case toggle(Bool)
  }

  struct Option: Equatable {
    var id: String
    var title: String
    var subtitle = ""
    var selected = false
    var symbol = ""
  }

  var id: String
  var icon: Icon
  var title: String
  var placeholder = false
  var loading = false
  var choice: Choice = .none
}

enum CreateSessionContext {
  static let chatOption = "chat"
  static let pickProjectOption = "pick-project"

  private static func text(_ key: String) -> String { String(localized: String.LocalizationValue(key), bundle: .lody) }

  static func rows(_ form: CreateSessionForm) -> [CreateSessionContextRow] {
    let page = form.current
    var rows: [CreateSessionContextRow] = []

    let machines = page.machines
    let machineName = page.machine?.name ?? form.machineNames[page.machineId]
    rows.append(CreateSessionContextRow(
      id: "machine", icon: .lody(.machine),
      title: machineName ?? (page.loading ? text("common.reading") : text("create.row.selectMachine")),
      placeholder: machineName == nil, loading: page.loading && machineName == nil,
      choice: machines.count > 1
        ? .menu(machines.map { .init(id: $0.id, title: $0.name, selected: $0.id == page.machine?.id) })
        : .none
    ))

    if page.chat {
      rows.append(CreateSessionContextRow(
        id: "project", icon: .lody(.session), title: text("create.context.chat"),
        choice: form.locked ? .none : projectMenu(chat: true)
      ))
    } else {
      let project = form.projects.first { $0.id == page.projectId }
      rows.append(CreateSessionContextRow(
        id: "project", icon: .lody(page.github ? .github : .folder),
        title: project?.name ?? text("create.row.selectProject"), placeholder: project == nil,
        choice: form.locked ? .none : projectMenu(chat: false)
      ))
      if page.github {
        var branch = page.branch
        var placeholder = false
        if branch.isEmpty {
          placeholder = true
          branch = text(page.branchesLoading ? "create.context.branchLoading" : page.branchesFailed ? "create.branch.unavailable" : "create.branch.placeholder")
        }
        rows.append(CreateSessionContextRow(
          id: "branch", icon: .lody(.branch), title: branch, placeholder: placeholder,
          loading: page.branchesLoading && page.branch.isEmpty, choice: .push
        ))
      }
      if page.local {
        rows.append(CreateSessionContextRow(
          id: "worktree", icon: .lody(.branch), title: text("create.context.worktree"),
          placeholder: !page.worktree, choice: .toggle(page.worktree)
        ))
      }
    }

    let agent = page.agent
    let agentIcon = LodyAgentIcon.asset(modelId: agent?.agentType, name: nil).map(CreateSessionContextRow.Icon.asset) ?? .lody(.agent)
    rows.append(CreateSessionContextRow(
      id: "agent", icon: agentIcon,
      title: agent?.name ?? text(page.loading ? "common.reading" : "create.row.selectAgent"),
      placeholder: agent == nil, loading: page.loading && agent == nil,
      // A failed setup read retries on tap (the note says so).
      choice: page.failed ? .push
        : page.agents.count > 1 || (agent == nil && !page.agents.isEmpty)
        ? .menu(page.agents.map { .init(id: $0.key, title: $0.name, selected: $0.key == page.agentKey) })
        : .none
    ))
    return rows
  }

  /// Below the rows: why the session cannot start yet, or that the setup is still loading.
  static func note(_ form: CreateSessionForm) -> (text: String, warning: Bool)? {
    let page = form.current
    if form.deferUnresolved && page.options == nil { return (text("native.share.deferred"), false) }
    if let message = form.deferUnresolved ? nil : page.unavailableMessage { return (message, true) }
    if page.failed { return (text("create.machineConfig.retry"), false) }
    return nil
  }

  private static func projectMenu(chat: Bool) -> CreateSessionContextRow.Choice {
    .menu([
      .init(id: pickProjectOption, title: text("create.context.pickProject"), selected: !chat, symbol: "folder"),
      .init(id: chatOption, title: text("create.context.chat"), selected: chat, symbol: "bubble.left"),
    ])
  }
}

@MainActor
final class CreateSessionContextView: UIView {
  /// A row without a menu was tapped, or a menu option was picked (row id, option id).
  var onPress: ((String) -> Void)?
  var onPick: ((String, String) -> Void)?
  var onToggle: ((String, Bool) -> Void)?

  private let stack = UIStackView()
  private let note = UILabel()
  private var buttons: [String: UIButton] = [:]
  private var switches: [String: UISwitch] = [:]
  private var rows: [CreateSessionContextRow] = []

  override init(frame: CGRect) {
    super.init(frame: frame)
    stack.axis = .vertical
    stack.alignment = .leading
    stack.spacing = 0
    stack.translatesAutoresizingMaskIntoConstraints = false
    addSubview(stack)
    note.font = LodyTextStyle.footnote.uiFont
    note.adjustsFontForContentSizeCategory = true
    note.numberOfLines = 0
    note.isHidden = true
    NSLayoutConstraint.activate([
      stack.topAnchor.constraint(equalTo: topAnchor),
      stack.leadingAnchor.constraint(equalTo: leadingAnchor),
      stack.trailingAnchor.constraint(equalTo: trailingAnchor),
      stack.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  func update(rows next: [CreateSessionContextRow], note text: (text: String, warning: Bool)?) {
    if next.map(\.id) != rows.map(\.id) {
      stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
      buttons = [:]
      switches = [:]
      for row in next {
        let button = UIButton(type: .system)
        button.contentHorizontalAlignment = .leading
        button.accessibilityIdentifier = "create-context-\(row.id)"
        button.addAction(UIAction { [weak self] _ in self?.pressed(row.id) }, for: .primaryActionTriggered)
        buttons[row.id] = button
        guard case .toggle = row.choice else {
          stack.addArrangedSubview(button)
          continue
        }
        // The switch row spans the block so the switch sits trailing; the label stays inert.
        let toggle = UISwitch()
        toggle.onTintColor = LodyUIColor.accentSolid
        toggle.accessibilityIdentifier = "create-context-\(row.id)-switch"
        toggle.addAction(UIAction { [weak self, weak toggle] _ in
          guard let toggle else { return }
          self?.onToggle?(row.id, toggle.isOn)
        }, for: .valueChanged)
        switches[row.id] = toggle
        let line = UIStackView(arrangedSubviews: [button, toggle])
        line.alignment = .center
        line.spacing = LodySpacing.md
        stack.addArrangedSubview(line)
        line.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
      }
      if let last = stack.arrangedSubviews.last { stack.setCustomSpacing(LodySpacing.xs, after: last) }
      stack.addArrangedSubview(note)
    }
    for row in next where rows.first(where: { $0.id == row.id }) != row {
      if let button = buttons[row.id] { configure(button, row) }
    }
    rows = next
    note.text = text?.text
    note.textColor = text?.warning == true ? LodyUIColor.warningDefault : LodyUIColor.textTertiary
    note.isHidden = text == nil
  }

  private func configure(_ button: UIButton, _ row: CreateSessionContextRow) {
    var config = UIButton.Configuration.plain()
    config.contentInsets = NSDirectionalEdgeInsets(top: LodySpacing.md, leading: 0, bottom: LodySpacing.md, trailing: LodySpacing.sm)
    config.imagePadding = LodySpacing.md
    config.imagePlacement = .leading
    config.showsActivityIndicator = row.loading
    config.activityIndicatorColorTransformer = UIConfigurationColorTransformer { _ in LodyUIColor.iconTertiary }
    switch row.icon {
    case .lody(let icon):
      config.image = icon.uiImage?.withRenderingMode(.alwaysTemplate)
      config.imageColorTransformer = UIConfigurationColorTransformer { _ in LodyUIColor.iconSecondary }
    case .asset(let name):
      config.image = UIImage(named: name, in: .main, compatibleWith: nil)?.withRenderingMode(.alwaysOriginal)
    }
    config.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(pointSize: LodyIconSize.lg)
    if let image = config.image {
      // Lucide assets are 24 pt; scale to the row's icon size so marks line up.
      config.image = image.resized(to: CGSize(width: LodyIconSize.lg, height: LodyIconSize.lg))
    }
    var title = AttributedString(row.title)
    title.font = LodyTextStyle.title.uiFont
    title.foregroundColor = row.placeholder ? LodyUIColor.textTertiary : LodyUIColor.textPrimary
    config.attributedTitle = title
    config.titleLineBreakMode = .byTruncatingMiddle
    switch row.choice {
    case .none:
      config.indicator = .none
      button.menu = nil
      button.showsMenuAsPrimaryAction = false
    case .push:
      config.indicator = .popup
      button.menu = nil
      button.showsMenuAsPrimaryAction = false
    case .toggle(let on):
      config.indicator = .none
      button.menu = nil
      button.showsMenuAsPrimaryAction = false
      if let toggle = switches[row.id], toggle.isOn != on { toggle.setOn(on, animated: true) }
      switches[row.id]?.accessibilityLabel = row.title
    case .menu(let options):
      config.indicator = .popup
      button.menu = UIMenu(options: .singleSelection, children: options.map { option in
        UIAction(
          title: option.title, subtitle: option.subtitle.isEmpty ? nil : option.subtitle,
          image: option.symbol.isEmpty ? nil : UIImage(systemName: option.symbol),
          state: option.selected ? .on : .off
        ) { [weak self] _ in self?.onPick?(row.id, option.id) }
      })
      button.showsMenuAsPrimaryAction = true
    }
    button.configuration = config
    let interactive: Bool = switch row.choice {
    case .none, .toggle: false
    default: true
    }
    button.isUserInteractionEnabled = interactive
    button.accessibilityTraits = interactive ? .button : .staticText
    if case .toggle = row.choice { button.accessibilityElementsHidden = true }
  }

  private func pressed(_ id: String) {
    guard let row = rows.first(where: { $0.id == id }), row.choice == .push else { return }
    onPress?(id)
  }
}

private extension UIImage {
  func resized(to size: CGSize) -> UIImage {
    UIGraphicsImageRenderer(size: size).image { _ in draw(in: CGRect(origin: .zero, size: size)) }.withRenderingMode(renderingMode)
  }
}
