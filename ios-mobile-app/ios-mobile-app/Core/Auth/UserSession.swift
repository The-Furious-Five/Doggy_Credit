import Foundation

/// Quién usa la app, leído del access token: es el que recibe la API y el único que
/// contiene los roles (UserInfo no los sustituye). El `tenant_id` sale siempre del token.
nonisolated struct UserSession: Codable, Hashable, Sendable {
    let name: String
    let username: String
    let tenantId: String
    let roles: [AppRole]

    var isAdministrator: Bool { roles.contains(.tenantAdmin) || roles.contains(.platformAdmin) }
    var canResolveReviews: Bool { roles.contains(.analyst) || roles.contains(.tenantAdmin) }

    var initials: String {
        let parts = name
            .replacingOccurrences(of: "(demo)", with: "")
            .split(separator: " ")
            .prefix(2)
        let letters = parts.compactMap(\.first).map(String.init).joined()
        return letters.isEmpty ? "DC" : letters.uppercased()
    }

    var firstName: String {
        name.split(separator: " ").first.map(String.init) ?? name
    }
}

nonisolated extension UserSession {
    /// Impide restaurar tokens de otro entorno o cuentas sin institución.
    init(accessToken: String, configuration: AuthConfiguration) throws {
        let claims = try JWT.claims(of: accessToken)
        let audiences = (claims["aud"] as? [String]) ?? (claims["aud"] as? String).map { [$0] } ?? []
        guard claims["iss"] as? String == configuration.issuer.absoluteString,
              audiences.contains("doggycredit-api"),
              let tenant = claims["tenant_id"] as? String, UUID(uuidString: tenant) != nil else {
            throw JWT.Failure.invalidAccount
        }
        try self.init(accessToken: accessToken)
    }

    /// Construye la sesión desde las claims del access token de Keycloak.
    init(accessToken: String) throws {
        let claims = try JWT.claims(of: accessToken)
        let realmAccess = claims["realm_access"] as? [String: Any]
        let roles = (realmAccess?["roles"] as? [String] ?? []).compactMap(AppRole.init(rawValue:))
        let username = (claims["preferred_username"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        let name = (claims["name"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        self.init(
            name: name.isEmpty ? (username.isEmpty ? "Usuario" : username) : name,
            username: username,
            tenantId: (claims["tenant_id"] as? String) ?? "",
            roles: roles
        )
    }
}

/// Decodifica el payload de un JWT sin verificar la firma: eso lo hace la API.
/// La app solo lo usa para presentar nombre y permisos.
nonisolated enum JWT {
    enum Failure: Error {
        case malformed
        case invalidAccount
    }

    static func claims(of token: String) throws -> [String: Any] {
        let segments = token.split(separator: ".")
        guard segments.count >= 2,
              let data = Data(base64URLEncoded: String(segments[1])),
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw Failure.malformed }
        return object
    }
}
