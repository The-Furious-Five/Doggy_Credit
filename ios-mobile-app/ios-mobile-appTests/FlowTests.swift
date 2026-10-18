import Foundation
import Testing
@testable import ios_mobile_app

/// Flujos completos contra el servicio de demostración (mismas reglas y códigos del contrato).
@Suite("Flujos de la app")
struct FlowTests {
    private func service(_ scenario: DemoScenario = .analyst) -> DemoDoggyCreditService {
        DemoDoggyCreditService(scenario: scenario, latency: .zero)
    }

    private func filledFlow(_ service: DemoDoggyCreditService, onCreated: @escaping () async -> Void = {}) -> EvaluationFlowModel {
        let flow = EvaluationFlowModel(service: service, onAssessmentCreated: onCreated)
        flow.segment = .entrepreneur
        flow.document = "  ci-test-777  "
        flow.activity = "Venta de abarrotes en la feria"
        flow.seniorityMonths = 30
        flow.monthlyIncome = 6000
        flow.requestedAmount = 12000
        flow.termMonths = 12
        flow.purpose = .workingCapital
        return flow
    }

    @Test("Evaluación completa: solicitante, consentimiento, fuentes y resultado")
    func evaluationHappyPath() async throws {
        let service = service()
        var notified = 0
        let flow = filledFlow(service) { notified += 1 }

        await flow.next()
        #expect(flow.step == .consent)
        #expect(flow.applicantId != nil)

        flow.consentBureau = true
        await flow.next()
        #expect(flow.step == .sources)
        #expect(flow.sources.value?.count == IntegrationSource.allCases.count)

        await flow.next()
        #expect(flow.step == .result)
        let result = try #require(flow.result)
        #expect(result.simulated)
        #expect(result.score != nil)
        #expect(flow.paymentCapacity != nil)
        #expect(notified == 1)
        #expect(!flow.isInProgress)
        #expect(try await service.usage().monthlyAssessments == 413)
    }

    @Test("Sin datos obligatorios no se registra nada")
    func incompleteApplicant() async throws {
        let service = service()
        let flow = EvaluationFlowModel(service: service)
        flow.document = "CI"
        await flow.next()
        #expect(flow.problem == .incompleteApplicant)
        #expect(flow.step == .applicant)
        #expect(try await service.applicants(page: 1, size: 1, segment: nil, document: "CI").total == 0)
    }

    @Test("El buró es obligatorio y nada viene preseleccionado")
    func bureauConsentRequired() async {
        let flow = filledFlow(service())
        #expect(!flow.consentBureau && !flow.consentBank && !flow.consentWallet)
        await flow.next()
        await flow.next()
        #expect(flow.problem == .missingBureauConsent)
        #expect(flow.step == .consent)
    }

    @Test("Reutiliza al solicitante si el documento ya existe")
    func reusesExistingApplicant() async throws {
        let service = service()
        let existing = try #require(try await service.applicants(page: 1, size: 1, segment: nil, document: "CI-DEMO-0001001").items.first)
        let flow = filledFlow(service)
        flow.document = "CI-DEMO-0001001"
        await flow.next()
        #expect(flow.applicantId == existing.id)
    }

    @Test("Un consentimiento revocado devuelve al paso de consentimiento")
    func revokedConsent() async throws {
        let service = service()
        let flow = filledFlow(service)
        await flow.next()
        flow.consentBureau = true
        await flow.next()
        let applicantId = try #require(flow.applicantId)
        for consent in try await service.consents(applicantId: applicantId) {
            _ = try await service.revokeConsent(id: consent.id)
        }
        await flow.next()
        #expect(flow.problem == .consentInvalid)
        #expect(flow.step == .consent)
    }

    @Test("La misma Idempotency-Key no duplica la evaluación")
    func idempotency() async throws {
        let service = service()
        let applicant = try await service.createApplicant(NewApplicant(
            document: "CI-IDEM-1", activity: "Comercio", activitySeniorityMonths: 24, monthlyIncome: "5000.00",
            currency: .bob, creditPurpose: "Capital de trabajo", segment: .person
        ))
        let consent = try await service.createConsent(NewConsent(holderDocument: applicant.document, sources: [.bureau],
                                                                 purpose: "Evaluación de crédito", validityDays: 90))
        let request = NewAssessment(holderDocument: applicant.document, consentId: consent.id, purpose: nil,
                                    requestedAmount: "8000.00", termMonths: 12)
        let first = try await service.createAssessment(request, idempotencyKey: "clave-estable-1")
        let second = try await service.createAssessment(request, idempotencyKey: "clave-estable-1")
        #expect(first.id == second.id)
        #expect(try await service.assessments(applicantId: applicant.id, since: nil, page: 1, size: 10).total == 1)
    }

    @Test("Resolver exige justificación y producto al aprobar")
    func resolutionValidation() async throws {
        let queue = ReviewQueueModel(service: service())
        await queue.refresh()
        let reviewCase = try #require(queue.cases.first)
        let model = queue.makeResolution(for: reviewCase, preset: .approve)
        model.productId = nil
        model.justification = "Corto"
        #expect(!model.canSubmit)
        #expect(model.missingCharacters == 5)
        model.justification = "Ingresos verificados en la visita"
        #expect(!model.canSubmit)
        model.productId = model.products.first?.id
        #expect(model.canSubmit)
        model.decision = .reject
        model.productId = nil
        #expect(model.canSubmit)
    }

    @Test("Resolver un caso lo saca de la bandeja")
    func resolveRemovesCase() async throws {
        let queue = ReviewQueueModel(service: service())
        await queue.refresh()
        let total = queue.total
        let reviewCase = try #require(queue.cases.first)
        let model = queue.makeResolution(for: reviewCase, preset: .reject)
        model.justification = "Capacidad de pago insuficiente según el perfil"
        let resolution = try #require(await model.submit())
        #expect(resolution.decision == .reject)
        #expect(resolution.productId == nil)
        #expect(queue.total == total - 1)
        #expect(!queue.cases.contains { $0.id == reviewCase.id })
    }

    @Test("Si otra persona resolvió el caso, se explica y se retira de la bandeja")
    func concurrentResolution() async throws {
        let service = service()
        let queue = ReviewQueueModel(service: service)
        await queue.refresh()
        let reviewCase = try #require(queue.cases.first)
        _ = try await service.resolveCase(
            assessmentId: reviewCase.assessmentId,
            resolution: NewResolution(decision: .reject, productId: nil, justification: "Resuelto por otra persona")
        )
        let model = queue.makeResolution(for: reviewCase, preset: .reject)
        model.justification = "Mi decisión llega tarde"
        #expect(await model.submit() == nil)
        #expect(model.problem == .alreadyResolved)
        #expect(!model.canSubmit)
        #expect(!queue.cases.contains { $0.id == reviewCase.id })
    }

    @Test("Revocar consentimientos desde el perfil")
    func revokeFromProfile() async throws {
        let service = service()
        let applicant = try #require(try await service.applicants(page: 1, size: 1, segment: nil, document: "CI-DEMO-0001001").items.first)
        let model = ClientDetailModel(applicantId: applicant.id, service: service)
        await model.load()
        #expect(!model.activeConsents.isEmpty)
        #expect(await model.revokeConsents())
        #expect(model.activeConsents.isEmpty)
        #expect(model.detail.value?.profile != nil, "El perfil consolidado se conserva")
    }

    @Test("La búsqueda es exacta e insensible a mayúsculas y espacios")
    func exactSearch() async {
        let search = ClientSearchModel(service: service())
        search.query = " ci-demo-0001002 "
        await search.search()
        guard case let .found(applicant) = search.state else {
            Issue.record("Se esperaba un cliente")
            return
        }
        #expect(applicant.document == "CI-DEMO-0001002")

        search.query = "CI-DEMO-00010"
        await search.search()
        #expect(search.state == .notFound("CI-DEMO-00010"))
    }

    @Test("Analítica avanzada solo en Pro")
    func analyticsByPlan() async throws {
        let free = try await service(.analyst).analyticsSummary(from: nil, to: nil)
        let pro = try await service(.administrator).analyticsSummary(from: nil, to: nil)
        #expect(free.pro == nil)
        #expect(pro.pro != nil)
    }
}
