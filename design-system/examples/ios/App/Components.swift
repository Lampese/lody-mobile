// Content-layer building blocks. Each mirrors a composable in android/.../Components.kt
// with the same tokens, so the two previews can be compared side by side.

import SwiftUI

struct Glyph: View {
    let icon: LodyIcon
    var size: CGFloat = LodyIconSize.md
    var color: Color = LodyColor.iconSecondary

    var body: some View {
        icon.image
            .resizable()
            .renderingMode(.template)
            .foregroundStyle(color)
            .frame(width: size, height: size)
    }
}

struct StateGlyph: View {
    let state: SessionState
    var size: CGFloat = LodyIconSize.md
    @State private var spinning = false

    var body: some View {
        Glyph(icon: state.icon, size: size, color: state.color)
            .rotationEffect(.degrees(spinning ? 360 : 0))
            .animation(
                state == .running ? .linear(duration: 1).repeatForever(autoreverses: false) : nil,
                value: spinning
            )
            .onAppear { spinning = state == .running }
    }
}

struct Hairline: View {
    @Environment(\.displayScale) private var scale
    var leadingInset: CGFloat = 0

    var body: some View {
        Rectangle()
            .fill(LodyColor.borderSubtle)
            .frame(height: 1 / scale)
            .padding(.leading, leadingInset)
    }
}

/// Pressed-state highlight for custom rows: iOS highlights, Android ripples.
struct RowPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? LodyColor.fillPressed : LodyColor.bgBase)
    }
}

struct SessionRow: View {
    let session: SampleSession

    var body: some View {
        Button {} label: {
            HStack(spacing: LodyLayout.listRowGap) {
                StateGlyph(state: session.state)
                VStack(alignment: .leading, spacing: LodySpacing.xxs) {
                    Text(session.title)
                        .lodyTextStyle(.body)
                        .foregroundStyle(LodyColor.textPrimary)
                        .lineLimit(1)
                    Text(session.subtitle)
                        .lodyTextStyle(.footnote)
                        .foregroundStyle(LodyColor.textSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: LodySpacing.sm)
                if let label = session.label { LabelChip(label: label) }
                Text(session.meta)
                    .lodyTextStyle(.footnote)
                    .foregroundStyle(LodyColor.textTertiary)
            }
            .padding(.horizontal, LodyLayout.listRowPaddingX)
            .frame(maxWidth: .infinity, minHeight: LodySize.listRowTwoLine, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowPressStyle())
    }
}

struct SectionHeader: View {
    let title: String
    var count: Int? = nil
    var showsAdd = false

    var body: some View {
        HStack(spacing: LodySpacing.sm) {
            Text(title)
                .lodyTextStyle(.subhead)
                .foregroundStyle(LodyColor.textTertiary)
            if let count {
                Text("\(count)")
                    .lodyTextStyle(.subhead)
                    .foregroundStyle(LodyColor.textPlaceholder)
            }
            Spacer()
            if showsAdd {
                Glyph(icon: .plus, size: LodyIconSize.md, color: LodyColor.iconTertiary)
            }
        }
        .padding(.horizontal, LodyLayout.listRowPaddingX)
        .frame(height: LodySize.sectionHeader)
    }
}

struct FilterChip: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .lodyTextStyle(.subhead)
                .foregroundStyle(selected ? LodyColor.textPrimary : LodyColor.textSecondary)
                .padding(.horizontal, LodySpacing.md)
                .frame(height: LodySize.chip + LodySpacing.xs)
                .background(selected ? LodyColor.fillSelected : Color.clear, in: Capsule())
                .overlay(Capsule().strokeBorder(selected ? Color.clear : LodyColor.borderDefault, lineWidth: LodyStroke.default))
        }
        .buttonStyle(.plain)
    }
}

struct StatusBadge: View {
    let state: SessionState

    private var colors: (bg: Color, fg: Color) {
        switch state {
        case .running: (LodyColor.accentSubtle, LodyColor.accentOnSubtle)
        case .waiting: (LodyColor.warningSubtle, LodyColor.warningOnSubtle)
        case .done, .merged: (LodyColor.successSubtle, LodyColor.successOnSubtle)
        case .failed: (LodyColor.dangerSubtle, LodyColor.dangerOnSubtle)
        case .idle: (LodyColor.fillSecondary, LodyColor.textSecondary)
        }
    }

    var body: some View {
        HStack(spacing: LodySpacing.xs) {
            Glyph(icon: state.icon, size: LodyIconSize.sm, color: colors.fg)
            Text(state.label).lodyTextStyle(.caption).foregroundStyle(colors.fg)
        }
        .padding(.horizontal, LodySpacing.sm)
        .frame(height: LodySize.chipSm)
        .background(colors.bg, in: RoundedRectangle(cornerRadius: LodyRadius.sm))
    }
}

enum LodyButtonKind { case primary, secondary, destructive, ghost }

struct LodyButton: View {
    let title: String
    var icon: LodyIcon? = nil
    var kind: LodyButtonKind = .primary
    var enabled = true
    var action: () -> Void = {}

    private var colors: (bg: Color, fg: Color) {
        switch kind {
        case .primary: (LodyColor.accentDefault, LodyColor.textOnAccent)
        case .secondary: (LodyColor.fillSecondary, LodyColor.textPrimary)
        case .destructive: (LodyColor.dangerSolid, LodyColor.textOnAccent)
        case .ghost: (Color.clear, LodyColor.textLink)
        }
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: LodySpacing.sm) {
                if let icon { Glyph(icon: icon, size: LodyIconSize.sm, color: colors.fg) }
                Text(title).lodyTextStyle(.subhead).foregroundStyle(colors.fg)
            }
            .padding(.horizontal, LodySpacing.lg)
            .frame(height: LodySize.controlMd)
            .background(colors.bg, in: RoundedRectangle(cornerRadius: LodyRadius.md))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : LodyOpacity.disabled)
    }
}

struct LabelChip: View {
    let label: LabelColor

    var body: some View {
        HStack(spacing: LodySpacing.xs + LodySpacing.xxs) {
            Circle().fill(label.fg).frame(width: LodySize.dot, height: LodySize.dot)
            Text(label.rawValue.capitalized).lodyTextStyle(.caption).foregroundStyle(label.fg)
        }
        .padding(.horizontal, LodySpacing.sm)
        .frame(height: LodySize.chipSm)
        .background(label.bg, in: RoundedRectangle(cornerRadius: LodyRadius.sm))
    }
}

struct Toast: View {
    let text: String

    var body: some View {
        HStack(spacing: LodySpacing.sm) {
            Glyph(icon: .check, size: LodyIconSize.md, color: LodyColor.textInverse)
            Text(text).lodyTextStyle(.subhead).foregroundStyle(LodyColor.textInverse)
        }
        .padding(.horizontal, LodySpacing.md)
        .frame(height: LodySize.controlMd)
        .background(LodyColor.bgInverse, in: RoundedRectangle(cornerRadius: LodyRadius.md))
        .lodyElevation(.md)
    }
}

struct ElevationTile: View {
    let name: String
    let elevation: LodyElevation

    var body: some View {
        VStack(alignment: .leading, spacing: LodySpacing.xxs) {
            Text(name).lodyTextStyle(.footnote).foregroundStyle(LodyColor.textPrimary)
            Text("y \(Int(elevation.y)) · blur \(Int(elevation.blur))")
                .lodyTextStyle(.caption)
                .foregroundStyle(LodyColor.textTertiary)
        }
        .padding(LodySpacing.md)
        .frame(width: 104, alignment: .leading)
        .background(LodyColor.bgOverlay, in: RoundedRectangle(cornerRadius: LodyRadius.md))
        .lodyElevation(elevation)
    }
}

struct Composer: View {
    @State private var text = ""

    var body: some View {
        HStack(spacing: LodySpacing.sm) {
            Glyph(icon: .attach, color: LodyColor.iconSecondary)
            TextField("", text: $text, prompt: Text("Ask Lody…").foregroundStyle(LodyColor.textPlaceholder))
                .lodyTextStyle(.body)
                .foregroundStyle(LodyColor.textPrimary)
                .tint(LodyColor.accentDefault)
            Button {} label: {
                Glyph(icon: .send, size: LodyIconSize.md, color: LodyColor.textOnAccent)
                    .frame(width: LodySize.controlSm, height: LodySize.controlSm)
                    .background(LodyColor.accentDefault, in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, LodySpacing.md)
        .padding(.trailing, LodySpacing.sm)
        .frame(minHeight: LodySize.composerMin)
        .background(LodyColor.bgRaised, in: RoundedRectangle(cornerRadius: LodyRadius.xl))
        .overlay(RoundedRectangle(cornerRadius: LodyRadius.xl).strokeBorder(LodyColor.borderDefault, lineWidth: LodyStroke.default))
    }
}

struct LodyTextField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(LodyColor.textPlaceholder))
            .lodyTextStyle(.body)
            .foregroundStyle(LodyColor.textPrimary)
            .tint(LodyColor.accentDefault)
            .padding(.horizontal, LodySpacing.md)
            .frame(height: LodySize.controlMd)
            .background(LodyColor.bgSunken, in: RoundedRectangle(cornerRadius: LodyRadius.md))
            .overlay(RoundedRectangle(cornerRadius: LodyRadius.md).strokeBorder(LodyColor.borderDefault, lineWidth: LodyStroke.default))
    }
}

struct Banner: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: LodySpacing.sm) {
            Glyph(icon: .alert, size: LodyIconSize.md, color: LodyColor.warningDefault)
            Text(text)
                .lodyTextStyle(.footnote)
                .foregroundStyle(LodyColor.warningOnSubtle)
            Spacer(minLength: 0)
        }
        .padding(LodySpacing.md)
        .background(LodyColor.warningSubtle, in: RoundedRectangle(cornerRadius: LodyRadius.md))
    }
}

struct CodeBlock: View {
    let lines: [[(SyntaxKind, String)]]

    private var highlighted: AttributedString {
        var result = AttributedString()
        for (index, line) in lines.enumerated() {
            for (kind, text) in line {
                var run = AttributedString(text)
                run.foregroundColor = kind.color
                result += run
            }
            if index < lines.count - 1 { result += AttributedString("\n") }
        }
        return result
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            Text(highlighted)
                .lodyTextStyle(.code)
                .fixedSize()
                .padding(LodySpacing.md)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LodyColor.codeBg, in: RoundedRectangle(cornerRadius: LodyRadius.md))
        .overlay(RoundedRectangle(cornerRadius: LodyRadius.md).strokeBorder(LodyColor.codeBorder, lineWidth: LodyStroke.default))
    }
}

struct DiffView: View {
    let lines: [(kind: DiffKind, text: String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                Text(line.text)
                    .lodyTextStyle(.codeSmall)
                    .foregroundStyle(foreground(line.kind))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, LodySpacing.md)
                    .background(background(line.kind))
            }
        }
        .padding(.vertical, LodySpacing.sm)
        .background(LodyColor.codeBg)
        .clipShape(RoundedRectangle(cornerRadius: LodyRadius.md))
        .overlay(RoundedRectangle(cornerRadius: LodyRadius.md).strokeBorder(LodyColor.codeBorder, lineWidth: LodyStroke.default))
    }

    private func foreground(_ kind: DiffKind) -> Color {
        switch kind {
        case .hunk: LodyColor.diffHunkText
        case .context: LodyColor.codeText
        case .added: LodyColor.diffAddedText
        case .removed: LodyColor.diffRemovedText
        }
    }

    private func background(_ kind: DiffKind) -> Color {
        switch kind {
        case .hunk: LodyColor.diffHunkBg
        case .context: .clear
        case .added: LodyColor.diffAddedBg
        case .removed: LodyColor.diffRemovedBg
        }
    }
}

struct EmptyState: View {
    var body: some View {
        VStack(spacing: LodySpacing.md) {
            Glyph(icon: .inbox, size: LodyIconSize.xl, color: LodyColor.iconSecondary)
                .frame(width: LodySize.avatarLg + LodySpacing.lg, height: LodySize.avatarLg + LodySpacing.lg)
                .background(LodyColor.fillTertiary, in: Circle())
            Text("No sessions yet").lodyTextStyle(.headline).foregroundStyle(LodyColor.textPrimary)
            Text("Start a session from a project to see it here.")
                .lodyTextStyle(.footnote)
                .foregroundStyle(LodyColor.textSecondary)
                .multilineTextAlignment(.center)
            LodyButton(title: "New session", icon: .plus)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, LodySpacing.xxl)
    }
}

/// Titled card on bg.grouped, used by the Components and Tokens screens.
struct GroupCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: LodySpacing.sm) {
            Text(title)
                .lodyTextStyle(.subhead)
                .foregroundStyle(LodyColor.textTertiary)
                .padding(.horizontal, LodySpacing.xs)
            VStack(alignment: .leading, spacing: LodySpacing.md) { content }
                .padding(LodySpacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(LodyColor.bgRaised, in: RoundedRectangle(cornerRadius: LodyRadius.lg))
        }
    }
}

/// Cycles system -> light -> dark so both modes can be reviewed on one device.
struct AppearanceButton: View {
    @AppStorage("appearance") private var appearance = 0

    var body: some View {
        Button {
            appearance = (appearance + 1) % 3
        } label: {
            Glyph(icon: appearance == 2 ? .moon : .sun, size: LodyIconSize.md, color: LodyColor.iconPrimary)
        }
        .accessibilityLabel("Toggle appearance")
    }
}
