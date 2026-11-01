import LodyCore
import Foundation

extension LodyListRow {
  static func item(
    _ id: String, _ title: String, subtitle: String = "", value: String = "", image: String = "",
    imageAsset: String = "", action: Bool = true, disclosure: Bool = false, navigates: Bool = false,
    selected: Bool = false, accessibilityValue: String = "", toggle: Bool? = nil, subtitleMono: Bool = false
  ) -> LodyListRow {
    var row = LodyListRow()
    row.id = id
    row.title = title
    row.subtitle = subtitle
    row.value = value
    row.image = image
    row.imageAsset = imageAsset
    row.action = action
    row.disclosure = disclosure
    row.navigates = navigates
    row.selected = selected
    row.accessibilityValue = accessibilityValue
    row.toggle = toggle
    row.subtitleMono = subtitleMono
    return row
  }
}

extension LodyListSection {
  static func group(_ id: String, header: String = "", footer: String = "", _ rows: [LodyListRow]) -> LodyListSection {
    var section = LodyListSection()
    section.id = id
    section.header = header
    section.footer = footer
    section.rows = rows
    return section
  }
}

enum CreateSessionSections {
  private static func text(_ key: String) -> String { String(localized: String.LocalizationValue(key), bundle: .lody) }

  static func choices(
    _ options: [(id: String, title: String, subtitle: String)], header: String, selected: String?
  ) -> [LodyListSection] {
    [.group("options", header: header, options.map { option in
      .item(
        option.id, option.title, subtitle: option.subtitle, selected: option.id == selected,
        accessibilityValue: option.id == selected ? text("settings.history.selected") : "")
    })]
  }
}

enum CreateProjectPicker {
  static func isGithub(_ id: String) -> Bool { id.hasPrefix("github:") }

  static func filter(_ projects: [CreateProject], _ query: String) -> [CreateProject] {
    let term = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard !term.isEmpty else { return projects }
    return projects.filter { [$0.name, $0.rootPath, $0.id].joined(separator: " ").lowercased().contains(term) }
  }

  static func row(_ project: CreateProject, machineNames: [String: String], selected: String) -> LodyListRow {
    let github = isGithub(project.id)
    return .item(
      project.id, project.name,
      subtitle: [machineNames[project.machineId] ?? project.machineId, project.rootPath]
        .filter { !$0.isEmpty }.joined(separator: " · "),
      image: github ? "" : "folder", imageAsset: github ? "lody-mark-github" : "",
      selected: project.id == selected, subtitleMono: !project.rootPath.isEmpty)
  }

  static func githubStatusRows(loaded: Bool, failed: Bool, empty: Bool) -> [LodyListRow] {
    if !loaded {
      return [.item(
        "github-retry", String(localized: String.LocalizationValue(failed ? "projectPicker.githubFailed" : "common.reading"), bundle: .lody),
        image: failed ? "arrow.clockwise" : "", action: failed)]
    }
    if empty { return [.item("github-empty", String(localized: "projectPicker.githubEmpty", bundle: .lody), action: false)] }
    return []
  }
}
