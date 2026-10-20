import AuthenticationServices
import Foundation

/// Estado raíz de la sesión autenticada contra el servidor de DoggyCredit.
@Observable
final class AppModel {
    enum Phase {
        case restoring
        case signedOut
        case signedIn(SessionStore)
    }

    /// Las sesiones antiguas de demostración ya no son restaurables.
    private enum StoredMode: Codable {
        case live
    }

    private enum Keys {
        static let mode = "session.mode"
        static let server = "session.server"
    }

    private(set) var phase: Phase = .restoring
    private(set) var isSigningIn = false
    /// Mensaje para la pantalla de bienvenida (sesión vencida, error de acceso).
    var notice: String?
    let lock: AppLock

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard, lock: AppLock? = nil) {
        self.defaults = defaults
        self.lock = lock ?? AppLock(defaults: defaults)
    }

    // El estado no requiere limpieza en MainActor al liberar la instancia.
    nonisolated deinit {}

    var server: URL { AuthConfiguration.defaultServer }

    var session: SessionStore? {
        if case let .signedIn(session) = phase { session } else { nil }
    }

    // MARK: Entrar

    func restore() {
        guard case .restoring = phase else { return }
        #if DEBUG
        if DebugLaunchOptions.startsSignedOut {
            signOutLocally()
            return
        }
        #endif
        // No enviar al VPS tokens guardados para otro servidor.
        if let previousServer = defaults.url(forKey: Keys.server), previousServer != server {
            signOutLocally()
            return
        }
        switch storedMode {
        case .live:
            if let tokens = TokenStore.load(),
               let user = try? UserSession(accessToken: tokens.accessToken, configuration: AuthConfiguration(server: server)) {
                phase = .signedIn(makeLiveSession(tokens: tokens, user: user))
            } else {
                signOutLocally()
            }
        case nil:
            signOutLocally()
        }
    }

    /// Authorization Code + PKCE en una sesión web efímera: la contraseña solo se escribe en Keycloak.
    func signIn(authenticate: (URL) async throws -> URL) async {
        guard !isSigningIn else { return }
        isSigningIn = true
        notice = nil
        defer { isSigningIn = false }
        let client = OIDCClient(configuration: AuthConfiguration(server: server))
        let request = client.makeAuthorizationRequest()
        do {
            let callback = try await authenticate(request.url)
            let tokens = try await client.completeAuthorization(request, callback: callback)
            let user = try UserSession(accessToken: tokens.accessToken, configuration: client.configuration)
            let session = makeLiveSession(tokens: tokens, user: user)
            // Completar el acceso solo si la API reconoce la cuenta y su institución.
            _ = try await session.service.currentTenant()
            TokenStore.save(tokens)
            storedMode = .live
            defaults.set(server, forKey: Keys.server)
            phase = .signedIn(session)
        } catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin {
            // La persona cerró la hoja de acceso: no es un error.
        } catch let error as URLError {
            notice = APIError.transport(error.code).message
        } catch let error as APIError {
            notice = error.message
        } catch {
            notice = "No pudimos iniciar sesión con \(server.host() ?? "el servidor"). Comprueba que tu cuenta tenga una institución y permisos asignados, y vuelve a intentarlo."
        }
    }

    // MARK: Salir

    func signOut() async {
        if case let .signedIn(session) = phase,
           let refreshToken = TokenStore.load()?.refreshToken {
            await OIDCClient(configuration: session.configuration).endSession(refreshToken: refreshToken)
        }
        signOutLocally()
    }

    /// El refresh token venció o fue revocado.
    func sessionExpired() {
        guard session != nil else { return }
        signOutLocally()
        notice = "Tu sesión venció. Inicia sesión de nuevo para continuar."
    }

    private func signOutLocally() {
        TokenStore.clear()
        storedMode = nil
        defaults.removeObject(forKey: Keys.server)
        lock.reset()
        phase = .signedOut
    }

    private func makeLiveSession(tokens: TokenSet, user: UserSession) -> SessionStore {
        let configuration = AuthConfiguration(server: server)
        let provider = TokenProvider(tokens: tokens, client: OIDCClient(configuration: configuration)) { [weak self] in
            Task { @MainActor in self?.sessionExpired() }
        }
        let service = LiveDoggyCreditService(http: HTTPClient(baseURL: configuration.apiBaseURL, tokens: provider))
        return SessionStore(user: user, configuration: configuration, service: service)
    }

    private var storedMode: StoredMode? {
        get { defaults.data(forKey: Keys.mode).flatMap { try? JSONDecoder().decode(StoredMode.self, from: $0) } }
        set {
            if let newValue, let data = try? JSONEncoder().encode(newValue) {
                defaults.set(data, forKey: Keys.mode)
            } else {
                defaults.removeObject(forKey: Keys.mode)
            }
        }
    }
}
