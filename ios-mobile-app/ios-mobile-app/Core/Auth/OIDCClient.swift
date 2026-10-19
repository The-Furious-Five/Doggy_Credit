import Foundation

/// Flujo Authorization Code + PKCE contra Keycloak. Las credenciales se escriben solo
/// en Keycloak (dentro de ASWebAuthenticationSession), nunca en la app.
nonisolated struct OIDCClient: Sendable {
    enum Failure: Error, Equatable {
        case cancelled
        case stateMismatch
        case authorizationDenied(String)
        case missingCode
        case invalidCallback
        case tokenRequestFailed(status: Int)
    }

    /// Lo que hay que conservar entre abrir el navegador y recibir el callback.
    struct AuthorizationRequest: Sendable {
        let url: URL
        let pkce: PKCE
        let state: String
    }

    let configuration: AuthConfiguration
    var session: URLSession = .doggyCredit

    func makeAuthorizationRequest() -> AuthorizationRequest {
        let pkce = PKCE()
        let state = PKCE.randomURLSafeString(byteCount: 16)
        var components = URLComponents(url: configuration.authorizationEndpoint, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: AuthConfiguration.clientId),
            URLQueryItem(name: "redirect_uri", value: AuthConfiguration.redirectURI),
            URLQueryItem(name: "scope", value: AuthConfiguration.scopes),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: pkce.challenge),
            URLQueryItem(name: "code_challenge_method", value: pkce.method),
        ]
        return AuthorizationRequest(url: components.url!, pkce: pkce, state: state)
    }

    /// Valida el callback (state, error) y canjea el código por tokens.
    func completeAuthorization(_ request: AuthorizationRequest, callback: URL) async throws -> TokenSet {
        guard callback.scheme == AuthConfiguration.callbackScheme,
              callback.host() == "oauth", callback.path() == "/callback" else {
            throw Failure.invalidCallback
        }
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let value = { (name: String) in items.first { $0.name == name }?.value }
        guard value("state") == request.state else { throw Failure.stateMismatch }
        if let error = value("error") {
            throw Failure.authorizationDenied(value("error_description") ?? error)
        }
        guard let code = value("code"), !code.isEmpty else { throw Failure.missingCode }
        return try await tokenRequest([
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": AuthConfiguration.redirectURI,
            "client_id": AuthConfiguration.clientId,
            "code_verifier": request.pkce.verifier,
        ])
    }

    func refresh(_ refreshToken: String) async throws -> TokenSet {
        try await tokenRequest([
            "grant_type": "refresh_token",
            "refresh_token": refreshToken,
            "client_id": AuthConfiguration.clientId,
        ])
    }

    /// Cierra la sesión de Keycloak (cliente público: basta el refresh token).
    /// Es de mejor esfuerzo: los tokens locales se borran igual.
    func endSession(refreshToken: String) async {
        var request = URLRequest(url: configuration.logoutEndpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Self.formBody(["client_id": AuthConfiguration.clientId, "refresh_token": refreshToken])
        _ = try? await session.data(for: request)
    }

    private func tokenRequest(_ parameters: [String: String]) async throws -> TokenSet {
        var request = URLRequest(url: configuration.tokenEndpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = Self.formBody(parameters)
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw Failure.tokenRequestFailed(status: status) }
        let body = try JSONDecoder().decode(TokenResponse.self, from: data)
        return TokenSet(
            accessToken: body.accessToken,
            refreshToken: body.refreshToken,
            idToken: body.idToken,
            expiresAt: Date.now.addingTimeInterval(TimeInterval(body.expiresIn))
        )
    }

    private static func formBody(_ parameters: [String: String]) -> Data {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return parameters
            .sorted { $0.key < $1.key }
            .map { key, value in
                "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value)"
            }
            .joined(separator: "&")
            .data(using: .utf8) ?? Data()
    }

    private struct TokenResponse: Decodable {
        let accessToken: String
        let refreshToken: String?
        let idToken: String?
        let expiresIn: Int

        enum CodingKeys: String, CodingKey {
            case accessToken = "access_token"
            case refreshToken = "refresh_token"
            case idToken = "id_token"
            case expiresIn = "expires_in"
        }
    }
}
