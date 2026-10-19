import Foundation

/// Resultado de una evaluación de crédito (`Evaluacion`). El contrato no marca campos
/// obligatorios: todo lo que no llegue se muestra como desconocido.
nonisolated struct Assessment: Codable, Hashable, Sendable {
    let id: UUID?
    let status: AssessmentStatus?
    let decision: PolicyDecision?
    let score: Int?
    let band: ScoreBand?
    let reasons: [String]?
    let modelVersion: String?
    let simulated: Bool
    let correlationId: String?

    var isPartial: Bool { status == .partial }

    /// Las 3 razones de mayor impacto (la API ya las devuelve ordenadas).
    var topReasons: [ScoreReason] { (reasons ?? []).prefix(3).map(ScoreReason.init(raw:)) }

    enum CodingKeys: String, CodingKey {
        case id
        case status = "estado"
        case decision, score
        case band = "banda"
        case reasons = "razones"
        case modelVersion
        case simulated = "simulado"
        case correlationId
    }

    init(
        id: UUID?, status: AssessmentStatus?, decision: PolicyDecision?, score: Int?, band: ScoreBand?,
        reasons: [String]?, modelVersion: String?, simulated: Bool, correlationId: String?
    ) {
        self.id = id
        self.status = status
        self.decision = decision
        self.score = score
        self.band = band
        self.reasons = reasons
        self.modelVersion = modelVersion
        self.simulated = simulated
        self.correlationId = correlationId
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id)
        status = try container.decodeIfPresent(AssessmentStatus.self, forKey: .status)
        decision = try container.decodeIfPresent(PolicyDecision.self, forKey: .decision)
        score = try container.decodeIfPresent(Int.self, forKey: .score)
        band = try container.decodeIfPresent(ScoreBand.self, forKey: .band)
        reasons = try container.decodeIfPresent([String].self, forKey: .reasons)
        modelVersion = try container.decodeIfPresent(String.self, forKey: .modelVersion)
        // `simulado` tiene `default: true` en el contrato.
        simulated = try container.decodeIfPresent(Bool.self, forKey: .simulated) ?? true
        correlationId = try container.decodeIfPresent(String.self, forKey: .correlationId)
    }
}

nonisolated struct NewAssessment: Encodable, Sendable {
    let holderDocument: String
    let consentId: UUID
    let purpose: String?
    let requestedAmount: String?
    let termMonths: Int?

    enum CodingKeys: String, CodingKey {
        case holderDocument = "titularDocumento"
        case consentId
        case purpose = "proposito"
        case requestedAmount = "montoSolicitado"
        case termMonths = "plazoMeses"
    }
}

/// Una evaluación en un listado. El estado es la decisión (nunca un rechazo automático).
nonisolated struct AssessmentSummary: Codable, Hashable, Identifiable, Sendable {
    let id: UUID
    let applicantId: UUID
    let status: AssessmentStatus
    let decision: PolicyDecision?
    let score: Int
    let band: ScoreBand
    let requestedAmount: String?
    let termMonths: Int?
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case applicantId = "solicitanteId"
        case status = "estado"
        case decision, score
        case band = "banda"
        case requestedAmount = "montoSolicitado"
        case termMonths = "plazoMeses"
        case createdAt = "creadoEn"
    }
}

/// Razón del score tal como la expone el scorecard (p. ej. `MORA_1_30_DIAS` o `MORA_1_30_DIAS (-80)`).
nonisolated struct ScoreReason: Hashable, Identifiable, Sendable {
    let code: String
    let points: Int?

    var id: String { code + (points.map { "\($0)" } ?? "") }

    init(code: String, points: Int?) {
        self.code = code
        self.points = points
    }

    /// Acepta el código solo o seguido de su impacto en puntos entre paréntesis o tras dos puntos.
    init(raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        let separators = CharacterSet(charactersIn: "(:")
        guard let range = trimmed.rangeOfCharacter(from: separators) else {
            self.init(code: trimmed, points: nil)
            return
        }
        let code = trimmed[..<range.lowerBound].trimmingCharacters(in: .whitespaces)
        let tail = trimmed[range.upperBound...].filter { $0 == "-" || $0 == "+" || $0.isNumber }
        self.init(code: code, points: Int(tail))
    }
}
