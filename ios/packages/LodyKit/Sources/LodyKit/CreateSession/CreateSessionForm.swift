import LodyCore
import Foundation

struct CreateSessionSelection: Equatable {
  var chat: Bool { projectId.isEmpty }
  var projectId = ""
  var options: CreationOptions?
  var machineId = ""
  var agentKey = ""
  var choice = ModelChoice()
  var branch = ""
  var branches: CreateBranches?
  var branchesStale = false
  var branchesLoading = false
  var branchesFailed = false
  /// Local projects only: start the session in a new git worktree (web's workdir mode). GitHub
  /// sessions always run in a worktree.
  var worktree = false
  var loading = true
  var failed = false

  var target: String { chat ? CreateLogic.chatTarget : projectId }
  var github: Bool { !chat && projectId.hasPrefix("github:") }
  var local: Bool { !chat && !projectId.isEmpty && !github }

  // The workspace snapshot covers every machine; the selected project determines the target.
  var machines: [(id: String, name: String)] {
    var seen = Set<String>()
    return (options?.agents ?? []).compactMap { agent in
      seen.insert(agent.machineId).inserted ? (agent.machineId, agent.machineName) : nil
    }
  }
  var machine: (id: String, name: String)? {
    if machineId.isEmpty { return machines.first }
    return machines.first { $0.id == machineId }
  }
  var unavailableMessage: String? {
    if failed { return String(localized: "create.devices.failed", bundle: .lody) }
    if options?.availability == "unknown" { return String(localized: "devices.unknown", bundle: .lody) }
    if !machineId.isEmpty && machine == nil {
      return String(localized: String.LocalizationValue(!chat && !github ? "create.devices.projectOffline" : "create.devices.selectedOffline"), bundle: .lody)
    }
    if options?.availability == "offline" {
      return String(localized: String.LocalizationValue(!chat && !github ? "create.devices.projectOffline" : "create.devices.offline"), bundle: .lody)
    }
    if !agents.isEmpty && agent == nil { return String(localized: "create.devices.chooseAgent", bundle: .lody) }
    if options != nil && agent == nil { return String(localized: "create.devices.noAgents", bundle: .lody) }
    return nil
  }
  var agents: [CreationAgent] { (options?.agents ?? []).filter { $0.machineId == machine?.id } }
  var agent: CreationAgent? { agents.first { $0.key == agentKey } }
  var capability: Capability? { CreateLogic.capabilityFor(options, agent) }
}

struct CreateSessionDraft: Codable, Equatable {
  var sessionId: String
  var userId: String
  var workspaceId: String
  var projectId: String?
  var projectName: String
  var branch: String?
  var agent: CreationAgent
  var choice: ModelChoice
  var reasoningEffortConfigId: String?
  /// Local project in a new git worktree; nil keeps drafts saved before the field decodable.
  var useWorktree: Bool?
}

struct CreateSessionForm {
  var userId = ""
  var workspaceId = ""
  var projects: [CreateProject] = []
  var machineNames: [String: String] = [:]
  /// Computer id to OS, for the computer's icon.
  var machineOs: [String: String] = [:]
  var prefs: CreatePrefs?
  var locked = false
  // Share Extension: a selection without cached options is sent and finished in the app.
  var deferUnresolved = false
  var signedOut = false
  var current = CreateSessionSelection()

  var prefsTarget: String { current.target }

  mutating func open(projectId: String?, context: String?) {
    let selected = context == "chat" ? nil : (projectId
      ?? CreateLogic.rememberedProject(prefs, projects)
      ?? projects.first?.id)
    locked = projectId != nil && context != "chat"
    current = CreateSessionSelection(projectId: selected ?? "")
    if current.local { current.machineId = projects.first { $0.id == selected }?.machineId ?? "" }
  }

  mutating func applyCachedOptions(_ options: CreationOptions) {
    applyOptions(options)
    // Cache is for display only; live validation still controls Send.
    current.loading = true
  }

  mutating func applyOptions(_ options: CreationOptions) {
    let previous = current
    current.options = options
    current.loading = false
    current.failed = false
    let restored = CreateLogic.restoreSelection(prefs, target: current.target, options: options)
    current.machineId = restored.machineId
    current.agentKey = restored.agentKey
    current.choice = restored.choice
    // Preserve a computer picked while loading, including before the cache arrives.
    if !previous.machineId.isEmpty {
      current.machineId = previous.machineId
      current.agentKey = previous.agentKey
      if previous.agentKey.isEmpty {
        if current.agents.contains(where: { $0.key == restored.agentKey }) {
          current.agentKey = restored.agentKey
          current.choice = restored.choice
          return
        }
        current.agentKey = current.agents.first?.key ?? ""
      }
      current.choice = previous.choice
      if let agent = current.agent {
        current.choice = CreateLogic.rememberedModelChoice(
          prefs, agentKey: agent.key, capability: current.capability, modelId: previous.choice.modelId)
      }
    }
  }

  mutating func failOptions() {
    current.loading = false
    current.failed = true
  }

  /// One form and one workspace-wide options snapshot; changing project only changes selection.
  mutating func selectProject(_ picked: CreateProject?) {
    if let picked, !projects.contains(where: { $0.id == picked.id }) { projects.append(picked) }
    let id = picked?.id ?? ""
    guard id != current.projectId else { return }
    current.projectId = id
    current.branch = ""
    current.branches = nil
    current.branchesStale = false
    current.branchesLoading = false
    current.branchesFailed = false
    current.worktree = false
    if current.local, let picked, picked.machineId != current.machineId { selectMachine(picked.machineId) }
    var value = prefs ?? CreatePrefs()
    value.context = current.chat ? "chat" : "project"
    if let picked { value.projectId = picked.id }
    prefs = value
    updateChoice(current.choice)
  }

  mutating func applyCachedBranches(_ value: CreateBranches) {
    applyBranches(value)
    current.branchesStale = true
  }

  mutating func selectBranch(_ name: String) {
    guard current.branches?.names.contains(name) == true else { return }
    current.branch = name
    if prefs == nil { prefs = CreatePrefs() }
    if prefs?.branches == nil { prefs?.branches = [:] }
    prefs?.branches?[current.projectId] = name
  }

  mutating func applyBranches(_ value: CreateBranches, replacing: Bool = false) {
    if current.branches == nil || replacing { current.branches = value }
    else { current.branches?.append(value) }
    current.branchesStale = false
    current.branchesLoading = false
    current.branchesFailed = false
    if current.branch.isEmpty, let remembered = prefs?.branches?[current.projectId],
      current.branches?.names.contains(remembered) == true { current.branch = remembered }
    if current.branch.isEmpty, let name = current.branches?.defaultBranch,
      current.branches?.names.contains(name) == true { current.branch = name }
  }

  mutating func selectMachine(_ id: String) {
    current.machineId = id
    let first = current.options?.agents.first { $0.machineId == id }
    current.agentKey = first?.key ?? ""
    updateChoice(CreateLogic.rememberedModelChoice(
      prefs, agentKey: first?.key ?? "", capability: CreateLogic.capabilityFor(current.options, first)))
  }

  mutating func selectAgent(_ key: String) {
    current.agentKey = key
    let selected = current.options?.agents.first { $0.key == key }
    updateChoice(CreateLogic.rememberedModelChoice(
      prefs, agentKey: key, capability: CreateLogic.capabilityFor(current.options, selected)))
  }

  mutating func selectModel(_ modelId: String?) {
    updateChoice(CreateLogic.rememberedModelChoice(
      prefs, agentKey: current.agentKey, capability: current.capability, modelId: modelId))
  }

  mutating func updateChoice(_ next: ModelChoice) {
    current.choice = next
    guard let agent = current.agent else { return }
    prefs = CreateLogic.withSelection(prefs, target: prefsTarget, selection: ProjectPrefs(
      modelId: next.modelId, effort: next.effort, modeId: next.modeId, configOptionValues: next.configOptionValues,
      machineId: agent.machineId, agentKey: agent.key), chat: current.chat)
  }

  var canSend: Bool {
    !userId.isEmpty && !current.loading &&
      (deferUnresolved || (!current.failed && current.agent != nil && (!current.github || !current.branch.isEmpty)))
  }

  var notice: String {
    if signedOut { return "" }
    if !deferUnresolved, current.unavailableMessage != nil { return "" }
    if current.loading { return String(localized: "create.composer.loading", bundle: .lody) }
    if current.agent == nil {
      return String(localized: String.LocalizationValue(deferUnresolved ? "native.share.deferred" : "create.composer.needAgent"), bundle: .lody)
    }
    return ""
  }

  func draft(sessionId: String) -> CreateSessionDraft? {
    let page = current
    guard let agent = page.agent, !userId.isEmpty, !page.github || !page.branch.isEmpty else { return nil }
    let project = projects.first { $0.id == page.projectId }
    return CreateSessionDraft(
      sessionId: sessionId,
      userId: userId,
      workspaceId: workspaceId,
      projectId: page.chat ? nil : page.projectId,
      projectName: page.chat ? "" : (project?.name ?? page.options?.project?.name ?? ""),
      branch: page.github ? page.branch.trimmingCharacters(in: .whitespaces) : nil,
      agent: agent,
      choice: page.choice,
      reasoningEffortConfigId: page.capability?.reasoningEffortConfigId,
      useWorktree: page.local && page.worktree ? true : nil
    )
  }
}
