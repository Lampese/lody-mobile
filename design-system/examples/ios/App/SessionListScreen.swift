// The chat list: the app's home. Mirrors android/.../SessionListScreen.kt.
//
// Simplicity: one line per chat, no separators, subtitles or timestamps; chats
// are indented so their titles align with the group title. Efficiency: the
// indent gutter shows a glyph only when a chat needs attention (running,
// waiting, failed, unread); collapsed groups surface a count and their most
// urgent state; search filters every group; swipe and long-press act in place.

import LodyDesignSystem
import SwiftUI

enum PreviewDestination: Hashable {
    case components, tokens
}

struct SessionListScreen: View {
    @State private var path: [PreviewDestination] = []
    @State private var collapsed = SampleData.initiallyCollapsed
    @State private var showingAll: Set<String> = []
    @State private var query = ""
    @AppStorage("appearance") private var appearance = 0

    var body: some View {
        NavigationStack(path: $path) {
            List {
                ForEach(Array(groups.enumerated()), id: \.element.id) { index, group in
                    let isCollapsed = isSearching ? false : collapsed.contains(group.id)
                    GroupHeaderRow(group: group, collapsed: isCollapsed) {
                        withAnimation(LodySpring.snappy) { toggle(group.id) }
                    }
                    .padding(.top, index == 0 ? 0 : LodySpacing.sm)
                    .chatListRow()

                    if !isCollapsed {
                        ForEach(visibleChats(of: group)) { chat in
                            ChatRow(chat: chat).chatListRow()
                        }
                        let hidden = group.chats.count - visibleChats(of: group).count
                        if hidden > 0 {
                            ShowMoreRow(count: hidden) {
                                withAnimation(LodySpring.snappy) { _ = showingAll.insert(group.id) }
                            }
                            .chatListRow()
                        }
                    }
                }
                if groups.isEmpty {
                    Text("No chats match “\(query)”")
                        .lodyTextStyle(.body)
                        .foregroundStyle(LodyColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, LodySpacing.xxxl)
                        .chatListRow()
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(LodyColor.bgBase)
            .environment(\.defaultMinListRowHeight, LodySize.listRow)
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query, prompt: Text("Search chats"))
            .toolbar { toolbar }
            .navigationDestination(for: PreviewDestination.self) { destination in
                switch destination {
                case .components: ComponentsScreen()
                case .tokens: TokensScreen()
                }
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button {} label: {
                Text(SampleData.workspace.initial)
                    .lodyTextStyle(.subhead)
                    .foregroundStyle(LodyColor.textPrimary)
                    .frame(width: LodySize.avatarMd, height: LodySize.avatarMd)
                    .background(LodyColor.fillSecondary, in: Circle())
            }
            .accessibilityLabel("Account and workspaces")
        }
        ToolbarItem(placement: .principal) {
            VStack(spacing: 0) {
                Text(SampleData.workspace.name)
                    .font(LodyTextStyle.headline.font)
                    .foregroundStyle(LodyColor.textPrimary)
                Text(SampleData.workspace.organization)
                    .font(LodyTextStyle.footnote.font)
                    .foregroundStyle(LodyColor.textSecondary)
            }
        }
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Picker("Appearance", selection: $appearance) {
                    Text("System").tag(0)
                    Text("Light").tag(1)
                    Text("Dark").tag(2)
                }
                Button { path.append(.components) } label: { Label { Text("Components") } icon: { LodyIcon.sparkles.image } }
                Button { path.append(.tokens) } label: { Label { Text("Tokens") } icon: { LodyIcon.settings.image } }
            } label: {
                Glyph(icon: .more, color: LodyColor.iconPrimary)
            }
            .accessibilityLabel("More")
        }
        DefaultToolbarItem(kind: .search, placement: .bottomBar)
        ToolbarSpacer(.fixed, placement: .bottomBar)
        ToolbarItem(placement: .bottomBar) {
            Button {} label: { Glyph(icon: .compose, color: LodyColor.iconPrimary) }
                .accessibilityLabel("New chat")
        }
    }

    private var isSearching: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty }

    /// While searching, groups show only matching chats and drop out when empty.
    private var groups: [SampleGroup] {
        guard isSearching else { return SampleData.groups }
        return SampleData.groups.compactMap { group in
            let matches = group.chats.filter { $0.title.localizedCaseInsensitiveContains(query) }
            return matches.isEmpty ? nil : SampleGroup(id: group.id, name: group.name, kind: group.kind, chats: matches)
        }
    }

    private func visibleChats(of group: SampleGroup) -> [SampleChat] {
        if isSearching || showingAll.contains(group.id) { return group.chats }
        return Array(group.chats.prefix(SampleData.visibleChatLimit))
    }

    private func toggle(_ id: String) {
        if collapsed.contains(id) { collapsed.remove(id) } else { collapsed.insert(id) }
    }
}

private extension View {
    /// Plain, edge-to-edge rows: no separators, screen-padding insets, base background.
    func chatListRow() -> some View {
        listRowInsets(EdgeInsets(top: 0, leading: LodyLayout.screenPaddingX, bottom: 0, trailing: LodyLayout.screenPaddingX))
            .listRowSeparator(.hidden)
            .listRowBackground(LodyColor.bgBase)
    }
}

/// The most urgent thing a chat (or a collapsed group) wants from the user, if anything.
enum ChatAttention {
    case running, waiting, failed, unread

    init?(_ chat: SampleChat) {
        switch chat.state {
        case .running: self = .running
        case .waiting: self = .waiting
        case .failed: self = .failed
        default:
            guard chat.unread else { return nil }
            self = .unread
        }
    }

    /// Waiting and failed need a decision, so they outrank work in progress.
    static func mostUrgent(in chats: [SampleChat]) -> ChatAttention? {
        let all = chats.compactMap(ChatAttention.init)
        return [.waiting, .failed, .running, .unread].first(where: all.contains)
    }
}

struct AttentionGlyph: View {
    let attention: ChatAttention

    var body: some View {
        switch attention {
        case .running: StateGlyph(state: .running, size: LodyIconSize.sm)
        case .waiting: StateGlyph(state: .waiting, size: LodyIconSize.sm)
        case .failed: StateGlyph(state: .failed, size: LodyIconSize.sm)
        case .unread:
            Circle().fill(LodyColor.accentDefault).frame(width: LodySize.dot, height: LodySize.dot)
        }
    }
}

struct GroupHeaderRow: View {
    let group: SampleGroup
    let collapsed: Bool
    let onToggle: () -> Void

    var body: some View {
        HStack(spacing: LodyLayout.listRowGap) {
            Glyph(icon: group.icon, color: LodyColor.iconPrimary)
            Text(group.name)
                .lodyTextStyle(.bodyEmphasis)
                .foregroundStyle(LodyColor.textPrimary)
                .lineLimit(1)
            Spacer(minLength: LodySpacing.sm)
            if collapsed {
                if let attention = ChatAttention.mostUrgent(in: group.chats) {
                    AttentionGlyph(attention: attention)
                }
                Text("\(group.chats.count)")
                    .lodyTextStyle(.footnote)
                    .foregroundStyle(LodyColor.textTertiary)
                    .monospacedDigit()
            }
            if group.kind == .project {
                Button {} label: { Glyph(icon: .compose, color: LodyColor.iconSecondary) }
                    .buttonStyle(.borderless)
                    .frame(width: LodySize.touchTarget, height: LodySize.touchTarget)
                    .accessibilityLabel("New chat in \(group.name)")
            }
        }
        .frame(minHeight: LodySize.listRow)
        .contentShape(Rectangle())
        .onTapGesture(perform: onToggle)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(collapsed ? "Collapsed, \(group.chats.count) chats" : "Expanded")
    }
}

struct ChatRow: View {
    let chat: SampleChat

    var body: some View {
        Button {} label: {
            HStack(spacing: 0) {
                // The gutter under the group icon: empty unless the chat needs attention.
                ZStack {
                    if let attention = ChatAttention(chat) { AttentionGlyph(attention: attention) }
                }
                .frame(width: LodyIconSize.md)
                .frame(width: LodyLayout.childIndent - LodyLayout.screenPaddingX, alignment: .leading)
                Text(chat.title)
                    .lodyTextStyle(chat.unread ? .bodyEmphasis : .body)
                    .foregroundStyle(LodyColor.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .frame(minHeight: LodySize.listRow)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button {} label: { Label { Text("Archive") } icon: { LodyIcon.inbox.image } }
                .tint(LodyColor.accentSolid)
        }
        .contextMenu {
            Button {} label: { Label { Text("Rename") } icon: { LodyIcon.edit.image } }
            Button {} label: { Label { Text("Copy link") } icon: { LodyIcon.link.image } }
            Button {} label: { Label { Text("Archive") } icon: { LodyIcon.inbox.image } }
            Button(role: .destructive) {} label: { Label { Text("Delete") } icon: { LodyIcon.trash.image } }
        }
    }
}

struct ShowMoreRow: View {
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Show \(count) more")
                .lodyTextStyle(.body)
                .foregroundStyle(LodyColor.textTertiary)
                .padding(.leading, LodyLayout.childIndent - LodyLayout.screenPaddingX)
                .frame(maxWidth: .infinity, minHeight: LodySize.listRow, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
