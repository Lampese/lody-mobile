import LodyDesignSystem
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
        SessionListScreen()
            .tint(LodyColor.accentDefault)
            .preferredColorScheme(appearance == 1 ? .light : appearance == 2 ? .dark : nil)
    }
}
