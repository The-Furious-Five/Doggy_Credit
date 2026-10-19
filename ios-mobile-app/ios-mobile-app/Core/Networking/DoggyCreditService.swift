import Foundation

/// Operaciones de la API pública v1 que usa la app móvil.
///
/// La app no administra productos, políticas, integraciones, claves ni equipo: eso
/// vive en la consola web. Aquí está solo lo que un analista necesita en el día a día.
/// La app usa exclusivamente `LiveDoggyCreditService` (HTTP contra el backend).
/// Los dobles de prueba se compilan únicamente en el target de pruebas.
nonisolated protocol DoggyCreditService: Sendable {
    // Tenant
    func currentTenant() async throws -> Tenant
    func usage() async throws -> TenantUsage
    func preferences() async throws -> TenantPreferences

    // Solicitantes
    /// El documento viaja en la cabecera `X-Documento`, nunca en la URL (B-08).
    func applicants(page: Int, size: Int, segment: Segment?, document: String?) async throws -> Page<Applicant>
    func createApplicant(_ applicant: NewApplicant) async throws -> Applicant
    func applicant(id: UUID) async throws -> ApplicantDetail

    // Consentimientos
    func consents(applicantId: UUID) async throws -> [Consent]
    func createConsent(_ consent: NewConsent) async throws -> Consent
    /// Idempotente: revocar uno ya revocado o expirado lo devuelve sin cambios.
    func revokeConsent(id: UUID) async throws -> Consent

    // Integraciones (solo lectura en la app)
    func integrations() async throws -> [Integration]

    // Evaluaciones
    func assessments(applicantId: UUID?, since: Date?, page: Int, size: Int) async throws -> Page<AssessmentSummary>
    /// La `Idempotency-Key` se conserva entre reintentos para no duplicar evaluaciones.
    func createAssessment(_ assessment: NewAssessment, idempotencyKey: String) async throws -> Assessment
    func assessment(id: UUID) async throws -> Assessment

    // Revisión manual (RF-DEC-03)
    func reviewCases(page: Int, size: Int) async throws -> ReviewCasePage
    func resolveCase(assessmentId: UUID, resolution: NewResolution) async throws -> Resolution

    // Analítica
    func analyticsSummary(from: Date?, to: Date?) async throws -> AnalyticsSummary
}
