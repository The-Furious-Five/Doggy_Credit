import Foundation

/// Solicitante del tenant con sus datos declarados.
nonisolated struct Applicant: Codable, Hashable, Identifiable, Sendable {
    let id: UUID
    let document: String
    let activity: String
    let activitySeniorityMonths: Int
    let monthlyIncome: String
    let currency: Currency
    let creditPurpose: String
    /// En el alta es PERSONA si no se indica; el contrato no lo exige en la respuesta.
    let segment: Segment?
    let createdAt: Date

    var resolvedSegment: Segment { segment ?? .person }

    enum CodingKeys: String, CodingKey {
        case id
        case document = "documento"
        case activity = "actividad"
        case activitySeniorityMonths = "antiguedadActividadMeses"
        case monthlyIncome = "ingresosMensuales"
        case currency = "moneda"
        case creditPurpose = "destinoCredito"
        case segment = "segmento"
        case createdAt = "creadoEn"
    }
}

nonisolated struct NewApplicant: Encodable, Sendable {
    let document: String
    let activity: String
    let activitySeniorityMonths: Int
    let monthlyIncome: String
    let currency: Currency
    let creditPurpose: String
    let segment: Segment

    enum CodingKeys: String, CodingKey {
        case document = "documento"
        case activity = "actividad"
        case activitySeniorityMonths = "antiguedadActividadMeses"
        case monthlyIncome = "ingresosMensuales"
        case currency = "moneda"
        case creditPurpose = "destinoCredito"
        case segment = "segmento"
    }
}

/// Un solicitante con su Perfil Financiero 360 más reciente (`SolicitanteDetalle`, allOf plano).
nonisolated struct ApplicantDetail: Hashable, Sendable {
    let applicant: Applicant
    let profile: FinancialProfile?
}

nonisolated extension ApplicantDetail: Decodable {
    private enum CodingKeys: String, CodingKey {
        case profile = "perfil"
    }

    init(from decoder: any Decoder) throws {
        applicant = try Applicant(from: decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)
        profile = try container.decodeIfPresent(FinancialProfile.self, forKey: .profile)
    }
}

/// Resumen del Perfil Financiero 360. Un campo ausente significa desconocido
/// (la fuente no respondió o no se consultó), nunca cero.
nonisolated struct FinancialProfile: Codable, Hashable, Sendable {
    let id: UUID
    let hash: String
    let createdAt: Date
    let monthlyIncome: Money
    let incomeOrigin: IncomeOrigin
    let outstandingDebt: Money?
    let obligations: Int?
    let monthlyInstallment: Money?
    let debtRatioPercent: String?
    let paymentCapacity: Money?
    let financialSystemSeniorityMonths: Int?
    let sources: [ProfileSource]

    enum IncomeOrigin: String, Codable, Sendable {
        case bank = "BANCO"
        case wallet = "WALLET"
        case declared = "DECLARADO"
    }

    enum CodingKeys: String, CodingKey {
        case id, hash
        case createdAt = "creadoEn"
        case monthlyIncome = "ingresoMensual"
        case incomeOrigin = "origenIngreso"
        case outstandingDebt = "deudaVigente"
        case obligations = "obligaciones"
        case monthlyInstallment = "cuotaMensual"
        case debtRatioPercent = "endeudamientoPorcentaje"
        case paymentCapacity = "capacidadPago"
        case financialSystemSeniorityMonths = "antiguedadSistemaFinancieroMeses"
        case sources = "fuentes"
    }
}

nonisolated struct ProfileSource: Codable, Hashable, Identifiable, Sendable {
    let source: DataSource
    let state: State
    let queriedAt: Date?
    let sha256: String?

    var id: DataSource { source }

    enum State: String, Codable, Sendable {
        case complete = "COMPLETA"
        case noRecord = "SIN_REGISTRO"
        case unavailable = "NO_DISPONIBLE"
        case notQueried = "NO_CONSULTADA"
    }

    enum CodingKeys: String, CodingKey {
        case source = "fuente"
        case state = "estado"
        case queriedAt = "consultadaEn"
        case sha256
    }
}
