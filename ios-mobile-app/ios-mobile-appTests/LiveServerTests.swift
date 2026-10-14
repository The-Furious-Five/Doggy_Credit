import Foundation
import Testing
@testable import ios_mobile_app

/// Pruebas optativas, de solo lectura, contra el VPS. No crean cuentas ni registros.
@Suite("Servidor desplegado", .enabled(if: ProcessInfo.processInfo.environment["DC_LIVE_TESTS"] == "1"))
struct LiveServerTests {
    @Test("El issuer y el acceso PKCE móvil están disponibles en el servidor real")
    func publicAuthentication() async throws {
        let configuration = AuthConfiguration(server: AuthConfiguration.defaultServer)
        let discovery = configuration.issuer.appending(path: ".well-known/openid-configuration")
        let (data, response) = try await URLSession.doggyCredit.data(from: discovery)
        #expect((response as? HTTPURLResponse)?.statusCode == 200)
        let metadata = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(metadata["issuer"] as? String == configuration.issuer.absoluteString)
        #expect(metadata["token_endpoint"] as? String == configuration.tokenEndpoint.absoluteString)

        let authorization = OIDCClient(configuration: configuration).makeAuthorizationRequest()
        let (page, loginResponse) = try await URLSession.doggyCredit.data(from: authorization.url)
        #expect((loginResponse as? HTTPURLResponse)?.statusCode == 200)
        #expect(String(decoding: page, as: UTF8.self).contains("kc-form-login"))
    }

    @Test("La API real rechaza peticiones sin credenciales con un error del contrato")
    func requiresAuthentication() async throws {
        let url = AuthConfiguration(server: AuthConfiguration.defaultServer).apiBaseURL.appending(path: "tenant/actual")
        let (data, response) = try await URLSession.doggyCredit.data(from: url)
        #expect((response as? HTTPURLResponse)?.statusCode == 401)
        let problem = try JSONDecoder.doggyCredit.decode(ProblemDetail.self, from: data)
        #expect(problem.errorCode == .unauthenticated)
    }

    @Test("Lectura de todos los flujos con una cuenta real", .enabled(if: ProcessInfo.processInfo.environment["DC_LIVE_ACCESS_TOKEN"] != nil))
    func authenticatedReads() async throws {
        let configuration = AuthConfiguration(server: AuthConfiguration.defaultServer)
        let token = try #require(ProcessInfo.processInfo.environment["DC_LIVE_ACCESS_TOKEN"])
        let user = try UserSession(accessToken: token, configuration: configuration)
        let provider = TokenProvider(
            tokens: TokenSet(accessToken: token, refreshToken: nil, idToken: nil, expiresAt: .now.addingTimeInterval(3600)),
            client: OIDCClient(configuration: configuration), onSessionExpired: {}
        )
        let service = LiveDoggyCreditService(http: HTTPClient(baseURL: configuration.apiBaseURL, tokens: provider))
        let tenant = try await service.currentTenant()
        #expect(tenant.id.uuidString.lowercased() == user.tenantId.lowercased())
        _ = try await service.preferences()
        _ = try await service.usage()
        _ = try await service.integrations()
        _ = try await service.analyticsSummary(from: nil, to: nil)
        if user.canResolveReviews { _ = try await service.reviewCases(page: 1, size: 10) }
        let clients = try await service.applicants(page: 1, size: 10, segment: nil, document: nil)
        if let client = clients.items.first {
            _ = try await service.applicant(id: client.id)
            _ = try await service.consents(applicantId: client.id)
            _ = try await service.assessments(applicantId: client.id, since: nil, page: 1, size: 10)
            let search = try await service.applicants(page: 1, size: 1, segment: nil, document: client.document)
            #expect(search.items.first?.id == client.id)
        }
        let assessments = try await service.assessments(applicantId: nil, since: nil, page: 1, size: 10)
        if let first = assessments.items.first { _ = try await service.assessment(id: first.id) }
    }
}
