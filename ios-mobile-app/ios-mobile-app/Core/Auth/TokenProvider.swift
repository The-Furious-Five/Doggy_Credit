import Foundation

/// Fuente única del access token. Un actor serializa las renovaciones: si varias
/// peticiones encuentran el token vencido a la vez, todas esperan la misma renovación.
actor TokenProvider {
    private var tokens: TokenSet
    private var refreshTask: Task<TokenSet, any Error>?
    private let client: OIDCClient
    private let onSessionExpired: @Sendable () -> Void

    init(tokens: TokenSet, client: OIDCClient, onSessionExpired: @escaping @Sendable () -> Void) {
        self.tokens = tokens
        self.client = client
        self.onSessionExpired = onSessionExpired
    }

    func accessToken() async throws -> String {
        if !tokens.isExpired() { return tokens.accessToken }
        return try await refreshed().accessToken
    }

    /// Fuerza una renovación tras un 401 aunque el reloj diga que el token sigue vigente.
    func refreshAfterRejection(rejectedToken: String) async throws -> String {
        // Otro request pudo completar la renovación mientras este 401 estaba en tránsito.
        if tokens.accessToken != rejectedToken, !tokens.isExpired() { return tokens.accessToken }
        return try await refreshed().accessToken
    }

    func reject(accessToken: String) {
        guard tokens.accessToken == accessToken else { return }
        expire()
    }

    func currentRefreshToken() -> String? {
        tokens.refreshToken
    }

    private func refreshed() async throws -> TokenSet {
        if let refreshTask {
            do {
                return try await refreshTask.value
            } catch let error as URLError {
                throw error
            } catch let OIDCClient.Failure.tokenRequestFailed(status) where status >= 500 || status == 429 {
                throw APIError.http(status: status)
            } catch {
                throw APIError.unauthenticated
            }
        }
        guard let refreshToken = tokens.refreshToken else {
            expire()
            throw APIError.unauthenticated
        }
        let task = Task { [client] in try await client.refresh(refreshToken) }
        refreshTask = task
        defer { refreshTask = nil }
        do {
            let response = try await task.value
            let renewed = TokenSet(
                accessToken: response.accessToken, refreshToken: response.refreshToken ?? tokens.refreshToken,
                idToken: response.idToken ?? tokens.idToken, expiresAt: response.expiresAt
            )
            tokens = renewed
            TokenStore.save(renewed)
            return renewed
        } catch let error as URLError {
            // Sin red no se pierde la sesión: se reintentará con conexión.
            throw error
        } catch let OIDCClient.Failure.tokenRequestFailed(status) where status >= 500 || status == 429 {
            // Una caída temporal del servidor de identidad no revoca la sesión.
            throw APIError.http(status: status)
        } catch {
            expire()
            throw APIError.unauthenticated
        }
    }

    private func expire() {
        TokenStore.clear()
        onSessionExpired()
    }
}
