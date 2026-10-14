import Foundation

/// Enmascara documentos y montos según la preferencia del tenant y el rol, como la
/// consola web: solo administración ve los datos completos. Mientras la preferencia
/// no se conoce, se enmascara (falla cerrado).
nonisolated struct SensitiveDataPolicy: Hashable, Sendable {
    let shouldMask: Bool

    init(preferences: TenantPreferences?, user: UserSession) {
        shouldMask = (preferences?.maskSensitiveData ?? true) && !user.isAdministrator
    }

    init(shouldMask: Bool) {
        self.shouldMask = shouldMask
    }

    /// "CI •••• 1001", como Wallet muestra "Visa •••• 1234": conserva el tipo de documento
    /// y los 4 últimos caracteres para reconocer al cliente, sin revelar su longitud.
    func document(_ value: String) -> String {
        guard shouldMask else { return value }
        let masked = "•••• " + value.suffix(4)
        guard let kind = Self.documentKind(value) else { return masked }
        return "\(kind) \(masked)"
    }

    /// "CI" de "CI-DEMO-0001001" o "NIT" de "NIT 1000000010"; nil si no hay prefijo de letras.
    private static func documentKind(_ value: String) -> String? {
        let prefix = value.prefix { $0.isLetter }
        guard (2...4).contains(prefix.count), prefix.count < value.count,
              let separator = value.dropFirst(prefix.count).first, separator == "-" || separator == " "
        else { return nil }
        return prefix.uppercased()
    }

    func amount(_ formatted: String) -> String {
        shouldMask ? "••••••" : formatted
    }

    /// En resúmenes de una línea un monto oculto no aporta: se omite.
    func visibleAmount(_ formatted: String) -> String? {
        shouldMask ? nil : formatted
    }
}
