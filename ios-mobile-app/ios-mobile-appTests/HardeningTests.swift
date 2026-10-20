import Foundation
import Testing
@testable import ios_mobile_app

@MainActor
@Suite("Resiliencia de flujos y listados")
struct HardeningTests {
    private func session(_ service: any DoggyCreditService) -> SessionStore {
        SessionStore(user: DemoScenario.analyst.user,
                     configuration: AuthConfiguration(server: AuthConfiguration.defaultServer), service: service)
    }

    @Test("Cambiar de cliente exige decidir qué hacer con el borrador")
    func draftConflict() {
        let session = session(HardeningService())
        session.startEvaluation(document: "CI-CLIENTE-A")
        session.evaluation.activity = "Actividad que debe conservarse"
        session.router.isEvaluationPresented = false
        session.startEvaluation(document: "CI-CLIENTE-B")
        #expect(session.pendingEvaluation?.document == "CI-CLIENTE-B")
        #expect(!session.router.isEvaluationPresented)
        #expect(session.evaluation.document == "CI-CLIENTE-A")
        session.pendingEvaluation = nil // Cancelar conserva todo.
        #expect(session.evaluation.activity == "Actividad que debe conservarse")
        session.startEvaluation(document: "CI-CLIENTE-B")
        session.continueEvaluation()
        #expect(session.router.isEvaluationPresented)
        #expect(session.evaluation.document == "CI-CLIENTE-A")
        session.router.isEvaluationPresented = false
        session.startEvaluation(document: "CI-CLIENTE-B")
        let confirmation = session.pendingEvaluation
        session.pendingEvaluation = nil // SwiftUI puede cerrar el diálogo antes de ejecutar su acción.
        session.replaceEvaluation(confirmation)
        #expect(session.evaluation.document == "CI-CLIENTE-B")
        #expect(session.evaluation.activity.isEmpty)
        #expect(!session.evaluation.consentBureau)
        #expect(session.router.isEvaluationPresented)
    }

    @Test("Reabrir el mismo cliente conserva su avance")
    func resumeSameApplicant() {
        let session = session(HardeningService())
        session.startEvaluation(document: "CI-CLIENTE-A")
        session.evaluation.activity = "Comercio"
        session.router.isEvaluationPresented = false
        session.startEvaluation(document: " ci-cliente-a ")
        #expect(session.pendingEvaluation == nil)
        #expect(session.router.isEvaluationPresented)
        #expect(session.evaluation.activity == "Comercio")
        session.router.isEvaluationPresented = false
        session.startEvaluation() // Nueva evaluación tampoco reemplaza el borrador en silencio.
        #expect(session.pendingEvaluation != nil)
        #expect(!session.router.isEvaluationPresented)
    }

    @Test("Una solicitud con solo monto también cuenta como borrador")
    func amountOnlyDraft() {
        let session = session(HardeningService())
        session.evaluation.requestedAmount = 15000
        session.startEvaluation(document: "CI-OTRO")
        #expect(session.pendingEvaluation != nil)
        #expect(session.evaluation.requestedAmount == 15000)
        #expect(!session.router.isEvaluationPresented)
    }

    @Test("Reevaluar una ficha conocida no pide repetir ingreso ni actividad")
    func knownApplicant() async throws {
        let service = HardeningService()
        let applicant = try #require(try await service.applicants(page: 1, size: 1, segment: nil, document: nil).items.first)
        let session = session(service)
        session.startEvaluation(document: applicant.document, applicant: applicant)
        let flow = session.evaluation
        #expect(flow.monthlyIncome == nil)
        #expect(flow.activity.isEmpty)
        flow.purpose = .workingCapital
        #expect(flow.isApplicantValid)
        await flow.next()
        #expect(flow.step == .consent)
        #expect(flow.applicantId == applicant.id)
        #expect(flow.existingApplicant == applicant)
    }

    @Test("Una coincidencia inesperada muestra la ficha antes de avanzar")
    func discoveredApplicant() async throws {
        let service = HardeningService()
        let applicant = try #require(try await service.applicants(page: 1, size: 1, segment: nil, document: nil).items.first)
        let flow = EvaluationFlowModel(service: service)
        flow.document = applicant.document
        flow.activity = "Actividad diferente que no se guardará"
        flow.monthlyIncome = 999999
        flow.purpose = .workingCapital
        await flow.next()
        #expect(flow.step == .applicant)
        #expect(flow.problem == .existingApplicant)
        #expect(flow.existingApplicant?.monthlyIncome == applicant.monthlyIncome)
        await flow.next()
        #expect(flow.step == .consent)
        let unchanged = try #require(try await service.applicants(page: 1, size: 1, segment: nil, document: applicant.document).items.first)
        #expect(unchanged == applicant)
    }

    @Test("Montos y plazos inválidos no permiten avanzar")
    func invalidRequest() async {
        let flow = EvaluationFlowModel(service: HardeningService())
        flow.document = "CI-NUEVO"
        flow.activity = "Comercio"
        flow.monthlyIncome = 5000
        flow.purpose = .workingCapital
        flow.requestedAmount = -1
        #expect(!flow.isApplicantValid)
        #expect(flow.pendingRequirement?.contains("mayor que cero") == true)
        await flow.next()
        #expect(flow.applicantId == nil)
        flow.requestedAmount = nil
        flow.termMonths = 121
        #expect(!flow.isApplicantValid)
        flow.termMonths = 12
        flow.seniorityMonths = -1
        #expect(!flow.isApplicantValid)
    }

    @Test("Cambiar de documento invalida los permisos del titular anterior")
    func consentBelongsToApplicant() {
        let flow = EvaluationFlowModel(service: HardeningService())
        flow.document = "CI-CLIENTE-A"
        flow.consentBureau = true
        flow.consentBank = true
        flow.consentWallet = true
        flow.document = "CI-CLIENTE-B"
        #expect(!flow.consentBureau)
        #expect(!flow.consentBank)
        #expect(!flow.consentWallet)
        #expect(flow.consentId == nil)
        #expect(flow.existingApplicant == nil)
        #expect(flow.step == .applicant)
    }

    @Test("Los filtros pueden buscar coincidencias después de varias páginas vacías")
    func filteredPagination() async {
        let queue = ReviewQueueModel(service: HardeningService())
        await queue.refresh()
        queue.filter = .partial
        #expect(queue.visibleCases.isEmpty)
        #expect(queue.hasMore)
        await queue.loadMore()
        #expect(queue.visibleCases.isEmpty)
        #expect(queue.hasMore)
        await queue.loadMore()
        #expect(queue.visibleCases.count == 1)
        #expect(!queue.hasMore)
        #expect(queue.cases.count == 41)
    }

    @Test("La última fila visible permite avanzar aunque no sea la última sin filtrar")
    func visiblePaginationTrigger() async throws {
        let service = HardeningService()
        await service.includeFirstPartial()
        let queue = ReviewQueueModel(service: service)
        await queue.refresh()
        queue.filter = .partial
        let first = try #require(queue.visibleCases.first)
        #expect(first.id != queue.cases.last?.id)
        await queue.loadMoreIfNeeded(after: first)
        #expect(queue.cases.count == 40)
    }

    @Test("Clientes conserva el listado y expone fallos de actualización y paginación")
    func clientErrorsAndRetry() async {
        let service = HardeningService()
        let clients = ClientsModel(service: service)
        await clients.refresh()
        let initial = clients.applicants
        let updated = clients.lastUpdatedAt
        await service.failApplicants(.transport(.notConnectedToInternet))
        await clients.refresh()
        #expect(clients.applicants == initial)
        #expect(clients.phase.error != nil)
        #expect(clients.lastUpdatedAt == updated)
        await clients.loadMore()
        #expect(clients.loadMoreError != nil)
        #expect(clients.applicants == initial)
        await service.failApplicants(nil)
        await clients.refresh()
        await clients.loadMore()
        #expect(clients.applicants.count == 30)
        #expect(clients.phase.error == nil)
        #expect(clients.loadMoreError == nil)
    }

    @Test("La bandeja conserva casos ante fallos y permite reintentar la página")
    func reviewErrorsAndRetry() async {
        let service = HardeningService()
        let queue = ReviewQueueModel(service: service)
        await queue.refresh()
        let initial = queue.cases
        await service.failReviews(.transport(.timedOut))
        await queue.refresh()
        #expect(queue.cases == initial)
        #expect(queue.phase.error != nil)
        await queue.loadMore()
        #expect(queue.loadMoreError != nil)
        await service.failReviews(nil)
        await queue.loadMore()
        #expect(queue.cases.count == 40)
        #expect(queue.loadMoreError == nil)
        await queue.refresh()
        #expect(queue.phase.error == nil)
    }

    @Test("Un permiso revocado retira los datos conservados")
    func forbiddenRefresh() async {
        let service = HardeningService()
        let clients = ClientsModel(service: service)
        let queue = ReviewQueueModel(service: service)
        await clients.refresh()
        await queue.refresh()
        await service.failApplicants(.http(status: 403))
        await service.failReviews(.http(status: 403))
        await clients.refresh()
        await queue.refresh()
        #expect(clients.applicants.isEmpty)
        #expect(queue.cases.isEmpty)
        #expect(queue.products.isEmpty)
        #expect(clients.phase.error?.isForbidden == true)
        #expect(queue.phase.error?.isForbidden == true)
    }

    @Test("Una respuesta antigua no reemplaza la búsqueda de otro cliente")
    func staleSearchResponse() async {
        let service = HardeningService()
        let search = ClientSearchModel(service: service)
        search.query = "CI-HARDEN-0"
        await service.holdNextApplicants()
        let old = Task { await search.search() }
        await service.waitForHeldApplicants()
        search.query = "CI-HARDEN-1"
        #expect(search.state == .idle)
        await search.search()
        await service.releaseApplicants()
        await old.value
        guard case let .found(result) = search.state else {
            Issue.record("La consulta vigente debe conservar su resultado")
            return
        }
        #expect(result.document == "CI-HARDEN-1")
        search.query = "CI-HARDEN-2"
        #expect(search.state == .idle)
    }

    @Test("La pérdida de permisos al paginar también retira los datos")
    func forbiddenNextPage() async {
        let service = HardeningService()
        let clients = ClientsModel(service: service)
        let queue = ReviewQueueModel(service: service)
        await clients.refresh()
        await queue.refresh()
        await service.failApplicants(.http(status: 403))
        await service.failReviews(.http(status: 403))
        await clients.loadMore()
        await queue.loadMore()
        #expect(clients.applicants.isEmpty)
        #expect(queue.cases.isEmpty)
        #expect(clients.phase.error?.isForbidden == true)
        #expect(queue.phase.error?.isForbidden == true)
    }

    @Test("Borrar una búsqueda invalida una respuesta que sigue en vuelo")
    func clearPendingSearch() async {
        let service = HardeningService()
        let search = ClientSearchModel(service: service)
        search.query = "CI-HARDEN-0"
        await service.holdNextApplicants()
        let old = Task { await search.search() }
        await service.waitForHeldApplicants()
        search.query = ""
        await service.releaseApplicants()
        await old.value
        #expect(search.state == .idle)
    }

    @Test("Cambiar de segmento descarta la página anterior que llega tarde")
    func staleClientPage() async {
        let service = HardeningService()
        let clients = ClientsModel(service: service)
        await clients.refresh()
        await service.holdNextApplicants()
        let oldPage = Task { await clients.loadMore() }
        await service.waitForHeldApplicants()
        await clients.select(.smallBusiness)
        await service.releaseApplicants()
        await oldPage.value
        #expect(clients.segment == .smallBusiness)
        #expect(clients.applicants.count == 10)
        #expect(clients.applicants.allSatisfy { $0.resolvedSegment == .smallBusiness })
        #expect(!clients.isLoadingMore)
        #expect(!clients.isRefreshing)
    }
}

/// Datos y fallos deterministas exclusivos del target de pruebas, sin tráfico real.
actor HardeningService: DoggyCreditService {
    private let base = DemoDoggyCreditService(scenario: .analyst, latency: .zero)
    private var applicantFailure: APIError?
    private var reviewFailure: APIError?
    private var firstPartial = false
    private var holdsNextApplicants = false
    private var heldApplicants: (Page<Applicant>, CheckedContinuation<Page<Applicant>, Never>)?
    private var heldWaiters: [CheckedContinuation<Void, Never>] = []

    func failApplicants(_ error: APIError?) { applicantFailure = error }
    func failReviews(_ error: APIError?) { reviewFailure = error }
    func includeFirstPartial() { firstPartial = true }
    func holdNextApplicants() { holdsNextApplicants = true }
    func waitForHeldApplicants() async {
        if heldApplicants != nil { return }
        await withCheckedContinuation { heldWaiters.append($0) }
    }
    func releaseApplicants() {
        guard let (page, continuation) = heldApplicants else { return }
        heldApplicants = nil
        continuation.resume(returning: page)
    }

    func applicants(page: Int, size: Int, segment: Segment?, document: String?) async throws -> Page<Applicant> {
        if let applicantFailure { throw applicantFailure }
        let all = (0..<30).map { index in
            Applicant(id: UUID(uuidString: String(format: "00000000-0000-4000-8000-%012d", index + 1))!,
                      document: "CI-HARDEN-\(index)", activity: "Comercio \(index)", activitySeniorityMonths: 24,
                      monthlyIncome: "5000.00", currency: .bob, creditPurpose: "Capital de trabajo",
                      segment: index % 3 == 0 ? .smallBusiness : .person, createdAt: .distantPast)
        }.filter { (segment == nil || $0.resolvedSegment == segment)
            && (document == nil || $0.document == document?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()) }
        let start = min(all.count, max(0, (page - 1) * size))
        let result = Page(items: Array(all[start..<min(all.count, start + size)]), total: all.count)
        if holdsNextApplicants {
            holdsNextApplicants = false
            return await withCheckedContinuation { continuation in
                heldApplicants = (result, continuation)
                for waiter in heldWaiters { waiter.resume() }
                heldWaiters = []
            }
        }
        return result
    }

    func reviewCases(page: Int, size: Int) async throws -> ReviewCasePage {
        if let reviewFailure { throw reviewFailure }
        let all = (0..<41).map { index in
            ReviewCase(assessmentId: UUID(uuidString: String(format: "10000000-0000-4000-8000-%012d", index + 1))!,
                       applicantId: UUID(uuidString: "00000000-0000-4000-8000-000000000001")!,
                       document: "CI-HARDEN-\(index)", createdAt: .distantPast, score: 600, band: .b,
                       motive: .manualReview, partialResult: index == 40 || (firstPartial && index == 0),
                       reasons: [], requestedAmount: "8000", termMonths: 12, purpose: "Capital de trabajo")
        }
        let start = min(all.count, max(0, (page - 1) * size))
        return ReviewCasePage(items: Array(all[start..<min(all.count, start + size)]), total: all.count, products: [])
    }

    func currentTenant() async throws -> Tenant { try await base.currentTenant() }
    func usage() async throws -> TenantUsage { try await base.usage() }
    func preferences() async throws -> TenantPreferences { try await base.preferences() }
    func createApplicant(_ applicant: NewApplicant) async throws -> Applicant { try await base.createApplicant(applicant) }
    func applicant(id: UUID) async throws -> ApplicantDetail { try await base.applicant(id: id) }
    func consents(applicantId: UUID) async throws -> [Consent] { try await base.consents(applicantId: applicantId) }
    func createConsent(_ consent: NewConsent) async throws -> Consent { try await base.createConsent(consent) }
    func revokeConsent(id: UUID) async throws -> Consent { try await base.revokeConsent(id: id) }
    func integrations() async throws -> [Integration] { try await base.integrations() }
    func assessments(applicantId: UUID?, since: Date?, page: Int, size: Int) async throws -> Page<AssessmentSummary> {
        try await base.assessments(applicantId: applicantId, since: since, page: page, size: size)
    }
    func createAssessment(_ assessment: NewAssessment, idempotencyKey: String) async throws -> Assessment {
        try await base.createAssessment(assessment, idempotencyKey: idempotencyKey)
    }
    func assessment(id: UUID) async throws -> Assessment { try await base.assessment(id: id) }
    func resolveCase(assessmentId: UUID, resolution: NewResolution) async throws -> Resolution {
        try await base.resolveCase(assessmentId: assessmentId, resolution: resolution)
    }
    func analyticsSummary(from: Date?, to: Date?) async throws -> AnalyticsSummary {
        try await base.analyticsSummary(from: from, to: to)
    }
}
