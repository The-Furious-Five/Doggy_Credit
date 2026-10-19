import Foundation

/// Una evaluación que las políticas dejaron en revisión manual (RF-DEC-03).
/// Nunca es un rechazo automático: aprobar o rechazar lo decide una persona.
nonisolated struct ReviewCase: Codable, Hashable, Identifiable, Sendable {
    let assessmentId: UUID
    let applicantId: UUID
    let document: String
    let createdAt: Date
    let score: Int
    let band: ScoreBand
    let motive: Motive
    let partialResult: Bool
    let reasons: [String]
    let requestedAmount: String?
    let termMonths: Int?
    let purpose: String?

    var id: UUID { assessmentId }
    var topReasons: [ScoreReason] { reasons.prefix(3).map(ScoreReason.init(raw:)) }

    enum Motive: String, Codable, Sendable {
        case manualReview = "REVISION_MANUAL"
        case requestData = "SOLICITAR_DATOS"
    }

    enum CodingKeys: String, CodingKey {
        case assessmentId = "evaluacionId"
        case applicantId = "solicitanteId"
        case document = "documento"
        case createdAt = "creadoEn"
        case score
        case band = "banda"
        case motive = "motivo"
        case partialResult = "resultadoParcial"
        case reasons = "razones"
        case requestedAmount = "montoSolicitado"
        case termMonths = "plazoMeses"
        case purpose = "proposito"
    }
}

nonisolated struct ReviewCasePage: Codable, Hashable, Sendable {
    let items: [ReviewCase]
    let total: Int
    /// Productos activos que se pueden elegir al aprobar (ANALYST no puede leer /productos).
    let products: [Product]

    enum CodingKeys: String, CodingKey {
        case items, total
        case products = "productos"
    }
}

nonisolated enum ResolutionDecision: String, Codable, CaseIterable, Identifiable, Sendable {
    case approve = "APROBADO"
    case reject = "RECHAZADO"

    var id: String { rawValue }
}

nonisolated struct NewResolution: Encodable, Sendable {
    /// La API exige al menos 10 caracteres de justificación.
    static let minimumJustificationLength = 10
    static let maximumJustificationLength = 1000

    let decision: ResolutionDecision
    /// Obligatorio al aprobar; se omite al rechazar.
    let productId: UUID?
    let justification: String

    enum CodingKeys: String, CodingKey {
        case decision
        case productId = "productoId"
        case justification = "justificacion"
    }
}

nonisolated struct Resolution: Codable, Hashable, Sendable {
    let assessmentId: UUID
    let decision: ResolutionDecision
    let productId: UUID?
    let justification: String
    let resolvedBy: String
    let resolvedAt: Date

    enum CodingKeys: String, CodingKey {
        case assessmentId = "evaluacionId"
        case decision
        case productId = "productoId"
        case justification = "justificacion"
        case resolvedBy = "resueltoPor"
        case resolvedAt = "resueltoEn"
    }
}

/// Producto de crédito del catálogo del tenant.
nonisolated struct Product: Codable, Hashable, Identifiable, Sendable {
    let id: UUID
    let kind: ProductKind
    let currency: Currency
    let minimumAmount: String
    let maximumAmount: String
    let minimumTermMonths: Int
    let maximumTermMonths: Int
    let annualRate: String
    let minimumBand: ScoreBand
    let active: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case kind = "tipo"
        case currency = "moneda"
        case minimumAmount = "montoMinimo"
        case maximumAmount = "montoMaximo"
        case minimumTermMonths = "plazoMinimoMeses"
        case maximumTermMonths = "plazoMaximoMeses"
        case annualRate = "tasaAnual"
        case minimumBand = "bandaMinima"
        case active = "activo"
    }
}
