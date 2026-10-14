import Foundation
import Synchronization
import Testing
@testable import ios_mobile_app

@Suite("Integración HTTP de iOS", .serialized)
struct NetworkingTests {
    @Test("La búsqueda transmite documento en cabecera, paginación, segmento y bearer")
    func applicantSearch() async throws {
        let transport = TestHTTPTransport { request in
            #expect(request.httpMethod == "GET")
            #expect(request.url?.path() == "/api/v1/solicitantes")
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer original-token")
            #expect(request.value(forHTTPHeaderField: "X-Documento") == "CI-4455667")
            #expect(request.value(forHTTPHeaderField: "Cache-Control") == "no-store")
            let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)!.queryItems!
            #expect(query.contains(URLQueryItem(name: "pagina", value: "2")))
            #expect(query.contains(URLQueryItem(name: "tamano", value: "25")))
            #expect(query.contains(URLQueryItem(name: "segmento", value: "PERSONA")))
            #expect(!request.url!.absoluteString.contains("4455667"))
            return (200, #"{"items":[],"total":0}"#)
        }
        let page = try await transport.service().applicants(page: 2, size: 25, segment: .person, document: "CI-4455667")
        #expect(page.items.isEmpty && page.total == 0)
    }

    @Test("Una respuesta inválida falla sin sustituirla por datos locales")
    func invalidResponse() async {
        let transport = TestHTTPTransport { _ in (200, "<html>Servidor no disponible</html>") }
        await #expect(throws: APIError.decoding) { try await transport.service().currentTenant() }
    }

    @Test("Los errores del servidor conservan código, detalle y correlación")
    func serverProblem() async {
        let transport = TestHTTPTransport { _ in
            (429, #"{"title":"Cuota agotada","status":429,"code":"QUOTA_EXCEEDED","detail":"Límite del servidor.","correlationId":"request-123"}"#)
        }
        do {
            _ = try await transport.service().usage()
            Issue.record("Se esperaba el error de la API")
        } catch {
            let api = APIError(error)
            #expect(api.code == .quotaExceeded)
            #expect(api.message == "Límite del servidor.")
            #expect(api.correlationId == "request-123")
        }
    }

    @Test("Un 401 renueva y reintenta el POST con el mismo cuerpo e Idempotency-Key")
    func retriesAssessmentAfterRefresh() async throws {
        let requests = Mutex<[URLRequest]>([])
        let expired = Mutex(false)
        let transport = TestHTTPTransport { request in
            requests.withLock { $0.append(request) }
            if request.url!.path().hasSuffix("/token") {
                #expect(request.httpMethod == "POST")
                let form = String(decoding: TestHTTPTransport.body(of: request), as: UTF8.self)
                #expect(form.contains("grant_type=refresh_token"))
                #expect(form.contains("client_id=doggycredit-mobile"))
                return (200, #"{"access_token":"renewed-token","refresh_token":"rotated-refresh","expires_in":300}"#)
            }
            #expect(request.url!.path() == "/api/v1/credit-assessments")
            #expect(request.httpMethod == "POST")
            #expect(request.value(forHTTPHeaderField: "Idempotency-Key") == "stable-assessment-key")
            let body = try JSONSerialization.jsonObject(with: TestHTTPTransport.body(of: request)) as! [String: Any]
            #expect(body["titularDocumento"] as? String == "CI-4455667")
            #expect(body["montoSolicitado"] as? String == "12000.00")
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer original-token" { return (401, "{}") }
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer renewed-token")
            return (201, #"{"score":710,"banda":"B","simulado":false,"razones":["SIN_MORA_12M"]}"#)
        }
        let tokens = transport.tokens { expired.withLock { $0 = true } }
        let service = LiveDoggyCreditService(http: HTTPClient(baseURL: transport.configuration.apiBaseURL,
                                                             tokens: tokens, session: transport.session))
        let result = try await service.createAssessment(
            NewAssessment(holderDocument: "CI-4455667", consentId: UUID(), purpose: "Capital de trabajo",
                          requestedAmount: "12000.00", termMonths: 12), idempotencyKey: "stable-assessment-key"
        )
        #expect(result.score == 710 && !result.simulated)
        #expect(requests.withLock { $0.count } == 3)
        #expect(!expired.withLock { $0 })
        #expect(await tokens.currentRefreshToken() == "rotated-refresh")
    }

    @Test("Un segundo 401 invalida la sesión sin bucles de renovación")
    func repeatedUnauthorized() async {
        let expired = Mutex(false)
        let count = Mutex(0)
        let transport = TestHTTPTransport { request in
            count.withLock { $0 += 1 }
            if request.url!.path().hasSuffix("/token") {
                return (200, #"{"access_token":"renewed-token","expires_in":300}"#)
            }
            return (401, "{}")
        }
        let http = HTTPClient(baseURL: transport.configuration.apiBaseURL,
                              tokens: transport.tokens { expired.withLock { $0 = true } }, session: transport.session)
        await #expect(throws: APIError.unauthenticated) {
            try await http.send(APIRequest(path: "tenant/actual"), as: Tenant.self)
        }
        #expect(expired.withLock { $0 })
        #expect(count.withLock { $0 } == 3)
    }

    @Test("Una caída de Keycloak conserva la sesión y permite renovar después")
    func transientRefreshFailure() async throws {
        let expired = Mutex(false)
        let attempts = Mutex(0)
        let transport = TestHTTPTransport { _ in
            let attempt = attempts.withLock { $0 += 1; return $0 }
            return attempt == 1 ? (503, "{}") : (200, #"{"access_token":"renewed-token","expires_in":300}"#)
        }
        let tokens = transport.tokens(expired: true) { expired.withLock { $0 = true } }
        await #expect(throws: APIError.http(status: 503)) { try await tokens.accessToken() }
        #expect(!expired.withLock { $0 })
        #expect(try await tokens.accessToken() == "renewed-token")
        #expect(await tokens.currentRefreshToken() == "refresh-token")
    }

    @Test("Un 401 tardío reutiliza el token recién renovado")
    func lateUnauthorized() async throws {
        let count = Mutex(0)
        let transport = TestHTTPTransport { _ in
            count.withLock { $0 += 1 }
            return (200, #"{"access_token":"renewed-token","expires_in":300}"#)
        }
        let tokens = transport.tokens()
        #expect(try await tokens.refreshAfterRejection(rejectedToken: "original-token") == "renewed-token")
        #expect(try await tokens.refreshAfterRejection(rejectedToken: "original-token") == "renewed-token")
        #expect(count.withLock { $0 } == 1)
    }

    @Test("El canje PKCE envía el código y el verificador del flujo autorizado")
    func codeExchange() async throws {
        let transport = TestHTTPTransport { request in
            #expect(request.httpMethod == "POST")
            #expect(request.url!.path().hasSuffix("/protocol/openid-connect/token"))
            let body = String(decoding: TestHTTPTransport.body(of: request), as: UTF8.self)
            #expect(body.contains("grant_type=authorization_code"))
            #expect(body.contains("code=valid-code"))
            #expect(body.contains("code_verifier="))
            #expect(body.contains("redirect_uri=doggycredit%3A%2F%2Foauth%2Fcallback"))
            return (200, #"{"access_token":"api-token","refresh_token":"refresh","expires_in":300}"#)
        }
        let client = OIDCClient(configuration: transport.configuration, session: transport.session)
        let request = client.makeAuthorizationRequest()
        let callback = URL(string: "doggycredit://oauth/callback?state=\(request.state)&code=valid-code")!
        #expect(try await client.completeAuthorization(request, callback: callback).accessToken == "api-token")
    }
}
