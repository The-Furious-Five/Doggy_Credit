import LocalAuthentication
import SwiftUI

/// Bloqueo con Face ID, Touch ID o el código del iPhone al volver a la app.
/// La app maneja datos financieros de terceros: la protección es opcional pero recomendada.
@Observable
final class AppLock {
    private static let enabledKey = "security.lockEnabled"

    private(set) var isEnabled: Bool
    private(set) var isLocked = false
    private(set) var isAuthenticating = false
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        isEnabled = defaults.bool(forKey: Self.enabledKey)
        // Si estaba activo, la app arranca bloqueada.
        isLocked = isEnabled
    }

    /// Nombre del método disponible para mostrarlo en Ajustes.
    var methodName: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "código del iPhone"
        }
    }

    var methodSymbol: String {
        switch methodName {
        case "Face ID": "faceid"
        case "Touch ID": "touchid"
        case "Optic ID": "opticid"
        default: "lock.fill"
        }
    }

    var isAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// Activar exige autenticarse: así nadie más lo enciende o apaga por la persona.
    func setEnabled(_ enabled: Bool) async {
        guard enabled != isEnabled else { return }
        guard await authenticate(reason: enabled ? "Activa el bloqueo de DoggyCredit." : "Desactiva el bloqueo de DoggyCredit.") else {
            return
        }
        isEnabled = enabled
        defaults.set(enabled, forKey: Self.enabledKey)
    }

    func handle(_ phase: ScenePhase) {
        switch phase {
        case .background:
            if isEnabled { isLocked = true }
        case .active:
            if isLocked, !isAuthenticating { Task { await unlock() } }
        default:
            break
        }
    }

    func unlock() async {
        guard isLocked, !isAuthenticating else { return }
        if await authenticate(reason: "Desbloquea DoggyCredit para ver los datos de tus clientes.") {
            isLocked = false
        }
    }

    /// Al cerrar sesión no queda nada que proteger.
    func reset() {
        isLocked = false
    }

    private func authenticate(reason: String) async -> Bool {
        isAuthenticating = true
        defer { isAuthenticating = false }
        let context = LAContext()
        context.localizedCancelTitle = "Cancelar"
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}
