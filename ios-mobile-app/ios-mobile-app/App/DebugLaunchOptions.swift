#if DEBUG
import SwiftUI

/// Atajos de arranque para capturas y pruebas de interfaz (solo en DEBUG).
///
///     -DCSignedOut YES                 arranca sin sesión guardada
///     -DCAppearance light|dark         fuerza el tema
///     -DCTab review|clients|analytics|search
///     -DCOpen evaluation|account|resolve|client   (-DCDocument <doc> elige el cliente)
/// Los accesos a pantallas requieren una sesión real; no crean registros.
enum DebugLaunchOptions {
    static var startsSignedOut: Bool {
        UserDefaults.standard.bool(forKey: "DCSignedOut")
    }

    static var colorScheme: ColorScheme? {
        switch UserDefaults.standard.string(forKey: "DCAppearance") {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    static func apply(to session: SessionStore) async {
        let defaults = UserDefaults.standard
        switch defaults.string(forKey: "DCTab") {
        case "review": session.router.selectedTab = .review
        case "clients": session.router.selectedTab = .clients
        case "analytics": session.router.selectedTab = .analytics
        case "search": session.router.selectedTab = .search
        default: break
        }
        switch defaults.string(forKey: "DCOpen") {
        case "evaluation":
            session.startEvaluation()
        case "account":
            session.router.isAccountPresented = true
        case "resolve":
            await session.reviewQueue.loadIfNeeded()
            if let first = session.reviewQueue.cases.first { session.resolve(first, preset: .approve) }
        case "client":
            await session.clients.loadIfNeeded()
            let document = defaults.string(forKey: "DCDocument")
            let applicants = session.clients.applicants
            if let target = applicants.first(where: { $0.document == document }) ?? applicants.first {
                session.router.selectedTab = .clients
                session.router.clientsPath = [.client(target.id)]
            }
        default:
            break
        }
    }
}
#endif
