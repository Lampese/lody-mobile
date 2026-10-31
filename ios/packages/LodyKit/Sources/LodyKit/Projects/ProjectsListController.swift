import UIKit

/// iPhone Projects tab: every project with its open chat count and most urgent state.
/// Rows open the existing `ProjectController`; the trailing button and context menu reuse
/// the project actions from `SessionRows.projectMenu`.
@MainActor
final class ProjectsListController: UIViewController {
  let list = ChatListView(frame: .zero)
  private let source: SessionSource
  private lazy var actions = SessionActions(source: source)
  private var observers: [NSObjectProtocol] = []

  init(source: SessionSource = .live) {
    self.source = source
    super.init(nibName: nil, bundle: nil)
    title = HomeTab.projects.title
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  isolated deinit {
    for observer in observers { NotificationCenter.default.removeObserver(observer) }
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    list.frame = view.bounds
    list.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    list.accessibilityIdentifier = "home-projects-list"
    list.forwarded = { [weak self] name, body in self?.event(name, body) }
    view.addSubview(list)
    observers = (source.changes + [UIApplication.didBecomeActiveNotification]).map { name in
      NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
        MainActor.assumeIsolated { self?.render() }
      }
    }
    render()
  }

  private func render() {
    guard isViewLoaded else { return }
    let state = source.state
    guard !state.userId.isEmpty else {
      list.setPlaceholder("")
      list.setSections([])
      return
    }
    let key = state.loading ? "sessions.placeholder.loading" : "home.projects.empty"
    list.setPlaceholder(String(localized: String.LocalizationValue(key), bundle: .lody))
    list.setSections(InboxSections.projectIndex(state.catalog))
  }

  private func event(_ name: String, _ body: [String: Any]) {
    guard let id = body["id"] as? String else { return }
    switch name {
    case "rowPress": actions.open(id, from: self)
    case "rowAction":
      guard let action = body["actionId"] as? String else { return }
      actions.perform(action, row: id, from: self)
    default: break
    }
  }
}
