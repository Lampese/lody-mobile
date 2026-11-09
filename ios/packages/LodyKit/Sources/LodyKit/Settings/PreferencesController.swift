import UIKit

@MainActor
final class PreferencesController: UIViewController {
  private let list = LodyGroupedList(frame: .zero)
  private var observer: NSObjectProtocol?

  init() {
    super.init(nibName: nil, bundle: nil)
    title = text("preferences.title")
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  isolated deinit { observer.map(NotificationCenter.default.removeObserver) }

  override func viewDidLoad() {
    super.viewDidLoad()
    list.frame = view.bounds
    list.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    list.forwarded = { [weak self] name, body in self?.event(name, body) }
    view.addSubview(list)
    observer = NotificationCenter.default.addObserver(
      forName: .lodyPreferencesDidChange, object: nil, queue: .main
    ) { [weak self] _ in
      MainActor.assumeIsolated { self?.render() }
    }
    render()
  }

  private func render() {
    let language = text("preferences.languageName")
    let current = LodyPreferences.queuedMessageBehavior
    var queued = LodyListRow.item(
      "queued-message-behavior", text("queuedMessageBehavior.title"), value: text("queuedMessageBehavior.\(current)"),
      image: "arrow.uturn.forward"
    )
    queued.options = ["queue", "guide"].map { option($0, text("queuedMessageBehavior.\($0)"), selected: $0 == current) }
    var developer = LodyListRow.item("developer-mode", text("developerMode"), value: text(LodyPreferences.developerModeEnabled ? "on" : "off"), image: "hammer")
    developer.options = ["off", "on"].map { option($0, text($0), selected: ($0 == "on") == LodyPreferences.developerModeEnabled) }
    var roost = LodyListRow.item("roost-history", text("roostHistory"), value: text(LodyPreferences.roostHistoryExperimentEnabled ? "on" : "off"), image: "externaldrive")
    roost.options = ["off", "on"].map { option($0, text($0), selected: ($0 == "on") == LodyPreferences.roostHistoryExperimentEnabled) }
    var sections: [LodyListSection] = [
      .group("language-section", footer: text("preferences.languageHint"), [
        .item(
          "language", text("preferences.language"), value: language, image: "globe", disclosure: true,
          accessibilityValue: language
        ),
      ]),
      .group("notifications-section", header: text("preferences.notifications"), [
        .item("notifications", text("notifications.title"), image: "bell", disclosure: true, navigates: true),
      ]),
      .group("chat", header: text("preferences.chat"), footer: text("queuedMessageBehavior.hint"), [
        queued,
      ]),
      .group("developer", [developer]),
    ]
    if LodyPreferences.developerModeEnabled {
      sections.append(.group("beta", header: text("betaFeatures"), footer: text("roostHistoryHint"), [roost]))
    }
    list.setSections(sections)
  }

  private func event(_ name: String, _ body: [String: Any]) {
    guard let id = body["id"] as? String else { return }
    switch (name, id, body["actionId"] as? String) {
    case ("rowAction", "developer-mode", let value?) where ["on", "off"].contains(value):
      LodyPreferences.saveDeveloperMode(value == "on")
    case ("rowAction", "roost-history", let value?) where ["on", "off"].contains(value):
      LodyPreferences.saveRoostHistoryExperiment(value == "on")
    case ("rowAction", "queued-message-behavior", let value?) where ["queue", "guide"].contains(value):
      LodyPreferences.saveQueuedMessageBehavior(value)
    case ("rowPress", "language", _):
      openLanguageSettings()
    case ("rowPress", "notifications", _):
      LodyShell.shared.openPage("notification-settings", from: self)
    default:
      break
    }
  }

  private func openLanguageSettings() {
    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
    UIApplication.shared.open(url) { opened in
      guard !opened else { return }
      LodyToastOverlay.shared.show(message: String(localized: "settings.preferences.languageFailed", bundle: .lody), kind: "error")
    }
  }

  private func option(_ id: String, _ title: String, selected: Bool) -> LodyListAction {
    var action = LodyListAction()
    action.id = id
    action.title = title
    action.selected = selected
    return action
  }

  private func text(_ key: String) -> String { String(localized: String.LocalizationValue("settings." + key), bundle: .lody) }
}
