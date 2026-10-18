import SwiftUI

#Preview("Bienvenida") {
    WelcomeView()
        .environment(AppModel(defaults: UserDefaults(suiteName: "preview") ?? .standard))
}
