import Foundation

/// Implementación HTTP de la API v1 (contract-first: rutas y cuerpos del OpenAPI).
nonisolated struct LiveDoggyCreditService: DoggyCreditService {
    let http: HTTPClient

    func currentTenant() async throws -> Tenant {
        try await http.send(APIRequest(path: "tenant/actual"))
    }

    func usage() async throws -> TenantUsage {
        try await http.send(APIRequest(path: "tenant/actual/uso"))
    }

    func preferences() async throws -> TenantPreferences {
        try await http.send(APIRequest(path: "tenant/actual/preferencias"))
    }

    func applicants(page: Int, size: Int, segment: Segment?, document: String?) async throws -> Page<Applicant> {
        var request = APIRequest(path: "solicitantes", query: pagination(page, size))
        if let segment { request.query.append(URLQueryItem(name: "segmento", value: segment.rawValue)) }
        // El documento va en una cabecera para que no quede en logs de acceso (B-08).
        if let document { request.headers["X-Documento"] = document }
        return try await http.send(request)
    }

    func createApplicant(_ applicant: NewApplicant) async throws -> Applicant {
        try await http.send(.json(.post, "solicitantes", body: applicant))
    }

    func applicant(id: UUID) async throws -> ApplicantDetail {
        try await http.send(APIRequest(path: "solicitantes/\(id.uuidString.lowercased())"))
    }

    func consents(applicantId: UUID) async throws -> [Consent] {
        try await http.send(APIRequest(
            path: "consentimientos",
            query: [URLQueryItem(name: "solicitanteId", value: applicantId.uuidString.lowercased())]
        ))
    }

    func createConsent(_ consent: NewConsent) async throws -> Consent {
        try await http.send(.json(.post, "consentimientos", body: consent))
    }

    func revokeConsent(id: UUID) async throws -> Consent {
        try await http.send(APIRequest(method: .post, path: "consentimientos/\(id.uuidString.lowercased())/revocar"))
    }

    func integrations() async throws -> [Integration] {
        try await http.send(APIRequest(path: "integraciones"))
    }

    func assessments(applicantId: UUID?, since: Date?, page: Int, size: Int) async throws -> Page<AssessmentSummary> {
        var request = APIRequest(path: "credit-assessments", query: pagination(page, size))
        if let applicantId {
            request.query.append(URLQueryItem(name: "solicitanteId", value: applicantId.uuidString.lowercased()))
        }
        if let since {
            request.query.append(URLQueryItem(name: "desde", value: APIDate.dateTimeString(since)))
        }
        return try await http.send(request)
    }

    func createAssessment(_ assessment: NewAssessment, idempotencyKey: String) async throws -> Assessment {
        try await http.send(.json(.post, "credit-assessments", body: assessment, headers: ["Idempotency-Key": idempotencyKey]))
    }

    func assessment(id: UUID) async throws -> Assessment {
        try await http.send(APIRequest(path: "credit-assessments/\(id.uuidString.lowercased())"))
    }

    func reviewCases(page: Int, size: Int) async throws -> ReviewCasePage {
        try await http.send(APIRequest(path: "revision-manual", query: pagination(page, size)))
    }

    func resolveCase(assessmentId: UUID, resolution: NewResolution) async throws -> Resolution {
        try await http.send(.json(.post, "revision-manual/\(assessmentId.uuidString.lowercased())/resolucion", body: resolution))
    }

    func analyticsSummary(from: Date?, to: Date?) async throws -> AnalyticsSummary {
        var request = APIRequest(path: "analytics/resumen")
        if let from { request.query.append(URLQueryItem(name: "desde", value: APIDate.dayString(from))) }
        if let to { request.query.append(URLQueryItem(name: "hasta", value: APIDate.dayString(to))) }
        return try await http.send(request)
    }

    private func pagination(_ page: Int, _ size: Int) -> [URLQueryItem] {
        [URLQueryItem(name: "pagina", value: String(page)), URLQueryItem(name: "tamano", value: String(size))]
    }
}
