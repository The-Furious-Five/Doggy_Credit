import Foundation
@testable import ios_mobile_app

/// Implementación en memoria de la API para explorar la app sin backend. Respeta las
/// reglas del contrato (consentimiento, cuota, idempotencia, concurrencia en la bandeja)
/// y responde con los mismos códigos del catálogo de errores.
actor DemoDoggyCreditService: DoggyCreditService {
    private let scenario: DemoScenario
    private let latency: Duration
    private var applicants: [Applicant]
    private var profiles: [UUID: FinancialProfile]
    private var consentsByApplicant: [UUID: [Consent]]
    private var summaries: [AssessmentSummary]
    private var details: [UUID: Assessment]
    private var queue: [ReviewCase]
    private let products: [Product]
    private let integrationCatalog: [Integration]
    private var usedThisMonth: Int
    private var createdInSession = 0
    private var idempotentResults: [String: Assessment] = [:]

    init(scenario: DemoScenario, latency: Duration = .milliseconds(450), now: Date = .now) {
        let seed = DemoSeed.build(for: scenario, now: now)
        self.scenario = scenario
        self.latency = latency
        applicants = seed.applicants
        profiles = seed.profiles
        consentsByApplicant = seed.consents
        summaries = seed.assessments
        details = seed.details
        queue = seed.queue
        products = seed.products
        integrationCatalog = seed.integrations
        usedThisMonth = seed.usage
    }

    // MARK: Tenant

    func currentTenant() async throws -> Tenant {
        try await simulateLatency()
        return scenario.tenant
    }

    func usage() async throws -> TenantUsage {
        try await simulateLatency(0.5)
        return TenantUsage(monthlyAssessments: usedThisMonth, monthlyLimit: scenario.tenant.plan == .free ? 500 : 0)
    }

    func preferences() async throws -> TenantPreferences {
        try await simulateLatency(0.5)
        return TenantPreferences(maskSensitiveData: true)
    }

    // MARK: Solicitantes

    func applicants(page: Int, size: Int, segment: Segment?, document: String?) async throws -> Page<Applicant> {
        try await simulateLatency()
        let filtered = applicants.filter { applicant in
            (segment == nil || applicant.resolvedSegment == segment)
                && (document.map { Self.normalize($0) == Self.normalize(applicant.document) } ?? true)
        }
        return Page(items: Self.slice(filtered, page: page, size: size), total: filtered.count)
    }

    func createApplicant(_ new: NewApplicant) async throws -> Applicant {
        try await simulateLatency()
        if applicants.contains(where: { Self.normalize($0.document) == Self.normalize(new.document) }) {
            throw problem(.applicantAlreadyExists, status: 409, title: "Solicitante ya registrado",
                          detail: "El documento ya corresponde a un solicitante del tenant.")
        }
        let applicant = Applicant(
            id: UUID(), document: new.document.trimmingCharacters(in: .whitespaces), activity: new.activity,
            activitySeniorityMonths: new.activitySeniorityMonths, monthlyIncome: new.monthlyIncome, currency: new.currency,
            creditPurpose: new.creditPurpose, segment: new.segment, createdAt: .now
        )
        applicants.insert(applicant, at: 0)
        return applicant
    }

    func applicant(id: UUID) async throws -> ApplicantDetail {
        try await simulateLatency()
        guard let applicant = applicants.first(where: { $0.id == id }) else {
            throw problem(.unknownApplicant, status: 404, title: "Solicitante no encontrado",
                          detail: "El solicitante no existe o pertenece a otro tenant.")
        }
        return ApplicantDetail(applicant: applicant, profile: profiles[id])
    }

    // MARK: Consentimientos

    func consents(applicantId: UUID) async throws -> [Consent] {
        try await simulateLatency(0.6)
        return (consentsByApplicant[applicantId] ?? []).sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
    }

    func createConsent(_ new: NewConsent) async throws -> Consent {
        try await simulateLatency()
        guard let applicant = applicants.first(where: { Self.normalize($0.document) == Self.normalize(new.holderDocument) }) else {
            throw problem(.applicantNotFound, status: 422, title: "Solicitante no registrado",
                          detail: "Debe registrar al solicitante antes de evaluarlo.")
        }
        let consent = Consent(
            id: UUID(), holderDocument: applicant.document, sources: new.sources, purpose: new.purpose,
            validityDays: new.validityDays, status: .active, createdAt: .now,
            expiresAt: .now.addingTimeInterval(TimeInterval(new.validityDays) * 86_400), revokedAt: nil
        )
        consentsByApplicant[applicant.id, default: []].insert(consent, at: 0)
        return consent
    }

    func revokeConsent(id: UUID) async throws -> Consent {
        try await simulateLatency()
        for (applicantId, consents) in consentsByApplicant {
            guard let index = consents.firstIndex(where: { $0.id == id }) else { continue }
            let current = consents[index]
            guard current.status == .active else { return current }
            let revoked = Consent(
                id: current.id, holderDocument: current.holderDocument, sources: current.sources, purpose: current.purpose,
                validityDays: current.validityDays, status: .revoked, createdAt: current.createdAt,
                expiresAt: current.expiresAt, revokedAt: .now
            )
            consentsByApplicant[applicantId]?[index] = revoked
            return revoked
        }
        throw problem(.consentNotFound, status: 404, title: "Consentimiento no encontrado",
                      detail: "El consentimiento no existe o pertenece a otro tenant.")
    }

    func integrations() async throws -> [Integration] {
        try await simulateLatency(0.6)
        return integrationCatalog
    }

    // MARK: Evaluaciones

    func assessments(applicantId: UUID?, since: Date?, page: Int, size: Int) async throws -> Page<AssessmentSummary> {
        try await simulateLatency()
        let filtered = summaries.filter { summary in
            (applicantId == nil || summary.applicantId == applicantId) && (since.map { summary.createdAt >= $0 } ?? true)
        }
        return Page(items: Self.slice(filtered, page: page, size: size), total: filtered.count)
    }

    func createAssessment(_ new: NewAssessment, idempotencyKey: String) async throws -> Assessment {
        // Misma clave, misma respuesta: un reintento nunca duplica la evaluación.
        if let previous = idempotentResults[idempotencyKey] { return previous }
        try await simulateLatency(2.6)
        guard let applicant = applicants.first(where: { Self.normalize($0.document) == Self.normalize(new.holderDocument) }) else {
            throw problem(.applicantNotFound, status: 422, title: "Solicitante no registrado",
                          detail: "Debe registrar al solicitante antes de evaluarlo.")
        }
        let consent = consentsByApplicant[applicant.id]?.first { $0.id == new.consentId }
        guard let consent, consent.status == .active, consent.sources.contains(.bureau) else {
            throw problem(.consentRequired, status: 422, title: "Consentimiento requerido",
                          detail: "No hay consentimiento vigente para la fuente BURO.")
        }
        if scenario.tenant.plan == .free, usedThisMonth >= 500 {
            throw problem(.quotaExceeded, status: 429, title: "Cuota mensual agotada",
                          detail: "El plan FREE alcanzó su límite mensual de evaluaciones.")
        }

        let income = Decimal(apiString: applicant.monthlyIncome) ?? 0
        let amount = new.requestedAmount.flatMap(Decimal.init(apiString:))
        let score = Self.score(for: applicant, income: income, amount: amount, term: new.termMonths)
        let connected = Set(integrationCatalog.filter { $0.status == .connected }.compactMap { DataSource(rawValue: $0.source.rawValue) })
        let consulted = connected.intersection(consent.sources)
        // La billetera del simulador a veces no responde a tiempo: el resultado queda PARCIAL.
        let partial = consulted.contains(.wallet) && DemoSeed.stableHash(applicant.document + "wallet") % 5 == 0
        let outcome = DemoPolicy.decide(score: score, amount: amount, partial: partial)
        let band = ScoreBand(score: score)
        let id = UUID()
        let assessment = Assessment(
            id: id, status: outcome.status, decision: outcome.decision, score: score, band: band,
            reasons: DemoPolicy.reasons(score: score, seniority: applicant.activitySeniorityMonths, amount: amount ?? income,
                                        income: income, term: new.termMonths ?? 12),
            modelVersion: "scorecard-v1.3.0", simulated: true, correlationId: UUID().uuidString.lowercased()
        )

        profiles[applicant.id] = DemoSeed.profile(
            for: applicant.document, income: income, seniority: applicant.activitySeniorityMonths,
            connected: consulted, partial: partial, createdAt: .now
        )
        summaries.insert(AssessmentSummary(
            id: id, applicantId: applicant.id, status: outcome.status, decision: outcome.decision, score: score, band: band,
            requestedAmount: new.requestedAmount, termMonths: new.termMonths, createdAt: .now
        ), at: 0)
        details[id] = assessment
        if outcome.decision != .preApproved {
            queue.append(ReviewCase(
                assessmentId: id, applicantId: applicant.id, document: applicant.document, createdAt: .now,
                score: score, band: band, motive: outcome.decision == .requestData ? .requestData : .manualReview,
                partialResult: partial, reasons: assessment.reasons ?? [], requestedAmount: new.requestedAmount,
                termMonths: new.termMonths, purpose: new.purpose
            ))
        }
        usedThisMonth += 1
        createdInSession += 1
        idempotentResults[idempotencyKey] = assessment
        return assessment
    }

    func assessment(id: UUID) async throws -> Assessment {
        try await simulateLatency()
        guard let assessment = details[id] else {
            throw problem(.assessmentNotFound, status: 404, title: "Evaluación no encontrada",
                          detail: "La evaluación no existe o pertenece a otro tenant.")
        }
        return assessment
    }

    // MARK: Revisión manual

    func reviewCases(page: Int, size: Int) async throws -> ReviewCasePage {
        try await simulateLatency()
        return ReviewCasePage(items: Self.slice(queue, page: page, size: size), total: queue.count,
                              products: products.filter(\.active))
    }

    func resolveCase(assessmentId: UUID, resolution: NewResolution) async throws -> Resolution {
        try await simulateLatency(1.4)
        let justification = resolution.justification.trimmingCharacters(in: .whitespacesAndNewlines)
        guard justification.count >= NewResolution.minimumJustificationLength,
              resolution.decision == .reject || resolution.productId != nil
        else {
            throw problem(.validationError, status: 400, title: "Error de validación",
                          detail: "Uno o más campos no son válidos.")
        }
        guard let index = queue.firstIndex(where: { $0.assessmentId == assessmentId }) else {
            throw problem(.concurrentUpdate, status: 409, title: "Cambio simultáneo",
                          detail: "Otra persona modificó este registro al mismo tiempo. Recarga y vuelve a intentarlo.")
        }
        if let productId = resolution.productId, !products.contains(where: { $0.id == productId && $0.active }) {
            throw problem(.productNotFound, status: 404, title: "Producto no encontrado",
                          detail: "El producto no existe o pertenece a otro tenant.")
        }
        queue.remove(at: index)
        return Resolution(
            assessmentId: assessmentId, decision: resolution.decision,
            productId: resolution.decision == .approve ? resolution.productId : nil,
            justification: justification, resolvedBy: scenario.user.username, resolvedAt: .now
        )
    }

    // MARK: Analítica

    func analyticsSummary(from: Date?, to: Date?) async throws -> AnalyticsSummary {
        try await simulateLatency()
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .laPaz
        let end = to ?? .now
        let start = from ?? calendar.dateInterval(of: .month, for: end)?.start ?? end
        let days = max(1, (calendar.dateComponents([.day], from: calendar.startOfDay(for: start),
                                                   to: calendar.startOfDay(for: end)).day ?? 0) + 1)
        let isAdmin = scenario == .administrator
        let total = days * (isAdmin ? 43 : 14) + createdInSession
        let shares: [(NeedBreakdown.Category, Double)] = [
            (.workingCapital, 38.4), (.productiveInvestment, 26.9), (.energyEfficiency, 21.3), (.consumption, 13.4),
        ]
        let needs = shares.map { category, percent in
            NeedBreakdown(category: category, count: Int((Double(total) * percent / 100).rounded()), percent: percent)
        }
        var pro: AdvancedAnalytics?
        if isAdmin {
            let recommended = Double(total) * 0.682
            let productShares: [(ProductKind, Double, Double)] = [(.microcredit, 57.6, 12_400), (.green, 27.1, 46_800), (.smallBusiness, 15.3, 182_000)]
            pro = AdvancedAnalytics(
                conversionPercent: 54.7,
                medianDecisionTimeMs: 1430,
                recommendedAmount: Decimal(productShares.reduce(0) { $0 + recommended * $1.1 / 100 * $1.2 }).rounded(2).apiString,
                products: productShares.map { kind, percent, ticket in
                    let count = Int((recommended * percent / 100).rounded())
                    return ProductBreakdown(kind: kind, count: count, percent: percent,
                                            recommendedAmount: Decimal(Double(count) * ticket).apiString)
                },
                models: [
                    ModelMonitor(version: "scorecard-v1.3.0", psi: 0.041, state: .stable, from: end.addingTimeInterval(-7 * 86_400),
                                 to: end, currentSamples: 296, baselineSamples: 5000),
                    ModelMonitor(version: "advanced-v2.0.1", psi: 0.137, state: .watch, from: end.addingTimeInterval(-7 * 86_400),
                                 to: end, currentSamples: 214, baselineSamples: 5000),
                ]
            )
        }
        return AnalyticsSummary(
            from: start, to: end, plan: scenario.tenant.plan.rawValue, currency: "BOB", advancedAnalytics: isAdmin,
            assessments: total, averageScore: isAdmin ? 668.9 : 641.3,
            activeIntegrations: integrationCatalog.filter { $0.status == .connected }.count,
            recommendationPercent: isAdmin ? 68.2 : 61.4, needs: needs, pro: pro
        )
    }

    // MARK: Utilidades

    private func simulateLatency(_ factor: Double = 1) async throws {
        try await Task.sleep(for: latency * factor)
    }

    private func problem(_ code: ErrorCode, status: Int, title: String, detail: String) -> APIError {
        .problem(ProblemDetail(
            type: "https://docs.doggycredit.example/errors/\(code.rawValue.lowercased().replacingOccurrences(of: "_", with: "-"))",
            title: title, status: status, detail: detail, code: code.rawValue,
            correlationId: UUID().uuidString.lowercased()
        ))
    }

    private static func normalize(_ document: String) -> String {
        document.uppercased().filter { !$0.isWhitespace }
    }

    private static func slice<T>(_ items: [T], page: Int, size: Int) -> [T] {
        let start = max(0, (page - 1) * size)
        guard start < items.count else { return [] }
        return Array(items[start..<min(items.count, start + size)])
    }

    /// Score sintético y estable: capacidad de pago, antigüedad y plazo, como el scorecard v1.
    private static func score(for applicant: Applicant, income: Decimal, amount: Decimal?, term: Int?) -> Int {
        let ratio = income > 0 ? NSDecimalNumber(decimal: (amount ?? income) / income).doubleValue : 10
        var score = 720.0
        score -= min(260, max(0, ratio - 3) * 38)
        score -= applicant.activitySeniorityMonths < 6 ? 150 : (applicant.activitySeniorityMonths < 24 ? 40 : 0)
        score -= (term ?? 12) > 36 ? 30 : 0
        score += Double(DemoSeed.stableHash(applicant.document) % 90) - 30
        return Int(min(940, max(180, score)))
    }
}
