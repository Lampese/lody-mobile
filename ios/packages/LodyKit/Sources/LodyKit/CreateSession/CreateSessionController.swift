import LodyCore
import LodyDesignSystem
import SwiftUI
import UIKit

@MainActor
final class CreateSessionController: UIViewController {
  var form = CreateSessionForm()
  var loadCachedOptions: ((_ projectId: String?) async -> CreationOptions?)?
  var loadOptions: ((_ projectId: String?) async throws -> CreationOptions)?
  var loadRepositories: (() async throws -> [CreateProject])?
  var loadBranches: ((_ repo: String, _ page: Int) async throws -> CreateBranches)?
  var onBranches: ((String, CreateBranches) -> Void)?
  private var branchTask: Task<Void, Never>?
  private weak var branchPicker: CreateBranchController?
  var browseProject: (() async -> CreateProject?)?
  var onPrefs: ((CreatePrefs) -> Void)?
  var onSelection: ((CreateSessionForm) -> Void)?
  var onSubmit: ((CreateSessionDraft?, [String: Any]) -> Void)?
  var onCancel: (() -> Void)?
  var showMessage: ((String) -> Void)?
  var restoreDraft: ((Int) -> Void)?
  private(set) var sending = false
  /// Pushed full screen: lead with a back button instead of the sheet's close button.
  var pushed = false
  private var restoreToken = 0

  let composer: ChatComposerView
  private let host: UIView
  /// Computer / project / worktree-or-branch cards with inline lists (SwiftUI), and the
  /// composer chip's run-config sheet, both driven by one state object.
  private let panelState = CreatePanelState()
  private lazy var context = UIHostingController(rootView: CreateContextPanel(state: panelState))
  private weak var runConfigSheet: UIViewController?
  private let signedOutLabel = UILabel()
  private lazy var hostHeight = host.heightAnchor.constraint(equalToConstant: 64)
  private var loads: [Bool: Task<Void, Never>] = [:]
  private var retryTimer: Timer?

  isolated deinit { retryTimer?.invalidate(); branchTask?.cancel() }

  init(composer: ChatComposerView, host: UIView) {
    self.composer = composer
    self.host = host
    super.init(nibName: nil, bundle: nil)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  override func viewDidLoad() {
    super.viewDidLoad()
    title = String(localized: "create.title", bundle: .lody)
    navigationItem.backButtonDisplayMode = .minimal
    let close = UIButton(type: .close)
    close.accessibilityLabel = String.localizedStringWithFormat(String(localized: "accessibility.closeSheet", bundle: .lody), String(describing: String(localized: "create.title", bundle: .lody)))
    close.addAction(UIAction { [weak self] _ in self?.onCancel?() }, for: .primaryActionTriggered)
    close.frame = CGRect(x: 0, y: 0, width: 30, height: 30)
    if pushed {
      let back = UIBarButtonItem(image: UIImage(systemName: "chevron.backward"), primaryAction: UIAction { [weak self] _ in self?.onCancel?() })
      back.accessibilityLabel = String(localized: "common.back", bundle: .lody)
      navigationItem.leftBarButtonItem = back
    } else {
      navigationItem.rightBarButtonItem = UIBarButtonItem(customView: close)
    }

    view.backgroundColor = LodyUIColor.bgBase
    panelState.onPick = { [weak self] id, option in self?.optionPicked(id, option) }
    panelState.onWorktree = { [weak self] on in
      guard let self, !self.sending else { return }
      self.form.project.worktree = on
      self.changed(prefs: false)
    }
    composer.onRunConfig = { [weak self] in self?.presentRunConfig() }
    addChild(context)
    context.view.backgroundColor = .clear
    context.view.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(context.view)
    context.didMove(toParent: self)
    signedOutLabel.text = String(localized: "native.share.signIn", bundle: .lody)
    signedOutLabel.font = LodyTextStyle.body.uiFont
    signedOutLabel.adjustsFontForContentSizeCategory = true
    signedOutLabel.textColor = LodyUIColor.textSecondary
    signedOutLabel.textAlignment = .center
    signedOutLabel.numberOfLines = 0
    signedOutLabel.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(signedOutLabel)

    host.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(host)
    let keyboard = host.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
    keyboard.priority = .defaultHigh
    NSLayoutConstraint.activate([
      host.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      host.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      host.bottomAnchor.constraint(lessThanOrEqualTo: view.bottomAnchor, constant: -16),
      keyboard, hostHeight,
      // The context fills the space above the composer, bottom-aligned within thumb reach, and
      // rides up with it; expanded lists grow upward and scroll when they run out of room.
      context.view.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
      context.view.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
      context.view.bottomAnchor.constraint(equalTo: host.topAnchor),
      context.view.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
      signedOutLabel.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),
      signedOutLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: LodyLayout.screenPaddingX * 2),
      signedOutLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -LodyLayout.screenPaddingX * 2),
    ])
    composer.setInputIdentifier("create-session-input")
    composer.setComposerState(CreateJSON.encode(["placeholder": String(localized: "create.composer.placeholder", bundle: .lody)]))
    render()
    load(chat: false)
    fetchBranches()
    if !form.locked { load(chat: true) }
    if !form.deferUnresolved {
      retryTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
        MainActor.assumeIsolated {
          guard let self, self.viewIfLoaded?.window != nil, !self.sending else { return }
          for chat in [false, true] where !chat || !self.form.locked {
            let page = chat ? self.form.chatPage : self.form.project
            if !page.loading && page.needsOptions { self.load(chat: chat) }
          }
        }
      }
    }
  }

  func setComposerHeight(_ height: CGFloat) {
    guard hostHeight.constant != height else { return }
    hostHeight.constant = height
    viewIfLoaded?.setNeedsLayout()
  }

  func render() {
    guard isViewLoaded else { return }
    let signedOut = form.signedOut
    signedOutLabel.isHidden = !signedOut
    context.view.isHidden = signedOut
    if !signedOut {
      let panel = CreateSessionContext.panel(form)
      if panelState.panel != panel { panelState.panel = panel }
      let rows = CreateSessionContext.runConfig(form)
      if panelState.runConfig != rows { panelState.runConfig = rows }
    }
    renderComposer()
  }

  /// Web's run-config sheet from the composer chip: Agent (when there is a choice), Model,
  /// Reasoning and Mode rows that expand in place.
  private func presentRunConfig() {
    guard !sending, runConfigSheet == nil, !panelState.runConfig.isEmpty else { return }
    panelState.openInSheet = nil
    let sheet = UIHostingController(rootView: CreateRunConfigSheet(state: panelState))
    if let presentation = sheet.sheetPresentationController {
      presentation.detents = [.medium(), .large()]
      presentation.prefersGrabberVisible = true
    }
    runConfigSheet = sheet
    present(sheet, animated: true)
  }

  /// A choice in the context panel or the run-config sheet.
  private func optionPicked(_ id: String, _ option: String) {
    guard !sending else { return render() }
    switch (id, option) {
    case ("project", CreateSessionContext.chatOption):
      guard !form.chat else { return }
      form.switchPage(chat: true)
      changed()
    case ("project", CreateSessionContext.moreOption):
      pickProject()
    case ("branch", CreateSessionContext.loadMoreOption):
      fetchBranches()
    case ("branch", _):
      guard form.project.branches?.names.contains(option) == true else { return }
      form.project.branch = option
      changed(prefs: false)
    case ("model", _):
      composerOptionChanged(["modelId": option, "effort": form.current.choice.effort ?? ""])
    case ("effort", _):
      composerOptionChanged(["modelId": form.current.choice.modelId ?? "", "effort": option])
    case ("permission", _):
      composerOptionChanged(["permissionId": option])
    case ("project", _):
      guard let project = form.projects.first(where: { $0.id == option }) else { return pickProject() }
      if form.chat { form.switchPage(chat: false) }
      apply(project)
    case ("machine", _):
      pickMachine(option)
    case ("agent", _):
      form.selectAgent(option)
      changed()
    default:
      break
    }
  }

  private func renderComposer() {
    let page = form.current
    let capability = page.capability
    let fast = CreateLogic.fastMode(capability, page.choice)
    var options: [String: Any] = [
      "modelId": page.choice.modelId ?? "", "effort": page.choice.effort ?? "",
      "models": (capability?.models ?? []).map { ["id": $0.id, "title": $0.name] },
      "efforts": CreateLogic.effortsFor(capability, modelId: page.choice.modelId).map { ["id": $0, "title": $0] },
    ]
    if let permission = CreateLogic.permissionMode(capability, page.choice) {
      options["permissionId"] = permission.value ?? ""
      options["permissions"] = permission.options.map { ["id": $0.id, "title": $0.name, "description": $0.description ?? ""] }
    }
    if let fast { options["fast"] = fast.enabled }
    if let agent = page.agent { options["agentName"] = agent.name }
    if let asset = LodyAgentIcon.asset(modelId: page.agent?.agentType, name: nil) { options["agentAsset"] = asset }
    composer.setComposerOptions(Self.json(options))
    composer.setComposerState(Self.json([
      "editable": true, "canSend": form.canSend, "sending": sending, "notice": form.notice,
      "reconnect": false, "placeholder": placeholder,
    ]))
  }

  /// Web's landing prompt: "Work with <assistant>" once one is chosen.
  private var placeholder: String {
    guard let agent = form.current.agent else { return String(localized: "create.composer.placeholder", bundle: .lody) }
    return String.localizedStringWithFormat(String(localized: "create.composer.placeholderAgent", bundle: .lody), agent.name)
  }

  private static func json(_ value: [String: Any]) -> String {
    guard let data = try? JSONSerialization.data(withJSONObject: value) else { return "{}" }
    return String(decoding: data, as: UTF8.self)
  }

  private func changed(prefs: Bool = true) {
    render()
    if prefs, let value = form.prefs { onPrefs?(value) }
    onSelection?(form)
  }

  func load(chat: Bool) {
    guard !sending else { return }
    var page = chat ? form.chatPage : form.project
    if !chat && page.projectId.isEmpty { return }
    page.loading = true
    if chat { form.chatPage = page } else { form.project = page }
    render()
    let projectId = chat ? nil : page.projectId
    loads[chat]?.cancel()
    loads[chat] = Task { [weak self] in
      do {
        if let self, (chat ? self.form.chatPage : self.form.project).options == nil,
          let cached = await self.loadCachedOptions?(projectId) {
          guard !Task.isCancelled, (chat ? nil : self.form.project.projectId) == projectId else { return }
          self.form.applyCachedOptions(cached, chat: chat)
          self.changed(prefs: false)
        }
        guard let options = try await self?.loadOptions?(projectId) else { return }
        guard let self, !Task.isCancelled, (chat ? nil : self.form.project.projectId) == projectId else { return }
        self.form.applyOptions(options, chat: chat)
        self.changed(prefs: false)
      } catch is CancellationError {
        return
      } catch {
        guard let self, !Task.isCancelled else { return }
        self.form.failOptions(chat: chat)
        self.changed(prefs: false)
      }
    }
  }

  func refreshOptions() {
    guard isViewLoaded, !sending, !form.deferUnresolved else { return }
    load(chat: false)
    if !form.locked { load(chat: true) }
  }

  private func rowPressed(_ id: String) {
    guard !sending else { return }
    let page = form.current
    switch id {
    case "project": pickProject()
    case "machine", "agent":
      guard !page.failed, page.options != nil, !page.machines.isEmpty else { return load(chat: form.chat) }
      if id == "agent" && page.agents.isEmpty { return load(chat: form.chat) }
      id == "machine" ? pickMachine() : pickAgent()
    case "model": pickModel()
    case "branch": pickBranch()
    default: break
    }
  }

  private func push(_ controller: UIViewController) {
    navigationController?.pushViewController(controller, animated: true)
  }

  private func pickProject() {
    let picker = CreateProjectPickerController(
      projects: form.projects, machineNames: form.machineNames, selectedId: form.project.projectId,
      loadRepositories: loadRepositories, browse: browseProject)
    picker.onPick = { [weak self, weak picker] project in
      guard let self else { return }
      picker?.navigationController?.popViewController(animated: true)
      if self.form.chat { self.form.switchPage(chat: false) }
      self.apply(project)
    }
    push(picker)
  }

  private func apply(_ project: CreateProject) {
    let changedProject = project.id != form.project.projectId
    form.selectProject(project)
    if changedProject {
      branchTask?.cancel()
      branchPicker = nil
      load(chat: false)
      fetchBranches()
    }
    changed(prefs: false)
  }

  /// A local project belongs to one computer: picking another moves to that computer's most
  /// recent project, or to a chat there when it has none. Chats and GitHub projects just switch.
  private func pickMachine(_ id: String) {
    let page = form.current
    guard page.local, let current = form.projects.first(where: { $0.id == page.projectId }), current.machineId != id else {
      form.selectMachine(id)
      return changed()
    }
    if let next = form.projects.first(where: { $0.machineId == id && !CreateProjectPicker.isGithub($0.id) }) {
      apply(next)
    } else {
      form.switchPage(chat: true)
      form.selectMachine(id)
      changed()
    }
  }

  private func pickMachine() {
    let page = form.current
    push(CreateChoiceController(
      title: String(localized: "create.row.selectMachine", bundle: .lody), header: String(localized: "create.label.machine", bundle: .lody),
      placeholder: String(localized: "create.picker.machine.placeholder", bundle: .lody),
      options: page.machines.map { ($0.id, $0.name, "") }, selected: page.machine?.id
    ) { [weak self] id in
      self?.form.selectMachine(id)
      self?.changed()
    })
  }

  private func pickAgent() {
    let page = form.current
    push(CreateChoiceController(
      title: String(localized: "create.row.selectAgent", bundle: .lody), header: String(localized: "create.label.agent", bundle: .lody),
      placeholder: String(localized: "create.picker.agent.placeholder", bundle: .lody),
      options: page.agents.map { ($0.key, $0.name, $0.machineName) }, selected: page.agentKey
    ) { [weak self] key in
      self?.form.selectAgent(key)
      self?.changed()
    })
  }

  private func pickModel() {
    let page = form.current
    guard let capability = page.capability else { return }
    let title = CreateModelOptions.hasTabs(capability)
      ? (page.agent?.name ?? String(localized: "model.title", bundle: .lody))
      : String(localized: "create.row.selectModel", bundle: .lody)
    let controller = CreateModelController(capability: capability, choice: page.choice)
    controller.title = title
    controller.choiceForModel = { [weak self] modelId in
      guard let self else { return ModelChoice(modelId: modelId) }
      return CreateLogic.rememberedModelChoice(
        self.form.prefs, agentKey: self.form.current.agentKey, capability: capability, modelId: modelId)
    }
    controller.onChange = { [weak self] choice in
      self?.form.updateChoice(choice)
      self?.changed()
    }
    push(controller)
  }

  func fetchBranches() {
    guard form.project.github, !form.project.branchesLoading, let loadBranches else { return }
    if form.project.branches != nil && (form.project.branches?.nextPage == nil || form.deferUnresolved) { return }
    let projectId = form.project.projectId
    let repo = String(projectId.dropFirst(7))
    let page = form.project.branches?.nextPage ?? 1
    form.project.branchesLoading = true
    form.project.branchesFailed = false
    render()
    updateBranchPicker()
    branchTask?.cancel()
    branchTask = Task { [weak self] in
      do {
        let value = try await loadBranches(repo, page)
        guard let self, !Task.isCancelled, self.form.project.projectId == projectId else { return }
        self.form.applyBranches(value)
        if let branches = self.form.project.branches { self.onBranches?(repo, branches) }
      } catch {
        guard let self, !Task.isCancelled, self.form.project.projectId == projectId else { return }
        self.form.project.branchesLoading = false
        self.form.project.branchesFailed = true
      }
      self?.changed(prefs: false)
      self?.updateBranchPicker()
    }
  }

  private func updateBranchPicker() {
    branchPicker?.update(form.project, cachedOnly: form.deferUnresolved)
  }

  private func pickBranch() {
    let projectId = form.project.projectId
    let picker = CreateBranchController(repo: String(projectId.dropFirst(7)))
    picker.onMore = { [weak self] in self?.fetchBranches() }
    picker.onPick = { [weak self] name in
      guard let self, self.form.project.projectId == projectId,
        self.form.project.branches?.names.contains(name) == true else { return }
      self.form.project.branch = name
      self.changed(prefs: false)
    }
    branchPicker = picker
    updateBranchPicker()
    push(picker)
  }

  func composerOptionChanged(_ body: [String: Any]) {
    let page = form.current
    if let permission = body["permissionId"] as? String {
      form.updateChoice(CreateLogic.withPermissionMode(page.capability, page.choice, value: permission))
    } else if let fast = body["fast"] as? Bool {
      form.updateChoice(CreateLogic.withFastMode(page.capability, page.choice, enabled: fast))
    } else {
      let modelId = (body["modelId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
      if modelId != page.choice.modelId {
        form.selectModel(modelId)
      } else {
        var next = page.choice
        next.effort = (body["effort"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        form.updateChoice(next)
      }
    }
    changed()
  }

  @discardableResult
  func submit(_ payload: [String: Any]) -> Bool {
    let text = payload["text"] as? String ?? ""
    let attachments = payload["attachments"] as? [Any] ?? []
    let draft = form.current.options.flatMap { form.draft(sessionId: $0.sessionId) }
    guard !sending, form.canSend, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !attachments.isEmpty,
      draft != nil || form.deferUnresolved
    else {
      restore()
      return false
    }
    if !form.deferUnresolved && form.current.github && form.current.branch.trimmingCharacters(in: .whitespaces).isEmpty {
      restore()
      message(String(localized: "create.toast.branchRequired", bundle: .lody))
      return false
    }
    form.updateChoice(form.current.choice)
    if let value = form.prefs { onPrefs?(value) }
    sending = true
    renderComposer()
    onSubmit?(draft, payload)
    return true
  }

  func submissionFailed() {
    sending = false
    restore()
    renderComposer()
  }

  private func restore() {
    restoreToken += 1
    if let restoreDraft { restoreDraft(restoreToken) } else { composer.restoreDraft(token: restoreToken) }
  }

  func message(_ text: String) {
    if let showMessage { return showMessage(text) }
    let alert = UIAlertController(title: text, message: nil, preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: String(localized: "common.ok", bundle: .lody), style: .default))
    present(alert, animated: true)
  }
}
