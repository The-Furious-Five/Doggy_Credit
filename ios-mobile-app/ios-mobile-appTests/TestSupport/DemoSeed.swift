import CryptoKit
import Foundation
@testable import ios_mobile_app

/// Cartera sintética para el modo demostración. Los documentos llevan el prefijo
/// `DEMO` como en `seed/tenants.yaml`; las actividades reflejan el mercado boliviano.
nonisolated struct DemoSeed: Sendable {
    var applicants: [Applicant] = []
    var profiles: [UUID: FinancialProfile] = [:]
    var consents: [UUID: [Consent]] = [:]
    var assessments: [AssessmentSummary] = []
    var details: [UUID: Assessment] = [:]
    var queue: [ReviewCase] = []
    let products: [Product]
    let integrations: [Integration]
    let usage: Int

    private struct ApplicantSpec {
        let document: String
        let segment: Segment
        let activity: String
        let seniority: Int
        let income: Decimal
        let purpose: String
        let daysAgo: Double
        let history: [AssessmentSpec]
        var consentRevoked = false
    }

    private struct AssessmentSpec {
        let daysAgo: Double
        let score: Int
        let amount: Decimal
        let term: Int
        var partial = false
        var requestData = false
    }

    private static let portfolio: [ApplicantSpec] = [
        ApplicantSpec(document: "CI-DEMO-0001001", segment: .person, activity: "Venta de abarrotes", seniority: 60, income: 4800, purpose: "Capital de trabajo", daysAgo: 40,
                      history: [AssessmentSpec(daysAgo: 38, score: 702, amount: 9000, term: 12), AssessmentSpec(daysAgo: 0.1, score: 688, amount: 12000, term: 18)]),
        ApplicantSpec(document: "NIT-DEMO-9001001", segment: .smallBusiness, activity: "Taller metalmecánico", seniority: 96, income: 38000, purpose: "Eficiencia energética", daysAgo: 25,
                      history: [AssessmentSpec(daysAgo: 1.2, score: 714, amount: 240_000, term: 48)]),
        ApplicantSpec(document: "CI-DEMO-0001002", segment: .entrepreneur, activity: "Confección de polleras", seniority: 30, income: 6200, purpose: "Inversión productiva", daysAgo: 18,
                      history: [AssessmentSpec(daysAgo: 0.3, score: 538, amount: 15000, term: 24)]),
        ApplicantSpec(document: "CI-DEMO-0001003", segment: .person, activity: "Transporte interprovincial", seniority: 84, income: 7500, purpose: "Consumo o consolidación", daysAgo: 12,
                      history: [AssessmentSpec(daysAgo: 5, score: 412, amount: 20000, term: 36)]),
        ApplicantSpec(document: "CI-DEMO-0001004", segment: .entrepreneur, activity: "Panadería artesanal", seniority: 14, income: 5400, purpose: "Eficiencia energética", daysAgo: 9,
                      history: [AssessmentSpec(daysAgo: 2.5, score: 621, amount: 18000, term: 24, partial: true)]),
        ApplicantSpec(document: "NIT-DEMO-9001002", segment: .smallBusiness, activity: "Exportación de quinua", seniority: 120, income: 92000, purpose: "Capital de trabajo", daysAgo: 30,
                      history: [AssessmentSpec(daysAgo: 20, score: 781, amount: 150_000, term: 24)]),
        ApplicantSpec(document: "CI-DEMO-0001005", segment: .entrepreneur, activity: "Venta de celulares y accesorios", seniority: 22, income: 8900, purpose: "Capital de trabajo", daysAgo: 7,
                      history: [AssessmentSpec(daysAgo: 1.8, score: 667, amount: 14000, term: 12)]),
        ApplicantSpec(document: "CI-DEMO-0001006", segment: .person, activity: "Docente de secundaria", seniority: 72, income: 5100, purpose: "Consumo o consolidación", daysAgo: 15,
                      history: [AssessmentSpec(daysAgo: 14, score: 598, amount: 10000, term: 24), AssessmentSpec(daysAgo: 3.2, score: 668, amount: 8000, term: 18)]),
        ApplicantSpec(document: "CI-DEMO-0001007", segment: .entrepreneur, activity: "Salteñería", seniority: 40, income: 6800, purpose: "Inversión productiva", daysAgo: 5,
                      history: [AssessmentSpec(daysAgo: 4.5, score: 745, amount: 16000, term: 24)]),
        ApplicantSpec(document: "NIT-DEMO-9001003", segment: .smallBusiness, activity: "Carpintería industrial", seniority: 64, income: 54000, purpose: "Inversión productiva", daysAgo: 45,
                      history: [AssessmentSpec(daysAgo: 44, score: 694, amount: 120_000, term: 36)], consentRevoked: true),
        ApplicantSpec(document: "CI-DEMO-0001008", segment: .entrepreneur, activity: "Textiles en la feria 16 de Julio", seniority: 4, income: 4300, purpose: "Capital de trabajo", daysAgo: 3,
                      history: [AssessmentSpec(daysAgo: 2.9, score: 356, amount: 12000, term: 24, requestData: true)]),
        ApplicantSpec(document: "CI-DEMO-0001009", segment: .person, activity: "Enfermería", seniority: 50, income: 6000, purpose: "Eficiencia energética", daysAgo: 2,
                      history: []),
    ]

    static func build(for scenario: DemoScenario, now: Date) -> DemoSeed {
        let connected: Set<DataSource> = scenario.tenant.plan == .free ? [.bureau] : [.bureau, .bank, .wallet]
        var seed = DemoSeed(
            products: products(for: scenario),
            integrations: integrations(for: scenario),
            usage: scenario == .analyst ? 412 : 1284
        )
        let date = { (daysAgo: Double) in now.addingTimeInterval(-daysAgo * 86_400) }

        for spec in portfolio {
            let applicantId = stableUUID("applicant", spec.document, scenario.rawValue)
            let applicant = Applicant(
                id: applicantId, document: spec.document, activity: spec.activity,
                activitySeniorityMonths: spec.seniority, monthlyIncome: spec.income.apiString, currency: .bob,
                creditPurpose: spec.purpose, segment: spec.segment, createdAt: date(spec.daysAgo)
            )
            seed.applicants.append(applicant)
            guard let first = spec.history.first, let latest = spec.history.last else { continue }

            let consentSources: [DataSource] = [.bureau] + connected.subtracting([.bureau]).sorted { $0.rawValue < $1.rawValue }
            seed.consents[applicantId] = [Consent(
                id: stableUUID("consent", spec.document, scenario.rawValue), holderDocument: spec.document,
                sources: consentSources, purpose: "Evaluación de crédito", validityDays: 90,
                status: spec.consentRevoked ? .revoked : .active, createdAt: date(first.daysAgo + 0.01),
                expiresAt: date(first.daysAgo - 90), revokedAt: spec.consentRevoked ? date(first.daysAgo - 2) : nil
            )]

            for (index, history) in spec.history.enumerated() {
                let id = stableUUID("assessment", spec.document, scenario.rawValue, String(index))
                let outcome = DemoPolicy.decide(score: history.score, amount: history.amount,
                                                partial: history.partial, requestData: history.requestData)
                seed.assessments.append(AssessmentSummary(
                    id: id, applicantId: applicantId, status: outcome.status, decision: outcome.decision,
                    score: history.score, band: ScoreBand(score: history.score), requestedAmount: history.amount.apiString,
                    termMonths: history.term, createdAt: date(history.daysAgo)
                ))
                seed.details[id] = Assessment(
                    id: id, status: outcome.status, decision: outcome.decision, score: history.score,
                    band: ScoreBand(score: history.score),
                    reasons: DemoPolicy.reasons(score: history.score, seniority: spec.seniority, amount: history.amount,
                                                income: spec.income, term: history.term),
                    modelVersion: "scorecard-v1.3.0", simulated: true, correlationId: stableUUID("corr", id.uuidString).uuidString.lowercased()
                )
            }
            seed.profiles[applicantId] = profile(
                for: spec.document, income: spec.income, seniority: spec.seniority, connected: connected,
                partial: latest.partial, createdAt: date(latest.daysAgo)
            )

            let latestId = stableUUID("assessment", spec.document, scenario.rawValue, String(spec.history.count - 1))
            let outcome = DemoPolicy.decide(score: latest.score, amount: latest.amount, partial: latest.partial,
                                            requestData: latest.requestData)
            if outcome.decision != .preApproved {
                seed.queue.append(ReviewCase(
                    assessmentId: latestId, applicantId: applicantId, document: spec.document, createdAt: date(latest.daysAgo),
                    score: latest.score, band: ScoreBand(score: latest.score),
                    motive: outcome.decision == .requestData ? .requestData : .manualReview,
                    partialResult: latest.partial, reasons: seed.details[latestId]?.reasons ?? [],
                    requestedAmount: latest.amount.apiString, termMonths: latest.term, purpose: spec.purpose
                ))
            }
        }
        seed.applicants.sort { $0.createdAt > $1.createdAt }
        seed.assessments.sort { $0.createdAt > $1.createdAt }
        seed.queue.sort { $0.createdAt < $1.createdAt }
        return seed
    }

    static func profile(
        for document: String, income: Decimal, seniority: Int, connected: Set<DataSource>, partial: Bool, createdAt: Date
    ) -> FinancialProfile {
        let obligations = Int(stableHash(document) % 4)
        let observedIncome = connected.contains(.bank) ? (income * 1.04).rounded(0) : income
        let debt = (income * Decimal(obligations) * 1.8).rounded(0)
        let installment = (debt / 24).rounded(0)
        let ratio = observedIncome > 0 ? installment / observedIncome * 100 : 0
        let capacity = max(0, (observedIncome * 0.35 - installment).rounded(0))
        let money = { (value: Decimal) in Money(amount: value.apiString, currency: .bob) }
        let sources = DataSource.allCases.map { source -> ProfileSource in
            guard connected.contains(source) else {
                return ProfileSource(source: source, state: .notQueried, queriedAt: nil, sha256: nil)
            }
            let unavailable = partial && source != .bureau
            return ProfileSource(
                source: source,
                state: unavailable ? .unavailable : (source == .bureau && obligations == 0 ? .noRecord : .complete),
                queriedAt: unavailable ? nil : createdAt,
                sha256: unavailable ? nil : sha256(document + source.rawValue)
            )
        }
        return FinancialProfile(
            id: stableUUID("profile", document, createdAt.formatted(.iso8601)), hash: sha256(document + "profile"),
            createdAt: createdAt, monthlyIncome: money(observedIncome),
            incomeOrigin: connected.contains(.bank) && !partial ? .bank : .declared,
            outstandingDebt: money(debt), obligations: obligations, monthlyInstallment: money(installment),
            debtRatioPercent: ratio.rounded(1).formatted(.number.precision(.fractionLength(1)).locale(Locale(identifier: "en_US_POSIX"))),
            paymentCapacity: money(capacity), financialSystemSeniorityMonths: seniority + 12, sources: sources
        )
    }

    private static func products(for scenario: DemoScenario) -> [Product] {
        // Catálogo sembrado por V2026_10_01_2310 (PYME inactivo por defecto).
        [
            Product(id: stableUUID("product", "MICROCREDITO", scenario.rawValue), kind: .microcredit, currency: .bob,
                    minimumAmount: "1000.00", maximumAmount: "20000.00", minimumTermMonths: 3, maximumTermMonths: 24,
                    annualRate: "11.50", minimumBand: .c, active: true),
            Product(id: stableUUID("product", "VERDE", scenario.rawValue), kind: .green, currency: .bob,
                    minimumAmount: "10000.00", maximumAmount: "150000.00", minimumTermMonths: 12, maximumTermMonths: 60,
                    annualRate: "11.50", minimumBand: .c, active: true),
            Product(id: stableUUID("product", "PYME", scenario.rawValue), kind: .smallBusiness, currency: .bob,
                    minimumAmount: "50000.00", maximumAmount: "700000.00", minimumTermMonths: 6, maximumTermMonths: 84,
                    annualRate: "7.00", minimumBand: .b, active: scenario == .administrator),
        ]
    }

    private static func integrations(for scenario: DemoScenario) -> [Integration] {
        let free = scenario.tenant.plan == .free
        return IntegrationSource.allCases.map { source in
            switch source {
            case .bureau: Integration(source: source, status: .connected)
            case .bank, .wallet: Integration(source: source, status: free ? .requiresPro : .connected)
            case .openBanking, .invoicing, .collateral: Integration(source: source, status: .comingSoon)
            }
        }
    }

    // MARK: - Utilidades deterministas (la demo es igual en cada arranque)

    static func stableHash(_ value: String) -> UInt64 {
        // FNV-1a: estable entre ejecuciones, a diferencia de Hasher.
        value.utf8.reduce(14_695_981_039_346_656_037) { ($0 ^ UInt64($1)) &* 1_099_511_628_211 }
    }

    static func stableUUID(_ parts: String...) -> UUID {
        let digest = Array(SHA256.hash(data: Data(parts.joined(separator: "|").utf8)))
        var bytes = Array(digest.prefix(16))
        bytes[6] = (bytes[6] & 0x0F) | 0x40
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
                           bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
    }

    static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Políticas por defecto del tenant (V2026_10_06_1100): ninguna rechaza.
nonisolated enum DemoPolicy {
    struct Outcome: Sendable {
        let status: AssessmentStatus
        let decision: PolicyDecision
    }

    static func decide(score: Int, amount: Decimal?, partial: Bool, requestData: Bool = false) -> Outcome {
        // Un resultado PARCIAL nunca se preaprueba.
        if partial { return Outcome(status: .partial, decision: .manualReview) }
        if requestData { return Outcome(status: .manualReview, decision: .requestData) }
        // MONTO_ALTO: monto > 200.000.
        if let amount, amount > 200_000 { return Outcome(status: .manualReview, decision: .manualReview) }
        // BANDA_BAJA: banda D o E.
        if ScoreBand(score: score) <= .d { return Outcome(status: .manualReview, decision: .manualReview) }
        // PREAPROBACION_SCORE: score ≥ 650 y resultado completo.
        if score >= 650 { return Outcome(status: .preApproved, decision: .preApproved) }
        return Outcome(status: .manualReview, decision: .manualReview)
    }

    /// Razones del scorecard v1 ordenadas por impacto, con el formato `CODIGO (±puntos)`.
    static func reasons(score: Int, seniority: Int, amount: Decimal, income: Decimal, term: Int) -> [String] {
        var reasons: [(String, Int)] = []
        switch score {
        case 600...: reasons.append(("SIN_MORA_12M", 0))
        case 450..<600: reasons.append(("MORA_1_30_DIAS", -80))
        default: reasons.append(("MORA_31_90_DIAS", -200))
        }
        switch seniority {
        case ..<6: reasons.append(("ACTIVIDAD_MENOR_6_MESES", -150))
        case ..<24: reasons.append(("ACTIVIDAD_6_23_MESES", -40))
        default: reasons.append(("ACTIVIDAD_MAYOR_24_MESES", 0))
        }
        let ratio = income > 0 ? amount / income : 99
        switch ratio {
        case ...3: reasons.append(("MONTO_HASTA_3_INGRESOS", 30))
        case ...6: reasons.append(("MONTO_4_6_INGRESOS", -20))
        default: reasons.append(("MONTO_MAYOR_6_INGRESOS", -90))
        }
        switch term {
        case ...12: reasons.append(("PLAZO_HASTA_12_MESES", 20))
        case ...36: reasons.append(("PLAZO_13_36_MESES", 0))
        default: reasons.append(("PLAZO_MAYOR_36_MESES", -30))
        }
        reasons.append(("CON_REGISTRO_BURO", 25))
        return reasons
            .sorted { abs($0.1) > abs($1.1) }
            .map { code, points in points == 0 ? code : "\(code) (\(points > 0 ? "+" : "")\(points))" }
    }
}

nonisolated extension Decimal {
    func rounded(_ scale: Int) -> Decimal {
        var value = self
        var result = Decimal()
        NSDecimalRound(&result, &value, scale, .plain)
        return result
    }
}
