import Foundation

/// Dónde vive el entorno de DoggyCredit. Caddy es la entrada única: la SPA, `/auth`
/// (Keycloak) y `/api/v1` comparten origen (ver README del monorepo).
nonisolated struct AuthConfiguration: Hashable, Sendable {
    /// Cliente público del realm para la app móvil (PKCE S256, sin secreto).
    static let clientId = "doggycredit-mobile"
    static let callbackScheme = "doggycredit"
    static let redirectURI = "doggycredit://oauth/callback"
    static let scopes = "openid profile email roles"
    static let defaultServer = URL(string: "https://doggycredit.los5furiosos.cloud")!

    let server: URL

    var apiBaseURL: URL { server.appending(path: "api/v1") }
    var issuer: URL { server.appending(path: "auth/realms/doggycredit") }
    // Rutas estándar de Keycloak bajo el issuer del realm.
    var authorizationEndpoint: URL { issuer.appending(path: "protocol/openid-connect/auth") }
    var tokenEndpoint: URL { issuer.appending(path: "protocol/openid-connect/token") }
    var logoutEndpoint: URL { issuer.appending(path: "protocol/openid-connect/logout") }
    var webConsoleURL: URL { server.appending(path: "app") }

    /// Normaliza lo que la persona escribe ("doggycredit.example" → "https://doggycredit.example").
    static func server(from text: String) -> URL? {
        var candidate = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !candidate.isEmpty else { return nil }
        if !candidate.contains("://") { candidate = "https://" + candidate }
        while candidate.hasSuffix("/") { candidate.removeLast() }
        guard let url = URL(string: candidate), url.scheme == "https", url.host() != nil else { return nil }
        return url
    }
}
