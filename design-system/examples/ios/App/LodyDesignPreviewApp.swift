import SwiftUI

@main
struct LodyDesignPreviewApp: App {
    init() {
        LodyFonts.register()
        LodyAppearance.apply()
    }

    var body: some Scene {
        WindowGroup { RootView() }
    }
}

struct RootView: View {
    @AppStorage("appearance") private var appearance = 0

    var body: some View {
        TabView {
            Tab("Sessions", image: LodyIcon.sessions.rawValue) { SessionsScreen() }
            Tab("Components", image: LodyIcon.sparkles.rawValue) { ComponentsScreen() }
            Tab("Tokens", image: LodyIcon.settings.rawValue) { TokensScreen() }
        }
        .tint(LodyColor.accentDefault)
        .preferredColorScheme(appearance == 1 ? .light : appearance == 2 ? .dark : nil)
    }
}
