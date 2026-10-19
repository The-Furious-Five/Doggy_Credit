import Foundation
import Testing
@testable import ios_mobile_app

@Suite("Autenticación")
struct AuthTests {
    @Test("PKCE S256 coincide con el vector del RFC 7636")
    func pkceMatchesRFCVector() {
        let pkce = PKCE(verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        #expect(pkce.challenge == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        #expect(pkce.method == "S256")
    }

    @Test("Los verificadores aleatorios son URL-safe y distintos")
    func randomVerifiers() {
        let first = PKCE().verifier
        let second = PKCE().verifier
        #expect(first != second)
        #expect(first.count >= 43)
        #expect(first.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" })
    }

    @Test("La URL de autorización lleva PKCE, state y el cliente móvil")
    func authorizationURL() throws {
        let client = OIDCClient(configuration: AuthConfiguration(server: URL(string: "https://doggycredit.example")!))
        let request = client.makeAuthorizationRequest()
        let items = try #require(URLComponents(url: request.url, resolvingAgainstBaseURL: false)?.queryItems)
        let value = { (name: String) in items.first { $0.name == name }?.value }
        #expect(request.url.path() == "/auth/realms/doggycredit/protocol/openid-connect/auth")
        #expect(value("client_id") == "doggycredit-mobile")
        #expect(value("response_type") == "code")
        #expect(value("code_challenge_method") == "S256")
        #expect(value("code_challenge") == request.pkce.challenge)
        #expect(value("state") == request.state)
        #expect(value("redirect_uri") == "doggycredit://oauth/callback")
    }

    @Test("Un callback con otro state se rechaza antes de canjear el código")
    func rejectsStateMismatch() async {
        let client = OIDCClient(configuration: AuthConfiguration(server: AuthConfiguration.defaultServer))
        let request = client.makeAuthorizationRequest()
        let callback = URL(string: "doggycredit://oauth/callback?code=abc&state=otro")!
        await #expect(throws: OIDCClient.Failure.stateMismatch) {
            try await client.completeAuthorization(request, callback: callback)
        }
    }

    @Test("Un callback con error de Keycloak informa el motivo")
    func reportsAuthorizationError() async {
        let client = OIDCClient(configuration: AuthConfiguration(server: AuthConfiguration.defaultServer))
        let request = client.makeAuthorizationRequest()
        let callback = URL(string: "doggycredit://oauth/callback?state=\(request.state)&error=access_denied&error_description=Denegado")!
        await #expect(throws: OIDCClient.Failure.authorizationDenied("Denegado")) {
            try await client.completeAuthorization(request, callback: callback)
        }
    }

    @Test("Un callback de otro origen se rechaza antes del canje")
    func rejectsCallbackOrigin() async {
        let client = OIDCClient(configuration: AuthConfiguration(server: AuthConfiguration.defaultServer))
        let request = client.makeAuthorizationRequest()
        let callback = URL(string: "https://other.example/oauth/callback?state=\(request.state)&code=abc")!
        await #expect(throws: OIDCClient.Failure.invalidCallback) {
            try await client.completeAuthorization(request, callback: callback)
        }
    }

    @Test("La cuenta real requiere issuer, audiencia e institución del servidor")
    func validatesLiveAccount() throws {
        let configuration = AuthConfiguration(server: AuthConfiguration.defaultServer)
        var claims: [String: Any] = [
            "iss": configuration.issuer.absoluteString, "aud": ["doggycredit-api", "account"],
            "tenant_id": UUID().uuidString, "preferred_username": "analista",
        ]
        #expect(try UserSession(accessToken: Self.jwt(claims), configuration: configuration).username == "analista")
        claims["iss"] = "https://localhost/auth/realms/doggycredit"
        #expect(throws: JWT.Failure.self) { try UserSession(accessToken: Self.jwt(claims), configuration: configuration) }
        claims["iss"] = configuration.issuer.absoluteString
        claims.removeValue(forKey: "tenant_id")
        #expect(throws: JWT.Failure.self) { try UserSession(accessToken: Self.jwt(claims), configuration: configuration) }
    }

    @Test("Una sesión de demostración antigua no abre la app autenticada")
    func migratesLegacyDemo() {
        let suiteName = "migration-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        defaults.set(Data(#"{"demo":{"_0":"analyst"}}"#.utf8), forKey: "session.mode")
        let app = AppModel(defaults: defaults)
        app.restore()
        #expect(app.session == nil)
        if case .signedOut = app.phase {} else { Issue.record("Debe exigir acceso real") }
        #expect(defaults.data(forKey: "session.mode") == nil)
    }

    @Test("La sesión sale del access token: nombre, tenant y solo roles conocidos")
    func sessionFromAccessToken() throws {
        let token = try Self.jwt([
            "name": "Rosa Mamani",
            "preferred_username": "admin.progreso",
            "tenant_id": "9c7e7c1a-0000-4000-8000-000000000001",
            "realm_access": ["roles": ["TENANT_ADMIN", "offline_access", "uma_authorization"]],
        ])
        let session = try UserSession(accessToken: token)
        #expect(session.name == "Rosa Mamani")
        #expect(session.username == "admin.progreso")
        #expect(session.tenantId == "9c7e7c1a-0000-4000-8000-000000000001")
        #expect(session.roles == [.tenantAdmin])
        #expect(session.isAdministrator)
        #expect(session.initials == "RM")
    }

    @Test("Sin nombre se usa el usuario")
    func fallsBackToUsername() throws {
        let session = try UserSession(accessToken: Self.jwt(["preferred_username": "analista.progreso"]))
        #expect(session.name == "analista.progreso")
        #expect(session.roles.isEmpty)
        #expect(!session.canResolveReviews)
    }

    @Test("Un token mal formado no crea sesión")
    func malformedToken() {
        #expect(throws: JWT.Failure.self) { try UserSession(accessToken: "no-es-un-jwt") }
    }

    @Test("Vencimiento con margen")
    func tokenExpiry() {
        let now = Date.now
        let tokens = TokenSet(accessToken: "a", refreshToken: "r", idToken: nil, expiresAt: now.addingTimeInterval(20))
        #expect(tokens.isExpired(at: now))
        #expect(!tokens.isExpired(at: now, leeway: 5))
    }

    @Test("Normaliza la dirección del servidor y exige HTTPS", arguments: [
        ("doggycredit.example", "https://doggycredit.example"),
        ("https://doggycredit.example/", "https://doggycredit.example"),
        ("  https://localhost  ", "https://localhost"),
    ])
    func serverNormalization(input: String, expected: String) {
        #expect(AuthConfiguration.server(from: input)?.absoluteString == expected)
    }

    @Test("Rechaza HTTP y textos vacíos", arguments: ["http://doggycredit.example", "", "   "])
    func serverRejection(input: String) {
        #expect(AuthConfiguration.server(from: input) == nil)
    }

    @Test("Rutas derivadas del origen único de Caddy")
    func derivedEndpoints() {
        let configuration = AuthConfiguration(server: URL(string: "https://doggycredit.example")!)
        #expect(configuration.apiBaseURL.absoluteString == "https://doggycredit.example/api/v1")
        #expect(configuration.tokenEndpoint.absoluteString
            == "https://doggycredit.example/auth/realms/doggycredit/protocol/openid-connect/token")
    }

    @Test("Una instalación nueva usa el VPS público")
    @MainActor
    func defaultProductionServer() {
        let suiteName = "auth-tests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let app = AppModel(defaults: defaults)
        #expect(app.server.absoluteString == "https://doggycredit.los5furiosos.cloud")
        let configuration = AuthConfiguration(server: app.server)
        #expect(configuration.apiBaseURL.absoluteString == "https://doggycredit.los5furiosos.cloud/api/v1")
        #expect(configuration.authorizationEndpoint.host() == "doggycredit.los5furiosos.cloud")
        let customServer = URL(string: "https://localhost")!
        defaults.set(customServer, forKey: "session.server")
        #expect(AppModel(defaults: defaults).server == AuthConfiguration.defaultServer)
    }

    static func jwt(_ claims: [String: Any]) throws -> String {
        let header = Data(#"{"alg":"RS256","typ":"JWT"}"#.utf8).base64URLEncodedString()
        let payload = try JSONSerialization.data(withJSONObject: claims).base64URLEncodedString()
        return "\(header).\(payload).firma"
    }
}
