import SwiftUI

/// Decide qué se ve: bienvenida, la app o el bloqueo. Al pasar a segundo plano tapa
/// el contenido para que los datos de clientes no queden en el selector de apps.
struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            switch app.phase {
            case .restoring:
                BrandBackdrop()
            case .signedOut:
                WelcomeView()
                    .transition(.opacity)
            case let .signedIn(session):
                MainTabView()
                    .environment(session)
                    .environment(session.router)
                    .id(session.id)
                    .transition(.opacity)
            }

            if app.session != nil {
                if app.lock.isLocked {
                    LockView()
                        .transition(.opacity)
                } else if scenePhase != .active {
                    BrandBackdrop()
                        .accessibilityHidden(true)
                }
            }
        }
        .animation(.smooth, value: phaseKey)
        .animation(.smooth, value: app.lock.isLocked)
        .onAppear { app.restore() }
        .onChange(of: scenePhase) { _, phase in
            if app.session != nil { app.lock.handle(phase) }
        }
    }

    private var phaseKey: String {
        switch app.phase {
        case .restoring: "restoring"
        case .signedOut: "signedOut"
        case let .signedIn(session): session.id.uuidString
        }
    }
}

/// Superficie de marca a pantalla completa: arranque y portada de privacidad.
struct BrandBackdrop: View {
    var body: some View {
        ZStack {
            Color.brandSurface.ignoresSafeArea()
            BrandMark(size: 88)
        }
    }
}
