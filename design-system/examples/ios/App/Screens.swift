import SwiftUI

struct ComponentsScreen: View {
    @State private var name = ""
    @State private var notify = true
    @State private var mode = "Agent"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: LodyLayout.sectionGap) {
                GroupCard(title: "Buttons") {
                    HStack(spacing: LodySpacing.sm) {
                        LodyButton(title: "Run", icon: .send)
                        LodyButton(title: "Cancel", kind: .secondary)
                    }
                    HStack(spacing: LodySpacing.sm) {
                        LodyButton(title: "Delete", icon: .trash, kind: .destructive)
                        LodyButton(title: "View diff", kind: .ghost)
                        LodyButton(title: "Disabled", enabled: false)
                    }
                }
                GroupCard(title: "Status") {
                    FlowRow {
                        ForEach(SessionState.allCases, id: \.self) { StatusBadge(state: $0) }
                    }
                }
                GroupCard(title: "Labels") {
                    FlowRow {
                        ForEach(LabelColor.allCases, id: \.self) { LabelChip(label: $0) }
                    }
                }
                GroupCard(title: "Native controls (platform)") {
                    Toggle(isOn: $notify) {
                        Text("Notify when done").lodyTextStyle(.body).foregroundStyle(LodyColor.textPrimary)
                    }
                    .tint(LodyColor.accentDefault)
                    Picker("Mode", selection: $mode) {
                        ForEach(["Agent", "Plan", "Ask"], id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                GroupCard(title: "Inputs") {
                    Composer()
                    LodyTextField(placeholder: "Session name", text: $name)
                    Banner(text: "Agent needs approval to run `pnpm install` in lody-mobile.")
                }
                GroupCard(title: "Code") {
                    CodeBlock(lines: SampleData.code)
                    DiffView(lines: SampleData.diff)
                }
                GroupCard(title: "Elevation") {
                    HStack(spacing: LodySpacing.lg) {
                        ElevationTile(name: "sm", elevation: .sm)
                        ElevationTile(name: "md", elevation: .md)
                        ElevationTile(name: "lg", elevation: .lg)
                    }
                    .padding(.vertical, LodySpacing.sm)
                    Toast(text: "Session archived")
                }
                GroupCard(title: "Empty state") {
                    EmptyState()
                }
            }
            .padding(LodyLayout.screenPaddingX)
        }
        .background(LodyColor.bgGrouped)
        .navigationTitle("Components")
    }
}

struct TokensScreen: View {
    private let iconColumns = [GridItem(.adaptive(minimum: 72), spacing: LodySpacing.sm)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: LodyLayout.sectionGap) {
                GroupCard(title: "Type") {
                    ForEach(LodyTextStyle.all, id: \.name) { item in
                        VStack(alignment: .leading, spacing: LodySpacing.xxs) {
                            Text("\(item.name) · \(Int(item.style.size))/\(Int(item.style.lineHeight))")
                                .lodyTextStyle(.caption)
                                .foregroundStyle(LodyColor.textTertiary)
                            Text("同一个 worktree 多个 tabs")
                                .lodyTextStyle(item.style)
                                .foregroundStyle(LodyColor.textPrimary)
                        }
                    }
                }
                GroupCard(title: "Icons") {
                    LazyVGrid(columns: iconColumns, spacing: LodySpacing.md) {
                        ForEach(LodyIcon.allCases, id: \.self) { icon in
                            VStack(spacing: LodySpacing.xs) {
                                Glyph(icon: icon, size: LodyIconSize.lg, color: LodyColor.iconPrimary)
                                Text(String(icon.rawValue.dropFirst("lody.icon.".count)))
                                    .lodyTextStyle(.micro)
                                    .foregroundStyle(LodyColor.textTertiary)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                GroupCard(title: "Color") {
                    ForEach(LodyColor.all, id: \.name) { item in
                        HStack(spacing: LodySpacing.md) {
                            RoundedRectangle(cornerRadius: LodyRadius.sm)
                                .fill(item.color)
                                .frame(width: LodySize.avatarMd, height: LodySize.avatarMd)
                                .overlay(RoundedRectangle(cornerRadius: LodyRadius.sm).strokeBorder(LodyColor.borderDefault, lineWidth: LodyStroke.default))
                            Text(item.name).lodyTextStyle(.code).foregroundStyle(LodyColor.textPrimary)
                        }
                    }
                }
                GroupCard(title: "Spacing") {
                    ForEach(LodySpacing.all, id: \.name) { item in
                        HStack(spacing: LodySpacing.md) {
                            Text("\(item.name) \(Int(item.value))")
                                .lodyTextStyle(.codeSmall)
                                .foregroundStyle(LodyColor.textSecondary)
                                .frame(width: 80, alignment: .leading)
                            Rectangle()
                                .fill(LodyColor.accentDefault)
                                .frame(width: item.value, height: LodySpacing.sm)
                        }
                    }
                }
                GroupCard(title: "Radius") {
                    FlowRow {
                        ForEach(LodyRadius.all.filter { $0.name != "full" }, id: \.name) { item in
                            VStack(spacing: LodySpacing.xs) {
                                RoundedRectangle(cornerRadius: item.value)
                                    .strokeBorder(LodyColor.borderStrong, lineWidth: LodyStroke.focus)
                                    .frame(width: 56, height: 56)
                                Text(item.name).lodyTextStyle(.micro).foregroundStyle(LodyColor.textTertiary)
                            }
                        }
                    }
                }
            }
            .padding(LodyLayout.screenPaddingX)
        }
        .background(LodyColor.bgGrouped)
        .navigationTitle("Tokens")
    }
}

/// Minimal wrapping row for badges and swatches.
struct FlowRow: Layout {
    var spacing: CGFloat = LodySpacing.sm

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(0, rows.count - 1))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [(indices: [Int], width: CGFloat, height: CGFloat)] {
        var rows: [(indices: [Int], width: CGFloat, height: CGFloat)] = []
        var current: (indices: [Int], width: CGFloat, height: CGFloat) = ([], 0, 0)
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width, !current.indices.isEmpty {
                rows.append(current)
                current = ([index], size.width, size.height)
            } else {
                current = (current.indices + [index], needed, max(current.height, size.height))
            }
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}
