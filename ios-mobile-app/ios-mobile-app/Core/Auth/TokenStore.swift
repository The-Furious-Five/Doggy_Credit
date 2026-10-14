import Foundation
import Security

nonisolated struct TokenSet: Codable, Hashable, Sendable {
    let accessToken: String
    let refreshToken: String?
    let idToken: String?
    let expiresAt: Date

    /// Margen para no enviar un token que vence en tránsito.
    func isExpired(at date: Date = .now, leeway: TimeInterval = 30) -> Bool {
        date.addingTimeInterval(leeway) >= expiresAt
    }
}

/// Tokens en el Llavero, accesibles solo con el dispositivo desbloqueado y sin
/// migrar a otros dispositivos ni a copias de seguridad.
nonisolated enum TokenStore {
    private static let service = "com.doggycredit.mobile.tokens"
    private static let account = "oidc"

    static func load() -> TokenSet? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return try? JSONDecoder().decode(TokenSet.self, from: data)
    }

    static func save(_ tokens: TokenSet) {
        guard let data = try? JSONEncoder().encode(tokens) else { return }
        clear()
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data,
        ]
        SecItemAdd(attributes as CFDictionary, nil)
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
