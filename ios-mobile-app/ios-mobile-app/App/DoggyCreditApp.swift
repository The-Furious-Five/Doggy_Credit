import SwiftUI

@main
struct DoggyCreditApp: App {
    @State private var app = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                // Cifras, fechas y controles del sistema en español de Bolivia, como la consola web.
                .environment(\.locale, .bolivia)
                .tint(.accentColor)
                #if DEBUG
                .preferredColorScheme(DebugLaunchOptions.colorScheme)
                #endif
        }
    }
}
